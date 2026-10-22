package ar.edu.utn.vastio.agenda.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.Escenario;
import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.agenda.infraestructura.GeneradorComprobanteSena;
import ar.edu.utn.vastio.configuracion.aplicacion.LogoSalon;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Comprobante interno de la seña (M-07). Hoy es el 01/06/2032: los números son CS-2032-NNNNN. El escenario registra
 * las señas de 100.000 pesos con el DNI 30111222.
 */
@PruebaDeIntegracion
@Transactional
class ComprobanteSenaIT {

    private static final ZoneId ZONA = ZoneId.of(VastioApplication.ZONA_HORARIA);

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

    /** Sin configurar devuelve vacío: el salón no tiene logo (como hoy, hasta M-04). */
    @MockitoBean
    LogoSalon logos;

    Usuario lucia;
    long senado;

    @BeforeEach
    void evento() {
        when(reloj.getZone()).thenReturn(ZONA);
        when(reloj.instant()).thenReturn(LocalDate.of(2032, 6, 1).atTime(10, 30).atZone(ZONA).toInstant());
        lucia = personas.usuario("lucia.comprobante", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        senado = escenario.evento("avril", LocalDate.of(2032, 7, 10), "noche", "SENADO", lucia, "Bruno y Martina");
        jdbc.update("UPDATE evento SET firmante_nombre = 'Mariela Ruiz' WHERE evento_id = ?", senado);
    }

    @Test
    void laTitularDescargaElComprobanteConTodosLosDatos() throws Exception {
        byte[] pdf = descargar(lucia, senado)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("comprobante-sena-CS-2032-00001.pdf")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(texto(pdf)).contains("Comprobante de seña", "CS-2032-00001", "Emitido el 01/06/2032",
                GeneradorComprobanteSena.LEYENDA, "$ 100.000,00", "Cliente de Bruno y Martina", "Mariela Ruiz · DNI 30111222",
                "Bruno y Martina", "Avril", "10/07/2032 · Noche", "Lucía Ferreyra");
        assertThat(jdbc.queryForMap("SELECT numero, usuario_id FROM comprobante_sena WHERE evento_id = ?", senado))
                .containsEntry("numero", "CS-2032-00001").containsEntry("usuario_id", lucia.getId());
    }

    @Test
    void volverADescargarloRepiteElNumeroYOtroEventoTomaElSiguiente() throws Exception {
        long otro = escenario.evento("club", LocalDate.of(2032, 7, 17), "noche", "CONFIRMADO", lucia,
                personas.usuario("ana.comprobante", "Ana Sosa", RolCodigo.PLANNER), "Otro");

        descargar(lucia, senado).andExpect(status().isOk());
        descargar(personas.de(RolCodigo.ADMINISTRACION), senado)
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("CS-2032-00001")));
        descargar(lucia, otro)
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("CS-2032-00002")));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM comprobante_sena WHERE evento_id = ?", Integer.class, senado)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT usuario_id FROM comprobante_sena WHERE evento_id = ?", Long.class, senado))
                .as("queda quien lo emitió primero").isEqualTo(lucia.getId());
    }

    @Test
    void unaSenaEnDolaresSaleEnDolaresSinConversion() throws Exception {
        jdbc.update("UPDATE evento SET importe_sena = 1500, moneda_sena = 'USD' WHERE evento_id = ?", senado);

        assertThat(texto(descargar(lucia, senado).andReturn().getResponse().getContentAsByteArray()))
                .contains("US$ 1.500,00").doesNotContain("100.000");
    }

    @Test
    void conLogoDelSalonLoUsaEnElEncabezado() throws Exception {
        when(logos.de(anyShort())).thenReturn(Optional.of(png()));

        String pdf = new String(descargar(lucia, senado).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray(), StandardCharsets.ISO_8859_1);
        assertThat(pdf).contains("/Subtype/Image");
    }

    @Test
    void sinSenaNoHayComprobante() throws Exception {
        long preReserva = escenario.evento("santa-barbara", LocalDate.of(2032, 7, 24), "noche", "PRE_RESERVA", lucia, "Sin seña");

        descargar(lucia, preReserva)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SIN_SENA"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comprobante_sena", Integer.class)).isZero();
    }

    @Test
    void unEventoCanceladoConservaSuComprobante() throws Exception {
        long cancelado = escenario.evento("santa-barbara", LocalDate.of(2032, 7, 31), "noche", "CANCELADO", lucia,
                personas.usuario("carla.comprobante", "Carla Núñez", RolCodigo.PLANNER), "Se canceló");
        descargar(lucia, cancelado).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"DIRECCION", "COORDINACION", "ADMINISTRACION"})
    void loDescarganQuienesVenElImporte(RolCodigo rol) throws Exception {
        descargar(personas.de(rol), senado).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"PLANNER", "COMPRAS", "BARRA", "COCINA"})
    void quienesNoVenElImporteNoLoDescargan(RolCodigo rol) throws Exception {
        descargar(personas.de(rol), senado).andExpect(status().isForbidden());
    }

    @Test
    void otraVendedoraNoDescargaElComprobante() throws Exception {
        descargar(personas.usuario("sofia.comprobante", "Sofía Méndez", RolCodigo.VENDEDORA), senado)
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM comprobante_sena", Integer.class)).isZero();
    }

    @Test
    void sinSesionNoHayComprobante() throws Exception {
        mvc.perform(get("/api/v1/eventos/{id}/sena/comprobante", senado)).andExpect(status().isUnauthorized());
    }

    private ResultActions descargar(Usuario quien, long id) throws Exception {
        return mvc.perform(get("/api/v1/eventos/{id}/sena/comprobante", id)
                .header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)));
    }

    private static String texto(byte[] pdf) throws Exception {
        PdfReader lector = new PdfReader(pdf);
        try {
            return new PdfTextExtractor(lector).getTextFromPage(1);
        } finally {
            lector.close();
        }
    }

    private static byte[] png() throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "png", salida);
        return salida.toByteArray();
    }
}
