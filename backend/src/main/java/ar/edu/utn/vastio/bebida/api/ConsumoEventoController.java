package ar.edu.utn.vastio.bebida.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.bebida.aplicacion.ConsultaConsumoService;
import ar.edu.utn.vastio.comun.seguridad.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/eventos/{eventoId}/consumo")
@PreAuthorize(Permisos.CONSULTAR_CONSUMO)
@Tag(name = "Consumo por evento")
public class ConsumoEventoController {
    private final ConsultaConsumoService consultas;

    public ConsumoEventoController(ConsultaConsumoService consultas) {
        this.consultas = consultas;
    }

    @GetMapping
    @Operation(summary = "Consultar consumo de un evento cerrado", description = """
            Consolidado congelado en consumo_evento, por bebida y en botellas. Incluye todas las barras.
            Por invitado divide por la cantidad de invitados, no por asistencia real; null si es desconocida o cero.
            Un evento cerrado sin registros devuelve una lista vacía y consumo total cero.
            Acceso: Dirección, Coordinación, Administración y Compras. No produce cambios ni consulta stock.""")
    @ApiResponse(responseCode = "403", description = "Perfil sin permiso de consulta")
    @ApiResponse(responseCode = "404", description = "Evento inexistente")
    @ApiResponse(responseCode = "409", description = "EVENTO_NO_CERRADO: consumo aún no consolidado")
    public ConsumoEventoDto consultar(@PathVariable long eventoId) {
        return ConsumoEventoDto.de(consultas.consultar(eventoId));
    }
}
