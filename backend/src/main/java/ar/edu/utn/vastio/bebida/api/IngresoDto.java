package ar.edu.utn.vastio.bebida.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import ar.edu.utn.vastio.bebida.api.UbicacionDto.UbicacionResumen;
import ar.edu.utn.vastio.bebida.aplicacion.IngresoService.DatosIngreso;
import ar.edu.utn.vastio.bebida.aplicacion.IngresoService.IngresoRegistrado;
import ar.edu.utn.vastio.bebida.aplicacion.IngresoService.Renglon;
import ar.edu.utn.vastio.bebida.dominio.Bebida;
import ar.edu.utn.vastio.bebida.dominio.Ingreso;
import ar.edu.utn.vastio.bebida.dominio.MovimientoStock;

public final class IngresoDto {

    private IngresoDto() {
    }

    /** Una bebida en botellas enteras. La pantalla la carga en cajas y botellas sueltas. */
    public record RenglonRequest(
            @NotNull(message = "Elegí la bebida.") Long bebidaId,
            @NotNull(message = "Cargá la cantidad.") @Positive(message = "La cantidad tiene que ser mayor a 0.")
            @Digits(integer = 8, fraction = 0, message = "Cargá botellas enteras.") BigDecimal cantidad) {
    }

    /** Sin fecha, hoy. El destino es siempre el depósito madre. */
    public record IngresoRequest(
            LocalDate fecha,
            @Size(max = 30, message = "Usá hasta 30 caracteres.") String numeroRemito,
            @NotEmpty(message = "Cargá al menos una bebida con su cantidad.")
            @Size(max = 200, message = "Cargá hasta 200 bebidas por ingreso.") List<@Valid RenglonRequest> renglones) {

        DatosIngreso datos() {
            String remito = numeroRemito == null || numeroRemito.isBlank() ? null : numeroRemito.trim();
            return new DatosIngreso(fecha, remito, renglones.stream().map(r -> new Renglon(r.bebidaId(), r.cantidad())).toList());
        }
    }

    /** Lo necesario para mostrar la cantidad en cajas: unidad y botellas por bulto. */
    public record RenglonResponse(long bebidaId, String nombre, String presentacion, String unidad, short unidadesPorBulto,
            BigDecimal cantidad) {
        static RenglonResponse de(MovimientoStock m) {
            Bebida b = m.getBebida();
            return new RenglonResponse(b.getId(), b.getNombre(), b.getPresentacion(), b.getUnidad().getNombre(),
                    b.getUnidadesPorBulto(), m.getCantidad());
        }
    }

    public record Persona(long id, String nombre) {
    }

    /** Quién lo registró y cuándo (regla 9). */
    public record IngresoResponse(long id, LocalDate fechaIngreso, String numeroRemito, UbicacionResumen destino,
            List<RenglonResponse> renglones, Persona usuario, OffsetDateTime fechaRegistro) {
        static IngresoResponse de(IngresoRegistrado registrado, Map<Long, String> nombres) {
            Ingreso i = registrado.ingreso();
            return new IngresoResponse(i.getId(), i.getFechaIngreso(), i.getNumeroRemito(), UbicacionResumen.de(i.getDestino()),
                    registrado.renglones().stream().map(RenglonResponse::de).toList(),
                    new Persona(i.getUsuarioId(), nombres.getOrDefault(i.getUsuarioId(), "")), i.getFechaRegistro());
        }
    }
}
