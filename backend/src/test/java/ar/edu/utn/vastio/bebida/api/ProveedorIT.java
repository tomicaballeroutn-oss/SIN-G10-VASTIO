package ar.edu.utn.vastio.bebida.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.bebida.dominio.Proveedor;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;

/**
 * Proveedores (UI-28, paso 2) y proveedor habitual de las bebidas. Cada test deshace sus cambios.
 */
@PruebaDeIntegracion
@Transactional
class ProveedorIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    Personas personas;

    @Test
    void elDigitoVerificadorDelCuit() {
        assertThat(Proveedor.cuitValido("30711222339")).isTrue();
        assertThat(Proveedor.cuitValido("20123456786")).isTrue();
        assertThat(Proveedor.cuitValido("30711222330")).isFalse();
        assertThat(Proveedor.cuitValido("3071122233")).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"DIRECCION", "COORDINACION", "ADMINISTRACION", "COMPRAS"})
    void administracionComprasYAccesoTotalAdministranProveedores(RolCodigo rol) throws Exception {
        long id = crear(rol, """
                {"razonSocial":"Distribuidora de prueba"}""");
        pedir(get("/api/v1/proveedores"), rol).andExpect(status().isOk());
        pedir(post("/api/v1/proveedores/" + id + "/baja"), rol).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"VENDEDORA", "PLANNER", "BARRA", "COCINA"})
    void elRestoNoAccedeAProveedores(RolCodigo rol) throws Exception {
        pedir(get("/api/v1/proveedores"), rol).andExpect(status().isForbidden());
        escribir(post("/api/v1/proveedores"), rol, """
                {"razonSocial":"Distribuidora de prueba"}""").andExpect(status().isForbidden());
    }

    @Test
    void elAltaGuardaLosDatosYElCuitSinGuiones() throws Exception {
        long id = crear(RolCodigo.COMPRAS, """
                {"razonSocial":" Distribuidora del Centro ","cuit":"30-71122233-9","telefono":"351 555-0101","email":"ventas@centro.com.ar"}""");

        pedir(get("/api/v1/proveedores"), RolCodigo.COMPRAS)
                .andExpect(jsonPath("$[?(@.id == %d)].razonSocial".formatted(id)).value(hasItem("Distribuidora del Centro")))
                .andExpect(jsonPath("$[?(@.id == %d)].cuit".formatted(id)).value(hasItem("30711222339")))
                .andExpect(jsonPath("$[?(@.id == %d)].email".formatted(id)).value(hasItem("ventas@centro.com.ar")));
    }

    @Test
    void elCuitTieneQueSerValidoYNoRepetirse() throws Exception {
        escribir(post("/api/v1/proveedores"), RolCodigo.COMPRAS, """
                {"razonSocial":"Distribuidora A","cuit":"30711222330"}""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CUIT_INVALIDO"));
        crear(RolCodigo.COMPRAS, """
                {"razonSocial":"Distribuidora A","cuit":"30711222339"}""");
        escribir(post("/api/v1/proveedores"), RolCodigo.COMPRAS, """
                {"razonSocial":"Distribuidora B","cuit":"30711222339"}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CUIT_REPETIDO"));
    }

    @Test
    void laRazonSocialNoSeRepiteEntreLosActivos() throws Exception {
        long viejo = crear(RolCodigo.COMPRAS, """
                {"razonSocial":"Distribuidora A"}""");
        escribir(post("/api/v1/proveedores"), RolCodigo.COMPRAS, """
                {"razonSocial":"DISTRIBUIDORA a"}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya hay un proveedor activo «DISTRIBUIDORA a»."));

        pedir(post("/api/v1/proveedores/" + viejo + "/baja"), RolCodigo.COMPRAS).andExpect(status().isOk());
        crear(RolCodigo.COMPRAS, """
                {"razonSocial":"Distribuidora A"}""");
        pedir(post("/api/v1/proveedores/" + viejo + "/reactivacion"), RolCodigo.COMPRAS)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PROVEEDOR_REPETIDO"));
    }

    @Test
    void lasBebidasQueProveeSonLasQueLoTienenComoHabitual() throws Exception {
        long centro = crear(RolCodigo.COMPRAS, """
                {"razonSocial":"Distribuidora del Centro"}""");
        long norte = crear(RolCodigo.COMPRAS, """
                {"razonSocial":"Distribuidora del Norte"}""");
        long ron = crearBebida("Ron de prueba", centro);
        long gin = crearBebida("Gin de prueba", null);

        // El gin se agrega y el ron pasa del Centro al Norte.
        escribir(put("/api/v1/proveedores/" + norte + "/bebidas"), RolCodigo.COMPRAS, """
                {"bebidaIds":[%d,%d]}""".formatted(ron, gin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bebidas[*].nombre", contains("Gin de prueba", "Ron de prueba")));
        pedir(get("/api/v1/bebidas/" + ron), RolCodigo.COMPRAS).andExpect(jsonPath("$.proveedorHabitual.id").value(norte));
        pedir(get("/api/v1/proveedores"), RolCodigo.COMPRAS)
                .andExpect(jsonPath("$[?(@.id == %d)].bebidas[*]".formatted(centro), empty()));

        // Lo que deja de estar queda sin proveedor habitual.
        escribir(put("/api/v1/proveedores/" + norte + "/bebidas"), RolCodigo.COMPRAS, """
                {"bebidaIds":[%d]}""".formatted(gin)).andExpect(status().isOk());
        pedir(get("/api/v1/bebidas/" + ron), RolCodigo.COMPRAS).andExpect(jsonPath("$.proveedorHabitual").doesNotExist());
    }

    @Test
    void unaBebidaNoTomaUnProveedorDadoDeBaja() throws Exception {
        long id = crear(RolCodigo.COMPRAS, """
                {"razonSocial":"Distribuidora vieja"}""");
        pedir(post("/api/v1/proveedores/" + id + "/baja"), RolCodigo.COMPRAS).andExpect(status().isOk());

        escribir(post("/api/v1/bebidas"), RolCodigo.COMPRAS, """
                {"nombre":"Vermut de prueba","presentacion":"1 l","tipoId":4,"unidadId":1,"unidadesPorBulto":6,"proveedorId":%d}""".formatted(id))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PROVEEDOR_INACTIVO"));
        escribir(put("/api/v1/proveedores/" + id + "/bebidas"), RolCodigo.COMPRAS, """
                {"bebidaIds":[]}""")
                .andExpect(status().isUnprocessableContent());
    }

    private long crearBebida(String nombre, Long proveedor) throws Exception {
        String respuesta = escribir(post("/api/v1/bebidas"), RolCodigo.COMPRAS, """
                {"nombre":"%s","presentacion":"750 ml","tipoId":3,"unidadId":1,"unidadesPorBulto":6,"proveedorId":%s}""".formatted(nombre, proveedor))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }

    private long crear(RolCodigo rol, String cuerpo) throws Exception {
        String respuesta = escribir(post("/api/v1/proveedores"), rol, cuerpo)
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
