package ar.edu.utn.vastio.agenda.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Confirmar evento (UI-15): requisitos, transición y avisos.
 */
@PruebaDeIntegracion
@Transactional
class ConfirmacionIT {

    private static final LocalDate FECHA = LocalDate.now().plusMonths(2);

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
    void eventoListoParaConfirmar() {
        lucia = personas.usuario("lucia.confirmar", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        ana = personas.usuario("ana.confirmar", "Ana Sosa", RolCodigo.PLANNER);
        evento = escenario.evento("club", FECHA, "noche", "CONTRATADO", lucia, ana, "Bruno y Martina");
        jdbc.update("UPDATE evento SET cantidad_invitados = 180, invitados_definitivos = true WHERE evento_id = ?", evento);
        servicio(evento, 2, "Lomo");
        servicio(evento, 7, "Barra libre premium");
    }

    @Test
    void laPlannerAsignadaConfirmaYAvisaAComprasYCocina() throws Exception {
        Usuario compras = personas.de(RolCodigo.COMPRAS);
        Usuario cocina = personas.de(RolCodigo.COCINA);
        Usuario administracion = personas.de(RolCodigo.ADMINISTRACION);

        confirmar(ana, evento)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"))
                .andExpect(jsonPath("$.acciones.confirmar").value(false))
                .andExpect(jsonPath("$.requisitosConfirmacion").value(nullValue()))
                .andExpect(jsonPath("$.historial[?(@.estadoNuevo == 'CONFIRMADO')].estadoAnterior", hasItem("CONTRATADO")))
                .andExpect(jsonPath("$.historial[?(@.estadoNuevo == 'CONFIRMADO')].usuario.nombre", hasItem("Ana Sosa")));
        entityManager.flush();

        List<Long> destinatarios = jdbc.queryForList("""
                SELECT d.usuario_id FROM notificacion_destinatario d JOIN notificacion n USING (notificacion_id)
                WHERE n.evento_id = ? AND n.tipo = 'CONFIRMACION'""", Long.class, evento);
        assertThat(destinatarios).contains(compras.getId(), cocina.getId(), administracion.getId(), lucia.getId())
                .doesNotContain(ana.getId());
    }

    @Test
    void laFichaMuestraLosRequisitosCumplidos() throws Exception {
        ficha(ana, evento)
                .andExpect(jsonPath("$.acciones.confirmar").value(true))
                .andExpect(jsonPath("$.requisitosConfirmacion[*].codigo", contains("PLANNER", "INVITADOS", "SERVICIOS", "FECHA")))
                .andExpect(jsonPath("$.requisitosConfirmacion[*].cumplido", everyItem(is(true))))
                .andExpect(jsonPath("$.requisitosConfirmacion[0].detalle").value("Ana Sosa"))
                .andExpect(jsonPath("$.requisitosConfirmacion[1].detalle").value("180"))
                .andExpect(jsonPath("$.requisitosConfirmacion[2].titulo").value("Plato principal y Tipo de barra"));
    }

    @Test
    void sinLosRequisitosNoSeConfirmaYDiceQueFalta() throws Exception {
        long incompleto = escenario.evento("avril", FECHA, "noche", "CONTRATADO", lucia, "Incompleto");
        jdbc.update("UPDATE evento SET cantidad_invitados = 150 WHERE evento_id = ?", incompleto);
        servicio(incompleto, 2, "Lomo");

        ficha(personas.de(RolCodigo.COORDINACION), incompleto)
                .andExpect(jsonPath("$.requisitosConfirmacion[?(@.codigo == 'PLANNER')].cumplido", hasItem(false)))
                .andExpect(jsonPath("$.requisitosConfirmacion[?(@.codigo == 'INVITADOS')].detalle",
                        hasItem("Hay 150 previstos: falta marcar la cantidad como definitiva.")))
                .andExpect(jsonPath("$.requisitosConfirmacion[?(@.codigo == 'SERVICIOS')].detalle", hasItem("Falta cargar Tipo de barra.")));
        confirmar(personas.de(RolCodigo.COORDINACION), incompleto)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("REQUISITOS_PENDIENTES"))
                .andExpect(jsonPath("$.detail").value(containsString("Falta asignar la planner.")))
                .andExpect(jsonPath("$.detail").value(containsString("Falta cargar Tipo de barra.")));
    }

    @Test
    void unaFechaPasadaNoSeConfirma() throws Exception {
        long pasado = escenario.evento("avril", LocalDate.now().minusDays(1), "noche", "CONTRATADO", lucia, ana, "Pasado");
        jdbc.update("UPDATE evento SET cantidad_invitados = 150, invitados_definitivos = true WHERE evento_id = ?", pasado);
        servicio(pasado, 2, "Lomo");
        servicio(pasado, 7, "Barra");

        confirmar(ana, pasado)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("La fecha del evento ya pasó.")));
    }

    @Test
    void unaCategoriaRequeridaDadaDeBajaYaNoSeExige() throws Exception {
        jdbc.update("DELETE FROM servicio_contratado WHERE evento_id = ? AND categoria_id = 7", evento);
        jdbc.update("UPDATE categoria_servicio SET activo = false WHERE categoria_id = 7");

        confirmar(ana, evento).andExpect(status().isOk());
    }

    @Test
    void soloSeConfirmaUnEventoContratado() throws Exception {
        long senado = escenario.evento("avril", FECHA, "mediodia", "SENADO", lucia, ana, "Señado");

        confirmar(ana, senado)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("El evento está en Señado: se confirman eventos contratados."));
        ficha(ana, senado).andExpect(jsonPath("$.acciones.confirmar").value(false))
                .andExpect(jsonPath("$.requisitosConfirmacion").value(nullValue()));
    }

    @Test
    void quienesPuedenConfirmar() throws Exception {
        confirmar(lucia, evento).andExpect(status().isForbidden());
        confirmar(personas.usuario("carla.confirmar", "Carla Núñez", RolCodigo.PLANNER), evento).andExpect(status().isForbidden());
        ficha(lucia, evento).andExpect(jsonPath("$.acciones.confirmar").value(false));
        confirmar(personas.de(RolCodigo.DIRECCION), evento).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"ADMINISTRACION", "COMPRAS", "BARRA", "COCINA"})
    void elRestoDeLosPerfilesNoConfirma(RolCodigo rol) throws Exception {
        confirmar(personas.de(rol), evento).andExpect(status().isForbidden());
    }

    private void servicio(long id, int categoria, String descripcion) {
        jdbc.update("INSERT INTO servicio_contratado (evento_id, categoria_id, descripcion, usuario_id) VALUES (?, ?, ?, ?)",
                id, categoria, descripcion, lucia.getId());
    }

    private ResultActions confirmar(Usuario quien, long id) throws Exception {
        return mvc.perform(post("/api/v1/eventos/{id}/confirmacion", id).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)));
    }

    private ResultActions ficha(Usuario quien, long id) throws Exception {
        return mvc.perform(get("/api/v1/eventos/{id}", id).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)));
    }
}
