package ar.edu.utn.vastio.bebida.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import ar.edu.utn.vastio.bebida.aplicacion.BebidaService;
import ar.edu.utn.vastio.bebida.aplicacion.BebidaService.DatosBebida;
import ar.edu.utn.vastio.bebida.dominio.Bebida;
import ar.edu.utn.vastio.bebida.dominio.CodigoBarra;
import ar.edu.utn.vastio.bebida.dominio.Proveedor;
import ar.edu.utn.vastio.bebida.dominio.TipoBebida;
import ar.edu.utn.vastio.bebida.dominio.UnidadManipulacion;

public final class BebidaDto {

    private BebidaDto() {
    }

    public record TipoResponse(short id, String nombre) {
        static TipoResponse de(TipoBebida t) {
            return new TipoResponse(t.getId(), t.getNombre());
        }
    }

    /** {@code esBotella}: con esa unidad las unidades por bulto son siempre 1. */
    public record UnidadResponse(short id, String nombre, boolean esBotella) {
        static UnidadResponse de(UnidadManipulacion u) {
            return new UnidadResponse(u.getId(), u.getNombre(), u.esBotella());
        }
    }

    /** Código de barras y botellas que representa una lectura (1 la botella, 6 o 12 la caja). */
    public record CodigoDto(
            @NotBlank(message = "Escribí el código.")
            @Pattern(regexp = "\\s*[0-9]{8,14}\\s*", message = "El código tiene que tener de 8 a 14 números.") String codigo,
            @NotNull(message = "Indicá cuántas botellas representa.")
            @Min(value = 1, message = "Tiene que representar al menos 1 botella.")
            @Max(value = 999, message = "Usá hasta 999 botellas.") Short unidades) {
        static CodigoDto de(CodigoBarra c) {
            return new CodigoDto(c.getCodigo(), c.getUnidades());
        }
    }

    public record ProveedorResumen(long id, String razonSocial, boolean activo) {
        static ProveedorResumen de(Proveedor p) {
            return p == null ? null : new ProveedorResumen(p.getId(), p.getRazonSocial(), p.isActivo());
        }
    }

    /** Las cantidades van en botellas. Sin precio: el sistema no maneja dinero. */
    public record BebidaResponse(long id, String nombre, String presentacion, TipoResponse tipo, UnidadResponse unidad,
            short unidadesPorBulto, BigDecimal stockMinimo, List<CodigoDto> codigos, ProveedorResumen proveedorHabitual,
            boolean activo, OffsetDateTime fechaBaja) {
        static BebidaResponse de(Bebida b) {
            return new BebidaResponse(b.getId(), b.getNombre(), b.getPresentacion(), TipoResponse.de(b.getTipo()),
                    UnidadResponse.de(b.getUnidad()), b.getUnidadesPorBulto(), b.getStockMinimo(),
                    b.getCodigos().stream().map(CodigoDto::de).toList(), ProveedorResumen.de(b.getProveedorHabitual()),
                    b.isActivo(), b.getFechaBaja());
        }
    }

    /** Con unidad Botella, las unidades por bulto quedan en 1. El stock mínimo va en botellas. */
    public record BebidaRequest(
            @NotBlank(message = "Escribí el nombre.") @Size(max = 100, message = "Usá hasta 100 caracteres.") String nombre,
            @NotBlank(message = "Escribí la presentación (por ejemplo, 750 ml).")
            @Size(max = 40, message = "Usá hasta 40 caracteres.") String presentacion,
            @NotNull(message = "Elegí el tipo de bebida.") Short tipoId,
            @NotNull(message = "Elegí la unidad de manipulación.") Short unidadId,
            @NotNull(message = "Indicá cuántas botellas trae cada bulto.")
            @Min(value = 1, message = "Cada bulto trae al menos 1 botella.")
            @Max(value = 999, message = "Usá hasta 999 botellas.") Short unidadesPorBulto,
            @PositiveOrZero(message = "El stock mínimo no puede ser negativo.")
            @DecimalMax(value = "99999999", message = "Revisá el stock mínimo.")
            @Digits(integer = 8, fraction = 2, message = "Revisá el stock mínimo.") BigDecimal stockMinimo,
            @Size(max = 20, message = "Cargá hasta 20 códigos.") List<@Valid CodigoDto> codigos,
            Long proveedorId) {

        DatosBebida datos() {
            List<BebidaService.Codigo> lista = codigos == null ? List.of()
                    : codigos.stream().map(c -> new BebidaService.Codigo(c.codigo().trim(), c.unidades())).toList();
            return new DatosBebida(nombre.trim(), presentacion.trim(), tipoId, unidadId, unidadesPorBulto, stockMinimo, lista, proveedorId);
        }
    }

    /** Ubicación donde la bebida tiene saldo, en botellas. */
    public record SaldoResponse(short ubicacionId, String ubicacion, BigDecimal cantidad) {
        static SaldoResponse de(BebidaService.Saldo s) {
            return new SaldoResponse(s.ubicacionId(), s.ubicacion(), s.cantidad());
        }
    }
}
