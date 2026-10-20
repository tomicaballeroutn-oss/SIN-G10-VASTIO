package ar.edu.utn.vastio.bebida.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;

@PruebaDeIntegracion
class ProveedoresEliminadosIT {
    @Autowired MockMvc mvc;
    @Autowired Personas personas;
    @Autowired RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    void noHayMappingsNiEndpointsParaDireccionAutenticada() throws Exception {
        assertThat(requestMappingHandlerMapping.getHandlerMethods().keySet())
                .noneMatch(mapping -> mapping.toString().contains("/proveedores"));
        for (String[] ruta : new String[][] {
                {"GET", ""}, {"POST", ""}, {"PUT", "/1"}, {"PUT", "/1/bebidas"},
                {"POST", "/1/baja"}, {"POST", "/1/reactivacion"}}) {
            mvc.perform(request(HttpMethod.valueOf(ruta[0]), "/api/v1/proveedores" + ruta[1])
                    .header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.DIRECCION))
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isNotFound());
        }
    }
}
