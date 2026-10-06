package ar.edu.utn.vastio.agenda.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

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
 * Registrar servicios contratados (UI-13). Categorías de V2: 1 Recepción, 2 Plato principal (requerida), 3 Postre,
 * 6 Bodega (avisa a compras), 7 Tipo de barra (requerida, avisa a compras).
 */
@PruebaDeIntegracion
@Transactional
class ServiciosIT {

    private static final LocalDate FECHA = LocalDate.of(2032, 4, 17);

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
    void evento() {
        lucia = personas.usuario("lucia.servicios", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        ana = personas.usuario("ana.servicios", "Ana Sosa", RolCodigo.PLANNER);
        evento = escenario.evento("santa-barbara", FECHA, "noche", "SENADO", lucia, ana, "Bruno y Martina");
    }

    @Test
    void laTitularCargaLosServiciosYCadaCambioQuedaEnElHistorial() throws Exception {
        guardar(lucia, evento, """
                [{"categoriaId":2,"descripcion":"  Lomo con papas rústicas  "},{"categoriaId":6,"descripcion":"Malbec Luigi Bosca"}]""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicios[0].categoria").value("Recepción"))
                .andExpect(jsonPath("$.servicios[0].descripcion").value(nullValue()))
                .andExpect(jsonPath("$.servicios[?(@.categoriaId == 2)].descripcion", hasItem("Lomo con papas rústicas")))
                .andExpect(jsonPath("$.servicios[?(@.categoriaId == 2)].requeridaParaConfirmar", hasItem(true)))
                .andExpect(jsonPath("$.servicios[?(@.categoriaId == 2)].usuario.nombre", hasItem("Lucía Ferreyra")))
                .andExpect(jsonPath("$.historial[?(@.campo == 'servicio.Plato principal')].valorNuevo", hasItem("Lomo con papas rústicas")))
                .andExpect(jsonPath("$.historial[?(@.campo == 'servicio.Bodega')].usuario.nombre", hasItem("Lucía Ferreyra")));

        assertThat(jdbc.queryForList("SELECT categoria_id FROM servicio_contratado WHERE evento_id = ? ORDER BY categoria_id",
                Short.class, evento)).containsExactly((short) 2, (short) 6);
    }

    @Test
    void cambiarYVaciarUnaCategoriaTambienQuedaRegistrado() throws Exception {
        guardar(lucia, evento, """
                [{"categoriaId":3,"descripcion":"Mesa dulce"}]""").andExpect(status().isOk());
        guardar(lucia, evento, """
                [{"categoriaId":3,"descripcion":"Mesa dulce y torta"}]""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historial[?(@.campo == 'servicio.Postre')].valorAnterior", hasItem("Mesa dulce")));
        guardar(lucia, evento, """
                [{"categoriaId":3,"descripcion":"  "}]""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicios[?(@.categoriaId == 3)].descripcion", hasItem(nullValue())))
                .andExpect(jsonPath("$.historial[?(@.campo == 'servicio.Postre' && @.valorAnterior == 'Mesa dulce y torta')]").isNotEmpty());
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM servicio_contratado WHERE evento_id = ?", Integer.class, evento)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM modificacion_evento
                WHERE evento_id = ? AND campo = 'servicio.Postre' AND valor_anterior = 'Mesa dulce y torta' AND valor_nuevo IS NULL""",
                Integer.class, evento)).isEqualTo(1);
    }

    @Test
    void lasCategoriasQueNoVienenNoCambianYGuardarSinCambiosNoDejaRastro() throws Exception {
        guardar(lucia, evento, """
                [{"categoriaId":1,"descripcion":"Finger food"}]""").andExpect(status().isOk());
        entityManager.flush();
        int modificaciones = cantidad("SELECT count(*) FROM modificacion_evento WHERE evento_id = ?");
        int avisos = cantidad("SELECT count(*) FROM notificacion WHERE evento_id = ?");

        guardar(lucia, evento, """
                [{"categoriaId":1,"descripcion":"Finger food"}]""").andExpect(status().isOk());
        guardar(lucia, evento, "[]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicios[?(@.categoriaId == 1)].descripcion", hasItem("Finger food")));
        entityManager.flush();

        assertThat(cantidad("SELECT count(*) FROM modificacion_evento WHERE evento_id = ?")).isEqualTo(modificaciones);
        assertThat(cantidad("SELECT count(*) FROM notificacion WHERE evento_id = ?")).isEqualTo(avisos);
    }

    @Test
    void conElEventoConfirmadoNoSeVaciaUnaCategoriaRequerida() throws Exception {
        long confirmado = escenario.evento("avril", FECHA, "noche", "CONFIRMADO", lucia, ana, "Confirmado");
        jdbc.update("""
                INSERT INTO servicio_contratado (evento_id, categoria_id, descripcion, usuario_id)
                VALUES (?, 2, 'Lomo', ?), (?, 7, 'Barra libre premium', ?)""", confirmado, ana.getId(), confirmado, ana.getId());

        guardar(ana, confirmado, """
                [{"categoriaId":7,"descripcion":""}]""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SERVICIO_REQUERIDO"))
                .andExpect(jsonPath("$.detail").value("El evento está confirmado: Tipo de barra no puede quedar vacío."));
        guardar(ana, confirmado, """
                [{"categoriaId":7,"descripcion":"Barra libre clásica"}]""").andExpect(status().isOk());
    }

    @Test
    void conElEventoConfirmadoElMenuLeAvisaACocinaYLaBodegaACompras() throws Exception {
        Usuario cocina = personas.de(RolCodigo.COCINA);
        Usuario compras = personas.de(RolCodigo.COMPRAS);
        long confirmado = escenario.evento("avril", FECHA, "noche", "CONFIRMADO", lucia, ana, "Confirmado");
        jdbc.update("""
                INSERT INTO servicio_contratado (evento_id, categoria_id, descripcion, usuario_id)
                VALUES (?, 2, 'Lomo', ?), (?, 7, 'Barra libre', ?)""", confirmado, ana.getId(), confirmado, ana.getId());

        guardar(ana, confirmado, """
                [{"categoriaId":2,"descripcion":"Bondiola braseada"}]""").andExpect(status().isOk());
        entityManager.flush();
        assertThat(destinatariosDelUltimoAviso(confirmado)).contains(cocina.getId(), lucia.getId()).doesNotContain(compras.getId());

        guardar(ana, confirmado, """
                [{"categoriaId":6,"descripcion":"Malbec y Chardonnay"}]""").andExpect(status().isOk());
        entityManager.flush();
        assertThat(destinatariosDelUltimoAviso(confirmado)).contains(compras.getId()).doesNotContain(cocina.getId());
    }

    @Test
    void unaCategoriaDadaDeBajaConservaLoCargadoPeroNoSeModifica() throws Exception {
        guardar(lucia, evento, """
                [{"categoriaId":4,"descripcion":"DJ hasta las 5"}]""").andExpect(status().isOk());
        jdbc.update("UPDATE categoria_servicio SET activo = false WHERE categoria_id IN (4, 8)");
        entityManager.clear();

        guardar(lucia, evento, "[]")
                .andExpect(jsonPath("$.servicios[?(@.categoriaId == 4)].activa", hasItem(false)))
                .andExpect(jsonPath("$.servicios[?(@.categoriaId == 4)].descripcion", hasItem("DJ hasta las 5")))
                .andExpect(jsonPath("$.servicios[?(@.categoriaId == 8)]").isEmpty());
        guardar(lucia, evento, """
                [{"categoriaId":4,"descripcion":"DJ hasta las 6"}]""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CATEGORIA_DADA_DE_BAJA"));
        guardar(lucia, evento, """
                [{"categoriaId":4,"descripcion":"DJ hasta las 5"}]""").andExpect(status().isOk());
    }

    @Test
    void siOtraPersonaGuardoEnElMedioNoSePisa() throws Exception {
        guardar(lucia, evento, """
                [{"categoriaId":1,"descripcion":"Finger food"}]""").andExpect(status().isOk());
        entityManager.flush();

        mvc.perform(put("/api/v1/eventos/{id}/servicios", evento).header(HttpHeaders.AUTHORIZATION, personas.bearer(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"version":0,"servicios":[{"categoriaId":1,"descripcion":"Otra cosa"}]}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EVENTO_MODIFICADO"));
    }

    @Test
    void quienesPuedenRegistrarServicios() throws Exception {
        guardar(ana, evento, "[]").andExpect(status().isOk());
        guardar(personas.de(RolCodigo.COORDINACION), evento, "[]").andExpect(status().isOk());
        guardar(personas.usuario("sofia.servicios", "Sofía Méndez", RolCodigo.VENDEDORA), evento, "[]")
                .andExpect(status().isForbidden());
        guardar(personas.usuario("carla.servicios", "Carla Núñez", RolCodigo.PLANNER), evento, "[]")
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"ADMINISTRACION", "COMPRAS", "BARRA", "COCINA"})
    void elRestoDeLosPerfilesNoRegistraServicios(RolCodigo rol) throws Exception {
        guardar(personas.de(rol), evento, "[]").andExpect(status().isForbidden());
    }

    @Test
    void unEventoLiberadoNoSeModifica() throws Exception {
        long liberada = escenario.evento("club", FECHA, "noche", "LIBERADA", lucia, "No prosperó");

        guardar(lucia, liberada, """
                [{"categoriaId":1,"descripcion":"Algo"}]""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("El evento está en Liberada: no se puede modificar sus servicios."));
    }

    @Test
    void validaElPedido() throws Exception {
        guardar(lucia, evento, """
                [{"categoriaId":1,"descripcion":"%s"}]""".formatted("a".repeat(2001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("servicios[0].descripcion"));
        guardar(lucia, evento, """
                [{"categoriaId":1,"descripcion":"A"},{"categoriaId":1,"descripcion":"B"}]""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CATEGORIA_REPETIDA"));
        guardar(lucia, evento, """
                [{"categoriaId":999,"descripcion":"A"}]""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CATEGORIA_INEXISTENTE"));
    }

    private ResultActions guardar(Usuario quien, long id, String servicios) throws Exception {
        int version = jdbc.queryForObject("SELECT version FROM evento WHERE evento_id = ?", Integer.class, id);
        return mvc.perform(put("/api/v1/eventos/{id}/servicios", id).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"version":%d,"servicios":%s}""".formatted(version, servicios)));
    }

    private int cantidad(String sql) {
        return jdbc.queryForObject(sql, Integer.class, evento);
    }

    private List<Long> destinatariosDelUltimoAviso(long id) {
        return jdbc.queryForList("""
                SELECT usuario_id FROM notificacion_destinatario
                WHERE notificacion_id = (SELECT max(notificacion_id) FROM notificacion WHERE evento_id = ? AND tipo = 'MODIFICACION')""",
                Long.class, id);
    }
}
