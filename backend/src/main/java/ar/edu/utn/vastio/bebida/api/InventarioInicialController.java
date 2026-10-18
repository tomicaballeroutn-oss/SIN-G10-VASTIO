package ar.edu.utn.vastio.bebida.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.bebida.api.IngresoDto.Persona;
import ar.edu.utn.vastio.bebida.api.UbicacionDto.UbicacionResumen;
import ar.edu.utn.vastio.bebida.aplicacion.InventarioInicialService;
import ar.edu.utn.vastio.bebida.aplicacion.InventarioInicialService.CargaInicial;
import ar.edu.utn.vastio.bebida.aplicacion.InventarioInicialService.Renglon;
import ar.edu.utn.vastio.comun.seguridad.Permisos;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.usuarios.aplicacion.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Registrar inventario inicial (UI-28, paso 1). Dirección, Coordinación, Administración y Compras. Cantidades en botellas.
 */
@RestController
@RequestMapping("/api/v1/inventario-inicial")
@PreAuthorize(Permisos.INGRESO_BEBIDA)
@Tag(name = "Inventario inicial", description = "Carga de puesta en marcha de cada ubicación")
public class InventarioInicialController {

    private final InventarioInicialService inventario;
    private final UsuarioService usuarios;

    public InventarioInicialController(InventarioInicialService inventario, UsuarioService usuarios) {
        this.inventario = inventario;
        this.usuarios = usuarios;
    }

    /** Cantidad de una bebida en botellas enteras; 0 la saca de la carga. */
    public record RenglonDto(
            @NotNull(message = "Elegí la bebida.") Long bebidaId,
            @NotNull(message = "Cargá la cantidad.") @PositiveOrZero(message = "La cantidad no puede ser negativa.")
            @Digits(integer = 8, fraction = 0, message = "Cargá botellas enteras.") BigDecimal cantidad) {
    }

    public record CargaRequest(
            @NotNull(message = "Indicá las bebidas.") @Size(max = 500, message = "Cargá hasta 500 bebidas por vez.")
            List<@Valid RenglonDto> renglones) {
    }

    /** {@code ultimaModificacion}: quién y cuándo guardó por última vez (null si todavía no se cargó nada). */
    public record CargaResponse(UbicacionResumen ubicacion, boolean cerrada, List<RenglonDto> renglones,
            Persona usuario, OffsetDateTime ultimaModificacion) {
    }

    @GetMapping("/{ubicacionId}")
    @Operation(summary = "Carga inicial de una ubicación", description = """
            Lo cargado por bebida (ya corregido), en botellas, y si quedó cerrada porque la ubicación tiene otros
            movimientos.""")
    public CargaResponse consultar(@PathVariable short ubicacionId) {
        return respuesta(inventario.carga(ubicacionId));
    }

    @PutMapping("/{ubicacionId}")
    @Operation(summary = "Guardar la carga inicial", description = """
            Se puede guardar varias veces. Una bebida nueva genera INVENTARIO_INICIAL; una ya cargada que cambia, un AJUSTE
            por la diferencia con el motivo «Error de carga» que referencia al original. Si la ubicación tiene movimientos
            de otro tipo, se rechaza: se corrige con un recuento.""")
    public CargaResponse guardar(@PathVariable short ubicacionId, @Valid @RequestBody CargaRequest pedido,
            @AuthenticationPrincipal Jwt jwt) {
        List<Renglon> renglones = pedido.renglones().stream().map(r -> new Renglon(r.bebidaId(), r.cantidad())).toList();
        return respuesta(inventario.guardar(ubicacionId, renglones, UsuarioActual.de(jwt).id()));
    }

    private CargaResponse respuesta(CargaInicial carga) {
        Persona usuario = carga.ultimoUsuarioId() == null ? null
                : new Persona(carga.ultimoUsuarioId(), usuarios.nombres(List.of(carga.ultimoUsuarioId())).getOrDefault(carga.ultimoUsuarioId(), ""));
        List<RenglonDto> renglones = carga.cargado().entrySet().stream().map(e -> new RenglonDto(e.getKey(), e.getValue())).toList();
        return new CargaResponse(UbicacionResumen.de(carga.ubicacion()), carga.cerrada(), renglones, usuario, carga.ultimaModificacion());
    }
}
