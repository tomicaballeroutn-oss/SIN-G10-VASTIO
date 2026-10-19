package ar.edu.utn.vastio.bebida.api;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.bebida.api.EntregaDto.EntregaResponse;
import ar.edu.utn.vastio.bebida.api.EntregaDto.EventoDeLaJornada;
import ar.edu.utn.vastio.bebida.api.EntregaDto.RetiroRequest;
import ar.edu.utn.vastio.bebida.aplicacion.EntregaService;
import ar.edu.utn.vastio.bebida.aplicacion.EntregaService.PantallaEntrega;
import ar.edu.utn.vastio.bebida.aplicacion.EntregaService.Retiro;
import ar.edu.utn.vastio.comun.seguridad.Permisos;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.usuarios.aplicacion.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Registrar salida a barra (UI-31) y retiro adicional. La encargada de barra, Compras, Coordinación y Dirección; la
 * encargada, solo los eventos de la jornada. Ninguna respuesta trae saldos (operación a ciegas).
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize(Permisos.OPERAR_BARRA)
@Tag(name = "Barra", description = "Salidas a barra y retiros adicionales imputados al evento")
public class EntregaController {

    private final EntregaService entregas;
    private final UsuarioService usuarios;

    public EntregaController(EntregaService entregas, UsuarioService usuarios) {
        this.entregas = entregas;
        this.usuarios = usuarios;
    }

    @GetMapping("/entregas/jornada")
    @Operation(summary = "Eventos de la jornada con sus barras", description = """
            Eventos confirmados, en curso o realizados de la jornada actual (antes de las 06:00 es la de ayer), con las
            barras activas de su salón. Solo se registran retiros con el evento en curso.""")
    public List<EventoDeLaJornada> jornada() {
        return entregas.jornada().stream().map(EventoDeLaJornada::de).toList();
    }

    @GetMapping("/eventos/{eventoId}/entregas/{barraId}")
    @Operation(summary = "Entrega de un evento a una barra", description = """
            Lo ya entregado por bebida, cada retiro con quién y cuándo, el origen y las bebidas activas. Sin saldos. La
            encargada de barra solo pide eventos de la jornada y barras de su salón: lo demás es 403.""")
    public EntregaResponse pantalla(@PathVariable long eventoId, @PathVariable short barraId, @AuthenticationPrincipal Jwt jwt) {
        return respuesta(entregas.pantalla(eventoId, barraId, UsuarioActual.de(jwt)));
    }

    @PostMapping("/eventos/{eventoId}/entregas/{barraId}/retiros")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un retiro a la barra", description = """
            Solo con el evento en curso (si no, 409). El primer retiro abre la entrega y registra SALIDA_BARRA; los
            siguientes, RETIRO_ADICIONAL en la misma entrega. Sale de la ubicación de abastecimiento de la barra o, con
            directo=true y si la barra lo permite, del depósito madre. Bebidas activas, sin repetir, en botellas enteras.
            Si la barra tiene abierta la entrega de otro evento, 409 («Primero registrá la devolución de …»). Un saldo
            negativo se acepta y avisa a Compras y Administración (ALERTA_STOCK). Devuelve la entrega actualizada.""")
    public EntregaResponse retirar(@PathVariable long eventoId, @PathVariable short barraId,
            @Valid @RequestBody RetiroRequest pedido, @AuthenticationPrincipal Jwt jwt) {
        return respuesta(entregas.retirar(eventoId, barraId, pedido.datos(), UsuarioActual.de(jwt)));
    }

    private EntregaResponse respuesta(PantallaEntrega pantalla) {
        Map<Long, String> nombres = usuarios.nombres(pantalla.retiros().stream().map(Retiro::usuarioId).distinct().toList());
        return EntregaResponse.de(pantalla, nombres);
    }
}
