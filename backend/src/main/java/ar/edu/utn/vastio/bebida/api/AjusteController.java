package ar.edu.utn.vastio.bebida.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.bebida.aplicacion.AjusteService;
import ar.edu.utn.vastio.bebida.aplicacion.AjusteService.DatosAjuste;
import ar.edu.utn.vastio.bebida.aplicacion.AjusteService.Resultado;
import ar.edu.utn.vastio.bebida.dominio.TipoMovimiento;
import ar.edu.utn.vastio.comun.seguridad.Permisos;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Registrar ajuste de stock (UI-38): recuento físico y rotura declarada. Dirección, Coordinación, Administración y
 * Compras. Cantidades en botellas enteras.
 */
@RestController
@RequestMapping("/api/v1/ajustes")
@PreAuthorize(Permisos.AJUSTE_STOCK)
@Tag(name = "Ajustes de stock", description = "Recuento físico y rotura: asientos nuevos, nunca se edita un movimiento")
public class AjusteController {

    private final AjusteService ajustes;

    public AjusteController(AjusteService ajustes) {
        this.ajustes = ajustes;
    }

    public record RecuentoRequest(
            @NotNull(message = "Elegí la ubicación.") Short ubicacionId,
            @NotNull(message = "Elegí la bebida.") Long bebidaId,
            @NotNull(message = "Cargá lo que contaste.") @PositiveOrZero(message = "Lo contado no puede ser negativo.")
            @Digits(integer = 8, fraction = 0, message = "Cargá botellas enteras.") BigDecimal cantidadContada,
            @NotNull(message = "Elegí el motivo.") Short motivoId,
            @Size(max = 255, message = "Usá hasta 255 caracteres.") String detalle) {
    }

    public record RoturaRequest(
            @NotNull(message = "Elegí la ubicación.") Short ubicacionId,
            @NotNull(message = "Elegí la bebida.") Long bebidaId,
            @NotNull(message = "Cargá la cantidad.") @Positive(message = "La cantidad tiene que ser mayor a 0.")
            @Digits(integer = 8, fraction = 0, message = "Cargá botellas enteras.") BigDecimal cantidad,
            @NotNull(message = "Elegí el motivo.") Short motivoId,
            @Size(max = 255, message = "Usá hasta 255 caracteres.") String detalle) {
    }

    /**
     * {@code registrado} false si el recuento coincidió con el saldo. {@code diferencia}: lo que entró (positiva) o salió
     * (negativa). {@code avisoSaldoNegativo}: quedó negativo y se avisó a Compras y Administración.
     */
    public record AjusteResponse(boolean registrado, Long movimientoId, TipoMovimiento tipo, BigDecimal saldoAnterior,
            BigDecimal saldo, BigDecimal diferencia, boolean avisoSaldoNegativo, OffsetDateTime fechaHora) {
        static AjusteResponse de(Resultado r) {
            var m = r.movimiento();
            return new AjusteResponse(m != null, m == null ? null : m.getId(), m == null ? null : m.getTipo(), r.saldoAnterior(),
                    r.saldo(), r.saldo().subtract(r.saldoAnterior()), r.avisoSaldoNegativo(), m == null ? null : m.getFechaHora());
        }
    }

    @PostMapping("/recuento")
    @Operation(summary = "Registrar un recuento físico", description = """
            Registra un AJUSTE por la diferencia entre lo contado y el saldo teórico. Si coinciden, no registra nada
            (registrado = false). Motivo activo del ámbito Ajuste; detalle obligatorio con «Otro».""")
    public AjusteResponse recuento(@Valid @RequestBody RecuentoRequest pedido, @AuthenticationPrincipal Jwt jwt) {
        return AjusteResponse.de(ajustes.registrarRecuento(new DatosAjuste(pedido.ubicacionId(), pedido.bebidaId(),
                pedido.cantidadContada(), pedido.motivoId(), vacioANulo(pedido.detalle())), UsuarioActual.de(jwt).id()));
    }

    @PostMapping("/rotura")
    @Operation(summary = "Declarar una rotura", description = """
            Registra una MERMA que sale de la ubicación. Nunca se rechaza por falta de saldo: si queda negativo, se avisa a
            Compras y Administración (ALERTA_STOCK).""")
    public AjusteResponse rotura(@Valid @RequestBody RoturaRequest pedido, @AuthenticationPrincipal Jwt jwt) {
        return AjusteResponse.de(ajustes.declararRotura(new DatosAjuste(pedido.ubicacionId(), pedido.bebidaId(), pedido.cantidad(),
                pedido.motivoId(), vacioANulo(pedido.detalle())), UsuarioActual.de(jwt).id()));
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
