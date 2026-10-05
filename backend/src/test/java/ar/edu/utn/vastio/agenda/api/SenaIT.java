package ar.edu.utn.vastio.agenda.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.jayway.jsonpath.JsonPath;

import ar.edu.utn.vastio.Escenario;
import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Registrar seña (UI-11): la pre-reserva pasa a Señado.
 */
@PruebaDeIntegracion
@Transactional
class SenaIT {

    private static final LocalDate HOY = LocalDate.now();

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
    Usuario administracion;
    Usuario coordinacion;
    long preReserva;

    @BeforeEach
    void preReserva() throws Exception {
        lucia = personas.usuario("lucia.sena", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        administracion = personas.de(RolCodigo.ADMINISTRACION);
        coordinacion = personas.de(RolCodigo.COORDINACION);
        String respuesta = mvc.perform(post("/api/v1/eventos").header(HttpHeaders.AUTHORIZATION, personas.bearer(lucia))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"salonId":3,"fecha":"2032-06-19","turnoId":2,"tipoEventoId":1,"cliente":{"nombre":"Camila Ruiz"}}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        preReserva = ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }

    @Test
    void laTitularRegistraLaSenaYElEventoPasaASenado() throws Exception {
        registrar(lucia, preReserva, sena("150000.50", HOY, "30111222", "\"Mariela Ruiz\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SENADO"))
                .andExpect(jsonPath("$.sena.importe").value(150000.50))
                .andExpect(jsonPath("$.sena.fecha").value(HOY.toString()))
                .andExpect(jsonPath("$.sena.firmanteNombre").value("Mariela Ruiz"))
                .andExpect(jsonPath("$.sena.firmanteDni").value("30111222"))
                .andExpect(jsonPath("$.acciones.registrarSena").value(false))
                .andExpect(jsonPath("$.acciones.liberar").value(false))
                .andExpect(jsonPath("$.acciones.modificar").value(true))
                .andExpect(jsonPath("$.historial[?(@.estadoNuevo == 'SENADO')].estadoAnterior", hasItem("PRE_RESERVA")))
                .andExpect(jsonPath("$.historial[?(@.estadoNuevo == 'SENADO')].usuario.nombre", hasItem("Lucía Ferreyra")));
    }

    @Test
    void avisaAAdministracionYCoordinacionSinElImporte() throws Exception {
        registrar(lucia, preReserva, sena("150000", HOY, "30111222", null)).andExpect(status().isOk());
        entityManager.flush();

        Map<String, Object> aviso = jdbc.queryForMap(
                "SELECT notificacion_id, mensaje, usuario_origen_id FROM notificacion WHERE evento_id = ? AND tipo = 'SENA'", preReserva);
        assertThat(aviso).containsEntry("usuario_origen_id", lucia.getId());
        assertThat((String) aviso.get("mensaje")).startsWith("Seña registrada: Casamiento de Camila Ruiz · Santa Bárbara").doesNotContain("150");
        List<Long> destinatarios = jdbc.queryForList(
                "SELECT usuario_id FROM notificacion_destinatario WHERE notificacion_id = ?", Long.class, aviso.get("notificacion_id"));
        assertThat(destinatarios).contains(administracion.getId(), coordinacion.getId()).doesNotContain(lucia.getId());
    }

    @Test
    void coordinacionRegistraLaSenaDeCualquierVendedora() throws Exception {
        registrar(coordinacion, preReserva, sena("100000", HOY, "30111222", null)).andExpect(status().isOk());
    }

    @Test
    void otraVendedoraNoRegistraLaSena() throws Exception {
        registrar(personas.usuario("sofia.sena", "Sofía Méndez", RolCodigo.VENDEDORA), preReserva, sena("100000", HOY, "30111222", null))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"ADMINISTRACION", "PLANNER", "COMPRAS", "BARRA", "COCINA"})
    void elRestoDeLosPerfilesNoRegistraSenas(RolCodigo rol) throws Exception {
        registrar(personas.de(rol), preReserva, sena("100000", HOY, "30111222", null)).andExpect(status().isForbidden());
    }

    @Test
    void validaImporteFechaYDni() throws Exception {
        registrar(lucia, preReserva, sena("0", HOY, "3011", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[?(@.campo == 'importe')].mensaje", hasItem("El importe tiene que ser mayor a 0.")))
                .andExpect(jsonPath("$.errores[?(@.campo == 'firmanteDni')].mensaje", hasItem("Escribí el DNI con 7 u 8 números, sin puntos.")));
        registrar(lucia, preReserva, sena("100.125", HOY, "30111222", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("importe"));
        registrar(lucia, preReserva, """
                {"importe":1000}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("fechaPago")))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("firmanteDni")));
    }

    @Test
    void laFechaDelPagoNoPuedeSerFutura() throws Exception {
        registrar(lucia, preReserva, sena("100000", HOY.plusDays(1), "30111222", null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("FECHA_PAGO_FUTURA"));
    }

    @Test
    void soloSeSenaUnaPreReserva() throws Exception {
        registrar(lucia, preReserva, sena("100000", HOY, "30111222", null)).andExpect(status().isOk());
        registrar(lucia, preReserva, sena("100000", HOY, "30111222", null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("El evento está en Señado: la seña se registra sobre una pre-reserva."));

        long liberada = escenario.evento("avril", LocalDate.of(2032, 6, 19), "noche", "LIBERADA", lucia, "No prosperó");
        registrar(lucia, liberada, sena("100000", HOY, "30111222", null)).andExpect(status().isUnprocessableContent());
    }

    private ResultActions registrar(Usuario quien, long id, String json) throws Exception {
        return mvc.perform(post("/api/v1/eventos/{id}/sena", id).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String sena(String importe, LocalDate fecha, String dni, String firmanteNombre) {
        return """
                {"importe":%s,"fechaPago":"%s","firmanteDni":"%s","firmanteNombre":%s,"firmanteContacto":"351 444-0000"}"""
                .formatted(importe, fecha, dni, firmanteNombre);
    }
}
