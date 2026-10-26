package ar.edu.utn.vastio.agenda.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import ar.edu.utn.vastio.agenda.aplicacion.AvisosEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Registrar asistencia real de cierre (UI-20, docs/sprint-4.md, decisiones 6 a 9) y su uso como divisor del consumo por
 * asistente. Segmentos de V2: 1 Comensales cena, 2 Entradas anticipadas, 3 Venta en puerta. Tipo 4: Egresados.
 */
@PruebaDeIntegracion
@Transactional
class AsistenciaRealIT {

    private static final LocalDate FECHA = LocalDate.of(2033, 11, 19);

    /** Los tres segmentos: 132 + 58 + 14 = 204. */
    private static final String COMPLETA = """
            [{"segmentoId":1,"cantidad":132},{"segmentoId":2,"cantidad":58},{"segmentoId":3,"cantidad":14}]""";

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

    @Autowired
    AvisosEvento avisos;

    Usuario lucia;
    Usuario ana;
    long evento;

    @BeforeEach
    void egresadosRealizado() {
        lucia = personas.usuario("lucia.real", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        ana = personas.usuario("ana.real", "Ana Sosa", RolCodigo.PLANNER);
        evento = escenario.evento("avril", FECHA, "noche", "REALIZADO", lucia, ana, "Egresados 5to B");
        jdbc.update("UPDATE evento SET tipo_evento_id = 4, cantidad_invitados = 140 WHERE evento_id = ?", evento);
        jdbc.update("INSERT INTO asistencia_segmento (evento_id, tipo_segmento_id, cantidad_prevista) VALUES (?, 2, 60)", evento);
    }

    @Test
    void laPlannerCargaLaRealDeTodosLosSegmentos() throws Exception {
        ficha(ana, evento)
                .andExpect(jsonPath("$.acciones.registrarAsistenciaReal").value(true))
                .andExpect(jsonPath("$.acciones.registrarAsistenciaPrevista").value(false))
                .andExpect(jsonPath("$.asistencia.totalReal").value(nullValue()));

        registrar(ana, evento, COMPLETA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asistencia.segmentos[0].real").value(132))
                .andExpect(jsonPath("$.asistencia.segmentos[1].prevista").value(60))
                .andExpect(jsonPath("$.asistencia.segmentos[1].real").value(58))
                .andExpect(jsonPath("$.asistencia.totalReal").value(204))
                .andExpect(jsonPath("$.historial[?(@.campo == 'asistencia_real.Venta en puerta')].valorNuevo", hasItem("14")));
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM modificacion_evento WHERE evento_id = ?", Integer.class, evento))
                .isEqualTo(3);
    }

    @Test
    void faltaUnSegmentoYNoSeGuarda() throws Exception {
        registrar(ana, evento, """
                [{"segmentoId":1,"cantidad":132},{"segmentoId":2,"cantidad":58}]""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ASISTENCIA_INCOMPLETA"))
                .andExpect(jsonPath("$.detail").value(
                        "Cargá la asistencia real de todos los segmentos (0 si no vino nadie). Falta: Venta en puerta."));
    }

    @Test
    void conElEventoCerradoTambienSeCarga() throws Exception {
        jdbc.update("UPDATE evento SET estado = 'CERRADO' WHERE evento_id = ?", evento);

        registrar(ana, evento, COMPLETA).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONFIRMADO", "EN_CURSO"})
    void antesDeTerminarLaJornadaNoSeCarga(String estado) throws Exception {
        jdbc.update("UPDATE evento SET estado = ? WHERE evento_id = ?", estado, evento);

        registrar(ana, evento, COMPLETA)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ESTADO_NO_PERMITE"));
    }

    @Test
    void quienesPuedenCargarla() throws Exception {
        registrar(personas.de(RolCodigo.COORDINACION), evento, COMPLETA).andExpect(status().isOk());
        entityManager.flush();
        registrar(lucia, evento, COMPLETA).andExpect(status().isForbidden());
        registrar(personas.usuario("carla.real", "Carla Núñez", RolCodigo.PLANNER), evento, COMPLETA)
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"ADMINISTRACION", "COMPRAS", "BARRA", "COCINA"})
    void elRestoDeLosPerfilesNoLaCarga(RolCodigo rol) throws Exception {
        registrar(personas.de(rol), evento, COMPLETA).andExpect(status().isForbidden());
    }

    @Test
    void alPasarARealizadoSeLeAvisaALaPlanner() {
        Evento realizado = entityManager.find(Evento.class, evento);

        avisos.cambioDeEstado(realizado, null);
        entityManager.flush();

        assertThat(jdbc.queryForList("""
                SELECT n.mensaje FROM notificacion_destinatario d JOIN notificacion n USING (notificacion_id)
                WHERE n.evento_id = ? AND n.tipo = 'ASISTENCIA_REAL' AND d.usuario_id = ?""", String.class, evento, ana.getId()))
                .singleElement().asString().startsWith("Cargá la asistencia real de cierre: ");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM notificacion_destinatario d JOIN notificacion n USING (notificacion_id)
                WHERE n.evento_id = ? AND n.tipo = 'ASISTENCIA_REAL'""", Integer.class, evento)).isEqualTo(1);
    }

    @Test
    void unEventoSinSegmentosNoAvisaAlRealizarse() {
        long quince = escenario.evento("club", FECHA, "noche", "REALIZADO", lucia, ana, "Quince");

        avisos.cambioDeEstado(entityManager.find(Evento.class, quince), null);
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE evento_id = ?", Integer.class, quince)).isZero();
    }

    @Test
    void elCierreYElConsumoDividenPorLaAsistenciaRealCuandoEstaCompleta() throws Exception {
        jdbc.update("UPDATE evento SET estado = 'CERRADO' WHERE evento_id = ?", evento);
        long bebida = jdbc.queryForObject("""
                INSERT INTO bebida (nombre, presentacion, tipo_bebida_id, unidad_manipulacion_id, unidades_por_bulto, activo)
                VALUES ('Fernet asistencia', '750 ml', 3, 1, 6, true) RETURNING bebida_id""", Long.class);
        jdbc.update("""
                INSERT INTO consumo_evento (evento_id, bebida_id, cantidad_entregada, cantidad_devuelta, cantidad_remanente, cantidad_consumida)
                VALUES (?, ?, 60, 0, 9, 51)""", evento, bebida);
        Usuario compras = personas.de(RolCodigo.COMPRAS);

        // Sin la real: 51 / 140 invitados.
        consumo(compras).andExpect(jsonPath("$.denominador").value("INVITADOS"))
                .andExpect(jsonPath("$.porInvitadoTotal").value(0.3643));
        cierre(compras).andExpect(jsonPath("$.asistenciaReal").value(nullValue()))
                .andExpect(jsonPath("$.porAsistenteTotal").value(0.3643));

        registrar(ana, evento, COMPLETA).andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        // Con la real: 51 / 204.
        consumo(compras).andExpect(jsonPath("$.denominador").value("ASISTENCIA_REAL"))
                .andExpect(jsonPath("$.asistenciaReal").value(204))
                .andExpect(jsonPath("$.porInvitadoTotal").value(0.25));
        cierre(compras).andExpect(jsonPath("$.asistenciaReal").value(204))
                .andExpect(jsonPath("$.porAsistenteTotal").value(0.25))
                .andExpect(jsonPath("$.renglones[0].porAsistente").value(0.25));
        mvc.perform(get("/api/v1/estadisticas/consumo?desde=2033-11-19&hasta=2033-11-19")
                        .header(HttpHeaders.AUTHORIZATION, personas.bearer(compras)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.denominador").value("ASISTENTES"))
                .andExpect(jsonPath("$.resumen.invitadosRegistrados").value(204))
                .andExpect(jsonPath("$.resumen.eventosConAsistenciaReal").value(1));
    }

    private ResultActions consumo(Usuario quien) throws Exception {
        return mvc.perform(get("/api/v1/eventos/{id}/consumo", evento).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)))
                .andExpect(status().isOk());
    }

    private ResultActions cierre(Usuario quien) throws Exception {
        return mvc.perform(get("/api/v1/eventos/{id}/cierre", evento).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)))
                .andExpect(status().isOk());
    }

    private ResultActions ficha(Usuario quien, long id) throws Exception {
        return mvc.perform(get("/api/v1/eventos/{id}", id).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)))
                .andExpect(status().isOk());
    }

    private ResultActions registrar(Usuario quien, long id, String segmentos) throws Exception {
        int version = jdbc.queryForObject("SELECT version FROM evento WHERE evento_id = ?", Integer.class, id);
        return mvc.perform(put("/api/v1/eventos/{id}/asistencia/real", id).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"version":%d,"segmentos":%s}""".formatted(version, segmentos)));
    }
}
