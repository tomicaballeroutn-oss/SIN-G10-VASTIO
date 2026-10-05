package ar.edu.utn.vastio.agenda.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.Escenario;
import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Consultar agenda (UI-07). Marzo de 2030: lejos de cualquier otro dato.
 */
@PruebaDeIntegracion
@Transactional
class AgendaIT {

    private static final LocalDate DIA = LocalDate.of(2030, 3, 9);

    @Autowired
    MockMvc mvc;

    @Autowired
    Personas personas;

    @Autowired
    Escenario escenario;

    Usuario lucia;
    Usuario melina;

    @BeforeEach
    void eventos() {
        lucia = personas.usuario("lucia.agenda", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        melina = personas.usuario("melina.agenda", "Melina Ruiz", RolCodigo.VENDEDORA);
        escenario.evento("avril", DIA, "noche", "PRE_RESERVA", lucia, "Quince de Delfina");
        escenario.evento("club", DIA, "mediodia", "SENADO", melina, "Casamiento Gómez-Paz");
        escenario.evento("santa-barbara", DIA, "noche", "LIBERADA", lucia, "Pre-reserva que no prosperó");
        escenario.evento("santa-barbara", DIA, "mediodia", "CANCELADO", melina, "Evento cancelado");
        escenario.bloqueo("club", DIA, "noche", personas.de(RolCodigo.ADMINISTRACION), "Pintura del salón");
        // La noche del 31 es del 31 aunque termine el 1 de abril: fecha operativa.
        escenario.evento("avril", LocalDate.of(2030, 3, 31), "noche", "SENADO", lucia, "Fin de mes");
        escenario.evento("avril", LocalDate.of(2030, 4, 1), "noche", "SENADO", lucia, "Otro mes");
    }

    @Test
    void laVendedoraVeSusEventosConDetalleYDeLosOtrosSoloQuienTieneLaFecha() throws Exception {
        marzo(personas.bearer(lucia))
                .andExpect(jsonPath("$.mes").value("2030-03"))
                .andExpect(jsonPath("$.salones", hasSize(3)))
                .andExpect(jsonPath("$.turnos[1].codigo").value("noche"))
                // Orden: fecha, turno (mediodía antes que noche) y salón.
                .andExpect(jsonPath("$.unidades", hasSize(4)))
                .andExpect(jsonPath("$.unidades[0].salonId").value(2))
                .andExpect(jsonPath("$.unidades[0].estado").value("SENADO"))
                .andExpect(jsonPath("$.unidades[0].evento.detalle").value(false))
                .andExpect(jsonPath("$.unidades[0].evento.vendedora.nombre").value("Melina Ruiz"))
                .andExpect(jsonPath("$.unidades[0].evento.nombre").doesNotExist())
                .andExpect(jsonPath("$.unidades[0].evento.cliente").doesNotExist())
                .andExpect(jsonPath("$.unidades[0].evento.codigo").doesNotExist())
                .andExpect(jsonPath("$.unidades[0].evento.id").doesNotExist())
                .andExpect(jsonPath("$.unidades[1].salonId").value(1))
                .andExpect(jsonPath("$.unidades[1].estado").value("PRE_RESERVA"))
                .andExpect(jsonPath("$.unidades[1].evento.detalle").value(true))
                .andExpect(jsonPath("$.unidades[1].evento.nombre").value("Quince de Delfina"))
                .andExpect(jsonPath("$.unidades[1].evento.tipo").value("Quince"))
                .andExpect(jsonPath("$.unidades[1].evento.cliente").value("Cliente de Quince de Delfina"))
                .andExpect(jsonPath("$.unidades[1].evento.codigo").exists());
    }

    @Test
    void liberadasYCanceladasNoOcupanYLosBloqueosMuestranSuMotivo() throws Exception {
        marzo(personas.bearer(RolCodigo.COORDINACION))
                .andExpect(jsonPath("$.unidades[?(@.fecha == '2030-03-09' && @.salonId == 3)]").isEmpty())
                .andExpect(jsonPath("$.unidades[2].salonId").value(2))
                .andExpect(jsonPath("$.unidades[2].estado").value("BLOQUEADO"))
                .andExpect(jsonPath("$.unidades[2].bloqueo.motivo").value("Mantenimiento"))
                .andExpect(jsonPath("$.unidades[2].bloqueo.detalle").value("Pintura del salón"))
                .andExpect(jsonPath("$.unidades[2].evento").doesNotExist());
    }

    @Test
    void laNocheDelUltimoDiaEsDeEseMesYNoEntraElSiguiente() throws Exception {
        marzo(personas.bearer(RolCodigo.DIRECCION))
                .andExpect(jsonPath("$.unidades[3].fecha").value("2030-03-31"))
                .andExpect(jsonPath("$.unidades[3].evento.nombre").value("Fin de mes"));
    }

    @Test
    void laPlannerVeElDetalleDeTodos() throws Exception {
        marzo(personas.bearer(RolCodigo.PLANNER))
                .andExpect(jsonPath("$.unidades[0].evento.detalle").value(true))
                .andExpect(jsonPath("$.unidades[0].evento.nombre").value("Casamiento Gómez-Paz"));
    }

    @Test
    void unMesMalEscritoDevuelve400() throws Exception {
        mvc.perform(get("/api/v1/agenda?mes=marzo").header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.DIRECCION)))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"DIRECCION", "COORDINACION", "ADMINISTRACION", "VENDEDORA", "PLANNER", "COMPRAS"})
    void consultanLaAgenda(RolCodigo rol) throws Exception {
        marzo(personas.bearer(rol));
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"BARRA", "COCINA"})
    void barraYCocinaNoConsultanLaAgenda(RolCodigo rol) throws Exception {
        mvc.perform(get("/api/v1/agenda?mes=2030-03").header(HttpHeaders.AUTHORIZATION, personas.bearer(rol)))
                .andExpect(status().isForbidden());
    }

    private ResultActions marzo(String bearer) throws Exception {
        return mvc.perform(get("/api/v1/agenda?mes=2030-03").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
    }
}
