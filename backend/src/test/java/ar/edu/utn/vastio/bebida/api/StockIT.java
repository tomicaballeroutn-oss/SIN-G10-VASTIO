package ar.edu.utn.vastio.bebida.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import ar.edu.utn.vastio.Escenario;
import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Consultar stock (UI-27). El reloj está fijo: la jornada del test es el 10/03/2031. Los datos de demostración pueden
 * estar cargados en la misma base, así que se filtra por las bebidas de cada test. Cada test deshace sus cambios.
 */
@PruebaDeIntegracion
@Transactional
class StockIT {

    private static final ZoneId ZONA = ZoneId.of(VastioApplication.ZONA_HORARIA);
    private static final LocalDate JORNADA = LocalDate.of(2031, 3, 10);

    @MockitoBean
    Clock reloj;

    @Autowired
    MockMvc mvc;

    @Autowired
    Personas personas;

    @Autowired
    Escenario escenario;

    @Autowired
    JdbcTemplate jdbc;

    long ron;
    long gin;
    long vodka;

    @BeforeEach
    void escenario() throws Exception {
        ahora(JORNADA.atTime(22, 0));
        Usuario vendedora = personas.de(RolCodigo.VENDEDORA);
        Usuario planner = personas.de(RolCodigo.PLANNER);
        escenario.evento("avril", JORNADA, "noche", "CONFIRMADO", vendedora, planner, "Quince de prueba");
        escenario.evento("club", JORNADA, "noche", "PRE_RESERVA", vendedora, "Pre-reserva de prueba");
        ron = crearBebida("Ron de prueba", 12);
        gin = crearBebida("Gin de prueba", null);
        vodka = crearBebida("Vodka de prueba", null);
        saldo(1, ron, 10);   // bajo: stock mínimo 12
        saldo(1, gin, -3);   // negativo
        saldo(2, ron, 7);    // Barra Avril
        saldo(2, gin, 0);
        saldo(3, ron, 5);    // Barra Club de Campo
    }

    // ---------- barra: operación a ciegas ----------

    @Test
    void laBarraVeSoloLasBarrasConEventoEnLaJornada() throws Exception {
        pedir(get("/api/v1/stock/ubicaciones"), RolCodigo.BARRA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].ubicacion.id", contains(2)))
                .andExpect(jsonPath("$[0].evento.nombre").value("Quince de prueba"));
    }

    @Test
    void antesDeLasSeisSigueLaJornadaAnterior() throws Exception {
        ahora(JORNADA.plusDays(1).atTime(3, 0));
        pedir(get("/api/v1/stock/ubicaciones"), RolCodigo.BARRA).andExpect(jsonPath("$[*].ubicacion.id", contains(2)));

        ahora(JORNADA.plusDays(1).atTime(7, 0));
        pedir(get("/api/v1/stock/ubicaciones"), RolCodigo.BARRA).andExpect(jsonPath("$", empty()));
    }

    @Test
    void laBarraVeSoloElSaldoDeSuBarraSinEstadoNiStockMinimo() throws Exception {
        pedir(get("/api/v1/stock?ubicacionId=2"), RolCodigo.BARRA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ubicacion.nombre").value("Barra Avril"))
                .andExpect(jsonPath("$.renglones[?(@.bebida.id == %d)].cantidad".formatted(ron)).value(hasItem(7.0)))
                .andExpect(jsonPath("$.renglones[?(@.bebida.id == %d)]".formatted(gin)).isEmpty())
                .andExpect(jsonPath("$.renglones[*].estado").isEmpty())
                .andExpect(jsonPath("$.renglones[*].stockMinimo").isEmpty());
    }

    @Test
    void laBarraNoVeOtrasUbicacionesNiElTotal() throws Exception {
        pedir(get("/api/v1/stock?ubicacionId=1"), RolCodigo.BARRA).andExpect(status().isForbidden());
        pedir(get("/api/v1/stock?ubicacionId=3"), RolCodigo.BARRA).andExpect(status().isForbidden());
        pedir(get("/api/v1/stock"), RolCodigo.BARRA).andExpect(status().isForbidden());
    }

    // ---------- administración y compras ----------

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"DIRECCION", "COORDINACION", "ADMINISTRACION", "COMPRAS"})
    void venTodasLasUbicaciones(RolCodigo rol) throws Exception {
        pedir(get("/api/v1/stock/ubicaciones"), rol)
                .andExpect(jsonPath("$[*].ubicacion.id", hasItem(1)))
                .andExpect(jsonPath("$[*].ubicacion.id", hasItem(3)));
        pedir(get("/api/v1/stock?ubicacionId=3"), rol).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"VENDEDORA", "PLANNER", "COCINA"})
    void elRestoNoConsultaStock(RolCodigo rol) throws Exception {
        pedir(get("/api/v1/stock/ubicaciones"), rol).andExpect(status().isForbidden());
        pedir(get("/api/v1/stock?ubicacionId=1"), rol).andExpect(status().isForbidden());
    }

    @Test
    void elDepositoMadreListaTodasLasActivasConSuEstado() throws Exception {
        pedir(post("/api/v1/bebidas/" + vodka + "/baja"), RolCodigo.COMPRAS).andExpect(status().isOk());

        pedir(get("/api/v1/stock?ubicacionId=1"), RolCodigo.COMPRAS)
                .andExpect(jsonPath("$.renglones[?(@.bebida.id == %d)].estado".formatted(ron)).value(hasItem("BAJO")))
                .andExpect(jsonPath("$.renglones[?(@.bebida.id == %d)].stockMinimo".formatted(ron)).value(hasItem(12)))
                .andExpect(jsonPath("$.renglones[?(@.bebida.id == %d)].estado".formatted(gin)).value(hasItem("NEGATIVO")))
                .andExpect(jsonPath("$.renglones[?(@.bebida.id == %d)].cantidad".formatted(gin)).value(hasItem(-3.0)))
                // Dada de baja y sin saldo: no aparece.
                .andExpect(jsonPath("$.renglones[?(@.bebida.id == %d)]".formatted(vodka)).isEmpty());
    }

    @Test
    void unaBebidaActivaSinSaldoApareceEnElDepositoComoSinStock() throws Exception {
        pedir(get("/api/v1/stock?ubicacionId=1"), RolCodigo.ADMINISTRACION)
                .andExpect(jsonPath("$.renglones[?(@.bebida.id == %d)].estado".formatted(vodka)).value(hasItem("SIN_STOCK")))
                .andExpect(jsonPath("$.renglones[?(@.bebida.id == %d)].cantidad".formatted(vodka)).value(hasItem(0)));
    }

    @Test
    void enLasBarrasElEstadoBajoNoSeUsa() throws Exception {
        pedir(get("/api/v1/stock?ubicacionId=3"), RolCodigo.COMPRAS)
                .andExpect(jsonPath("$.renglones[?(@.bebida.id == %d)].estado".formatted(ron)).value(hasItem("OK")))
                .andExpect(jsonPath("$.renglones[?(@.bebida.id == %d)]".formatted(vodka)).isEmpty());
    }

    @Test
    void elTotalSumaTodasLasUbicaciones() throws Exception {
        pedir(get("/api/v1/stock"), RolCodigo.DIRECCION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ubicacion").doesNotExist())
                .andExpect(jsonPath("$.renglones[?(@.bebida.id == %d)].cantidad".formatted(ron)).value(hasItem(22.0)))
                .andExpect(jsonPath("$.renglones[?(@.bebida.id == %d)].estado".formatted(ron)).value(hasItem("OK")));
    }

    private void ahora(LocalDateTime momento) {
        when(reloj.getZone()).thenReturn(ZONA);
        when(reloj.instant()).thenReturn(momento.atZone(ZONA).toInstant());
    }

    private void saldo(int ubicacion, long bebida, int cantidad) {
        jdbc.update("INSERT INTO stock_ubicacion (ubicacion_id, bebida_id, cantidad, fecha_actualizacion) VALUES (?, ?, ?, now())",
                ubicacion, bebida, cantidad);
    }

    private long crearBebida(String nombre, Integer stockMinimo) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/bebidas").header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.ADMINISTRACION))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nombre":"%s","presentacion":"750 ml","tipoId":3,"unidadId":1,"unidadesPorBulto":6,"stockMinimo":%s}"""
                                .formatted(nombre, stockMinimo)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }

    private ResultActions pedir(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pedido, RolCodigo rol)
            throws Exception {
        return mvc.perform(pedido.header(HttpHeaders.AUTHORIZATION, personas.bearer(rol)));
    }
}
