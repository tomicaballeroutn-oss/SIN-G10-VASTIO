package ar.edu.utn.vastio.agenda.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Registrar tipo de asistencia: la asistencia prevista por segmento de un evento de egresados (UI-19, docs/sprint-4.md,
 * decisiones 1 a 5). Segmentos de V2: 1 Comensales cena (equivale a los invitados), 2 Entradas anticipadas, 3 Venta en
 * puerta. Tipo de evento 4: Egresados.
 */
@PruebaDeIntegracion
@Transactional
class AsistenciaPrevistaIT {

    private static final LocalDate FECHA = LocalDate.of(2032, 11, 20);

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
    long evento;

    @BeforeEach
    void egresados() {
        lucia = personas.usuario("lucia.asistencia", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        ana = personas.usuario("ana.asistencia", "Ana Sosa", RolCodigo.PLANNER);
        evento = escenario.evento("avril", FECHA, "noche", "CONTRATADO", lucia, ana, "Egresados 5to B");
        jdbc.update("UPDATE evento SET tipo_evento_id = 4, cantidad_invitados = 140, invitados_definitivos = true WHERE evento_id = ?",
                evento);
    }

    @Test
    void laFichaMuestraLosSegmentosConLosComensalesTomadosDeLosInvitados() throws Exception {
        ficha(ana, evento)
                .andExpect(jsonPath("$.asistencia.segmentos.length()").value(3))
                .andExpect(jsonPath("$.asistencia.segmentos[0].nombre").value("Comensales cena"))
                .andExpect(jsonPath("$.asistencia.segmentos[0].equivaleInvitados").value(true))
                .andExpect(jsonPath("$.asistencia.segmentos[0].prevista").value(140))
                .andExpect(jsonPath("$.asistencia.segmentos[1].prevista").value(nullValue()))
                .andExpect(jsonPath("$.asistencia.totalPrevisto").value(140))
                .andExpect(jsonPath("$.acciones.registrarAsistenciaPrevista").value(true));
    }

    @Test
    void laPlannerRegistraLaPrevistaYElTotalSumaLosInvitados() throws Exception {
        registrar(ana, evento, """
                [{"segmentoId":2,"cantidad":60},{"segmentoId":3,"cantidad":null}]""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asistencia.segmentos[1].prevista").value(60))
                .andExpect(jsonPath("$.asistencia.totalPrevisto").value(200))
                .andExpect(jsonPath("$.historial[?(@.campo == 'asistencia_prevista.Entradas anticipadas')].valorNuevo", hasItem("60")))
                .andExpect(jsonPath("$.historial[?(@.campo == 'asistencia_prevista.Entradas anticipadas')].usuario.nombre",
                        hasItem("Ana Sosa")));
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM modificacion_evento WHERE evento_id = ?", Integer.class, evento))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT cantidad_prevista FROM asistencia_segmento WHERE evento_id = ? AND tipo_segmento_id = 2""",
                Integer.class, evento)).isEqualTo(60);
    }

    @Test
    void cambiarLaPrevistaDejaElValorAnteriorEnElHistorial() throws Exception {
        registrar(ana, evento, """
                [{"segmentoId":2,"cantidad":60}]""").andExpect(status().isOk());
        entityManager.flush();
        registrar(ana, evento, """
                [{"segmentoId":2,"cantidad":75}]""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historial[?(@.valorNuevo == '75')].valorAnterior", hasItem("60")))
                .andExpect(jsonPath("$.asistencia.totalPrevisto").value(215));
    }

    @Test
    void losComensalesNoSeCarganAparte() throws Exception {
        registrar(ana, evento, """
                [{"segmentoId":1,"cantidad":150}]""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SEGMENTO_INVALIDO"))
                .andExpect(jsonPath("$.detail").value(
                        "«Comensales cena» es la cantidad de invitados del evento: cambiala desde «Cantidad de invitados»."));
    }

    @Test
    void unTipoSinSegmentosNoTieneAsistencia() throws Exception {
        long quince = escenario.evento("club", FECHA, "noche", "CONTRATADO", lucia, ana, "Quince de Sofía");

        ficha(ana, quince)
                .andExpect(jsonPath("$.asistencia").value(nullValue()))
                .andExpect(jsonPath("$.acciones.registrarAsistenciaPrevista").value(false));
        registrar(ana, quince, """
                [{"segmentoId":2,"cantidad":60}]""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SIN_SEGMENTOS"));
    }

    @Test
    void enUnEventoConfirmadoAvisaACompras() throws Exception {
        jdbc.update("UPDATE evento SET estado = 'CONFIRMADO' WHERE evento_id = ?", evento);
        Usuario compras = personas.de(RolCodigo.COMPRAS);

        registrar(ana, evento, """
                [{"segmentoId":2,"cantidad":60}]""").andExpect(status().isOk());
        entityManager.flush();

        assertThat(jdbc.queryForList("""
                SELECT n.mensaje FROM notificacion_destinatario d JOIN notificacion n USING (notificacion_id)
                WHERE n.evento_id = ? AND n.tipo = 'MODIFICACION' AND d.usuario_id = ?""", String.class, evento, compras.getId()))
                .singleElement().asString().contains("cambió la asistencia prevista de Entradas anticipadas");
    }

    @Test
    void confirmarEgresadosPideLaAsistenciaPrevista() throws Exception {
        jdbc.update("""
                INSERT INTO servicio_contratado (evento_id, categoria_id, descripcion, usuario_id)
                SELECT ?, categoria_id, 'Cargado', ? FROM categoria_servicio WHERE requerida_para_confirmar AND activo""",
                evento, ana.getId());

        ficha(ana, evento)
                .andExpect(jsonPath("$.requisitosConfirmacion[?(@.codigo == 'ASISTENCIA')].cumplido", hasItem(false)));
        confirmar(ana, evento)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Todavía no se puede confirmar. Falta cargar la asistencia prevista por segmento."));

        registrar(ana, evento, """
                [{"segmentoId":2,"cantidad":60}]""").andExpect(status().isOk());
        entityManager.flush();
        confirmar(ana, evento)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"));
    }

    @Test
    void quienesPuedenRegistrarla() throws Exception {
        registrar(personas.de(RolCodigo.COORDINACION), evento, """
                [{"segmentoId":2,"cantidad":10}]""").andExpect(status().isOk());
        entityManager.flush();
        registrar(personas.de(RolCodigo.DIRECCION), evento, """
                [{"segmentoId":2,"cantidad":20}]""").andExpect(status().isOk());
        entityManager.flush();
        registrar(lucia, evento, """
                [{"segmentoId":2,"cantidad":30}]""").andExpect(status().isForbidden());
        registrar(personas.usuario("carla.asistencia", "Carla Núñez", RolCodigo.PLANNER), evento, """
                [{"segmentoId":2,"cantidad":30}]""").andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"ADMINISTRACION", "COMPRAS", "BARRA", "COCINA"})
    void elRestoDeLosPerfilesNoLaRegistra(RolCodigo rol) throws Exception {
        registrar(personas.de(rol), evento, """
                [{"segmentoId":2,"cantidad":60}]""").andExpect(status().isForbidden());
    }

    @Test
    void conElEventoEnCursoYaNoSeCambia() throws Exception {
        jdbc.update("UPDATE evento SET estado = 'EN_CURSO' WHERE evento_id = ?", evento);

        registrar(ana, evento, """
                [{"segmentoId":2,"cantidad":60}]""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("El evento está en En curso: no se puede registrar la asistencia prevista."));
    }

    @Test
    void validaLasCantidades() throws Exception {
        registrar(ana, evento, """
                [{"segmentoId":2,"cantidad":-1}]""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("La asistencia no puede ser negativa."));
        registrar(ana, evento, """
                [{"segmentoId":2,"cantidad":1},{"segmentoId":2,"cantidad":2}]""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Hay un segmento repetido."));
        jdbc.update("UPDATE tipo_segmento_asistencia SET activo = false WHERE tipo_segmento_id = 3");
        registrar(ana, evento, """
                [{"segmentoId":3,"cantidad":5}]""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SEGMENTO_INVALIDO"));
    }

    @Test
    void siOtraPersonaGuardoEnElMedioNoSePisa() throws Exception {
        registrar(ana, evento, """
                [{"segmentoId":2,"cantidad":60}]""").andExpect(status().isOk());
        entityManager.flush();

        mvc.perform(put("/api/v1/eventos/{id}/asistencia/prevista", evento).header(HttpHeaders.AUTHORIZATION, personas.bearer(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"version":0,"segmentos":[{"segmentoId":2,"cantidad":70}]}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EVENTO_MODIFICADO"));
    }

    private ResultActions ficha(Usuario quien, long id) throws Exception {
        return mvc.perform(get("/api/v1/eventos/{id}", id).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)))
                .andExpect(status().isOk());
    }

    private ResultActions confirmar(Usuario quien, long id) throws Exception {
        return mvc.perform(post("/api/v1/eventos/{id}/confirmacion", id).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)));
    }

    private ResultActions registrar(Usuario quien, long id, String segmentos) throws Exception {
        int version = jdbc.queryForObject("SELECT version FROM evento WHERE evento_id = ?", Integer.class, id);
        return mvc.perform(put("/api/v1/eventos/{id}/asistencia/prevista", id).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"version":%d,"segmentos":%s}""".formatted(version, segmentos)));
    }
}
