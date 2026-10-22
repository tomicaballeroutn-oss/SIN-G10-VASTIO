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
 * Registrar servicios contratados (UI-13 y M-09). Categorías activas desde V10: 2 Menú (requerida), 5 Menús especiales,
 * 6 Bodega (avisa a compras), 7 Tipo de barra (requerida, avisa a compras) y Otros.
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
                .andExpect(jsonPath("$.servicios[0].categoria").value("Menú"))
                .andExpect(jsonPath("$.servicios[1].categoria").value("Menús especiales"))
                .andExpect(jsonPath("$.servicios[1].descripcion").value(nullValue()))
                .andExpect(jsonPath("$.servicios[?(@.categoriaId == 2)].descripcion", hasItem("Lomo con papas rústicas")))
                .andExpect(jsonPath("$.servicios[?(@.categoriaId == 2)].requeridaParaConfirmar", hasItem(true)))
                .andExpect(jsonPath("$.servicios[?(@.categoriaId == 2)].usuario.nombre", hasItem("Lucía Ferreyra")))
                .andExpect(jsonPath("$.historial[?(@.campo == 'servicio.Menú')].valorNuevo", hasItem("Lomo con papas rústicas")))
                .andExpect(jsonPath("$.historial[?(@.campo == 'servicio.Bodega')].usuario.nombre", hasItem("Lucía Ferreyra")));

        assertThat(jdbc.queryForList("SELECT categoria_id FROM servicio_contratado WHERE evento_id = ? ORDER BY categoria_id",
                Short.class, evento)).containsExactly((short) 2, (short) 6);
    }

    @Test
    void cambiarYVaciarUnaCategoriaTambienQuedaRegistrado() throws Exception {
        guardar(lucia, evento, """
                [{"categoriaId":5,"descripcion":"Mesa dulce"}]""").andExpect(status().isOk());
        guardar(lucia, evento, """
                [{"categoriaId":5,"descripcion":"Mesa dulce y torta"}]""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historial[?(@.campo == 'servicio.Menús especiales')].valorAnterior", hasItem("Mesa dulce")));
        guardar(lucia, evento, """
                [{"categoriaId":5,"descripcion":"  "}]""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicios[?(@.categoriaId == 5)].descripcion", hasItem(nullValue())))
                .andExpect(jsonPath("$.historial[?(@.campo == 'servicio.Menús especiales' && @.valorAnterior == 'Mesa dulce y torta')]").isNotEmpty());
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM servicio_contratado WHERE evento_id = ?", Integer.class, evento)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM modificacion_evento
                WHERE evento_id = ? AND campo = 'servicio.Menús especiales' AND valor_anterior = 'Mesa dulce y torta' AND valor_nuevo IS NULL""",
                Integer.class, evento)).isEqualTo(1);
    }

    @Test
    void lasCategoriasQueNoVienenNoCambianYGuardarSinCambiosNoDejaRastro() throws Exception {
        guardar(lucia, evento, """
                [{"categoriaId":5,"descripcion":"Finger food"}]""").andExpect(status().isOk());
        entityManager.flush();
        int modificaciones = cantidad("SELECT count(*) FROM modificacion_evento WHERE evento_id = ?");
        int avisos = cantidad("SELECT count(*) FROM notificacion WHERE evento_id = ?");

        guardar(lucia, evento, """
                [{"categoriaId":5,"descripcion":"Finger food"}]""").andExpect(status().isOk());
        guardar(lucia, evento, "[]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicios[?(@.categoriaId == 5)].descripcion", hasItem("Finger food")));
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
        assertThat(destinatariosDesde(confirmado, 0)).contains(cocina.getId(), lucia.getId()).doesNotContain(compras.getId());

        long anteriores = jdbc.queryForObject("SELECT max(notificacion_id) FROM notificacion", Long.class);
        guardar(ana, confirmado, """
                [{"categoriaId":6,"descripcion":"Malbec y Chardonnay"}]""").andExpect(status().isOk());
        entityManager.flush();
        assertThat(destinatariosDesde(confirmado, anteriores)).contains(compras.getId()).doesNotContain(cocina.getId());
    }

    @Test
    void unaCategoriaDadaDeBajaConservaLoCargadoPeroNoSeModifica() throws Exception {
        guardar(lucia, evento, """
                [{"categoriaId":5,"descripcion":"DJ hasta las 5"}]""").andExpect(status().isOk());
        jdbc.update("UPDATE categoria_servicio SET activo = false WHERE categoria_id IN (5, 6)");
        entityManager.clear();

        guardar(lucia, evento, "[]")
                .andExpect(jsonPath("$.servicios[?(@.categoriaId == 5)].activa", hasItem(false)))
                .andExpect(jsonPath("$.servicios[?(@.categoriaId == 5)].descripcion", hasItem("DJ hasta las 5")))
                .andExpect(jsonPath("$.servicios[?(@.categoriaId == 6)]").isEmpty());
        guardar(lucia, evento, """
                [{"categoriaId":5,"descripcion":"DJ hasta las 6"}]""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CATEGORIA_DADA_DE_BAJA"));
        guardar(lucia, evento, """
                [{"categoriaId":5,"descripcion":"DJ hasta las 5"}]""").andExpect(status().isOk());
    }

    @Test
    void siOtraPersonaGuardoEnElMedioNoSePisa() throws Exception {
        guardar(lucia, evento, """
                [{"categoriaId":5,"descripcion":"Finger food"}]""").andExpect(status().isOk());
        entityManager.flush();

        mvc.perform(put("/api/v1/eventos/{id}/servicios", evento).header(HttpHeaders.AUTHORIZATION, personas.bearer(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"version":0,"servicios":[{"categoriaId":5,"descripcion":"Otra cosa"}]}"""))
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
                [{"categoriaId":5,"descripcion":"Algo"}]""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("El evento está en Liberada: no se puede modificar sus servicios."));
    }

    @Test
    void validaElPedido() throws Exception {
        guardar(lucia, evento, """
                [{"categoriaId":5,"descripcion":"%s"}]""".formatted("a".repeat(2001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("servicios[0].descripcion"));
        guardar(lucia, evento, """
                [{"categoriaId":5,"descripcion":"A"},{"categoriaId":5,"descripcion":"B"}]""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CATEGORIA_REPETIDA"));
        guardar(lucia, evento, """
                [{"categoriaId":999,"descripcion":"A"}]""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CATEGORIA_INEXISTENTE"));
    }

    @Test
    void cargaRenglonesConCantidadEnOrdenYElHistorialLosMuestraLegibles() throws Exception {
        guardar(lucia, evento, """
                [{"categoriaId":5,"renglones":[{"texto":"  Celíaco ","cantidad":2},{"texto":"","cantidad":null},{"texto":"Sin lactosa","cantidad":3}],
                  "descripcion":"Avisar en la mesa 4"}]""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicios[?(@.categoriaId == 5)].renglones[0].texto", hasItem("Celíaco")))
                .andExpect(jsonPath("$.servicios[?(@.categoriaId == 5)].renglones[0].cantidad", hasItem(2)))
                .andExpect(jsonPath("$.servicios[?(@.categoriaId == 5)].renglones[1].texto", hasItem("Sin lactosa")))
                .andExpect(jsonPath("$.servicios[?(@.categoriaId == 5)].descripcion", hasItem("Avisar en la mesa 4")))
                .andExpect(jsonPath("$.historial[?(@.campo == 'servicio.Menús especiales')].valorNuevo",
                        hasItem("Celíaco (2), Sin lactosa (3) · Avisar en la mesa 4")));
        entityManager.flush();

        assertThat(jdbc.queryForList("""
                SELECT r.texto FROM servicio_renglon r JOIN servicio_contratado s USING (servicio_id)
                WHERE s.evento_id = ? ORDER BY r.orden""", String.class, evento)).containsExactly("Celíaco", "Sin lactosa");
    }

    @Test
    void soloConRenglonesLaCategoriaTieneServicioYSinNadaQuedaVacia() throws Exception {
        guardar(lucia, evento, """
                [{"categoriaId":6,"renglones":[{"texto":"Malbec"},{"texto":"Espumante","cantidad":24}]}]""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicios[?(@.categoriaId == 6)].descripcion", hasItem(nullValue())))
                .andExpect(jsonPath("$.servicios[?(@.categoriaId == 6)].renglones[0].cantidad", hasItem(nullValue())));
        guardar(lucia, evento, """
                [{"categoriaId":6,"renglones":[{"texto":"Malbec"},{"texto":"Espumante","cantidad":24}]}]""").andExpect(status().isOk());
        guardar(lucia, evento, """
                [{"categoriaId":6,"renglones":[],"descripcion":" "}]""").andExpect(status().isOk());
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM servicio_contratado WHERE evento_id = ?", Integer.class, evento)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM modificacion_evento WHERE evento_id = ? AND campo = 'servicio.Bodega'",
                Integer.class, evento)).as("guardar lo mismo no deja rastro").isEqualTo(2);
    }

    @Test
    void loCargadoAntesDeLosRenglonesSeConservaComoDetalle() throws Exception {
        jdbc.update("INSERT INTO servicio_contratado (evento_id, categoria_id, descripcion, usuario_id) VALUES (?, 2, 'Lomo', ?)",
                evento, lucia.getId());

        guardar(lucia, evento, "[]")
                .andExpect(jsonPath("$.servicios[?(@.categoriaId == 2)].descripcion", hasItem("Lomo")))
                .andExpect(jsonPath("$.servicios[?(@.categoriaId == 2)].renglones", hasItem(List.of())));
        guardar(lucia, evento, """
                [{"categoriaId":2,"descripcion":"Lomo","renglones":[{"texto":"Menú infantil","cantidad":12}]}]""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historial[?(@.campo == 'servicio.Menú')].valorAnterior", hasItem("Lomo")))
                .andExpect(jsonPath("$.historial[?(@.campo == 'servicio.Menú')].valorNuevo", hasItem("Menú infantil (12) · Lomo")));
    }

    @Test
    void unaCantidadSinTextoNoSeGuarda() throws Exception {
        guardar(lucia, evento, """
                [{"categoriaId":5,"renglones":[{"texto":"  ","cantidad":3}]}]""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("RENGLON_SIN_TEXTO"))
                .andExpect(jsonPath("$.detail").value("En Menús especiales hay una cantidad sin texto: escribí a qué corresponde."));
    }

    @Test
    void validaLosRenglones() throws Exception {
        guardar(lucia, evento, """
                [{"categoriaId":5,"renglones":[{"texto":"Celíaco","cantidad":0}]}]""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("servicios[0].renglones[0].cantidad"));
        guardar(lucia, evento, """
                [{"categoriaId":5,"renglones":[{"texto":"%s"}]}]""".formatted("a".repeat(121)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("servicios[0].renglones[0].texto"));
        String muchos = String.join(",", java.util.Collections.nCopies(31, "{\"texto\":\"x\"}"));
        guardar(lucia, evento, "[{\"categoriaId\":5,\"renglones\":[" + muchos + "]}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("servicios[0].renglones"));
    }

    @Test
    void lasCategoriasDelSprint4() {
        assertThat(jdbc.queryForList("SELECT nombre FROM categoria_servicio WHERE activo ORDER BY orden", String.class))
                .containsExactly("Menú", "Menús especiales", "Bodega", "Tipo de barra", "Otros");
        assertThat(jdbc.queryForList("SELECT nombre FROM categoria_servicio WHERE visible_en_cocina AND activo ORDER BY orden", String.class))
                .containsExactly("Menú", "Menús especiales");
        assertThat(jdbc.queryForList("SELECT nombre FROM categoria_servicio WHERE requerida_para_confirmar ORDER BY orden", String.class))
                .containsExactly("Menú", "Tipo de barra");
        assertThat(jdbc.queryForObject("SELECT visible_en_cocina FROM categoria_servicio WHERE nombre = 'Extras'", Boolean.class)).isTrue();
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

    /** Destinatarios de los avisos de modificación con id mayor a {@code anteriores}: el general y los personales. */
    private List<Long> destinatariosDesde(long id, long anteriores) {
        return jdbc.queryForList("""
                SELECT d.usuario_id FROM notificacion_destinatario d JOIN notificacion n USING (notificacion_id)
                WHERE n.evento_id = ? AND n.tipo = 'MODIFICACION' AND n.notificacion_id > ?""",
                Long.class, id, anteriores);
    }
}
