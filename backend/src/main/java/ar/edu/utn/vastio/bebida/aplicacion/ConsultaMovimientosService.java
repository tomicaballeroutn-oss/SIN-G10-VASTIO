package ar.edu.utn.vastio.bebida.aplicacion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService;
import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService.EventoResumido;
import ar.edu.utn.vastio.bebida.dominio.MovimientoStock;
import ar.edu.utn.vastio.bebida.dominio.TipoMovimiento;
import ar.edu.utn.vastio.bebida.infraestructura.MovimientoStockRepository;
import ar.edu.utn.vastio.bebida.infraestructura.MovimientoStockRepository.Operacion;
import ar.edu.utn.vastio.comun.errores.ProblemaException;

/**
 * Consultar movimientos de stock (UI-37, docs/sprint-4.md, decisiones 26 a 28): el historial para auditar una
 * ubicación, una bebida o un evento. Cada operación agrupa los asientos de un mismo ingreso, preparación, salida a barra
 * o devolución (ver {@link Operacion}). Solo lectura: los movimientos no se editan, se corrigen con un ajuste.
 */
@Service
@Transactional(readOnly = true)
public class ConsultaMovimientosService {

    /** Operaciones por página. */
    public static final int POR_PAGINA = 20;

    private static final ZoneId ZONA = ZoneId.of(VastioApplication.ZONA_HORARIA);

    private final MovimientoStockRepository movimientos;
    private final ConsultaEventoService eventos;

    public ConsultaMovimientosService(MovimientoStockRepository movimientos, ConsultaEventoService eventos) {
        this.movimientos = movimientos;
        this.eventos = eventos;
    }

    /**
     * @param desde, hasta días de la fecha y hora del movimiento, inclusive; null sin límite.
     */
    public record Filtros(Short ubicacionId, Long bebidaId, Long eventoId, TipoMovimiento tipo, LocalDate desde,
            LocalDate hasta) {
    }

    /** Una operación con sus asientos (por bebida). {@code evento} null si no es de un evento. */
    public record OperacionConAsientos(Operacion operacion, EventoResumido evento, List<MovimientoStock> asientos) {
    }

    public record Pagina(List<OperacionConAsientos> operaciones, boolean hayMas) {
    }

    /** Una página de operaciones, de la más nueva a la más vieja. */
    public Pagina consultar(Filtros filtros, int pagina) {
        if (pagina < 0 || (filtros.desde() != null && filtros.hasta() != null && filtros.desde().isAfter(filtros.hasta()))) {
            throw new ProblemaException(HttpStatus.BAD_REQUEST, "FILTROS_INVALIDOS", "Revisá los filtros",
                    "La fecha desde tiene que ser anterior o igual a la fecha hasta.");
        }
        OffsetDateTime desde = filtros.desde() == null ? OffsetDateTime.parse("2000-01-01T00:00:00Z")
                : filtros.desde().atStartOfDay(ZONA).toOffsetDateTime();
        OffsetDateTime hasta = filtros.hasta() == null ? OffsetDateTime.parse("9999-01-01T00:00:00Z")
                : filtros.hasta().plusDays(1).atStartOfDay(ZONA).toOffsetDateTime();
        Slice<Operacion> lista = movimientos.operaciones(filtros.ubicacionId(), filtros.bebidaId(), filtros.eventoId(),
                filtros.tipo(), desde, hasta, PageRequest.of(pagina, POR_PAGINA));
        boolean hayMas = lista.hasNext();
        List<Operacion> pagina0 = lista.getContent();
        if (pagina0.isEmpty()) {
            return new Pagina(List.of(), false);
        }
        long primero = pagina0.stream().mapToLong(Operacion::getPrimero).min().orElseThrow();
        long ultimo = pagina0.stream().mapToLong(Operacion::getUltimo).max().orElseThrow();
        Map<Operacion, List<MovimientoStock>> porOperacion = new LinkedHashMap<>();
        pagina0.forEach(o -> porOperacion.put(o, new ArrayList<>()));
        movimientos.asientos(filtros.ubicacionId(), filtros.bebidaId(), filtros.eventoId(), filtros.tipo(), primero, ultimo)
                .forEach(m -> pagina0.stream().filter(o -> pertenece(m, o)).findFirst()
                        .ifPresent(o -> porOperacion.get(o).add(m)));
        Set<Long> ids = new HashSet<>();
        pagina0.stream().map(Operacion::getEventoId).filter(Objects::nonNull).forEach(ids::add);
        Map<Long, EventoResumido> resumidos = eventos.resumidos(ids);
        return new Pagina(pagina0.stream().map(o -> new OperacionConAsientos(o,
                o.getEventoId() == null ? null : resumidos.get(o.getEventoId()), porOperacion.get(o))).toList(), hayMas);
    }

    /** Los eventos con algún movimiento, del más nuevo al más viejo: las opciones del filtro. */
    public List<EventoResumido> eventosConMovimientos() {
        return eventos.resumidos(movimientos.eventosConMovimientos()).values().stream()
                .sorted(Comparator.comparing(EventoResumido::fecha).reversed().thenComparing(EventoResumido::eventoId))
                .toList();
    }

    /**
     * El asiento es de la operación: mismo tipo, ingreso, preparación, entrega, usuario, evento, origen, destino, motivo
     * y observación, y su id entre el primero y el último de ella. No se compara el momento: un asiento recién guardado
     * lo tiene en memoria con más precisión que la base.
     */
    private static boolean pertenece(MovimientoStock m, Operacion o) {
        return m.getId() >= o.getPrimero() && m.getId() <= o.getUltimo() && m.getTipo() == o.getTipo()
                && Objects.equals(m.getIngresoId(), o.getIngresoId()) && Objects.equals(m.getPreparacionId(), o.getPreparacionId())
                && Objects.equals(m.getEntregaId(), o.getEntregaId())
                && m.getUsuarioId().equals(o.getUsuarioId()) && Objects.equals(m.getEventoId(), o.getEventoId())
                && Objects.equals(m.getOrigen() == null ? null : m.getOrigen().getId(), o.getOrigenId())
                && Objects.equals(m.getDestino() == null ? null : m.getDestino().getId(), o.getDestinoId())
                && Objects.equals(m.getMotivoId(), o.getMotivoId()) && Objects.equals(m.getObservacion(), o.getObservacion());
    }
}
