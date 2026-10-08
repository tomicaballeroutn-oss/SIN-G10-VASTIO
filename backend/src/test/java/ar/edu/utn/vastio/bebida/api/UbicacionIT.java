package ar.edu.utn.vastio.bebida.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
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
 * Configurar ubicaciones de stock (UI-26). Parte de lo que dejan V2 y V6: Depósito principal (1) y una barra por salón
 * (2 Avril, 3 Club de Campo, 4 Santa Bárbara), abastecidas desde el depósito. Cada test deshace sus cambios.
 */
@PruebaDeIntegracion
@Transactional
class UbicacionIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    Personas personas;

    @Autowired
    JdbcTemplate jdbc;

    // ---------- autorización ----------

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"DIRECCION", "COORDINACION", "ADMINISTRACION"})
    void administracionYAccesoTotalConfiguranUbicaciones(RolCodigo rol) throws Exception {
        long id = crear(rol, """
                {"nombre":"Transición de prueba","tipo":"TRANSICION"}""");
        escribir(put("/api/v1/ubicaciones/" + id), rol, """
                {"nombre":"Transición de prueba 2"}""").andExpect(status().isOk());
        pedir(post("/api/v1/ubicaciones/" + id + "/baja"), rol).andExpect(status().isOk());
        pedir(post("/api/v1/ubicaciones/" + id + "/reactivacion"), rol).andExpect(status().isOk());
    }

    @Test
    void comprasLasConsultaPeroNoLasConfigura() throws Exception {
        pedir(get("/api/v1/ubicaciones"), RolCodigo.COMPRAS).andExpect(status().isOk());
        escribir(post("/api/v1/ubicaciones"), RolCodigo.COMPRAS, """
                {"nombre":"Transición de prueba","tipo":"TRANSICION"}""").andExpect(status().isForbidden());
        pedir(post("/api/v1/ubicaciones/2/baja"), RolCodigo.COMPRAS).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"VENDEDORA", "PLANNER", "BARRA", "COCINA"})
    void elRestoNoAccede(RolCodigo rol) throws Exception {
        pedir(get("/api/v1/ubicaciones"), rol).andExpect(status().isForbidden());
    }

    // ---------- listado ----------

    @Test
    void listaEnElOrdenDelCircuitoConSalonYOrigen() throws Exception {
        pedir(get("/api/v1/ubicaciones"), RolCodigo.ADMINISTRACION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("DEPOSITO"))
                .andExpect(jsonPath("$[0].salon").value(nullValue()))
                .andExpect(jsonPath("$[?(@.id == 2)].salon.codigo").value(hasItem("avril")))
                .andExpect(jsonPath("$[?(@.id == 2)].abastecimiento.id").value(hasItem(1)))
                .andExpect(jsonPath("$[?(@.id == 2)].permiteRetiroDirecto").value(hasItem(false)));
    }

    // ---------- depósito madre ----------

    @Test
    void soloHayUnDepositoMadreYNoSeDaDeBaja() throws Exception {
        escribir(post("/api/v1/ubicaciones"), RolCodigo.ADMINISTRACION, """
                {"nombre":"Otro depósito","tipo":"DEPOSITO"}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DEPOSITO_MADRE_EXISTENTE"))
                .andExpect(jsonPath("$.detail").value("Ya hay un depósito madre: Depósito principal. Solo puede haber uno activo."));
        pedir(post("/api/v1/ubicaciones/1/baja"), RolCodigo.ADMINISTRACION)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("BAJA_DEPOSITO_MADRE"));
    }

    @Test
    void elDepositoMadreSeRenombraYNoTomaSalon() throws Exception {
        escribir(put("/api/v1/ubicaciones/1"), RolCodigo.ADMINISTRACION, """
                {"nombre":"Depósito Avril","salonId":1}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Depósito Avril"))
                .andExpect(jsonPath("$.salon").value(nullValue()));
    }

    @Test
    void elTipoNoCambiaYElNombreNoSeRepite() throws Exception {
        escribir(put("/api/v1/ubicaciones/2"), RolCodigo.ADMINISTRACION, """
                {"nombre":"Barra Avril","tipo":"TRANSICION"}""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("TIPO_NO_CAMBIA"));
        escribir(post("/api/v1/ubicaciones"), RolCodigo.ADMINISTRACION, """
                {"nombre":"barra avril","tipo":"TRANSICION"}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NOMBRE_REPETIDO"));
    }

    // ---------- barras ----------

    @Test
    void avrilAdmiteDosBarrasYLosOtrosSalonesUna() throws Exception {
        long segunda = crear(RolCodigo.ADMINISTRACION, """
                {"nombre":"Barra Avril 2","tipo":"BARRA","salonId":1}""");
        pedir(get("/api/v1/ubicaciones"), RolCodigo.ADMINISTRACION)
                .andExpect(jsonPath("$[?(@.id == " + segunda + ")].abastecimiento.nombre").value(hasItem("Depósito principal")));

        escribir(post("/api/v1/ubicaciones"), RolCodigo.ADMINISTRACION, """
                {"nombre":"Barra Avril 3","tipo":"BARRA","salonId":1}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Avril ya tiene 2 barras activas, que es el máximo."));
        escribir(post("/api/v1/ubicaciones"), RolCodigo.ADMINISTRACION, """
                {"nombre":"Barra Club 2","tipo":"BARRA","salonId":2}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Club de Campo ya tiene 1 barra activa, que es el máximo."));
    }

    @Test
    void unaBarraExigeSalonActivo() throws Exception {
        // Antes de cualquier pedido: después JPA ya tendría el salón en memoria.
        jdbc.update("UPDATE salon SET activo = false WHERE salon_id = 2");
        escribir(post("/api/v1/ubicaciones"), RolCodigo.ADMINISTRACION, """
                {"nombre":"Barra suelta","tipo":"BARRA"}""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("BARRA_SIN_SALON"));
        pedir(post("/api/v1/ubicaciones/3/baja"), RolCodigo.ADMINISTRACION).andExpect(status().isOk());
        escribir(post("/api/v1/ubicaciones"), RolCodigo.ADMINISTRACION, """
                {"nombre":"Barra Club nueva","tipo":"BARRA","salonId":2}""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SALON_INACTIVO"));
    }

    @Test
    void unaBarraAbastecidaPorTransicionPuedeTenerRetiroDirecto() throws Exception {
        long transicion = crear(RolCodigo.ADMINISTRACION, """
                {"nombre":"Transición Club de Campo","tipo":"TRANSICION","salonId":2}""");

        escribir(put("/api/v1/ubicaciones/3"), RolCodigo.ADMINISTRACION, """
                {"nombre":"Barra Club de Campo","salonId":2,"abastecimientoId":%d,"permiteRetiroDirecto":true}""".formatted(transicion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abastecimiento.id").value(transicion))
                .andExpect(jsonPath("$.permiteRetiroDirecto").value(true));

        // Desde el depósito madre el retiro ya es directo: el interruptor no queda prendido.
        escribir(put("/api/v1/ubicaciones/3"), RolCodigo.ADMINISTRACION, """
                {"nombre":"Barra Club de Campo","salonId":2,"abastecimientoId":1,"permiteRetiroDirecto":true}""")
                .andExpect(jsonPath("$.permiteRetiroDirecto").value(false));
    }

    @Test
    void unaBarraNoSeAbasteceDeOtraBarra() throws Exception {
        escribir(put("/api/v1/ubicaciones/3"), RolCodigo.ADMINISTRACION, """
                {"nombre":"Barra Club de Campo","salonId":2,"abastecimientoId":2}""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ABASTECIMIENTO_INVALIDO"));
    }

    // ---------- baja y reactivación ----------

    @Test
    void conSaldoNoSeDaDeBaja() throws Exception {
        jdbc.update("""
                INSERT INTO bebida (bebida_id, nombre, presentacion, tipo_bebida_id, unidad_manipulacion_id, unidades_por_bulto)
                VALUES (-1, 'Bebida de prueba', '750 ml', 1, 1, 6);
                INSERT INTO stock_ubicacion (ubicacion_id, bebida_id, cantidad, fecha_actualizacion) VALUES (4, -1, -3, now());""");

        pedir(post("/api/v1/ubicaciones/4/baja"), RolCodigo.ADMINISTRACION)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(
                        "Barra Santa Bárbara tiene saldo de 1 bebida. Pasalo a otra ubicación o registrá un recuento antes de darla de baja."));
    }

    @Test
    void unaTransicionQueAbasteceBarrasNoSeDaDeBajaYUnaBarraNoVuelveSinSuOrigen() throws Exception {
        long transicion = crear(RolCodigo.ADMINISTRACION, """
                {"nombre":"Transición Santa Bárbara","tipo":"TRANSICION","salonId":3}""");
        escribir(put("/api/v1/ubicaciones/4"), RolCodigo.ADMINISTRACION, """
                {"nombre":"Barra Santa Bárbara","salonId":3,"abastecimientoId":%d}""".formatted(transicion))
                .andExpect(status().isOk());

        pedir(post("/api/v1/ubicaciones/" + transicion + "/baja"), RolCodigo.ADMINISTRACION)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(
                        "Transición Santa Bárbara abastece a Barra Santa Bárbara. Cambiá desde dónde se abastece antes de darla de baja."));

        pedir(post("/api/v1/ubicaciones/4/baja"), RolCodigo.ADMINISTRACION).andExpect(status().isOk());
        pedir(post("/api/v1/ubicaciones/" + transicion + "/baja"), RolCodigo.ADMINISTRACION).andExpect(status().isOk());
        pedir(post("/api/v1/ubicaciones/4/reactivacion"), RolCodigo.ADMINISTRACION)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ABASTECIMIENTO_INACTIVO"));
    }

    @Test
    void reactivarUnaBarraRespetaElMaximoDelSalon() throws Exception {
        pedir(post("/api/v1/ubicaciones/3/baja"), RolCodigo.ADMINISTRACION).andExpect(status().isOk());
        crear(RolCodigo.ADMINISTRACION, """
                {"nombre":"Barra Club nueva","tipo":"BARRA","salonId":2}""");

        pedir(post("/api/v1/ubicaciones/3/reactivacion"), RolCodigo.ADMINISTRACION)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MAXIMO_BARRAS"));
        pedir(get("/api/v1/ubicaciones"), RolCodigo.ADMINISTRACION)
                .andExpect(jsonPath("$[?(@.salon.id == 2 && @.activo == true)].nombre", contains("Barra Club nueva")));
    }

    private long crear(RolCodigo rol, String cuerpo) throws Exception {
        String respuesta = escribir(post("/api/v1/ubicaciones"), rol, cuerpo)
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
