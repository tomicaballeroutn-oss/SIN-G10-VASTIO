package ar.edu.utn.vastio.agenda.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import ar.edu.utn.vastio.Escenario;
import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.agenda.aplicacion.ComprobanteSenaService;
import ar.edu.utn.vastio.agenda.infraestructura.GeneradorComprobanteSena;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.configuracion.aplicacion.LogoSalon;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/** Sin transacción de test: cada emisión confirma o revierte como en producción. */
@PruebaDeIntegracion
class ComprobanteSenaTransaccionesIT {
    private static final AtomicInteger SERIE = new AtomicInteger();
    private static final ZoneId ZONA = ZoneId.of(VastioApplication.ZONA_HORARIA);
    @Autowired ComprobanteSenaService servicio;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transacciones;
    @Autowired Personas personas;
    @Autowired Escenario escenario;
    @Autowired MockMvc mvc;
    @MockitoBean Clock reloj;
    @MockitoBean LogoSalon logos;
    @MockitoSpyBean GeneradorComprobanteSena generador;
    final List<Long> eventos = new ArrayList<>();
    Usuario lucia;
    UsuarioActual quien;

    @BeforeEach
    void preparar() {
        when(reloj.getZone()).thenReturn(ZONA);
        when(reloj.instant()).thenReturn(LocalDate.of(2044, 6, 1).atTime(10, 30).atZone(ZONA).toInstant());
        lucia = personas.usuario("lucia.tx." + SERIE.incrementAndGet(), "Lucía Ferreyra", RolCodigo.VENDEDORA);
        quien = new UsuarioActual(lucia.getId(), Set.of("VENDEDORA"));
    }

    long evento() {
        long id = escenario.evento("avril", LocalDate.of(2044, 7, 1).plusDays(SERIE.incrementAndGet()), "noche", "SENADO", lucia, "Prueba transaccional");
        eventos.add(id);
        return id;
    }

    @RepeatedTest(3)
    void primerasEmisionesConcurrentesDelMismoEventoDevuelvenUnSoloPdf() throws Exception {
        long id = evento();
        var resultados = concurrentes(java.util.Collections.nCopies(6, id));
        assertThat(resultados).extracting(ComprobanteSenaService.Comprobante::numero).containsOnly("CS-2044-00001");
        for (var r : resultados) assertThat(r.pdf()).isEqualTo(resultados.get(0).pdf());
        assertThat(resultados.stream().filter(ComprobanteSenaService.Comprobante::creado).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comprobante_sena WHERE evento_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT ultimo FROM numerador_comprobante WHERE anio = 2044", Integer.class)).isEqualTo(1);
    }

    @RepeatedTest(3)
    void eventosConcurrentesTomanNumerosDistintosYCorrelativos() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) ids.add(evento());
        var resultados = concurrentes(ids);
        assertThat(resultados).extracting(ComprobanteSenaService.Comprobante::numero)
                .containsExactlyInAnyOrder("CS-2044-00001", "CS-2044-00002", "CS-2044-00003", "CS-2044-00004", "CS-2044-00005", "CS-2044-00006");
    }

    private List<ComprobanteSenaService.Comprobante> concurrentes(List<Long> ids) throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        try (var hilos = Executors.newFixedThreadPool(6)) {
            List<java.util.concurrent.Future<ComprobanteSenaService.Comprobante>> futuros = new ArrayList<>();
            for (long id : ids) futuros.add(hilos.submit(() -> { largada.await(); return servicio.emitir(id, quien); }));
            largada.countDown();
            List<ComprobanteSenaService.Comprobante> resultado = new ArrayList<>();
            for (var futuro : futuros) resultado.add(futuro.get(30, TimeUnit.SECONDS));
            return resultado;
        }
    }

    @Test
    void falloDelGeneradorRespondeErrorYRevierteComprobanteYNumerador() throws Exception {
        long id = evento();
        doThrow(new IllegalStateException("fallo simulado")).when(generador).generar(any());
        mvc.perform(post("/api/v1/eventos/{id}/sena/comprobante", id).header(HttpHeaders.AUTHORIZATION, personas.bearer(lucia)))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.codigo").value("COMPROBANTE_GENERACION_FALLIDA"));
        sinEmision(id);
        reset(generador);
        assertThat(servicio.emitir(id, quien).numero()).isEqualTo("CS-2044-00001");
    }

    @Test
    void pdfInvalidoNoSePersisteNiConsumeNumero() {
        long id = evento();
        doReturn(new byte[] {1, 2, 3}).when(generador).generar(any());
        assertThatThrownBy(() -> servicio.emitir(id, quien)).isInstanceOf(ProblemaException.class);
        sinEmision(id);
        reset(generador);
        assertThat(servicio.emitir(id, quien).numero()).isEqualTo("CS-2044-00001");
    }

    @Test
    void rollbackPosteriorAlPdfGuardadoTambienRevierteElNumerador() {
        long id = evento();
        assertThatThrownBy(() -> transacciones.executeWithoutResult(t -> {
            servicio.emitir(id, quien);
            throw new IllegalStateException("rollback después de guardar");
        })).isInstanceOf(IllegalStateException.class);
        sinEmision(id);
        assertThat(servicio.emitir(id, quien).numero()).isEqualTo("CS-2044-00001");
    }

    @Test
    void ultimoNumeroNoDesbordaYUnAnioNuevoEmpiezaEnUno() {
        jdbc.update("INSERT INTO numerador_comprobante(anio, ultimo) VALUES (2044, 99998)");
        long primero = evento();
        assertThat(servicio.emitir(primero, quien).numero()).isEqualTo("CS-2044-99999");
        long segundo = evento();
        assertThatThrownBy(() -> servicio.emitir(segundo, quien)).isInstanceOfSatisfying(ProblemaException.class,
                p -> assertThat(p.getCodigo()).isEqualTo("NUMERADOR_COMPROBANTE_AGOTADO"));
        assertThat(jdbc.queryForObject("SELECT ultimo FROM numerador_comprobante WHERE anio = 2044", Integer.class)).isEqualTo(99999);
        assertThat(servicio.descargar(primero, quien).numero()).isEqualTo("CS-2044-99999");
        when(reloj.instant()).thenReturn(LocalDate.of(2045, 1, 1).atStartOfDay(ZONA).toInstant());
        assertThat(servicio.emitir(segundo, quien).numero()).isEqualTo("CS-2045-00001");
    }

    @Test
    void laBaseImpideCambiarOBorrarElOriginal() {
        long id = evento();
        var original = servicio.emitir(id, quien);
        assertThatThrownBy(() -> jdbc.update("UPDATE comprobante_sena SET numero = 'CS-2044-00999' WHERE evento_id = ?", id)).hasMessageContaining("es inmutable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM comprobante_sena WHERE evento_id = ?", id)).hasMessageContaining("es inmutable");
        assertThat(servicio.descargar(id, quien).pdf()).isEqualTo(original.pdf());
    }

    @Test
    void unRegistroAnteriorSinPdfNoSeRegeneraNiSeRenumera() {
        long id = evento();
        transacciones.executeWithoutResult(t -> {
            jdbc.execute("SET LOCAL session_replication_role = replica");
            jdbc.update("INSERT INTO comprobante_sena(evento_id,numero,fecha_emision,usuario_id) VALUES (?,'CS-2044-00007','2044-05-01T10:30:00-03:00',?)", id, lucia.getId());
        });
        var antes = jdbc.queryForMap("SELECT numero, fecha_emision, usuario_id FROM comprobante_sena WHERE evento_id = ?", id);
        assertThatThrownBy(() -> servicio.descargar(id, quien)).isInstanceOfSatisfying(ProblemaException.class,
                p -> assertThat(p.getCodigo()).isEqualTo("COMPROBANTE_SIN_ORIGINAL"));
        assertThatThrownBy(() -> servicio.emitir(id, quien)).isInstanceOfSatisfying(ProblemaException.class,
                p -> assertThat(p.getCodigo()).isEqualTo("COMPROBANTE_SIN_ORIGINAL"));
        assertThat(jdbc.queryForMap("SELECT numero, fecha_emision, usuario_id FROM comprobante_sena WHERE evento_id = ?", id)).isEqualTo(antes);
        org.mockito.Mockito.verify(generador, org.mockito.Mockito.never()).generar(any());
    }

    @Test
    void unAnioFueraDeCuatroCifrasNoConsumeNumero() {
        long id = evento();
        when(reloj.instant()).thenReturn(LocalDate.of(10000, 1, 1).atStartOfDay(ZONA).toInstant());
        assertThatThrownBy(() -> servicio.emitir(id, quien)).isInstanceOfSatisfying(ProblemaException.class,
                p -> assertThat(p.getCodigo()).isEqualTo("ANIO_COMPROBANTE_INVALIDO"));
        sinEmision(id);
    }

    private void sinEmision(long id) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comprobante_sena WHERE evento_id = ?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM numerador_comprobante WHERE anio = 2044", Integer.class)).isZero();
    }

    @AfterEach
    void limpiar() {
        // Solo la base descartable del test: permite retirar las filas inmutables de esta prueba.
        transacciones.executeWithoutResult(t -> {
            jdbc.execute("SET LOCAL session_replication_role = replica");
            for (long id : eventos) {
                jdbc.update("DELETE FROM comprobante_sena WHERE evento_id = ?", id);
                jdbc.update("DELETE FROM evento WHERE evento_id = ?", id);
            }
            jdbc.update("DELETE FROM numerador_comprobante WHERE anio IN (2044, 2045)");
        });
    }
}
