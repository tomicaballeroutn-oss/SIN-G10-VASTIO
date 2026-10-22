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

    @GetMapping("/{id}/sena/comprobante")
    @PreAuthorize(Permisos.COMPROBANTE_SENA)
    @Operation(summary = "Comprobante interno de la seña (PDF)", description = """
            Para quien ve el importe (Dirección, Coordinación, Administración y la vendedora titular), una vez señado el
            evento. La primera descarga asigna el número correlativo CS-AAAA-NNNNN; las siguientes lo repiten con la misma
            fecha de emisión. No es una factura.""")
    @ApiResponse(responseCode = "403", description = "No ve el importe de esa seña")
    @ApiResponse(responseCode = "422", description = "El evento todavía no tiene seña (SIN_SENA)")
    public ResponseEntity<byte[]> comprobante(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        ComprobanteSenaService.Comprobante c = comprobantes.emitir(id, UsuarioActual.de(jwt));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("comprobante-sena-" + c.numero() + ".pdf", StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(c.pdf());
    }
}
