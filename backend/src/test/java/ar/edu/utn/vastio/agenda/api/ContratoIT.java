package ar.edu.utn.vastio.agenda.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import ar.edu.utn.vastio.Escenario;
import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Registrar firma de contrato (UI-12): el evento señado pasa a Contratado con el contrato digitalizado.
 */
@PruebaDeIntegracion
@Transactional
class ContratoIT {

    private static final LocalDate HOY = LocalDate.now();
    /** El escenario registra la seña un mes antes de la fecha del evento: queda 10 días antes de hoy. */
    private static final LocalDate FECHA = HOY.plusDays(20);
    private static final byte[] PDF = "%PDF-1.7\n contrato firmado".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

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

    @Value("${vastio.legajo.directorio}")
    String directorio;

    Usuario lucia;
    Usuario coordinacion;
    Usuario administracion;
    long senado;

    @BeforeEach
    void eventoSenado() {
        lucia = personas.usuario("lucia.contrato", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        coordinacion = personas.usuario("melina.contrato", "Melina Sifón", RolCodigo.COORDINACION);
        administracion = personas.de(RolCodigo.ADMINISTRACION);
        senado = escenario.evento("club", FECHA, "noche", "SENADO", lucia, "Bruno y Martina");
    }

    @Test
    void coordinacionRegistraLaFirmaYElEventoPasaAContratado() throws Exception {
        registrar(coordinacion, senado, HOY, pdf("contrato hoja 1.pdf"), new MockMultipartFile("archivos", "hoja 2.jpg", "image/jpeg", JPG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONTRATADO"))
                .andExpect(jsonPath("$.fechaFirmaContrato").value(HOY.toString()))
                .andExpect(jsonPath("$.documentos", hasSize(2)))
                .andExpect(jsonPath("$.documentos[0].nombreArchivo").value("contrato hoja 1.pdf"))
                .andExpect(jsonPath("$.documentos[0].tipo").value("CONTRATO"))
                .andExpect(jsonPath("$.documentos[0].mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.documentos[1].mimeType").value("image/jpeg"))
                .andExpect(jsonPath("$.documentos[1].usuario.nombre").value("Melina Sifón"))
                .andExpect(jsonPath("$.acciones.registrarFirma").value(false))
                .andExpect(jsonPath("$.historial[?(@.estadoNuevo == 'CONTRATADO')].estadoAnterior", hasItem("SENADO")))
                .andExpect(jsonPath("$.historial[?(@.estadoNuevo == 'CONTRATADO')].usuario.nombre", hasItem("Melina Sifón")));

        List<String> rutas = jdbc.queryForList("SELECT ruta FROM documento_evento WHERE evento_id = ? ORDER BY documento_id", String.class, senado);
        assertThat(rutas).hasSize(2).allSatisfy(r -> assertThat(r).startsWith(senado + "/"));
        assertThat(rutas.get(0)).endsWith(".pdf");
        assertThat(Files.readAllBytes(Path.of(directorio, rutas.get(0)))).isEqualTo(PDF);
    }

    @Test
    void avisaAAdministracionCoordinacionYLaTitular() throws Exception {
        Usuario otraCoordinacion = personas.de(RolCodigo.COORDINACION);
        registrar(coordinacion, senado, HOY, pdf("contrato.pdf")).andExpect(status().isOk());
        entityManager.flush();

        List<Long> destinatarios = jdbc.queryForList("""
                SELECT d.usuario_id FROM notificacion_destinatario d JOIN notificacion n USING (notificacion_id)
                WHERE n.evento_id = ? AND n.tipo = 'CONTRATO'""", Long.class, senado);
        assertThat(destinatarios).contains(administracion.getId(), otraCoordinacion.getId(), lucia.getId())
                .doesNotContain(coordinacion.getId());
    }

    @Test
    void laFichaOfreceRegistrarLaFirmaSoloACoordinacionYDireccion() throws Exception {
        ficha(coordinacion, senado).andExpect(jsonPath("$.acciones.registrarFirma").value(true));
        ficha(personas.de(RolCodigo.DIRECCION), senado).andExpect(jsonPath("$.acciones.registrarFirma").value(true));
        ficha(lucia, senado).andExpect(jsonPath("$.acciones.registrarFirma").value(false));
        ficha(administracion, senado).andExpect(jsonPath("$.acciones.registrarFirma").value(false));
    }

    @Test
    void laTitularYAdministracionDescarganElContrato() throws Exception {
        String ficha = registrar(coordinacion, senado, HOY, pdf("contrato firmado.pdf"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int documento = JsonPath.read(ficha, "$.documentos[0].id");

        for (Usuario quien : List.of(lucia, administracion, coordinacion)) {
            descargar(quien, senado, documento)
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("contrato")))
                    .andExpect(content().bytes(PDF));
        }
    }

    @Test
    void laPlannerYComprasVenLaFichaPeroNoElContrato() throws Exception {
        String ficha = registrar(coordinacion, senado, HOY, pdf("contrato.pdf"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int documento = JsonPath.read(ficha, "$.documentos[0].id");

        for (RolCodigo rol : List.of(RolCodigo.PLANNER, RolCodigo.COMPRAS)) {
            ficha(personas.de(rol), senado)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fechaFirmaContrato").value(HOY.toString()))
                    .andExpect(jsonPath("$.documentos").value(nullValue()));
            descargar(personas.de(rol), senado, documento).andExpect(status().isForbidden());
        }
        descargar(personas.usuario("sofia.contrato", "Sofía Méndez", RolCodigo.VENDEDORA), senado, documento)
                .andExpect(status().isForbidden());
    }

    @Test
    void unDocumentoDeOtroEventoNoSeDescarga() throws Exception {
        String ficha = registrar(coordinacion, senado, HOY, pdf("contrato.pdf"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int documento = JsonPath.read(ficha, "$.documentos[0].id");
        long otro = escenario.evento("avril", FECHA, "noche", "SENADO", lucia, "Otro evento");

        descargar(coordinacion, otro, documento).andExpect(status().isNotFound());
    }

    @Test
    void laVendedoraTitularNoRegistraLaFirma() throws Exception {
        registrar(lucia, senado, HOY, pdf("contrato.pdf")).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"ADMINISTRACION", "VENDEDORA", "PLANNER", "COMPRAS", "BARRA", "COCINA"})
    void elRestoDeLosPerfilesNoRegistraLaFirma(RolCodigo rol) throws Exception {
        registrar(personas.de(rol), senado, HOY, pdf("contrato.pdf")).andExpect(status().isForbidden());
    }

    @Test
    void pideLaFechaYAlMenosUnArchivo() throws Exception {
        mvc.perform(multipart("/api/v1/eventos/{id}/contrato", senado).file(pdf("contrato.pdf"))
                        .header(HttpHeaders.AUTHORIZATION, personas.bearer(coordinacion)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[?(@.campo == 'fechaFirma')].mensaje", hasItem("Elegí la fecha de firma.")));

        registrar(coordinacion, senado, HOY)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("FALTA_CONTRATO"));
    }

    @Test
    void laFechaDeFirmaNoEsFuturaNiAnteriorALaSena() throws Exception {
        registrar(coordinacion, senado, HOY.plusDays(1), pdf("contrato.pdf"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("FECHA_FIRMA_FUTURA"));
        registrar(coordinacion, senado, FECHA.minusMonths(1).minusDays(1), pdf("contrato.pdf"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("FECHA_FIRMA_ANTERIOR_A_SENA"));
        registrar(coordinacion, senado, FECHA.minusMonths(1), pdf("contrato.pdf")).andExpect(status().isOk());
    }

    @Test
    void soloAceptaPdfJpgOPngReconocidosPorSuContenido() throws Exception {
        registrar(coordinacion, senado, HOY, new MockMultipartFile("archivos", "contrato.pdf", "application/pdf", "no soy un PDF".getBytes()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("FORMATO_NO_ADMITIDO"))
                .andExpect(jsonPath("$.detail").value(containsString("contrato.pdf")));
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0};
        registrar(coordinacion, senado, HOY, new MockMultipartFile("archivos", "foto", "application/octet-stream", png))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentos[0].mimeType").value("image/png"));
    }

    @Test
    void limitaLaCantidadYElTamanoDeLosArchivos() throws Exception {
        MockMultipartFile[] once = new MockMultipartFile[11];
        Arrays.setAll(once, i -> pdf("hoja " + i + ".pdf"));
        registrar(coordinacion, senado, HOY, once)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("DEMASIADOS_ARCHIVOS"));

        byte[] grande = Arrays.copyOf(PDF, 10 * 1024 * 1024 + 1);
        registrar(coordinacion, senado, HOY, new MockMultipartFile("archivos", "grande.pdf", "application/pdf", grande))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ARCHIVO_GRANDE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documento_evento WHERE evento_id = ?", Integer.class, senado)).isZero();
    }

    @Test
    void soloSeFirmaUnEventoSenado() throws Exception {
        long preReserva = escenario.evento("avril", FECHA, "mediodia", "PRE_RESERVA", lucia, "Pre-reserva");
        registrar(coordinacion, preReserva, HOY, pdf("contrato.pdf"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("El evento está en Pre-reserva: la firma del contrato se registra sobre un evento señado."));

        registrar(coordinacion, senado, HOY, pdf("contrato.pdf")).andExpect(status().isOk());
        registrar(coordinacion, senado, HOY, pdf("contrato.pdf"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ESTADO_NO_PERMITE"));
    }

    private static MockMultipartFile pdf(String nombre) {
        return new MockMultipartFile("archivos", nombre, "application/pdf", PDF);
    }

    private ResultActions registrar(Usuario quien, long id, LocalDate fechaFirma, MockMultipartFile... archivos) throws Exception {
        MockMultipartHttpServletRequestBuilder pedido = multipart("/api/v1/eventos/{id}/contrato", id);
        for (MockMultipartFile archivo : archivos) {
            pedido.file(archivo);
        }
        return mvc.perform(pedido.param("fechaFirma", fechaFirma.toString())
                .header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)));
    }

    private ResultActions ficha(Usuario quien, long id) throws Exception {
        return mvc.perform(get("/api/v1/eventos/{id}", id).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)));
    }

    private ResultActions descargar(Usuario quien, long id, long documento) throws Exception {
        return mvc.perform(get("/api/v1/eventos/{id}/documentos/{documento}", id, documento)
                .header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)));
    }
}
