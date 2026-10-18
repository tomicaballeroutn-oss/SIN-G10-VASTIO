package ar.edu.utn.vastio.bebida.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;

/**
 * Catálogo de bebidas (UI-25) y búsqueda por código (UI-30). Cada test deshace sus cambios. Los nombres y códigos no
 * coinciden con los datos de demostración, que pueden estar cargados en la misma base.
 */
@PruebaDeIntegracion
@Transactional
class BebidaIT {

    private static final String RON = """
            {"nombre":"Ron de prueba","presentacion":"750 ml","tipoId":3,"unidadId":1,"unidadesPorBulto":6,
             "stockMinimo":12,"codigos":[{"codigo":"99000000000011","unidades":6},{"codigo":" 9900000000014 ","unidades":1}]}""";

    @Autowired
    MockMvc mvc;

    @Autowired
    Personas personas;

    @Autowired
    JdbcTemplate jdbc;

    // ---------- autorización ----------

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"DIRECCION", "COORDINACION", "ADMINISTRACION", "COMPRAS"})
    void administracionComprasYAccesoTotalAdministranElCatalogo(RolCodigo rol) throws Exception {
        long id = crear(rol, RON);
        pedir(get("/api/v1/bebidas"), rol).andExpect(status().isOk()).andExpect(jsonPath("$[*].id", hasItem((int) id)));
        pedir(post("/api/v1/bebidas/" + id + "/baja"), rol).andExpect(status().isOk());
        pedir(get("/api/v1/tipos-bebida"), rol).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"VENDEDORA", "PLANNER", "BARRA", "COCINA"})
    void elRestoNoAccedeAlCatalogo(RolCodigo rol) throws Exception {
        pedir(get("/api/v1/bebidas"), rol).andExpect(status().isForbidden());
        pedir(get("/api/v1/bebidas/codigos/7790000000019"), rol).andExpect(status().isForbidden());
        escribir(post("/api/v1/bebidas"), rol, RON).andExpect(status().isForbidden());
        pedir(get("/api/v1/unidades-manipulacion"), rol).andExpect(status().isForbidden());
    }

    // ---------- listas fijas ----------

    @Test
    void soloHayTiposDeBebidaConAlcoholYLaCajaSeLlamaCaja() throws Exception {
        pedir(get("/api/v1/tipos-bebida"), RolCodigo.COMPRAS)
                .andExpect(jsonPath("$[*].nombre", contains("Vino", "Espumante", "Destilado", "Aperitivo", "Cerveza")));
        pedir(get("/api/v1/unidades-manipulacion"), RolCodigo.COMPRAS)
                .andExpect(jsonPath("$[*].nombre", contains("Caja", "Pack", "Botella")))
                .andExpect(jsonPath("$[2].esBotella").value(true));
    }

    // ---------- alta y modificación ----------

    @Test
    void elAltaGuardaLosDatosYLosCodigosSinPrecio() throws Exception {
        long id = crear(RolCodigo.COMPRAS, RON);

        pedir(get("/api/v1/bebidas/" + id), RolCodigo.COMPRAS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ron de prueba"))
                .andExpect(jsonPath("$.tipo.nombre").value("Destilado"))
                .andExpect(jsonPath("$.unidad.nombre").value("Caja"))
                .andExpect(jsonPath("$.unidadesPorBulto").value(6))
                .andExpect(jsonPath("$.stockMinimo").value(12))
                .andExpect(jsonPath("$.codigos[*].codigo", contains("99000000000011", "9900000000014")))
                .andExpect(jsonPath("$.codigos[0].unidades").value(6))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$", not(hasKey("precioReferencia"))));
    }

    @Test
    void conUnidadBotellaElBultoEsDeUnaBotella() throws Exception {
        long id = crear(RolCodigo.ADMINISTRACION, """
                {"nombre":"Whisky de prueba","presentacion":"1 l","tipoId":3,"unidadId":3,"unidadesPorBulto":12,"codigos":[]}""");

        pedir(get("/api/v1/bebidas/" + id), RolCodigo.ADMINISTRACION)
                .andExpect(jsonPath("$.unidadesPorBulto").value(1))
                .andExpect(jsonPath("$.stockMinimo").value(nullValue()));
    }

    @Test
    void laModificacionReemplazaDatosYCodigos() throws Exception {
        long id = crear(RolCodigo.COMPRAS, RON);

        escribir(put("/api/v1/bebidas/" + id), RolCodigo.COMPRAS, """
                {"nombre":"Ron de prueba añejo","presentacion":"1 l","tipoId":3,"unidadId":1,"unidadesPorBulto":12,
                 "codigos":[{"codigo":"99000000000011","unidades":12},{"codigo":"99000000022","unidades":1}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ron de prueba añejo"))
                .andExpect(jsonPath("$.unidadesPorBulto").value(12))
                .andExpect(jsonPath("$.stockMinimo").value(nullValue()))
                .andExpect(jsonPath("$.codigos[*].codigo", contains("99000000000011", "99000000022")))
                .andExpect(jsonPath("$.codigos[0].unidades").value(12));
    }

    @Test
    void validaLosCampos() throws Exception {
        escribir(post("/api/v1/bebidas"), RolCodigo.COMPRAS, """
                {"nombre":" ","presentacion":"750 ml","tipoId":3,"unidadId":1,"unidadesPorBulto":0,"stockMinimo":-1,
                 "codigos":[{"codigo":"12AB","unidades":1}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo",
                        org.hamcrest.Matchers.containsInAnyOrder("nombre", "unidadesPorBulto", "stockMinimo", "codigos[0].codigo")));
    }

    @Test
    void unTipoInexistenteSeRechaza() throws Exception {
        escribir(post("/api/v1/bebidas"), RolCodigo.COMPRAS, """
                {"nombre":"Sidra de prueba","presentacion":"750 ml","tipoId":6,"unidadId":1,"unidadesPorBulto":6}""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("TIPO_BEBIDA_INEXISTENTE"));
    }

    // ---------- unicidad ----------

    @Test
    void noHayDosActivasConElMismoNombreYPresentacion() throws Exception {
        crear(RolCodigo.COMPRAS, RON);

        escribir(post("/api/v1/bebidas"), RolCodigo.COMPRAS, """
                {"nombre":"RON DE PRUEBA","presentacion":"750 ML","tipoId":3,"unidadId":1,"unidadesPorBulto":6}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("BEBIDA_REPETIDA"))
                .andExpect(jsonPath("$.detail").value(
                        "Ya hay una bebida activa «Ron de prueba 750 ml». Modificá esa en lugar de cargar otra."));
    }

    @Test
    void reactivarSeRechazaSiYaHayOtraActivaIgual() throws Exception {
        long vieja = crear(RolCodigo.COMPRAS, RON);
        pedir(post("/api/v1/bebidas/" + vieja + "/baja"), RolCodigo.COMPRAS).andExpect(status().isOk());
        crear(RolCodigo.COMPRAS, """
                {"nombre":"Ron de prueba","presentacion":"750 ml","tipoId":3,"unidadId":1,"unidadesPorBulto":6}""");

        pedir(post("/api/v1/bebidas/" + vieja + "/reactivacion"), RolCodigo.COMPRAS)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("BEBIDA_REPETIDA"));
    }

    @Test
    void unCodigoEsDeUnaSolaBebidaAunqueEsteDadaDeBaja() throws Exception {
        long ron = crear(RolCodigo.COMPRAS, RON);
        pedir(post("/api/v1/bebidas/" + ron + "/baja"), RolCodigo.COMPRAS).andExpect(status().isOk());

        escribir(post("/api/v1/bebidas"), RolCodigo.COMPRAS, """
                {"nombre":"Cachaça de prueba","presentacion":"1 l","tipoId":3,"unidadId":1,"unidadesPorBulto":6,
                 "codigos":[{"codigo":"9900000000014","unidades":1}]}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CODIGO_DE_OTRA_BEBIDA"))
                .andExpect(jsonPath("$.detail").value(
                        "El código 9900000000014 ya es de Ron de prueba 750 ml (dada de baja). Quitáselo a esa bebida antes de usarlo acá."));
    }

    @Test
    void unCodigoRepetidoEnElPedidoSeRechaza() throws Exception {
        escribir(post("/api/v1/bebidas"), RolCodigo.COMPRAS, """
                {"nombre":"Pisco de prueba","presentacion":"750 ml","tipoId":3,"unidadId":1,"unidadesPorBulto":6,
                 "codigos":[{"codigo":"99000000033","unidades":1},{"codigo":"99000000033","unidades":6}]}""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CODIGO_REPETIDO"));
    }

    // ---------- búsqueda por código ----------

    @Test
    void elCodigoIdentificaLaBebida() throws Exception {
        long id = crear(RolCodigo.COMPRAS, RON);

        pedir(get("/api/v1/bebidas/codigos/99000000000011"), RolCodigo.COMPRAS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        pedir(get("/api/v1/bebidas/codigos/99999999999999"), RolCodigo.COMPRAS)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Ese código no está en el catálogo. Elegí la bebida a mano."));
    }

    // ---------- baja ----------

    @Test
    void laBajaSePermiteConSaldoYAntesSeConsultaDondeEsta() throws Exception {
        long id = crear(RolCodigo.ADMINISTRACION, RON);
        jdbc.update("INSERT INTO stock_ubicacion (ubicacion_id, bebida_id, cantidad, fecha_actualizacion) VALUES (1, ?, 18, now())", id);
        jdbc.update("INSERT INTO stock_ubicacion (ubicacion_id, bebida_id, cantidad, fecha_actualizacion) VALUES (2, ?, 0, now())", id);

        pedir(get("/api/v1/bebidas/" + id + "/saldos"), RolCodigo.ADMINISTRACION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ubicacionId").value(1))
                .andExpect(jsonPath("$[0].cantidad").value(18));
        pedir(post("/api/v1/bebidas/" + id + "/baja"), RolCodigo.ADMINISTRACION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false))
                .andExpect(jsonPath("$.fechaBaja").isNotEmpty());
        pedir(post("/api/v1/bebidas/" + id + "/reactivacion"), RolCodigo.ADMINISTRACION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.fechaBaja").value(nullValue()));
    }

    private long crear(RolCodigo rol, String cuerpo) throws Exception {
        String respuesta = escribir(post("/api/v1/bebidas"), rol, cuerpo)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }

    private ResultActions pedir(MockHttpServletRequestBuilder pedido, RolCodigo rol) throws Exception {
        return mvc.perform(pedido.header(HttpHeaders.AUTHORIZATION, personas.bearer(rol)));
    }

    private ResultActions escribir(MockHttpServletRequestBuilder pedido, RolCodigo rol, String cuerpo) throws Exception {
        return pedir(pedido.contentType(MediaType.APPLICATION_JSON).content(cuerpo), rol);
    }
}
