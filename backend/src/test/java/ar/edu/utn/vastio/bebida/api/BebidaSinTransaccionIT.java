package ar.edu.utn.vastio.bebida.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;

/**
 * Sin {@code @Transactional}, como en producción ({@code open-in-view: false}): cada pedido cierra su sesión antes de
 * armar la respuesta. Verifica que las respuestas no dependan de relaciones sin cargar. Borra lo que crea.
 */
@PruebaDeIntegracion
class BebidaSinTransaccionIT {

    private static final String CODIGO = "99887766554433";

    @Autowired
    MockMvc mvc;

    @Autowired
    Personas personas;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void lasRespuestasDeBebidasYUbicacionesSeArmanFueraDeLaTransaccion() throws Exception {
        String proveedor = pedir(post("/api/v1/proveedores"), """
                {"razonSocial":"Proveedor sin transacción"}""").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long proveedorId = ((Number) JsonPath.read(proveedor, "$.id")).longValue();
        String bebida = pedir(post("/api/v1/bebidas"), """
                {"nombre":"Bebida sin transacción","presentacion":"750 ml","tipoId":3,"unidadId":1,"unidadesPorBulto":6,
                 "codigos":[{"codigo":"%s","unidades":6}],"proveedorId":%d}""".formatted(CODIGO, proveedorId))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(bebida, "$.id")).longValue();

        pedir(get("/api/v1/bebidas/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.codigos[0].codigo").value(CODIGO))
                .andExpect(jsonPath("$.proveedorHabitual.razonSocial").value("Proveedor sin transacción"));
        pedir(get("/api/v1/bebidas/codigos/" + CODIGO)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        pedir(post("/api/v1/bebidas/" + id + "/baja")).andExpect(status().isOk()).andExpect(jsonPath("$.codigos[0].codigo").value(CODIGO));
        pedir(post("/api/v1/bebidas/" + id + "/reactivacion")).andExpect(status().isOk());
        pedir(get("/api/v1/bebidas")).andExpect(status().isOk());

        // Una barra con su origen: dar de baja y reactivar arman la respuesta con el nombre del origen.
        pedir(post("/api/v1/ubicaciones/4/baja")).andExpect(status().isOk()).andExpect(jsonPath("$.abastecimiento.nombre").isNotEmpty());
        pedir(post("/api/v1/ubicaciones/4/reactivacion")).andExpect(status().isOk());
        pedir(put("/api/v1/ubicaciones/4"), """
                {"nombre":"Barra Santa Bárbara","salonId":3}""").andExpect(status().isOk());
        pedir(get("/api/v1/ubicaciones")).andExpect(status().isOk());
        pedir(get("/api/v1/proveedores")).andExpect(status().isOk());
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM codigo_barra WHERE codigo = ?", CODIGO);
        jdbc.update("DELETE FROM bebida WHERE nombre = 'Bebida sin transacción'");
        jdbc.update("DELETE FROM proveedor WHERE razon_social = 'Proveedor sin transacción'");
        jdbc.update("UPDATE ubicacion SET activo = true WHERE ubicacion_id = 4");
    }

    private ResultActions pedir(MockHttpServletRequestBuilder pedido) throws Exception {
        return mvc.perform(pedido.header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.DIRECCION)));
    }

    private ResultActions pedir(MockHttpServletRequestBuilder pedido, String cuerpo) throws Exception {
        return pedir(pedido.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }
}
