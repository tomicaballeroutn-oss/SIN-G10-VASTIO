package ar.edu.utn.vastio.agenda.aplicacion;

import static org.assertj.core.api.Assertions.*;

import java.lang.management.ManagementFactory;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import ar.edu.utn.vastio.*;
import ar.edu.utn.vastio.agenda.dominio.Bloqueo;
import ar.edu.utn.vastio.agenda.dominio.UnidadComercializable;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.usuarios.dominio.*;
import ar.edu.utn.vastio.usuarios.infraestructura.UsuarioRepository;

/** Commit/rollback reales; no se comparte la transacción del test con el servicio. */
@PruebaDeIntegracion
class BloqueoAuditoriaIT {
    static final AtomicInteger CASO = new AtomicInteger();
    @Autowired BloqueoService bloqueos;
    @Autowired Personas personas;
    @Autowired Escenario escenario;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired UsuarioRepository usuarios;
    @Autowired EntityManagerFactory emf;
    LocalDate fecha;
    Usuario admin, vendedora;
    UsuarioActual quien;

    @BeforeEach void datos() {
        fecha = LocalDate.of(2044, 1, 1).plusDays(CASO.incrementAndGet() * 400L);
        String sufijo = UUID.randomUUID().toString().substring(0, 12);
        admin = personas.usuario("audit50.a." + sufijo, "Administración auditoría", RolCodigo.ADMINISTRACION);
        vendedora = personas.usuario("audit50.v." + sufijo, "Vendedora auditoría", RolCodigo.VENDEDORA);
        admin.setDebeCambiarContrasena(false);
        vendedora.setDebeCambiarContrasena(false);
        usuarios.save(admin);
        usuarios.save(vendedora);
        quien = new UsuarioActual(admin.getId(), Set.of("ADMINISTRACION"));
    }

    BloqueoService.Pedido pedido(Set<Short> salones, int dias, Set<Short> turnos) {
        return new BloqueoService.Pedido(salones, fecha, fecha.plusDays(dias - 1L), turnos, (short) 7, "Auditoría A1-02");
    }

    int contar(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class);
    }

    @ParameterizedTest
    @CsvSource({"0,true", "3,true", "11,true", "0,false", "3,false", "11,false"})
    void unConflictoAlInicioMedioOFinalNoDejaNingunaFilaParcial(int posicion, boolean evento) {
        short salon = (short) (1 + posicion / 6);
        LocalDate dia = fecha.plusDays((posicion % 6) / 2);
        String turno = posicion % 2 == 0 ? "mediodia" : "noche";
        String codigo = salon == 1 ? "avril" : "club";
        if (evento) escenario.evento(codigo, dia, turno, "PRE_RESERVA", vendedora, "Auditoría conflicto");
        else escenario.bloqueo(codigo, dia, turno, admin, "Existente");
        int unidadesAntes = contar("unidad_comercializable");
        int bloqueosAntes = contar("bloqueo");
        int relacionesAntes = contar("bloqueo_unidad");

        assertThatThrownBy(() -> bloqueos.registrar(pedido(Set.of((short) 1, (short) 2), 3, Set.of((short) 1, (short) 2)), quien))
                .isInstanceOfSatisfying(ProblemaException.class, e ->
                        assertThat(e.getCodigo()).isEqualTo(evento ? "UNIDAD_OCUPADA" : "YA_BLOQUEADA"));

        assertThat(contar("unidad_comercializable")).isEqualTo(unidadesAntes);
        assertThat(contar("bloqueo")).isEqualTo(bloqueosAntes);
        assertThat(contar("bloqueo_unidad")).isEqualTo(relacionesAntes);
    }

    @Test void unFalloPosteriorAlAltaRevierteCabeceraUnidadesYRelaciones() {
        int unidadesAntes = contar("unidad_comercializable");
        int bloqueosAntes = contar("bloqueo");
        int relacionesAntes = contar("bloqueo_unidad");
        assertThatThrownBy(() -> tx.executeWithoutResult(t -> {
            bloqueos.registrar(pedido(Set.of((short) 1, (short) 2), 3, Set.of((short) 1, (short) 2)), quien);
            throw new IllegalStateException("Fallo posterior");
        })).hasMessage("Fallo posterior");
        assertThat(contar("unidad_comercializable")).isEqualTo(unidadesAntes);
        assertThat(contar("bloqueo")).isEqualTo(bloqueosAntes);
        assertThat(contar("bloqueo_unidad")).isEqualTo(relacionesAntes);
        var creado = bloqueos.registrar(pedido(Set.of((short) 1, (short) 2), 3, Set.of((short) 1, (short) 2)), quien);
        assertThat(creado.cantidadUnidades()).isEqualTo(12);
        assertThat(creado.registradoPorId()).isEqualTo(admin.getId());
        assertThat(creado.fechaRegistro()).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({"1,1,180", "1,2,360", "3,2,1080"})
    void mideAltaYListadoDe180Dias(int salones, int turnos, int cantidad) {
        Set<Short> s = salones == 1 ? Set.of((short) 1) : Set.of((short) 1, (short) 2, (short) 3);
        Set<Short> t = turnos == 1 ? Set.of((short) 1) : Set.of((short) 1, (short) 2);
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        boolean anterior = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        try {
            stats.clear();
            long memoria = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            long inicio = System.nanoTime();
            var creado = bloqueos.registrar(pedido(s, 180, t), quien);
            double ms = (System.nanoTime() - inicio) / 1_000_000d;
            long delta = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() - memoria;
            System.out.printf("AUDIT50_ALTA unidades=%d ms=%.2f sql=%d heap_delta_bytes=%d%n",
                    cantidad, ms, stats.getPrepareStatementCount(), delta);
            assertThat(creado.cantidadUnidades()).isEqualTo(cantidad);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM bloqueo_unidad WHERE bloqueo_id=?", Integer.class, creado.id())).isEqualTo(cantidad);
            stats.clear();
            inicio = System.nanoTime();
            assertThat(bloqueos.activos()).extracting(BloqueoService.BloqueoVista::id).contains(creado.id());
            System.out.printf("AUDIT50_LISTA unidades=%d ms=%.2f sql=%d entidades=%d%n",
                    cantidad, (System.nanoTime() - inicio) / 1_000_000d, stats.getPrepareStatementCount(), stats.getEntityLoadCount());
        } finally {
            stats.setStatisticsEnabled(anterior);
        }
    }

    @Test void elListadoNoMaterializaLasUnidadesDeBloqueosYaPasados() {
        LocalDate hoy = LocalDate.now(ZoneId.of(VastioApplication.ZONA_HORARIA));
        long pasado = jdbc.queryForObject("INSERT INTO bloqueo(motivo_id,usuario_id,activo) VALUES (7,?,true) RETURNING bloqueo_id", Long.class, admin.getId());
        jdbc.update("""
                INSERT INTO unidad_comercializable(salon_id,fecha,turno_id)
                SELECT s, f::date, t FROM generate_series(1,3) s,
                generate_series(?::date,?::date,interval '1 day') f, generate_series(1,2) t
                ON CONFLICT DO NOTHING
                """, hoy.minusDays(180), hoy.minusDays(1));
        jdbc.update("""
                INSERT INTO bloqueo_unidad(bloqueo_id,unidad_id)
                SELECT ?, unidad_id FROM unidad_comercializable WHERE fecha BETWEEN ? AND ?
                """, pasado, hoy.minusDays(180), hoy.minusDays(1));
        var vigente = bloqueos.registrar(pedido(Set.of((short) 1), 1, Set.of((short) 1)), quien);
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        boolean anterior = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        stats.clear();
        try {
            long inicio = System.nanoTime();
            var lista = bloqueos.activos();
            long cargadas = stats.getEntityStatistics(UnidadComercializable.class.getName()).getLoadCount();
            System.out.printf("AUDIT50_LISTA_HISTORIA ms=%.2f sql=%d unidades_cargadas=%d bloqueos_cargados=%d%n",
                    (System.nanoTime() - inicio) / 1_000_000d, stats.getPrepareStatementCount(), cargadas,
                    stats.getEntityStatistics(Bloqueo.class.getName()).getLoadCount());
            assertThat(lista).extracting(BloqueoService.BloqueoVista::id).contains(vigente.id()).doesNotContain(pasado);
            assertThat(cargadas).as("solo debe materializar las unidades de los bloqueos vigentes").isEqualTo(1);
        } finally {
            stats.setStatisticsEnabled(anterior);
        }
    }


    @Test void unBloqueoQueCruzaHoyConservaTodoSuRangoYLaHistoria() {
        LocalDate hoy = LocalDate.now(ZoneId.of(VastioApplication.ZONA_HORARIA));
        long id = jdbc.queryForObject("INSERT INTO bloqueo(motivo_id,usuario_id,activo) VALUES (7,?,true) RETURNING bloqueo_id", Long.class, admin.getId());
        jdbc.update("""
                INSERT INTO unidad_comercializable(salon_id,fecha,turno_id)
                SELECT 3,f::date,2 FROM generate_series(?::date,?::date,interval '1 day') f ON CONFLICT DO NOTHING
                """, hoy.minusDays(1), hoy.plusDays(1));
        jdbc.update("""
                INSERT INTO bloqueo_unidad(bloqueo_id,unidad_id)
                SELECT ?,unidad_id FROM unidad_comercializable WHERE salon_id=3 AND turno_id=2 AND fecha BETWEEN ? AND ?
                """, id, hoy.minusDays(1), hoy.plusDays(1));
        var vista = bloqueos.activos().stream().filter(b -> b.id() == id).findFirst().orElseThrow();
        assertThat(vista.desde()).isEqualTo(hoy.minusDays(1));
        assertThat(vista.hasta()).isEqualTo(hoy.plusDays(1));
        assertThat(vista.cantidadUnidades()).isEqualTo(3);
        bloqueos.desbloquear(id, quien);
        assertThat(bloqueos.activos()).extracting(BloqueoService.BloqueoVista::id).doesNotContain(id);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bloqueo_unidad WHERE bloqueo_id=?", Integer.class, id)).isEqualTo(3);
    }

    @AfterEach void limpiar() {
        tx.executeWithoutResult(t -> {
            jdbc.execute("SET LOCAL session_replication_role=replica");
            String eventos = "SELECT evento_id FROM evento WHERE vendedora_id=" + vendedora.getId();
            jdbc.update("DELETE FROM notificacion_destinatario WHERE notificacion_id IN (SELECT notificacion_id FROM notificacion WHERE evento_id IN (" + eventos + "))");
            jdbc.update("DELETE FROM notificacion WHERE evento_id IN (" + eventos + ")");
            jdbc.update("DELETE FROM cambio_estado_evento WHERE evento_id IN (" + eventos + ")");
            jdbc.update("DELETE FROM evento WHERE vendedora_id=?", vendedora.getId());
            jdbc.update("DELETE FROM bloqueo_unidad WHERE bloqueo_id IN (SELECT bloqueo_id FROM bloqueo WHERE usuario_id=?)", admin.getId());
            jdbc.update("DELETE FROM bloqueo WHERE usuario_id=?", admin.getId());
            jdbc.update("DELETE FROM unidad_comercializable WHERE fecha BETWEEN ? AND ? AND NOT EXISTS (SELECT 1 FROM evento e WHERE e.unidad_id=unidad_comercializable.unidad_id) AND NOT EXISTS (SELECT 1 FROM bloqueo_unidad b WHERE b.unidad_id=unidad_comercializable.unidad_id)", fecha.minusDays(1), fecha.plusDays(180));
        });
    }
}
