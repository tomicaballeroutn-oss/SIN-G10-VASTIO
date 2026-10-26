package ar.edu.utn.vastio.agenda.api;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.agenda.aplicacion.ComprobanteSenaService;
import ar.edu.utn.vastio.comun.seguridad.Permisos;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Comprobante interno de la seña (M-07).
 */
@RestController
@RequestMapping("/api/v1/eventos")
@Tag(name = "Eventos")
public class ComprobanteSenaController {

    private final ComprobanteSenaService comprobantes;

    public ComprobanteSenaController(ComprobanteSenaService comprobantes) {
        this.comprobantes = comprobantes;
    }

    @PostMapping("/{id}/sena/comprobante")
    @PreAuthorize(Permisos.COMPROBANTE_SENA)
    @Operation(summary = "Emitir comprobante interno de seña", description = """
            Primera emisión: guarda el PDF original, número CS-AAAA-NNNNN, fecha y emisor (201).
            Si existe, devuelve exactamente el original (200), sin consumir otro número.
            Solo quienes ven el importe; un cancelado conserva su comprobante. No es una factura.""")
    @ApiResponse(responseCode = "422", description = "SIN_SENA o NUMERADOR_COMPROBANTE_AGOTADO")
    @ApiResponse(responseCode = "409", description = "Comprobante anterior sin PDF original: requiere revisión")
    @ApiResponse(responseCode = "500", description = "Generación fallida: emisión y numerador se revierten")
    public ResponseEntity<byte[]> emitir(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        var c = comprobantes.emitir(id, UsuarioActual.de(jwt));
        return respuesta(c, c.creado() ? HttpStatus.CREATED : HttpStatus.OK, id);
    }

    @GetMapping("/{id}/sena/comprobante")
    @PreAuthorize(Permisos.COMPROBANTE_SENA)
    @Operation(summary = "Descargar comprobante existente", description = """
            Devuelve el PDF original sin modificar datos, consultar logos ni regenerar el documento.
            No emite: si no existe responde 404. Solo quienes ven el importe de esa seña.""")
    @ApiResponse(responseCode = "404", description = "COMPROBANTE_NO_EMITIDO")
    @ApiResponse(responseCode = "403", description = "No ve el importe de esa seña")
    @ApiResponse(responseCode = "409", description = "Comprobante anterior sin PDF original")
    public ResponseEntity<byte[]> comprobante(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return respuesta(comprobantes.descargar(id, UsuarioActual.de(jwt)), HttpStatus.OK, id);
    }

    private ResponseEntity<byte[]> respuesta(ComprobanteSenaService.Comprobante c, HttpStatus estado, long id) {
        return ResponseEntity.status(estado)
                .location(URI.create("/api/v1/eventos/" + id + "/sena/comprobante"))
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("comprobante-sena-" + c.numero() + ".pdf", StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(c.pdf());
    }
}
