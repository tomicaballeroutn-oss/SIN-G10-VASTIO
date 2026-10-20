package ar.edu.utn.vastio.bebida.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService.EventoOperativo;
import ar.edu.utn.vastio.bebida.api.BebidaDto.CodigoDto;
import ar.edu.utn.vastio.bebida.api.IngresoDto.Persona;
import ar.edu.utn.vastio.bebida.api.IngresoDto.RenglonRequest;
import ar.edu.utn.vastio.bebida.api.IngresoDto.RenglonResponse;
import ar.edu.utn.vastio.bebida.api.UbicacionDto.SalonResumen;
import ar.edu.utn.vastio.bebida.api.UbicacionDto.UbicacionResumen;
import ar.edu.utn.vastio.bebida.aplicacion.EntregaService.Consumo;
import ar.edu.utn.vastio.bebida.aplicacion.EntregaService.DatosDevolucion;
import ar.edu.utn.vastio.bebida.aplicacion.EntregaService.DatosRetiro;
import ar.edu.utn.vastio.bebida.aplicacion.EntregaService.DevolucionRegistrada;
import ar.edu.utn.vastio.bebida.aplicacion.EntregaService.RenglonDevolucion;
import ar.edu.utn.vastio.bebida.aplicacion.EntregaService.Entregado;
import ar.edu.utn.vastio.bebida.aplicacion.EntregaService.EventoConBarras;
import ar.edu.utn.vastio.bebida.aplicacion.EntregaService.PantallaEntrega;
import ar.edu.utn.vastio.bebida.aplicacion.EntregaService.Renglon;
import ar.edu.utn.vastio.bebida.aplicacion.EntregaService.Retiro;
import ar.edu.utn.vastio.bebida.dominio.Bebida;
import ar.edu.utn.vastio.bebida.dominio.EstadoEntrega;
import ar.edu.utn.vastio.bebida.dominio.TipoMovimiento;
import ar.edu.utn.vastio.bebida.dominio.Ubicacion;

/**
 * Salida a barra (UI-31). Ninguna respuesta lleva saldos: la encargada opera a ciegas (regla 6). Cantidades en botellas.
 */
public final class EntregaDto {

    private EntregaDto() {
    }

    /** Sin datos comerciales. {@code estado}: el código (EN_CURSO, …). */
    public record EventoResumen(long id, String codigo, String nombre, String estado) {
        static EventoResumen de(EventoOperativo e) {
            return new EventoResumen(e.eventoId(), e.codigo(), e.nombre(), e.estado().name());
        }
    }

    public record EventoDeLaJornada(EventoResumen evento, SalonResumen salon, List<UbicacionResumen> barras) {
        static EventoDeLaJornada de(EventoConBarras e) {
            return new EventoDeLaJornada(EventoResumen.de(e.evento()), SalonResumen.de(e.salon()),
                    e.barras().stream().map(UbicacionResumen::de).toList());
        }
    }

    /** Para el buscador y el lector de códigos: sin stock mínimo. */
    public record BebidaParaRetiro(long id, String nombre, String presentacion, String unidad, short unidadesPorBulto,
            List<CodigoDto> codigos) {
        static BebidaParaRetiro de(Bebida b) {
            return new BebidaParaRetiro(b.getId(), b.getNombre(), b.getPresentacion(), b.getUnidad().getNombre(),
                    b.getUnidadesPorBulto(), b.getCodigos().stream().map(CodigoDto::de).toList());
        }
    }

    /** Un retiro con quién y cuándo (regla 9). {@code tipo}: SALIDA_BARRA o RETIRO_ADICIONAL. */
    public record RetiroResponse(TipoMovimiento tipo, UbicacionResumen origen, List<RenglonResponse> renglones, Persona usuario,
            OffsetDateTime fechaHora) {
        static RetiroResponse de(Retiro r, Map<Long, String> nombres) {
            return new RetiroResponse(r.tipo(), UbicacionResumen.de(r.origen()),
                    r.renglones().stream().map(RenglonResponse::de).toList(),
                    new Persona(r.usuarioId(), nombres.getOrDefault(r.usuarioId(), "")), r.momento());
        }
    }

    /** «Ya entregado» de una bebida a la barra para este evento. */
    public record EntregadoResponse(long bebidaId, String nombre, String presentacion, String unidad, short unidadesPorBulto,
            BigDecimal cantidad) {
        static EntregadoResponse de(Entregado e) {
            Bebida b = e.bebida();
            return new EntregadoResponse(b.getId(), b.getNombre(), b.getPresentacion(), b.getUnidad().getNombre(),
                    b.getUnidadesPorBulto(), e.cantidad());
        }
    }

    /**
     * La pantalla de la barra para un evento. {@code estadoEntrega} null si todavía no hubo retiros (el próximo abre la
     * entrega). {@code depositoMadre} solo si la barra permite el retiro directo.
     */
    public record EntregaResponse(EventoResumen evento, SalonResumen salon, UbicacionResumen barra, UbicacionResumen origen,
            UbicacionResumen depositoMadre, EstadoEntrega estadoEntrega, List<EntregadoResponse> yaEntregado,
            List<RetiroResponse> retiros, List<BebidaParaRetiro> bebidas, List<BebidaParaRetiro> aDevolver,
            List<BebidaParaRetiro> enTransicion) {
        static EntregaResponse de(PantallaEntrega p, Map<Long, String> nombres) {
            return new EntregaResponse(EventoResumen.de(p.evento()), SalonResumen.de(p.salon()), UbicacionResumen.de(p.barra()),
                    UbicacionResumen.de(p.origen()), resumen(p.depositoMadre()),
                    p.entrega() == null ? null : p.entrega().getEstado(),
                    p.yaEntregado().stream().map(EntregadoResponse::de).toList(),
                    p.retiros().stream().map(r -> RetiroResponse.de(r, nombres)).toList(),
                    p.bebidas().stream().map(BebidaParaRetiro::de).toList(),
                    p.aDevolver().stream().map(BebidaParaRetiro::de).toList(),
                    p.enTransicion().stream().map(BebidaParaRetiro::de).toList());
        }

        private static UbicacionResumen resumen(Ubicacion u) {
            return u == null ? null : UbicacionResumen.de(u);
        }
    }

    /** Lo que vuelve al depósito y lo que queda en la barra, en botellas enteras ≥ 0. */
    public record RenglonDevolucionRequest(
            @NotNull(message = "Elegí la bebida.") Long bebidaId,
            @NotNull(message = "Cargá lo que vuelve al depósito.") @PositiveOrZero(message = "No puede ser negativo.")
            @Digits(integer = 8, fraction = 0, message = "Cargá botellas enteras: las abiertas cuentan como consumidas.") BigDecimal devuelto,
            @NotNull(message = "Cargá lo que queda en la barra.") @PositiveOrZero(message = "No puede ser negativo.")
            @Digits(integer = 8, fraction = 0, message = "Cargá botellas enteras: las abiertas cuentan como consumidas.") BigDecimal remanente) {
    }

    /** Lo que queda en el depósito de transición y vuelve al madre, en botellas enteras ≥ 0. */
    public record RenglonTransicionRequest(
            @NotNull(message = "Elegí la bebida.") Long bebidaId,
            @NotNull(message = "Cargá lo que queda en la transición.") @PositiveOrZero(message = "No puede ser negativo.")
            @Digits(integer = 8, fraction = 0, message = "Cargá botellas enteras.") BigDecimal cantidad) {
    }

    /** Una bebida que falta en {@code renglones} cuenta como 0 y 0: se consumió todo. */
    public record DevolucionRequest(
            @Size(max = 200, message = "Cargá hasta 200 bebidas.") List<@Valid RenglonDevolucionRequest> renglones,
            @Size(max = 200, message = "Cargá hasta 200 bebidas.") List<@Valid RenglonTransicionRequest> transicion) {

        DatosDevolucion datos() {
            return new DatosDevolucion(
                    renglones == null ? List.of()
                            : renglones.stream().map(r -> new RenglonDevolucion(r.bebidaId(), r.devuelto(), r.remanente())).toList(),
                    transicion == null ? List.of()
                            : transicion.stream().map(r -> new Renglon(r.bebidaId(), r.cantidad())).toList());
        }
    }

    /** Consumo real de una bebida en la barra: entregado − devuelto − remanente, en botellas. */
    public record ConsumoResponse(long bebidaId, String nombre, String presentacion, String unidad, short unidadesPorBulto,
            BigDecimal entregado, BigDecimal devuelto, BigDecimal remanente, BigDecimal consumo) {
        static ConsumoResponse de(Consumo c) {
            Bebida b = c.bebida();
            return new ConsumoResponse(b.getId(), b.getNombre(), b.getPresentacion(), b.getUnidad().getNombre(),
                    b.getUnidadesPorBulto(), c.entregado(), c.devuelto(), c.remanente(), c.consumo());
        }
    }

    /** Recién después de registrar: el consumo real por bebida y lo que volvió desde la transición. */
    public record DevolucionResponse(EntregaResponse entrega, List<ConsumoResponse> consumo, List<EntregadoResponse> desdeTransicion) {
        static DevolucionResponse de(DevolucionRegistrada d, Map<Long, String> nombres) {
            return new DevolucionResponse(EntregaResponse.de(d.pantalla(), nombres),
                    d.consumo().stream().map(ConsumoResponse::de).toList(),
                    d.desdeTransicion().stream().map(EntregadoResponse::de).toList());
        }
    }

    /** Renglones en botellas enteras. {@code directo}: del depósito madre, como contingencia. */
    public record RetiroRequest(
            boolean directo,
            @NotEmpty(message = "Cargá al menos una bebida con su cantidad.")
            @Size(max = 200, message = "Cargá hasta 200 bebidas por retiro.") List<@Valid RenglonRequest> renglones) {

        DatosRetiro datos() {
            return new DatosRetiro(directo, renglones.stream().map(r -> new Renglon(r.bebidaId(), r.cantidad())).toList());
        }
    }
}
