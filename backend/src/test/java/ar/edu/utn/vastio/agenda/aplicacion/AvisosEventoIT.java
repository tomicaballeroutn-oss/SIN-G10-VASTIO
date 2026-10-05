package ar.edu.utn.vastio.agenda.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.Escenario;
import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Notificar cambios de evento: quién recibe cada aviso (docs/sprint-2.md, decisión 10).
 */
@PruebaDeIntegracion
@Transactional
class AvisosEventoIT {

    private static final LocalDate FECHA = LocalDate.of(2033, 3, 12);

    @Autowired
    AvisosEvento avisos;

    @Autowired
    MockMvc mvc;

    @Autowired
    Personas personas;

    @Autowired
    Escenario escenario;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    Usuario lucia;
    Usuario ana;
    Usuario direccion;
    Usuario coordinacion;
    Usuario administracion;
    Usuario compras;
    Usuario cocina;
    Usuario barra;

    @BeforeEach
    void personas() {
        lucia = personas.usuario("lucia.avisos", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        ana = personas.usuario("ana.avisos", "Ana Sosa", RolCodigo.PLANNER);
        direccion = personas.de(RolCodigo.DIRECCION);
        coordinacion = personas.de(RolCodigo.COORDINACION);
        administracion = personas.de(RolCodigo.ADMINISTRACION);
        compras = personas.de(RolCodigo.COMPRAS);
        cocina = personas.de(RolCodigo.COCINA);
        barra = personas.de(RolCodigo.BARRA);
    }

    @Test
    void elContratoLeLlegaAAdministracionCoordinacionYLaTitular() {
        Evento evento = evento("CONTRATADO", ana);

        avisos.cambioDeEstado(evento, direccion.getId());

        assertThat(destinatarios(evento, "CONTRATO"))
                .contains(administracion.getId(), coordinacion.getId(), lucia.getId())
                .doesNotContain(direccion.getId(), ana.getId(), compras.getId(), cocina.getId(), barra.getId());
    }

    @Test
    void laConfirmacionLeLlegaAComprasCocinaAdministracionYLaTitular() {
        Evento evento = evento("CONFIRMADO", ana);

        avisos.cambioDeEstado(evento, ana.getId());

        assertThat(destinatarios(evento, "CONFIRMACION"))
                .contains(compras.getId(), cocina.getId(), administracion.getId(), lucia.getId())
                .doesNotContain(ana.getId(), coordinacion.getId(), barra.getId());
    }

    @Test
    void laCancelacionLeLlegaATodasLasAreasMenosBarra() {
        Evento evento = evento("CANCELADO", ana);

        avisos.cambioDeEstado(evento, coordinacion.getId());

        assertThat(destinatarios(evento, "CANCELACION"))
                .contains(direccion.getId(), administracion.getId(), compras.getId(), cocina.getId(), lucia.getId(), ana.getId())
                .doesNotContain(coordinacion.getId(), barra.getId());
    }

    @Test
    void liberarUnaPreReservaNoAvisa() {
        Evento evento = evento("LIBERADA", null);

        avisos.cambioDeEstado(evento, lucia.getId());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE evento_id = ?", Integer.class, evento.getId()))
                .isZero();
    }

    @Test
    void unaModificacionLeLlegaALaTitularLaPlannerCoordinacionYAdministracion() {
        Evento evento = evento("CONTRATADO", ana);

        avisos.modificacion(evento, Set.of("cantidad_invitados"), direccion.getId());

        assertThat(destinatarios(evento, "MODIFICACION"))
                .contains(lucia.getId(), ana.getId(), coordinacion.getId(), administracion.getId())
                .doesNotContain(direccion.getId(), compras.getId(), cocina.getId());
    }

    @Test
    void conElEventoConfirmadoLosInvitadosTambienLeLleganACocinaYCompras() {
        Evento evento = evento("CONFIRMADO", ana);

        avisos.modificacion(evento, Set.of("cantidad_invitados"), ana.getId());

        assertThat(destinatarios(evento, "MODIFICACION")).contains(cocina.getId(), compras.getId()).doesNotContain(ana.getId());
    }

    @Test
    void conElEventoConfirmadoLaHoraDeInicioLeLlegaACocinaPeroNoACompras() {
        Evento evento = evento("CONFIRMADO", ana);

        avisos.modificacion(evento, Set.of("hora_inicio"), ana.getId());

        assertThat(destinatarios(evento, "MODIFICACION")).contains(cocina.getId()).doesNotContain(compras.getId());
    }

    @Test
    void lasObservacionesInternasNuncaLeLleganACocina() {
        Evento evento = evento("CONFIRMADO", ana);

        avisos.modificacion(evento, Set.of("observaciones_internas", "contacto"), ana.getId());

        assertThat(destinatarios(evento, "MODIFICACION")).doesNotContain(cocina.getId(), compras.getId());
    }

    @Test
    void losServiciosDeCocinaYDeBebidaLeLleganASuArea() {
        Evento evento = evento("CONFIRMADO", ana);

        avisos.modificacion(evento, Set.of("servicio.Postre"), true, false, ana.getId());
        assertThat(destinatarios(evento, "MODIFICACION")).contains(cocina.getId()).doesNotContain(compras.getId());

        avisos.modificacion(evento, Set.of("servicio.Bodega"), false, true, ana.getId());
        assertThat(destinatarios(evento, "MODIFICACION")).contains(compras.getId()).doesNotContain(cocina.getId());
    }

    @Test
    void antesDeConfirmarCocinaYComprasNoRecibenModificaciones() {
        Evento evento = evento("CONTRATADO", ana);

        avisos.modificacion(evento, Set.of("cantidad_invitados"), true, true, ana.getId());

        assertThat(destinatarios(evento, "MODIFICACION")).doesNotContain(cocina.getId(), compras.getId());
    }

    @Test
    void sinCambiosNoHayAviso() {
        Evento evento = evento("SENADO", null);

        avisos.modificacion(evento, Set.of(), lucia.getId());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE evento_id = ?", Integer.class, evento.getId()))
                .isZero();
    }

    @Test
    void laReprogramacionDiceLaFechaAnterior() {
        Evento evento = evento("CONTRATADO", ana);

        avisos.reprogramacion(evento, "sáb 5/3 · Noche", coordinacion.getId());

        assertThat(jdbc.queryForObject("SELECT mensaje FROM notificacion WHERE evento_id = ? AND tipo = 'REPROGRAMACION'",
                String.class, evento.getId()))
                .startsWith("Evento reprogramado: Bruno y Martina · Avril")
                .endsWith("(antes sáb 5/3 · Noche)");
        assertThat(destinatarios(evento, "REPROGRAMACION"))
                .contains(direccion.getId(), administracion.getId(), compras.getId(), cocina.getId(), lucia.getId(), ana.getId())
                .doesNotContain(coordinacion.getId(), barra.getId());
    }

    @Test
    void laPlannerAsignadaLeLlegaALaNuevaALaAnteriorYAAdministracion() {
        Usuario carla = personas.usuario("carla.avisos", "Carla Núñez", RolCodigo.PLANNER);
        Evento evento = evento("CONTRATADO", carla);

        avisos.planner(evento, ana.getId(), "Carla Núñez", coordinacion.getId());

        assertThat(jdbc.queryForObject("SELECT mensaje FROM notificacion WHERE evento_id = ? AND tipo = 'PLANNER_ASIGNADA'",
                String.class, evento.getId())).startsWith("Planner asignada (Carla Núñez): Bruno y Martina");
        assertThat(destinatarios(evento, "PLANNER_ASIGNADA"))
                .contains(carla.getId(), ana.getId(), administracion.getId())
                .doesNotContain(coordinacion.getId(), lucia.getId());
    }

    @Test
    void unaPersonaDadaDeBajaNoRecibeAvisos() {
        Evento evento = evento("CONTRATADO", ana);
        jdbc.update("UPDATE usuario SET activo = false, fecha_baja = now() WHERE usuario_id = ?", ana.getId());
        entityManager.clear();
        evento = entityManager.find(Evento.class, evento.getId());

        avisos.modificacion(evento, Set.of("nombre"), coordinacion.getId());

        assertThat(destinatarios(evento, "MODIFICACION")).contains(lucia.getId()).doesNotContain(ana.getId());
    }

    @Test
    void registrarEventoAvisaALaTitularCuandoModificaOtraPersona() throws Exception {
        long id = escenario.evento("club", FECHA, "noche", "SENADO", lucia, "Quince de Delfina");

        guardar(id, coordinacion, "Los 15 de Delfina").andExpect(status().isOk());
        entityManager.flush();
        assertThat(destinatarios(id, "MODIFICACION")).contains(lucia.getId(), administracion.getId()).doesNotContain(coordinacion.getId());

        guardar(id, lucia, "Quince de Delfi").andExpect(status().isOk());
        entityManager.flush();
        assertThat(destinatarios(id, "MODIFICACION")).contains(coordinacion.getId()).doesNotContain(lucia.getId());
    }

    private ResultActions guardar(long id, Usuario quien, String nombre) throws Exception {
        int version = jdbc.queryForObject("SELECT version FROM evento WHERE evento_id = ?", Integer.class, id);
        return mvc.perform(put("/api/v1/eventos/{id}", id).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"version":%d,"nombre":"%s","tipoEventoId":2,"cliente":{"nombre":"Cliente de Quince de Delfina"},"contactos":[]}"""
                        .formatted(version, nombre)));
    }

    private Evento evento(String estado, Usuario planner) {
        long id = escenario.evento("avril", FECHA, "noche", estado, lucia, planner, "Bruno y Martina");
        return entityManager.find(Evento.class, id);
    }

    /** Destinatarios del último aviso de ese tipo. */
    private List<Long> destinatarios(Evento evento, String tipo) {
        entityManager.flush();
        return destinatarios(evento.getId(), tipo);
    }

    private List<Long> destinatarios(long eventoId, String tipo) {
        return jdbc.queryForList("""
                SELECT d.usuario_id FROM notificacion_destinatario d
                WHERE d.notificacion_id = (SELECT max(notificacion_id) FROM notificacion WHERE evento_id = ? AND tipo = ?)""",
                Long.class, eventoId, tipo);
    }
}
