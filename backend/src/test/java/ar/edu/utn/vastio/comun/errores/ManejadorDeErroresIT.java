package ar.edu.utn.vastio.comun.errores;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import ar.edu.utn.vastio.PruebaDeIntegracion;

@PruebaDeIntegracion
@WithMockUser(roles = "DIRECCION")
class ManejadorDeErroresIT {

    @Autowired
    MockMvc mvc;

    @Test
    void laFechaTomadaEsUn409ConElMensajeDelNegocio() throws Exception {
        mvc.perform(get("/api/v1/_sonda/fecha-tomada"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("FECHA_TOMADA"))
                .andExpect(jsonPath("$.detail").value("Esa fecha ya está tomada. Elegí otro salón, otra fecha u otro turno."));
    }

    @Test
    void unaReglaDeNegocioEsUn422ConSuMensaje() throws Exception {
        mvc.perform(get("/api/v1/_sonda/regla"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SIN_PLANNER"))
                .andExpect(jsonPath("$.detail").value("Asigná una planner antes de confirmar el evento."));
    }

    @Test
    void unErrorInesperadoNoMuestraDetallesTecnicos() throws Exception {
        mvc.perform(get("/api/v1/_sonda/inesperado"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.detail").value(Mensajes.ERROR_INTERNO));
    }

    @Test
    void unaRutaInexistenteDevuelve404EnCastellano() throws Exception {
        mvc.perform(get("/api/v1/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(Mensajes.NO_ENCONTRADO));
    }
}
