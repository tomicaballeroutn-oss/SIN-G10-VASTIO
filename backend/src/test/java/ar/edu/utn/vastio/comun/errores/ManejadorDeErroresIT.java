package ar.edu.utn.vastio.comun.errores;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;

@PruebaDeIntegracion
class ManejadorDeErroresIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    Personas personas;

    @Test
    void laFechaTomadaEsUn409ConElMensajeDelNegocio() throws Exception {
        mvc.perform(get("/api/v1/_sonda/fecha-tomada").header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.DIRECCION)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("FECHA_TOMADA"))
                .andExpect(jsonPath("$.detail").value("Esa fecha ya está tomada. Elegí otro salón, otra fecha u otro turno."));
    }

    @Test
    void unaReglaDeNegocioEsUn422ConSuMensaje() throws Exception {
        mvc.perform(get("/api/v1/_sonda/regla").header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.DIRECCION)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SIN_PLANNER"))
                .andExpect(jsonPath("$.detail").value("Asigná una planner antes de confirmar el evento."));
    }

    @Test
    void unErrorInesperadoNoMuestraDetallesTecnicos() throws Exception {
        mvc.perform(get("/api/v1/_sonda/inesperado").header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.DIRECCION)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.detail").value(Mensajes.ERROR_INTERNO));
    }

    @Test
    void unaRutaInexistenteDevuelve404EnCastellano() throws Exception {
        mvc.perform(get("/api/v1/no-existe").header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.DIRECCION)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(Mensajes.NO_ENCONTRADO));
    }
}
