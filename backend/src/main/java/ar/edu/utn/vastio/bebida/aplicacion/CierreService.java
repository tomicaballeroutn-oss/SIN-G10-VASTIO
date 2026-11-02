package ar.edu.utn.vastio.bebida.aplicacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.agenda.aplicacion.CierreEventoService;
import ar.edu.utn.vastio.agenda.aplicacion.CierreEventoService.DatosDeCierre;
import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService;
import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService.EventoOperativo;
import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.bebida.dominio.Bebida;
import ar.edu.utn.vastio.bebida.dominio.ConsumoEvento;
import ar.edu.utn.vastio.bebida.dominio.Entrega;
import ar.edu.utn.vastio.bebida.dominio.MovimientoStock;
import ar.edu.utn.vastio.bebida.dominio.Ubicacion;
import ar.edu.utn.vastio.bebida.infraestructura.ConsumoEventoRepository;
import ar.edu.utn.vastio.bebida.infraestructura.EntregaRepository;
import ar.edu.utn.vastio.bebida.infraestructura.MovimientoStockRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.Salon;

/**
 * Registrar cierre de evento (UI-33, docs/sprint-3-operacion.md, decisiones 12 a 14): la conciliación final. Por bebida,
 * lo entregado, devuelto, remanente y consumido en todas las barras del evento, y el consumo por asistente. Al cerrar,
 * en una transacción: el evento pasa a Cerrado, se congela {@code consumo_evento} y la orden de preparación queda
 * CERRADA. Lo consumido ya salió de la barra con la devolución: el cierre no mueve stock. Sin importes.
 */
@Service
@Transactional(readOnly = true)
public class CierreService {

    /** Botellas por asistente: alcanza con cuatro decimales (como {@code proporcion_consumo}). */
    private static final int DECIMALES_POR_ASISTENTE = 4;

    private static final Comparator<Bebida> POR_NOMBRE = Comparator.comparing(Bebida::getNombre, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Bebida::getPresentacion, String.CASE_INSENSITIVE_ORDER);

    private final EntregaRepository entregas;
    private final MovimientoStockRepository movimientos;
    private final ConsumoEventoRepository consumos;
    private final ConsultaEventoService eventos;
    private final CierreEventoService cierres;
    private final CatalogoService catalogos;

    public CierreService(EntregaRepository entregas, MovimientoStockRepository movimientos, ConsumoEventoRepository consumos,
            ConsultaEventoService eventos, CierreEventoService cierres, CatalogoService catalogos) {
        this.entregas = entregas;
        this.movimientos = movimientos;
        this.consumos = consumos;
        this.eventos = eventos;
        this.cierres = cierres;
        this.catalogos = catalogos;
    }

    /** Consumo de una bebida en el evento, en botellas. {@code porAsistente} null si no hay invitados cargados. */
    public record Renglon(Bebida bebida, BigDecimal entregado, BigDecimal devuelto, BigDecimal remanente, BigDecimal consumo,
            BigDecimal porAsistente) {
    }

    /**
     * La conciliación del evento. {@code pendientes}: las barras que todavía no registraron la devolución (no se puede
     * cerrar). {@code cerrado}: los renglones son los congelados en {@code consumo_evento}.
     */
    public record PantallaCierre(EventoOperativo evento, Salon salon, Integer invitados, boolean usaSegmentos,
            Integer asistenciaReal, List<Ubicacion> pendientes, List<Renglon> renglones, BigDecimal consumoTotal,
            BigDecimal porAsistenteTotal, boolean cerrado) {
    }

    /** Un evento realizado que espera su cierre. */
    public record EventoPorCerrar(EventoOperativo evento, Salon salon) {
    }

    public List<EventoPorCerrar> porCerrar() {
        return cierres.realizados().stream().map(e -> new EventoPorCerrar(e, catalogos.salon(e.salonId()))).toList();
    }

    public PantallaCierre pantalla(long eventoId) {
        EventoOperativo evento = eventos.operativo(eventoId);
        DatosDeCierre datos = cierres.datos(eventoId);
        if (evento.estado() == EstadoEvento.CERRADO) {
            List<Renglon> congelados = consumos.findByEventoId(eventoId).stream()
                    .map(c -> renglon(c.getBebida(), c.getCantidadEntregada(), c.getCantidadDevuelta(), c.getCantidadRemanente(),
                            c.getCantidadConsumida(), datos.asistentes()))
                    .sorted(Comparator.comparing(Renglon::bebida, POR_NOMBRE)).toList();
            return pantalla(evento, datos, List.of(), congelados, true);
        }
        List<Entrega> lista = entregas.findByEventoIdOrderByIdAsc(eventoId);
        List<Ubicacion> pendientes = lista.stream().filter(Entrega::isAbierta).map(Entrega::getBarra).toList();
        return pantalla(evento, datos, pendientes, conciliar(lista, datos.asistentes()), false);
    }

    /**
     * Cierra el evento. Primero lo pasa a Cerrado (bloqueándolo: dos cierres a la vez no pasan los dos) y después verifica
     * las entregas y congela el consumo; si algo falla, se deshace todo.
     */
    @Transactional
    public PantallaCierre cerrar(long eventoId, UsuarioActual quien) {
        EventoOperativo evento = eventos.operativo(eventoId);
        if (evento.estado() == EstadoEvento.CERRADO) {
            throw ProblemaException.conflicto("EVENTO_CERRADO", "El evento ya está cerrado: su consumo quedó fijado.");
        }
        cierres.cerrar(eventoId, quien.id());
        List<Entrega> lista = entregas.findByEventoIdOrderByIdAsc(eventoId);
        List<String> pendientes = lista.stream().filter(Entrega::isAbierta).map(e -> e.getBarra().getNombre()).toList();
        if (!pendientes.isEmpty()) {
            throw ProblemaException.conflicto("DEVOLUCION_PENDIENTE",
                    "Falta registrar la devolución de %s. Registrala antes de cerrar el evento.".formatted(String.join(" y ", pendientes)));
        }
        DatosDeCierre datos = cierres.datos(eventoId);
        for (Renglon r : conciliar(lista, datos.asistentes())) {
            consumos.save(new ConsumoEvento(eventoId, r.bebida(), r.entregado(), r.devuelto(), r.remanente(), r.consumo()));
        }
        consumos.cerrarOrdenDePreparacion(eventoId);
        return pantalla(eventoId);
    }

    /**
     * Suma, por bebida, lo que registraron las devoluciones de las entregas cerradas: lo devuelto desde la barra, el
     * remanente y lo consumido. Lo entregado es la suma de las tres (el saldo de la barra antes de devolver).
     */
    private List<Renglon> conciliar(List<Entrega> lista, Integer asistentes) {
        record Suma(Bebida bebida, BigDecimal[] valores) {
        }
        Map<Long, Suma> porBebida = new LinkedHashMap<>();
        for (Entrega entrega : lista) {
            if (entrega.isAbierta()) {
                continue;
            }
            for (MovimientoStock m : movimientos.deEntrega(entrega.getId())) {
                int columna = switch (m.getTipo()) {
                    // Solo lo que vuelve desde la barra: lo de la transición no pasó por ella.
                    case DEVOLUCION -> m.getOrigen().getId().equals(entrega.getBarra().getId()) ? 0 : -1;
                    case REMANENTE -> 1;
                    case CONSUMO -> 2;
                    default -> -1;
                };
                if (columna < 0) {
                    continue;
                }
                Suma suma = porBebida.computeIfAbsent(m.getBebida().getId(), id -> new Suma(m.getBebida(),
                        new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO}));
                suma.valores()[columna] = suma.valores()[columna].add(m.getCantidad());
            }
        }
        List<Renglon> renglones = new ArrayList<>();
        for (Suma s : porBebida.values()) {
            BigDecimal[] v = s.valores();
            renglones.add(renglon(s.bebida(), v[0].add(v[1]).add(v[2]), v[0], v[1], v[2], asistentes));
        }
        renglones.sort(Comparator.comparing(Renglon::bebida, POR_NOMBRE));
        return renglones;
    }

    private static Renglon renglon(Bebida bebida, BigDecimal entregado, BigDecimal devuelto, BigDecimal remanente,
            BigDecimal consumo, Integer asistentes) {
        return new Renglon(bebida, entregado, devuelto, remanente, consumo, porAsistente(consumo, asistentes));
    }

    private PantallaCierre pantalla(EventoOperativo evento, DatosDeCierre datos, List<Ubicacion> pendientes, List<Renglon> renglones,
            boolean cerrado) {
        BigDecimal total = renglones.stream().map(Renglon::consumo).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PantallaCierre(evento, catalogos.salon(evento.salonId()), datos.cantidadInvitados(), datos.usaSegmentos(),
                datos.asistenciaReal(), pendientes, renglones, total, porAsistente(total, datos.asistentes()), cerrado);
    }

    /**
     * Lo consumido dividido por los asistentes (docs/sprint-3-operacion.md, decisión 14): la asistencia real por
     * segmento si el tipo la cuenta así y ya se cargó (docs/sprint-4.md, decisión 8); si no, la cantidad de invitados.
     * Sin ninguna de las dos, no hay consumo por asistente.
     */
    private static BigDecimal porAsistente(BigDecimal consumo, Integer asistentes) {
        if (asistentes == null || asistentes <= 0) {
            return null;
        }
        return consumo.divide(BigDecimal.valueOf(asistentes), DECIMALES_POR_ASISTENTE, RoundingMode.HALF_UP);
    }
}
