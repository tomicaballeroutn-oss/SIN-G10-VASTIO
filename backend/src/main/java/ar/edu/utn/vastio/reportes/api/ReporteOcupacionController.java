package ar.edu.utn.vastio.reportes.api;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.Permisos;
import ar.edu.utn.vastio.reportes.aplicacion.ReporteOcupacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Reporte comercial de ocupación para Dirección y Coordinación. */
@RestController
@RequestMapping("/api/v1/reportes/ocupacion")
@Tag(name = "Reportes", description = "Ocupación por salón, temporada, tipo de evento y vendedora")
public class ReporteOcupacionController {

    private final ReporteOcupacionService reportes;

    public ReporteOcupacionController(ReporteOcupacionService reportes) {
        this.reportes = reportes;
    }

    @GetMapping
    @PreAuthorize(Permisos.CONSULTAR_REPORTE_OCUPACION)
    @Operation(summary = "Reporte de ocupación", description = "Incluye eventos señados, contratados, confirmados, en curso, realizados y cerrados; excluye pre-reservas, liberados y cancelados. Temporada alta: octubre a diciembre; baja: enero a septiembre.")
    public ReporteOcupacionDto consultar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Short salonId,
            @RequestParam(required = false) Short tipoEventoId,
            @RequestParam(required = false) Long vendedoraId,
            @RequestParam(required = false) String regimen) {
        if (hasta.isBefore(desde)) {
            throw ProblemaException.reglaDeNegocio("PERIODO_INVALIDO", "La fecha de fin debe ser igual o posterior a la fecha de inicio.");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) > 3660) {
            throw ProblemaException.reglaDeNegocio("PERIODO_EXTENSO", "Elegí un período de hasta diez años.");
        }
        if (regimen != null && !regimen.isBlank() && !regimen.equals("ALTA") && !regimen.equals("BAJA")) {
            throw ProblemaException.reglaDeNegocio("REGIMEN_INVALIDO", "Elegí temporada alta o baja.");
        }
        return reportes.consultar(desde, hasta, salonId, tipoEventoId, vendedoraId, regimen);
    }
}
