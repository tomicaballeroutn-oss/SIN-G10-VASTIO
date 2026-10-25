package ar.edu.utn.vastio.bebida.api;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.bebida.aplicacion.EstadisticaConsumoService;
import ar.edu.utn.vastio.bebida.aplicacion.EstadisticaConsumoService.Filtros;
import ar.edu.utn.vastio.comun.seguridad.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/estadisticas/consumo")
@PreAuthorize(Permisos.CONSULTAR_ESTADISTICAS_CONSUMO)
@Tag(name = "Estadísticas de consumo")
public class EstadisticaConsumoController {
    private final EstadisticaConsumoService estadisticas;

    public EstadisticaConsumoController(EstadisticaConsumoService estadisticas) {
        this.estadisticas = estadisticas;
    }

    @GetMapping
    @Operation(summary = "Consultar estadísticas históricas de consumo", description = """
            Solo eventos CERRADO y consumo_evento congelado, en botellas. Fechas de jornada inclusivas.
            Todos los filtros son opcionales; sin fechas incluye todo el historial. Bebida filtra cantidades,
            nunca la población de eventos. Promedios con cuatro decimales HALF_UP; null sin denominador.
            INVITADOS_REGISTRADOS excluye de numerador y denominador los eventos con invitados nulos o cero.
            Incluye resumen, grupos por bebida/tipo y detalle tipo-bebida. No consulta stock ni asistencia real.
            Solo Dirección, Coordinación, Administración y Compras. No modifica datos.""")
    @ApiResponse(responseCode = "400", description = "Formato, rango o combinación de filtros inválidos")
    @ApiResponse(responseCode = "401", description = "Sin sesión")
    @ApiResponse(responseCode = "403", description = "Perfil sin permiso")
    @ApiResponse(responseCode = "404", description = "Tipo, salón o bebida inexistente")
    public EstadisticaConsumoDto consultar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Short tipoEventoId,
            @RequestParam(required = false) Short salonId,
            @RequestParam(required = false) Long bebidaId) {
        return EstadisticaConsumoDto.de(estadisticas.consultar(new Filtros(desde, hasta, tipoEventoId, salonId, bebidaId)));
    }
}
