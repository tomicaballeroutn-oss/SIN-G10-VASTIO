package ar.edu.utn.vastio.bebida.api;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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

import com.jayway.jsonpath.JsonPath;

import ar.edu.utn.vastio.Escenario;
import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Ajustar orden de preparación (docs/sprint-4.md, decisiones 23 a 25): cantidades, bebidas agregadas, confirmación,
 * reapertura y la referencia en la pestaña Preparación.
 */
@PruebaDeIntegracion
@Transactional
class OrdenAjusteIT {

    private static final LocalDate FECHA = LocalDate.of(2034, 4, 15);

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

    Usuario compras;
    long evento;
    long fernet;
    long vino;

    @BeforeEach
    void ordenSugerida() throws Exception {
        compras = personas.usuario("tomas.ajuste", "Tomás Caballero", RolCodigo.COMPRAS);
        evento = escenario.evento("club", FECHA, "noche", "CONFIRMADO", personas.de(RolCodigo.VENDEDORA),
                personas.de(RolCodigo.PLANNER), "Quince de Sofía");
        fernet = bebida("Fernet ajuste", true);
        vino = bebida("Vino ajuste", true);
        long orden = jdbc.queryForObject("""
                INSERT INTO orden_preparacion (evento_id, estado, ubicacion_origen_id, fuente, eventos_base, asistentes_base,
                    margen_porcentaje) VALUES (?, 'SUGERIDA', 1, 'HISTORICO_TIPO', 3, 150, 10) RETURNING orden_id""",
                Long.class, evento);
        jdbc.update("""
                INSERT INTO detalle_orden_preparacion (orden_id, bebida_id, cantidad_sugerida, cantidad_a_retirar)
                VALUES (?, ?, 18, 18)""", orden, fernet);
    }

    @Test
    void comprasAjustaYAgregaUnaBebida() throws Exception {
        ajustar(compras, 0, """
                [{"bebidaId":%d,"cantidad":24},{"bebidaId":%d,"cantidad":6}]""".formatted(fernet, vino))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orden.estado").value("MODIFICADA"))
                .andExpect(jsonPath("$.orden.modificacion.usuario.nombre").value("Tomás Caballero"))
                .andExpect(jsonPath("$.orden.renglones[?(@.bebidaId == %d)].sugerida".formatted(fernet)).value(hasItem(18.0)))
                .andExpect(jsonPath("$.orden.renglones[?(@.bebidaId == %d)].aRetirar".formatted(fernet)).value(hasItem(24)))
                .andExpect(jsonPath("$.orden.renglones[?(@.bebidaId == %d)].sugerida".formatted(vino)).value(hasItem(0)))
                .andExpect(jsonPath("$.orden.renglones[?(@.bebidaId == %d)].aRetirar".formatted(vino)).value(hasItem(6)))
                .andExpect(jsonPath("$.acciones.confirmar").value(true));
    }

    @Test
    void confirmadaNoSeEditaHastaReabrirla() throws Exception {
        int version = version(confirmar(compras, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orden.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.orden.confirmacion.usuario.nombre").value("Tomás Caballero"))
                .andExpect(jsonPath("$.acciones.ajustar").value(false))
                .andExpect(jsonPath("$.acciones.reabrir").value(true)));

        ajustar(compras, version, """
                [{"bebidaId":%d,"cantidad":24}]""".formatted(fernet))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ORDEN_CONFIRMADA"));
        mvc.perform(post("/api/v1/eventos/{id}/orden-preparacion/recalculo", evento).header(HttpHeaders.AUTHORIZATION, personas.bearer(compras))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":%d}".formatted(version)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("La orden está confirmada: reabrila para recalcular."));

        int reabierta = version(reabrir(compras, version)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orden.estado").value("MODIFICADA"))
                .andExpect(jsonPath("$.orden.reapertura.usuario.nombre").value("Tomás Caballero")));
        ajustar(compras, reabierta, """
                [{"bebidaId":%d,"cantidad":24}]""".formatted(fernet)).andExpect(status().isOk());
    }

    @Test
    void reabrirUnaQueNoEstaConfirmadaNoTieneSentido() throws Exception {
        reabrir(compras, 0)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ORDEN_NO_CONFIRMADA"));
    }

    @Test
    void noPisaLosCambiosDeOtraPersona() throws Exception {
        ajustar(compras, 0, """
                [{"bebidaId":%d,"cantidad":24}]""".formatted(fernet)).andExpect(status().isOk());

        ajustar(compras, 0, """
                [{"bebidaId":%d,"cantidad":30}]""".formatted(fernet))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ORDEN_MODIFICADA"));
    }

    @Test
    void validaLasCantidadesYLasBebidas() throws Exception {
        ajustar(compras, 0, """
                [{"bebidaId":%d,"cantidad":1.5}]""".formatted(fernet))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("La cantidad va en botellas enteras."));
        ajustar(compras, 0, """
                [{"bebidaId":%d,"cantidad":-6}]""".formatted(fernet))
                .andExpect(status().isBadRequest());
        ajustar(compras, 0, """
                [{"bebidaId":%d,"cantidad":6},{"bebidaId":%d,"cantidad":12}]""".formatted(fernet, fernet))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Hay una bebida repetida."));
        long deBaja = bebida("Baja ajuste", false);
        ajustar(compras, 0, """
                [{"bebidaId":%d,"cantidad":6}]""".formatted(deBaja))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("BEBIDA_INVALIDA"));
    }

    @Test
    void conElEventoRealizadoYaNoSeAjusta() throws Exception {
        jdbc.update("UPDATE evento SET estado = 'REALIZADO' WHERE evento_id = ?", evento);

        ajustar(compras, 0, """
                [{"bebidaId":%d,"cantidad":24}]""".formatted(fernet))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ESTADO_NO_PERMITE"));
    }

    @Test
    void administracionNoAjustaNiConfirma() throws Exception {
        Usuario administracion = personas.de(RolCodigo.ADMINISTRACION);

        ajustar(administracion, 0, """
                [{"bebidaId":%d,"cantidad":24}]""".formatted(fernet)).andExpect(status().isForbidden());
        confirmar(administracion, 0).andExpect(status().isForbidden());
        reabrir(administracion, 0).andExpect(status().isForbidden());
    }

    @Test
    void laPreparacionMuestraLaOrdenComoReferencia() throws Exception {
        mvc.perform(get("/api/v1/eventos/{id}/preparaciones", evento).header(HttpHeaders.AUTHORIZATION, personas.bearer(compras)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orden.estado").value("SUGERIDA"))
                .andExpect(jsonPath("$.orden.renglones[0].bebidaId").value(fernet))
                .andExpect(jsonPath("$.orden.renglones[0].aRetirar").value(18.0));
    }

    private long bebida(String nombre, boolean activa) {
        return jdbc.queryForObject("""
                INSERT INTO bebida (nombre, presentacion, tipo_bebida_id, unidad_manipulacion_id, unidades_por_bulto, activo)
                VALUES (?, '750 ml', 3, 1, 6, ?) RETURNING bebida_id""", Long.class, nombre, activa);
    }

    private ResultActions ajustar(Usuario quien, int version, String renglones) throws Exception {
        ResultActions r = mvc.perform(put("/api/v1/eventos/{id}/orden-preparacion/renglones", evento)
                .header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":%d,\"renglones\":%s}".formatted(version, renglones)));
        entityManager.flush();
        return r;
    }

    private ResultActions confirmar(Usuario quien, int version) throws Exception {
        ResultActions r = mvc.perform(post("/api/v1/eventos/{id}/orden-preparacion/confirmacion", evento)
                .header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":%d}".formatted(version)));
        entityManager.flush();
        return r;
    }

    private ResultActions reabrir(Usuario quien, int version) throws Exception {
        ResultActions r = mvc.perform(post("/api/v1/eventos/{id}/orden-preparacion/reapertura", evento)
                .header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":%d}".formatted(version)));
        entityManager.flush();
        return r;
    }

    private static int version(ResultActions r) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.orden.version");
    }
}
