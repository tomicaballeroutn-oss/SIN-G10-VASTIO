package ar.edu.utn.vastio.bebida.api;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ar.edu.utn.vastio.bebida.api.IngresoDto.Persona;
import ar.edu.utn.vastio.bebida.api.IngresoDto.RenglonResponse;
import ar.edu.utn.vastio.bebida.api.UbicacionDto.UbicacionResumen;
import ar.edu.utn.vastio.bebida.api.EntregaDto.EventoResumen;
import ar.edu.utn.vastio.bebida.aplicacion.PreparacionService.*;

public final class PreparacionDto {
    private PreparacionDto() { }
    /** La integridad entera también se valida en el servicio; 12.00 representa 12 botellas. */
    public record RenglonRequest(@NotNull @Positive Long bebidaId,
            @NotNull @DecimalMin("1") @DecimalMax("99999999") BigDecimal cantidad) { }
    public record Request(@NotNull @Positive Short barraId, @NotNull @Positive Short destinoId,
            @NotEmpty @Size(max = 200) List<@NotNull @Valid RenglonRequest> renglones) {
        Datos datos() {
            return new Datos(barraId, destinoId, renglones.stream().map(r -> new Renglon(r.bebidaId(), r.cantidad())).toList());
        }
    }
    public record CircuitoResponse(UbicacionResumen barra, UbicacionResumen abastecimiento, boolean requierePreparacion) { }
    public record Response(long id, long eventoId, UbicacionResumen barra, UbicacionResumen origen,
            UbicacionResumen destino, Persona usuario, OffsetDateTime fechaHora, List<RenglonResponse> renglones) {
        static Response de(Registrada r, Map<Long, String> nombres) {
            var p = r.preparacion();
            return new Response(p.getId(), p.getEventoId(), UbicacionResumen.de(p.getBarra()), UbicacionResumen.de(p.getOrigen()),
                    UbicacionResumen.de(p.getDestino()), new Persona(p.getUsuarioId(), nombres.getOrDefault(p.getUsuarioId(), "")),
                    p.getFechaHora(), r.renglones().stream().map(RenglonResponse::de).toList());
        }
    }
    public record PantallaResponse(EventoResumen evento, UbicacionResumen madre, List<CircuitoResponse> circuitos,
            List<Response> historial) {
        static PantallaResponse de(Pantalla p, Map<Long, String> nombres) {
            return new PantallaResponse(EventoResumen.de(p.evento()), p.madre() == null ? null : UbicacionResumen.de(p.madre()),
                    p.circuitos().stream().map(c -> new CircuitoResponse(UbicacionResumen.de(c.barra()), UbicacionResumen.de(c.abastecimiento()),
                            c.abastecimiento().isActivo() && c.abastecimiento().getTipo() == ar.edu.utn.vastio.bebida.dominio.TipoUbicacion.TRANSICION)).toList(),
                    p.historial().stream().map(r -> Response.de(r, nombres)).toList());
        }
    }
}
