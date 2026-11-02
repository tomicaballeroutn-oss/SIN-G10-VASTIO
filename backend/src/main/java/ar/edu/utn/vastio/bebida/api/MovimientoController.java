package ar.edu.utn.vastio.bebida.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService.EventoResumido;
import ar.edu.utn.vastio.bebida.aplicacion.ConsultaMovimientosService;
import ar.edu.utn.vastio.bebida.aplicacion.ConsultaMovimientosService.Filtros;
import ar.edu.utn.vastio.bebida.aplicacion.ConsultaMovimientosService.OperacionConAsientos;
import ar.edu.utn.vastio.bebida.dominio.Bebida;
import ar.edu.utn.vastio.bebida.dominio.MovimientoStock;
import ar.edu.utn.vastio.bebida.dominio.TipoMovimiento;
import ar.edu.utn.vastio.bebida.dominio.Ubicacion;
import ar.edu.utn.vastio.bebida.api.UbicacionDto.UbicacionResumen;
import ar.edu.utn.vastio.comun.seguridad.Permisos;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.Salon;
import ar.edu.utn.vastio.usuarios.aplicacion.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Consultar movimientos de stock (UI-37, docs/sprint-4.md, decisiones 26 a 28). Dirección, Coordinación,
 * Administración y Compras; la barra no (operación a ciegas). Cantidades en botellas.
 */
@RestController
@RequestMapping("/api/v1/movimientos")
@PreAuthorize(Permisos.CONSULTAR_MOVIMIENTOS)
@Tag(name = "Movimientos de stock", description = "Historial de movimientos para auditar una ubicación, una bebida o un evento")
public class MovimientoController {

    private final ConsultaMovimientosService movimientos;
    private final CatalogoService catalogos;
    private final UsuarioService usuarios;

    public MovimientoController(ConsultaMovimientosService movimientos, CatalogoService catalogos, UsuarioService usuarios) {
        this.movimientos = movimientos;
        this.catalogos = catalogos;
        this.usuarios = usuarios;
    }

    public record Persona(long id, String nombre) {
    }

    public record EventoDelMovimiento(long id, String codigo, String nombre, LocalDate fecha, String salon) {
        static EventoDelMovimiento de(EventoResumido e, Salon salon) {
            return new EventoDelMovimiento(e.eventoId(), e.codigo(), e.nombre(), e.fecha(), salon.getCodigo());
        }
    }

    public record Asiento(long movimientoId, long bebidaId, String nombre, String presentacion, String unidad,
            short unidadesPorBulto, BigDecimal cantidad, Long corrige) {
        static Asiento de(MovimientoStock m) {
            Bebida b = m.getBebida();
            return new Asiento(m.getId(), b.getId(), b.getNombre(), b.getPresentacion(), b.getUnidad().getNombre(),
                    b.getUnidadesPorBulto(), m.getCantidad(), m.getMovimientoCorregidoId());
        }
    }

    /**
     * Una operación: los asientos del mismo tipo, momento, usuario, evento, origen y destino. {@code motivo} y
     * {@code observacion} en ajustes y mermas; {@code corrige} en cada asiento, el movimiento que corrige un ajuste.
     */
    public record OperacionResponse(TipoMovimiento tipo, OffsetDateTime fechaHora, Persona usuario, EventoDelMovimiento evento,
            UbicacionResumen origen, UbicacionResumen destino, String motivo, String observacion, List<Asiento> asientos) {
    }

    public record PaginaResponse(List<OperacionResponse> operaciones, boolean hayMas) {
    }

    @GetMapping
    @Operation(summary = "Movimientos de stock", description = """
            Operaciones de la más nueva a la más vieja, de a 20 (pagina desde 0). Filtros opcionales: ubicación (como
            origen o destino), bebida, evento, tipo y fechas desde/hasta inclusive (por la fecha y hora del movimiento,
            en hora de Córdoba). Con un filtro de bebida, cada operación muestra solo esa bebida.""")
    public PaginaResponse consultar(@RequestParam(required = false) Short ubicacionId, @RequestParam(required = false) Long bebidaId,
            @RequestParam(required = false) Long eventoId, @RequestParam(required = false) TipoMovimiento tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina) {
        var resultado = movimientos.consultar(new Filtros(ubicacionId, bebidaId, eventoId, tipo, desde, hasta), pagina);
        Map<Long, String> nombres = usuarios.nombres(resultado.operaciones().stream()
                .map(o -> o.operacion().getUsuarioId()).distinct().toList());
        return new PaginaResponse(resultado.operaciones().stream().map(o -> operacion(o, nombres)).toList(), resultado.hayMas());
    }

    @GetMapping("/eventos")
    @Operation(summary = "Eventos con movimientos", description = "Las opciones del filtro de evento, del más nuevo al más viejo.")
    public List<EventoDelMovimiento> eventos() {
        return movimientos.eventosConMovimientos().stream()
                .map(e -> EventoDelMovimiento.de(e, catalogos.salon(e.salonId()))).toList();
    }

    private OperacionResponse operacion(OperacionConAsientos o, Map<Long, String> nombres) {
        var op = o.operacion();
        MovimientoStock primero = o.asientos().isEmpty() ? null : o.asientos().getFirst();
        return new OperacionResponse(op.getTipo(), op.getFechaHora(), new Persona(op.getUsuarioId(), nombres.get(op.getUsuarioId())),
                o.evento() == null ? null : EventoDelMovimiento.de(o.evento(), catalogos.salon(o.evento().salonId())),
                ubicacion(primero == null ? null : primero.getOrigen()), ubicacion(primero == null ? null : primero.getDestino()),
                op.getMotivoId() == null ? null : catalogos.motivo(op.getMotivoId()).getNombre(), op.getObservacion(),
                o.asientos().stream().filter(Objects::nonNull).map(Asiento::de).toList());
    }

    private static UbicacionResumen ubicacion(Ubicacion u) {
        return u == null ? null : UbicacionResumen.de(u);
    }
}
