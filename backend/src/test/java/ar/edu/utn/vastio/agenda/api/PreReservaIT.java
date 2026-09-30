package ar.edu.utn.vastio.agenda.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import ar.edu.utn.vastio.Escenario;
import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.comun.errores.Mensajes;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Registrar pre-reserva (UI-08) e historial de cambios de estado.
 */
@PruebaDeIntegracion
@Transactional
class PreReservaIT {

    private static final LocalDate FECHA = LocalDate.of(2031, 5, 10);
    private static final int ANIO = LocalDate.now(ZoneId.of("America/Argentina/Cordoba")).getYear();

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
    Usuario sofia;
    Usuario coordinacion;
    Usuario administracion;

    @BeforeEach
    void usuarios() {
        lucia = personas.usuario("lucia.pre", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        sofia = personas.usuario("sofia.pre", "Sofía Méndez", RolCodigo.VENDEDORA);
        coordinacion = personas.de(RolCodigo.COORDINACION);
        administracion = personas.de(RolCodigo.ADMINISTRACION);
    }

    @Test
    void laVendedoraPreReservaConUnClienteNuevoYQuedaEnElHistorial() throws Exception {
        MvcResult resultado = preReservar(lucia, pedido("avril", FECHA, "noche", """
                {"nombre":"Delfina Ríos","telefono":"351 555-1234"}""", null, null))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern("/api/v1/eventos/\\d+")))
                .andExpect(jsonPath("$.codigo").value(matchesPattern("EV-" + ANIO + "-\\d{5}")))
                .andExpect(jsonPath("$.nombre").value("Quince de Delfina Ríos"))
                .andExpect(jsonPath("$.estado").value("PRE_RESERVA"))
                .andReturn();
        long id = ((Number) JsonPath.read(resultado.getResponse().getContentAsString(), "$.id")).longValue();
        entityManager.flush();

        Map<String, Object> evento = jdbc.queryForMap(
                "SELECT e.vendedora_id, c.nombre AS cliente, c.telefono FROM evento e JOIN cliente c USING (cliente_id) WHERE evento_id = ?", id);
        assertThat(evento).containsEntry("vendedora_id", lucia.getId())
                .containsEntry("cliente", "Delfina Ríos")
                .containsEntry("telefono", "351 555-1234");

        Map<String, Object> cambio = jdbc.queryForMap(
                "SELECT estado_anterior, estado_nuevo, usuario_id, fecha_hora FROM cambio_estado_evento WHERE evento_id = ?", id);
        assertThat(cambio.get("estado_anterior")).isNull();
        assertThat(cambio).containsEntry("estado_nuevo", "PRE_RESERVA").containsEntry("usuario_id", lucia.getId());
        assertThat(cambio.get("fecha_hora")).isNotNull();
    }

    @Test
    void avisaAAdministracionYCoordinacionPeroNoAQuienLaRegistro() throws Exception {
        long id = idDe(preReservar(coordinacion, pedido("club", FECHA, "mediodia", """
                {"nombre":"Grupo Arcor"}""", null, lucia.getId())).andExpect(status().isCreated()));
        entityManager.flush();

        Map<String, Object> aviso = jdbc.queryForMap(
                "SELECT notificacion_id, tipo, mensaje, usuario_origen_id FROM notificacion WHERE evento_id = ?", id);
        assertThat(aviso).containsEntry("tipo", "EVENTO_NUEVO").containsEntry("usuario_origen_id", coordinacion.getId());
        assertThat((String) aviso.get("mensaje")).startsWith("Nueva pre-reserva: Quince de Grupo Arcor · Club de Campo ·");
        List<Long> destinatarios = jdbc.queryForList(
                "SELECT usuario_id FROM notificacion_destinatario WHERE notificacion_id = ?", Long.class, aviso.get("notificacion_id"));
        assertThat(destinatarios).contains(administracion.getId()).doesNotContain(coordinacion.getId(), lucia.getId());
    }

    @Test
    void losCodigosSonCorrelativosDentroDelAnio() throws Exception {
        String primero = codigoDe(preReservar(lucia, pedido("avril", FECHA, "mediodia", clienteNuevo(), null, null)));
        String segundo = codigoDe(preReservar(lucia, pedido("club", FECHA, "mediodia", clienteNuevo(), null, null)));

        int n = Integer.parseInt(primero.substring(8));
        assertThat(segundo).isEqualTo("EV-%d-%05d".formatted(ANIO, n + 1));
    }

    // ---------- exclusividad ----------

    @Test
    void unaUnidadOcupadaDevuelveFechaTomada() throws Exception {
        escenario.evento("avril", FECHA, "noche", "SENADO", sofia, "Casamiento Gómez");

        preReservar(lucia, pedido("avril", FECHA, "noche", clienteNuevo(), null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("FECHA_TOMADA"))
                .andExpect(jsonPath("$.detail").value(Mensajes.FECHA_TOMADA));
    }

    @Test
    void unaPreReservaLiberadaDejaLaFechaDisponible() throws Exception {
        escenario.evento("avril", FECHA, "noche", "LIBERADA", sofia, "No prosperó");

        preReservar(lucia, pedido("avril", FECHA, "noche", clienteNuevo(), null, null)).andExpect(status().isCreated());
    }

    @Test
    void unaUnidadBloqueadaNoSePreReserva() throws Exception {
        escenario.bloqueo("santa-barbara", FECHA, "noche", administracion, null);

        preReservar(lucia, pedido("santa-barbara", FECHA, "noche", clienteNuevo(), null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("UNIDAD_BLOQUEADA"))
                .andExpect(jsonPath("$.detail").value("Esa fecha está bloqueada (mantenimiento). Elegí otro salón, otra fecha u otro turno."));
    }

    // ---------- reglas ----------

    @Test
    void noSePreReservaUnaFechaPasada() throws Exception {
        preReservar(lucia, pedido("avril", LocalDate.now().minusDays(1), "noche", clienteNuevo(), null, null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("FECHA_PASADA"));
    }

    @Test
    void niSalonesNiTiposDadosDeBaja() throws Exception {
        jdbc.update("UPDATE salon SET activo = false WHERE codigo = 'club'");
        jdbc.update("UPDATE tipo_evento SET activo = false WHERE tipo_evento_id = 5");

        preReservar(lucia, pedido("club", FECHA, "noche", clienteNuevo(), null, null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SALON_DADO_DE_BAJA"));
        preReservar(lucia, pedido("avril", FECHA, "noche", clienteNuevo(), null, null).replace("\"tipoEventoId\":2", "\"tipoEventoId\":5"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("TIPO_DADO_DE_BAJA"));
    }

    @Test
    void laVendedoraSoloPreReservaASuNombre() throws Exception {
        preReservar(lucia, pedido("avril", FECHA, "noche", clienteNuevo(), null, sofia.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SOLO_A_TU_NOMBRE"));
    }

    @Test
    void coordinacionEligeUnaVendedoraActiva() throws Exception {
        preReservar(coordinacion, pedido("avril", FECHA, "noche", clienteNuevo(), null, null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("FALTA_VENDEDORA"));
        preReservar(coordinacion, pedido("avril", FECHA, "noche", clienteNuevo(), null, administracion.getId()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("VENDEDORA_INVALIDA"));

        long id = idDe(preReservar(coordinacion, pedido("avril", FECHA, "noche", clienteNuevo(), null, sofia.getId()))
                .andExpect(status().isCreated()));
        assertThat(jdbc.queryForObject("SELECT vendedora_id FROM evento WHERE evento_id = ?", Long.class, id)).isEqualTo(sofia.getId());
    }

    @Test
    void conUnClienteExistenteActualizaSuTelefono() throws Exception {
        long cliente = escenario.cliente("Delfina Ríos");

        long id = idDe(preReservar(lucia, pedido("avril", FECHA, "noche", """
                {"id":%d,"telefono":"351 444-0000"}""".formatted(cliente), "Los 15 de Delfi", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Los 15 de Delfi")));
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT cliente_id FROM evento WHERE evento_id = ?", Long.class, id)).isEqualTo(cliente);
        assertThat(jdbc.queryForObject("SELECT telefono FROM cliente WHERE cliente_id = ?", String.class, cliente)).isEqualTo("351 444-0000");
    }

    @Test
    void validaElClienteYLosDatosObligatorios() throws Exception {
        preReservar(lucia, """
                {"salonId":1,"fecha":"2031-05-10","cliente":{"documento":"12-345","email":"no"}}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("turnoId")))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("tipoEventoId")))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("cliente.identificado")))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("cliente.documento")))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("cliente.email")));
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"ADMINISTRACION", "PLANNER", "COMPRAS", "BARRA", "COCINA"})
    void soloVendedoraCoordinacionYDireccionPreReservan(RolCodigo rol) throws Exception {
        preReservar(personas.de(rol), pedido("avril", FECHA, "noche", clienteNuevo(), null, lucia.getId()))
                .andExpect(status().isForbidden());
    }

    // ---------- búsqueda de clientes ----------

    @Test
    void buscaClientesPorParteDelNombreOPorDocumento() throws Exception {
        escenario.cliente("Delfina Ríos");
        jdbc.update("INSERT INTO cliente (nombre, documento) VALUES ('Estudio Ríos y Asociados', '30711222334')");

        buscar(lucia, "ríos")
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].nombre").value("Delfina Ríos"));
        buscar(lucia, "30711222334").andExpect(jsonPath("$[0].nombre").value("Estudio Ríos y Asociados"));
        buscar(lucia, "r").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void comprasNoBuscaClientes() throws Exception {
        mvc.perform(get("/api/v1/clientes?buscar=rios").header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.COMPRAS)))
                .andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private static String clienteNuevo() {
        return "{\"nombre\":\"Cliente de prueba\"}";
    }

    private static String pedido(String salon, LocalDate fecha, String turno, String cliente, String nombre, Long vendedoraId) {
        int salonId = switch (salon) {
            case "avril" -> 1;
            case "club" -> 2;
            default -> 3;
        };
        return """
                {"salonId":%d,"fecha":"%s","turnoId":%d,"tipoEventoId":2,"nombre":%s,"vendedoraId":%s,"cliente":%s}"""
                .formatted(salonId, fecha, turno.equals("noche") ? 2 : 1, nombre == null ? "null" : "\"" + nombre + "\"",
                        vendedoraId, cliente);
    }

    private ResultActions preReservar(Usuario quien, String json) throws Exception {
        return mvc.perform(post("/api/v1/eventos").header(HttpHeaders.AUTHORIZATION, personas.bearer(quien))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions buscar(Usuario quien, String texto) throws Exception {
        return mvc.perform(get("/api/v1/clientes").param("buscar", texto).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)))
                .andExpect(status().isOk());
    }

    private static long idDe(ResultActions resultado) throws Exception {
        return ((Number) JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private static String codigoDe(ResultActions resultado) throws Exception {
        return JsonPath.read(resultado.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.codigo");
    }
}
