package ar.edu.utn.vastio.agenda.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.Escenario;
import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Consultar vista de cocina (A7-02). Hoy es el 10/05/2032 y el horizonte de Cocina, 15 días (hasta el 25/05).
 * Categorías de V10: 2 Menú y 5 Menús especiales se ven en cocina; 6 Bodega, no.
 */
@PruebaDeIntegracion
@Transactional
class CocinaIT {

    private static final ZoneId ZONA = ZoneId.of(VastioApplication.ZONA_HORARIA);
    private static final LocalDate HOY = LocalDate.of(2032, 5, 10);

    @Autowired
    MockMvc mvc;

    @Autowired
    Personas personas;

    @Autowired
    Escenario escenario;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    Clock reloj;

    Usuario lucia;
    Usuario ana;
    long hoy;
    long dentro;
    long fuera;
    long enCurso;
    long senado;
    long cancelado;

    @BeforeEach
    void eventos() {
        when(reloj.getZone()).thenReturn(ZONA);
        when(reloj.instant()).thenReturn(HOY.atTime(11, 0).atZone(ZONA).toInstant());
        lucia = personas.usuario("lucia.cocina", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        ana = personas.usuario("ana.cocina", "Ana Sosa", RolCodigo.PLANNER);

        hoy = escenario.evento("avril", HOY, "noche", "CONFIRMADO", lucia, ana, "Bruno y Martina");
        dentro = escenario.evento("club", HOY.plusDays(10), "mediodia", "CONFIRMADO", lucia, ana, "Corporativo Arcor");
        fuera = escenario.evento("avril", HOY.plusDays(20), "noche", "CONFIRMADO", lucia, ana, "Lejano");
        enCurso = escenario.evento("santa-barbara", HOY.minusDays(1), "noche", "EN_CURSO", lucia, ana, "Toda la noche");
        senado = escenario.evento("club", HOY.plusDays(2), "noche", "SENADO", lucia, "Todavía señado");
        cancelado = escenario.evento("santa-barbara", HOY.plusDays(3), "noche", "CANCELADO", lucia, ana, "Se canceló");

        jdbc.update("UPDATE evento SET cantidad_invitados = 180, hora_inicio = '21:30', observaciones_internas = 'Cliente complicado' WHERE evento_id = ?", hoy);
        jdbc.update("""
                INSERT INTO servicio_contratado (evento_id, categoria_id, descripcion, usuario_id)
                VALUES (?, 2, 'Lomo braseado', ?), (?, 5, NULL, ?), (?, 6, 'Malbec reserva', ?)""",
                hoy, ana.getId(), hoy, ana.getId(), hoy, ana.getId());
        long especiales = jdbc.queryForObject("SELECT servicio_id FROM servicio_contratado WHERE evento_id = ? AND categoria_id = 5",
                Long.class, hoy);
        jdbc.update("INSERT INTO servicio_renglon (servicio_id, orden, texto, cantidad) VALUES (?, 0, 'Celíaco', 2), (?, 1, 'Sin lactosa', 3)",
                especiales, especiales);
    }

    @Test
    void cocinaVeLosConfirmadosDeSuHorizonteYLoEnCurso() throws Exception {
        consultar(personas.de(RolCodigo.COCINA), "2032-05")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hoy").value("2032-05-10"))
                .andExpect(jsonPath("$.hasta").value("2032-05-25"))
                .andExpect(jsonPath("$.eventos[*].id", containsInAnyOrder((int) hoy, (int) dentro)))
                .andExpect(jsonPath("$.jornadasAnteriores[*].id", containsInAnyOrder((int) enCurso)))
                .andExpect(jsonPath("$.eventos[*].id", not(hasItem((int) fuera))))
                .andExpect(jsonPath("$.eventos[*].id", not(hasItem((int) senado))))
                .andExpect(jsonPath("$.eventos[*].id", not(hasItem((int) cancelado))));
    }

    @Test
    void elRestoDeLosPerfilesVeElMesEntero() throws Exception {
        consultar(ana, "2032-05")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasta").doesNotExist())
                .andExpect(jsonPath("$.eventos[*].id", containsInAnyOrder((int) hoy, (int) dentro, (int) fuera)))
                .andExpect(jsonPath("$.jornadasAnteriores[*].id", containsInAnyOrder((int) enCurso)));
    }

    @Test
    void conVariosPerfilesSeSumanLosPermisos() throws Exception {
        Usuario ambos = personas.usuario("jefe.cocina", "Julián Torres", RolCodigo.COCINA, RolCodigo.COMPRAS);
        consultar(ambos, "2032-05").andExpect(jsonPath("$.eventos[*].id", hasItem((int) fuera)));
    }

    @Test
    void laTarjetaTraeLoGastronomicoYNadaMas() throws Exception {
        String cuerpo = mvc.perform(get("/api/v1/cocina/eventos/{id}", hoy)
                        .header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.COCINA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Bruno y Martina"))
                .andExpect(jsonPath("$.hora").value("21:30:00"))
                .andExpect(jsonPath("$.salon.nombre").value("Avril"))
                .andExpect(jsonPath("$.turno").value("noche"))
                .andExpect(jsonPath("$.invitados").value(180))
                .andExpect(jsonPath("$.servicios[*].categoria", containsInAnyOrder("Menú", "Menús especiales")))
                .andExpect(jsonPath("$.servicios[0].detalle").value("Lomo braseado"))
                .andExpect(jsonPath("$.servicios[1].renglones[0].texto").value("Celíaco"))
                .andExpect(jsonPath("$.servicios[1].renglones[1].cantidad").value(3))
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain("Malbec", "Cliente complicado", "100000", "estado", "CONFIRMADO", "Cliente de",
                "importe", "30111222", "Lucía");
    }

    @Test
    void sinHoraDeInicioUsaLaDelTurno() throws Exception {
        consultar(personas.de(RolCodigo.COCINA), "2032-05")
                .andExpect(jsonPath("$.eventos[?(@.id == %d)].hora".formatted(dentro), hasItem("12:00:00")));
    }

    @Test
    void elAvisoAbreLaTarjetaSiElEventoSigueEnLaVista() throws Exception {
        tarjeta(personas.de(RolCodigo.COCINA), enCurso).andExpect(status().isOk());
        tarjeta(personas.de(RolCodigo.COCINA), cancelado)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("FUERA_DE_COCINA"));
        tarjeta(personas.de(RolCodigo.COCINA), senado).andExpect(status().isNotFound());
        tarjeta(personas.de(RolCodigo.COCINA), fuera).andExpect(status().isNotFound());
        tarjeta(ana, fuera).andExpect(status().isOk());
        tarjeta(ana, 999_999).andExpect(status().isNotFound());
    }

    @Test
    void sinMesUsaElActual() throws Exception {
        mvc.perform(get("/api/v1/cocina").header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.COCINA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mes").value("2032-05"));
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"DIRECCION", "COORDINACION", "PLANNER", "COMPRAS", "COCINA"})
    void laConsultanLosPerfilesDeLaMatriz(RolCodigo rol) throws Exception {
        consultar(personas.de(rol), "2032-05").andExpect(status().isOk());
        tarjeta(personas.de(rol), hoy).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"ADMINISTRACION", "VENDEDORA", "BARRA"})
    void elRestoNoLaConsulta(RolCodigo rol) throws Exception {
        consultar(personas.de(rol), "2032-05").andExpect(status().isForbidden());
        tarjeta(personas.de(rol), hoy).andExpect(status().isForbidden());
    }

    @Test
    void sinSesionNoHayVistaDeCocina() throws Exception {
        mvc.perform(get("/api/v1/cocina").param("mes", "2032-05")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2032-06-01", "2033-01-01", "2032-05-10"})
    void jornadasAnterioresSeVenSinCambiarSuFechaNiDuplicarse(String fecha) throws Exception {
        LocalDate actual = LocalDate.parse(fecha);
        when(reloj.instant()).thenReturn(actual.atTime(1, 0).atZone(ZONA).toInstant());
        jdbc.update("UPDATE evento SET estado = 'CERRADO' WHERE evento_id = ?", enCurso);
        long anterior = escenario.evento("avril", actual.minusDays(1), "mediodia", "EN_CURSO", lucia, ana, "Sigue la preparación");
        escenario.evento("club", actual.minusDays(1), "mediodia", "CANCELADO", lucia, ana, "Cancelado anterior");
        escenario.evento("santa-barbara", actual.minusDays(1), "mediodia", "CERRADO", lucia, ana, "Cerrado anterior");
        consultar(personas.de(RolCodigo.COCINA), actual.toString().substring(0, 7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jornadasAnteriores.length()").value(1))
                .andExpect(jsonPath("$.jornadasAnteriores[0].id").value(anterior))
                .andExpect(jsonPath("$.jornadasAnteriores[0].fecha").value(actual.minusDays(1).toString()))
                .andExpect(jsonPath("$.eventos[*].id", not(hasItem((int) anterior))))
                .andExpect(jsonPath("$.jornadasAnteriores[0].estado").doesNotExist())
                .andExpect(jsonPath("$.jornadasAnteriores[0].cliente").doesNotExist())
                .andExpect(jsonPath("$.jornadasAnteriores[0].observacionesInternas").doesNotExist());
        tarjeta(personas.de(RolCodigo.COCINA), anterior).andExpect(status().isOk());
    }

    @Test
    void horizonteConfigurableIncluyeAmbosExtremosYExcluyeAyerYElDiaSiguiente() throws Exception {
        jdbc.update("UPDATE parametro SET valor = '3' WHERE clave = 'HORIZONTE_COCINA_DIAS'");
        long limite = escenario.evento("avril", HOY.plusDays(3), "mediodia", "CONFIRMADO", lucia, ana, "Límite");
        long siguiente = escenario.evento("avril", HOY.plusDays(4), "mediodia", "CONFIRMADO", lucia, ana, "Siguiente");
        long ayer = escenario.evento("avril", HOY.minusDays(1), "mediodia", "CONFIRMADO", lucia, ana, "Ayer confirmado");
        Usuario cocina = personas.de(RolCodigo.COCINA);
        consultar(cocina, "2032-05")
                .andExpect(jsonPath("$.hasta").value("2032-05-13"))
                .andExpect(jsonPath("$.eventos[*].id", containsInAnyOrder((int) hoy, (int) limite)));
        tarjeta(cocina, hoy).andExpect(status().isOk());
        tarjeta(cocina, limite).andExpect(status().isOk());
        tarjeta(cocina, siguiente).andExpect(status().isNotFound());
        tarjeta(cocina, ayer).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"VENDEDORA", "ADMINISTRACION", "BARRA", "COMPRAS", "PLANNER", "DIRECCION", "COORDINACION"})
    void cocinaConOtrosPerfilesConservaElHorizonteQueCorresponde(RolCodigo otro) throws Exception {
        Usuario ambos = personas.usuario("combinado." + otro.name(), "Cocina combinada", RolCodigo.COCINA, otro);
        boolean general = java.util.Set.of(RolCodigo.COMPRAS, RolCodigo.PLANNER, RolCodigo.DIRECCION, RolCodigo.COORDINACION).contains(otro);
        tarjeta(ambos, fuera).andExpect(general ? status().isOk() : status().isNotFound());
        consultar(ambos, "2032-05").andExpect(status().isOk());
    }

    @Test
    void listadoMensualNoFiltraDatosSensibles() throws Exception {
        String cuerpo = consultar(personas.de(RolCodigo.COCINA), "2032-05")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("estado", "importe", "observaciones", "cliente", "contactos", "vendedora", "Cliente complicado", "Malbec", "30111222");
    }

    @Test
    void cocinaVendedoraConsultaCocinaPeroNoLaFichaAjena() throws Exception {
        Usuario ambos = personas.usuario("cocina.vendedora", "Cocina vendedora", RolCodigo.COCINA, RolCodigo.VENDEDORA);
        long propio = escenario.evento("avril", HOY.plusDays(1), "mediodia", "CONFIRMADO", ambos, ana, "Propio");
        tarjeta(ambos, propio).andExpect(status().isOk());
        tarjeta(ambos, hoy).andExpect(status().isOk());
        mvc.perform(get("/api/v1/eventos/{id}", propio).header(HttpHeaders.AUTHORIZATION, personas.bearer(ambos)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/eventos/{id}", hoy).header(HttpHeaders.AUTHORIZATION, personas.bearer(ambos)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("EVENTO_DE_OTRA_VENDEDORA"));
    }

    private ResultActions consultar(Usuario quien, String mes) throws Exception {
        return mvc.perform(get("/api/v1/cocina").param("mes", mes).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)));
    }

    private ResultActions tarjeta(Usuario quien, long id) throws Exception {
        return mvc.perform(get("/api/v1/cocina/eventos/{id}", id).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)));
    }
}
