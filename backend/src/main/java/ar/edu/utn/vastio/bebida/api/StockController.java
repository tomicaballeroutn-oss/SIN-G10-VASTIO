package ar.edu.utn.vastio.bebida.api;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.bebida.api.UbicacionDto.UbicacionResumen;
import ar.edu.utn.vastio.bebida.aplicacion.StockService;
import ar.edu.utn.vastio.bebida.aplicacion.StockService.EstadoStock;
import ar.edu.utn.vastio.bebida.aplicacion.StockService.Existencias;
import ar.edu.utn.vastio.bebida.aplicacion.StockService.Renglon;
import ar.edu.utn.vastio.bebida.aplicacion.StockService.UbicacionVisible;
import ar.edu.utn.vastio.bebida.dominio.Bebida;
import ar.edu.utn.vastio.comun.seguridad.Permisos;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Consultar stock (UI-27). La encargada de barra solo ve las barras de la jornada y su saldo, sin estado ni stock mínimo
 * (operación a ciegas): el backend no se los manda.
 */
@RestController
@RequestMapping("/api/v1/stock")
@PreAuthorize(Permisos.CONSULTAR_STOCK)
@Tag(name = "Stock", description = "Saldo teórico por ubicación, en botellas")
public class StockController {

    private final StockService stock;

    public StockController(StockService stock) {
        this.stock = stock;
    }

    /** Para la barra, el evento que opera en su salón esa jornada. */
    public record EventoResumen(long id, String codigo, String nombre) {
    }

    public record UbicacionConsultable(UbicacionResumen ubicacion, EventoResumen evento) {
        static UbicacionConsultable de(UbicacionVisible v) {
            var e = v.evento();
            return new UbicacionConsultable(UbicacionResumen.de(v.ubicacion()),
                    e == null ? null : new EventoResumen(e.eventoId(), e.codigo(), e.nombre()));
        }
    }

    public record BebidaEnStock(long id, String nombre, String presentacion, String tipo, String unidad, short unidadesPorBulto,
            boolean activo) {
        static BebidaEnStock de(Bebida b) {
            return new BebidaEnStock(b.getId(), b.getNombre(), b.getPresentacion(), b.getTipo().getNombre(), b.getUnidad().getNombre(),
                    b.getUnidadesPorBulto(), b.isActivo());
        }
    }

    /** {@code estado} y {@code stockMinimo} no se mandan a la barra: ni siquiera como null. Cantidades en botellas. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RenglonStock(BebidaEnStock bebida, BigDecimal cantidad, EstadoStock estado, BigDecimal stockMinimo) {
        static RenglonStock de(Renglon r) {
            return new RenglonStock(BebidaEnStock.de(r.bebida()), r.cantidad(), r.estado(),
                    r.conStockMinimo() ? r.bebida().getStockMinimo() : null);
        }
    }

    /** {@code ubicacion} null en el total («Todas»). */
    public record ExistenciasResponse(UbicacionResumen ubicacion, List<RenglonStock> renglones) {
        static ExistenciasResponse de(Existencias e) {
            return new ExistenciasResponse(e.ubicacion() == null ? null : UbicacionResumen.de(e.ubicacion()),
                    e.renglones().stream().map(RenglonStock::de).toList());
        }
    }

    @GetMapping("/ubicaciones")
    @Operation(summary = "Ubicaciones que puedo consultar", description = """
            Todas las activas para Dirección, Coordinación, Administración y Compras. Para la encargada de barra, las barras
            de los salones con un evento confirmado, en curso o realizado en la jornada actual (antes de las 06:00 es la de
            ayer), con ese evento.""")
    public List<UbicacionConsultable> ubicaciones(@AuthenticationPrincipal Jwt jwt) {
        return stock.ubicacionesVisibles(UsuarioActual.de(jwt)).stream().map(UbicacionConsultable::de).toList();
    }

    @GetMapping
    @Operation(summary = "Existencias", description = """
            Saldo por bebida de la ubicación; sin ubicación, el total del complejo (no para la barra). En depósitos y
            transiciones, todas las bebidas activas; en barras, solo las que tienen saldo. Estado: NEGATIVO, SIN_STOCK, BAJO
            (solo en el depósito madre, hasta el stock mínimo) u OK.""")
    public ExistenciasResponse existencias(@RequestParam(required = false) Short ubicacionId, @AuthenticationPrincipal Jwt jwt) {
        return ExistenciasResponse.de(stock.existencias(ubicacionId, UsuarioActual.de(jwt)));
    }
}
