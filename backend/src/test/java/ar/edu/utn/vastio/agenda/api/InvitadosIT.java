package ar.edu.utn.vastio.agenda.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
 * Registrar cantidad de invitados (UI-14).
 */
@PruebaDeIntegracion
@Transactional
class InvitadosIT {

    private static final LocalDate FECHA = LocalDate.of(2032, 5, 8);

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
        lucia = personas.usuario("lucia.invitados", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        ana = personas.usuario("ana.invitados", "Ana Sosa", RolCodigo.PLANNER);
        evento = escenario.evento("avril", FECHA, "noche", "CONTRATADO", lucia, ana, "Bruno y Martina");
    }

    @Test
    void laTitularRegistraLaCantidadDefinitivaYQuedaEnElHistorial() throws Exception {
        registrar(lucia, evento, 180, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidadInvitados").value(180))
                .andExpect(jsonPath("$.invitadosDefinitivos").value(true))
                .andExpect(jsonPath("$.historial[?(@.campo == 'cantidad_invitados')].valorNuevo", hasItem("180")))
                .andExpect(jsonPath("$.historial[?(@.campo == 'invitados_definitivos')].valorAnterior", hasItem("no")))
                .andExpect(jsonPath("$.historial[?(@.campo == 'invitados_definitivos')].valorNuevo", hasItem("sí")))
                .andExpect(jsonPath("$.historial[?(@.campo == 'cantidad_invitados')].usuario.nombre", hasItem("Lucía Ferreyra")));
    }

    @Test
    void cambiarSoloLaCantidadDejaUnaSolaFilaYAvisa() throws Exception {
        registrar(ana, evento, 150, false).andExpect(status().isOk());
        entityManager.flush();

        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT campo, valor_anterior, valor_nuevo FROM modificacion_evento WHERE evento_id = ?", evento);
        assertThat(filas).singleElement().satisfies(f -> {
            assertThat(f).containsEntry("campo", "cantidad_invitados").containsEntry("valor_nuevo", "150");
            assertThat(f.get("valor_anterior")).isNull();
        });
        assertThat(jdbc.queryForList("""
                SELECT d.usuario_id FROM notificacion_destinatario d JOIN notificacion n USING (notificacion_id)
                WHERE n.evento_id = ? AND n.tipo = 'MODIFICACION'""", Long.class, evento))
                .contains(lucia.getId()).doesNotContain(ana.getId());
    }

    @Test
    void guardarLoMismoNoDejaRastro() throws Exception {
        registrar(lucia, evento, 180, true).andExpect(status().isOk());
        entityManager.flush();
        registrar(lucia, evento, 180, true).andExpect(status().isOk());
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM modificacion_evento WHERE evento_id = ?", Integer.class, evento)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE evento_id = ?", Integer.class, evento)).isEqualTo(1);
    }

    @Test
    void superarLaCapacidadDelSalonSeAceptaYLaFichaLaInforma() throws Exception {
        jdbc.update("UPDATE salon SET capacidad = 300 WHERE codigo = 'avril'");

        registrar(lucia, evento, 450, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidadInvitados").value(450))
                .andExpect(jsonPath("$.salon.capacidad").value(300));
    }

    @Test
    void conElEventoConfirmadoLaCantidadCambiaPeroSigueSiendoDefinitiva() throws Exception {
        long confirmado = escenario.evento("club", FECHA, "noche", "CONFIRMADO", lucia, ana, "Confirmado");

        registrar(ana, confirmado, 170, false)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("INVITADOS_DEFINITIVOS"));
        registrar(ana, confirmado, 170, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidadInvitados").value(170));
    }

    @Test
    void validaLaCantidad() throws Exception {
        registrar(lucia, evento, 0, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("cantidad"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("La cantidad de invitados tiene que ser mayor a 0."));
        registrar(lucia, evento, 5001, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("cantidad"));
    }

    @Test
    void siOtraPersonaGuardoEnElMedioNoSePisa() throws Exception {
        registrar(lucia, evento, 180, false).andExpect(status().isOk());
        entityManager.flush();

        mvc.perform(put("/api/v1/eventos/{id}/invitados", evento).header(HttpHeaders.AUTHORIZATION, personas.bearer(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"version":0,"cantidad":200,"definitivos":true}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EVENTO_MODIFICADO"));
    }

    @Test
    void quienesPuedenRegistrarlaCantidad() throws Exception {
        registrar(personas.de(RolCodigo.COORDINACION), evento, 100, false).andExpect(status().isOk());
        registrar(personas.de(RolCodigo.DIRECCION), evento, 110, false).andExpect(status().isOk());
        registrar(personas.usuario("sofia.invitados", "Sofía Méndez", RolCodigo.VENDEDORA), evento, 120, false)
                .andExpect(status().isForbidden());
        registrar(personas.usuario("carla.invitados", "Carla Núñez", RolCodigo.PLANNER), evento, 120, false)
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"ADMINISTRACION", "COMPRAS", "BARRA", "COCINA"})
    void elRestoDeLosPerfilesNoRegistraLaCantidad(RolCodigo rol) throws Exception {
        registrar(personas.de(rol), evento, 100, false).andExpect(status().isForbidden());
    }

    @Test
    void unEventoCanceladoNoSeModifica() throws Exception {
        long cancelado = escenario.evento("club", FECHA, "mediodia", "CANCELADO", lucia, "Cancelado");

        registrar(lucia, cancelado, 100, false)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("El evento está en Cancelado: no se puede cambiar la cantidad de invitados."));
    }

    @Test
    void elFormularioDeDatosYaNoCambiaLaCantidad() throws Exception {
        registrar(lucia, evento, 180, true).andExpect(status().isOk());
        entityManager.flush();
        int version = jdbc.queryForObject("SELECT version FROM evento WHERE evento_id = ?", Integer.class, evento);

        mvc.perform(put("/api/v1/eventos/{id}", evento).header(HttpHeaders.AUTHORIZATION, personas.bearer(lucia))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"version":%d,"nombre":"Bruno y Martina","tipoEventoId":2,"cantidadInvitados":10,
                                 "cliente":{"nombre":"Cliente de Bruno y Martina"},"contactos":[]}""".formatted(version)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/eventos/{id}", evento).header(HttpHeaders.AUTHORIZATION, personas.bearer(lucia)))
                .andExpect(jsonPath("$.cantidadInvitados").value(180));
    }

    private ResultActions registrar(Usuario quien, long id, int cantidad, boolean definitivos) throws Exception {
        int version = jdbc.queryForObject("SELECT version FROM evento WHERE evento_id = ?", Integer.class, id);
        return mvc.perform(put("/api/v1/eventos/{id}/invitados", id).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"version":%d,"cantidad":%d,"definitivos":%s}""".formatted(version, cantidad, definitivos)));
    }
}
