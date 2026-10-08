package ar.edu.utn.vastio.bebida.aplicacion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService;
import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService.EventoDeJornada;
import ar.edu.utn.vastio.bebida.dominio.Bebida;
import ar.edu.utn.vastio.bebida.dominio.TipoUbicacion;
import ar.edu.utn.vastio.bebida.dominio.Ubicacion;
import ar.edu.utn.vastio.bebida.infraestructura.BebidaRepository;
import ar.edu.utn.vastio.bebida.infraestructura.BebidaRepository.SaldoBebida;
import ar.edu.utn.vastio.bebida.infraestructura.UbicacionRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.Turno;

/**
 * Consultar stock (UI-27). Dirección, Coordinación, Administración y Compras ven cualquier ubicación y el total
 * («Todas»), con estado y stock mínimo. La encargada de barra ve solo las barras de los salones con un evento en la
 * jornada actual, y solo su saldo: ni estado, ni stock mínimo, ni otras ubicaciones (operación a ciegas, regla 6).
 * Ver docs/sprint-3.md, decisiones 15 y 16.
 */
@Service
@Transactional(readOnly = true)
public class StockService {

    private static final Set<String> VEN_TODO = Set.of("DIRECCION", "COORDINACION", "ADMINISTRACION", "COMPRAS");

    private final UbicacionRepository ubicaciones;
    private final BebidaRepository bebidas;
    private final ConsultaEventoService eventos;
    private final CatalogoService catalogos;
    private final Clock reloj;

    public StockService(UbicacionRepository ubicaciones, BebidaRepository bebidas, ConsultaEventoService eventos,
            CatalogoService catalogos, Clock reloj) {
        this.ubicaciones = ubicaciones;
        this.bebidas = bebidas;
        this.eventos = eventos;
        this.catalogos = catalogos;
        this.reloj = reloj;
    }

    /** NEGATIVO: falta registrar un movimiento. BAJO: solo en el depósito madre, hasta el stock mínimo. */
    public enum EstadoStock { NEGATIVO, SIN_STOCK, BAJO, OK }

    /** Una ubicación que la persona puede consultar; para la barra, con el evento de la jornada en su salón. */
    public record UbicacionVisible(Ubicacion ubicacion, EventoDeJornada evento) {
    }

    /** {@code estado} null para la barra (no ve estado ni stock mínimo). */
    public record Renglon(Bebida bebida, BigDecimal cantidad, EstadoStock estado, boolean conStockMinimo) {
    }

    /** {@code ubicacion} null en el total («Todas»). */
    public record Existencias(Ubicacion ubicacion, List<Renglon> renglones) {
    }

    /**
     * La jornada actual: hoy, salvo antes de la hora de fin del turno que cruza la medianoche (06:00), que todavía es la
     * jornada de ayer. Los movimientos se imputan por evento, pero para saber qué barra opera hoy hace falta la fecha.
     */
    public LocalDate jornada() {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        LocalTime corte = catalogos.turnos().stream().filter(Turno::isCruzaMedianoche).map(Turno::getHoraFin)
                .min(Comparator.naturalOrder()).orElse(LocalTime.MIDNIGHT);
        return ahora.toLocalTime().isBefore(corte) ? ahora.toLocalDate().minusDays(1) : ahora.toLocalDate();
    }

    /** Lo que puede elegir la persona, en el orden del circuito. */
    public List<UbicacionVisible> ubicacionesVisibles(UsuarioActual quien) {
        if (veTodo(quien)) {
            return activasEnOrden().stream().map(u -> new UbicacionVisible(u, null)).toList();
        }
        Map<Short, EventoDeJornada> porSalon = eventos.eventosDeLaJornada(jornada()).stream()
                .collect(Collectors.toMap(EventoDeJornada::salonId, e -> e, (a, b) -> a));
        return activasEnOrden().stream()
                .filter(u -> u.getTipo() == TipoUbicacion.BARRA && porSalon.containsKey(u.getSalonId()))
                .map(u -> new UbicacionVisible(u, porSalon.get(u.getSalonId())))
                .toList();
    }

    /** El saldo de una ubicación; sin ubicación, el total por bebida (solo para quien ve todo). */
    public Existencias existencias(Short ubicacionId, UsuarioActual quien) {
        boolean completo = veTodo(quien);
        if (ubicacionId == null) {
            if (!completo) {
                throw new AccessDeniedException("La barra no ve el total del complejo.");
            }
            return new Existencias(null, renglones(bebidas.saldosTotales(), true, false, true));
        }
        Ubicacion ubicacion = ubicaciones.findById(ubicacionId).orElseThrow(() -> ProblemaException.noEncontrado(
                "UBICACION_INEXISTENTE", "No encontramos esa ubicación."));
        if (!completo && ubicacionesVisibles(quien).stream().noneMatch(v -> v.ubicacion().getId().equals(ubicacionId))) {
            throw new AccessDeniedException("La barra solo ve las barras de la jornada.");
        }
        boolean todasLasActivas = ubicacion.getTipo() != TipoUbicacion.BARRA;
        boolean conBajo = ubicacion.getTipo() == TipoUbicacion.DEPOSITO;
        return new Existencias(ubicacion, renglones(bebidas.saldosDe(ubicacionId), todasLasActivas, conBajo, completo));
    }

    /**
     * En depósitos y transiciones (y en el total), todas las bebidas activas; en las barras, solo las que tienen saldo.
     * Una dada de baja aparece mientras tenga saldo.
     */
    private List<Renglon> renglones(List<SaldoBebida> saldos, boolean todasLasActivas, boolean conBajo, boolean completo) {
        Map<Long, BigDecimal> porBebida = saldos.stream().collect(Collectors.toMap(SaldoBebida::getBebidaId, SaldoBebida::getCantidad));
        return bebidas.findAllConCodigos().stream()
                .filter(b -> {
                    BigDecimal cantidad = porBebida.getOrDefault(b.getId(), BigDecimal.ZERO);
                    return cantidad.signum() != 0 || (todasLasActivas && b.isActivo());
                })
                .sorted(Comparator.comparing(Bebida::getNombre, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Bebida::getPresentacion, String.CASE_INSENSITIVE_ORDER))
                .map(b -> {
                    BigDecimal cantidad = porBebida.getOrDefault(b.getId(), BigDecimal.ZERO);
                    return new Renglon(b, cantidad, completo ? estado(b, cantidad, conBajo) : null, completo);
                })
                .toList();
    }

    private static EstadoStock estado(Bebida bebida, BigDecimal cantidad, boolean conBajo) {
        if (cantidad.signum() < 0) {
            return EstadoStock.NEGATIVO;
        }
        if (cantidad.signum() == 0) {
            return EstadoStock.SIN_STOCK;
        }
        if (conBajo && bebida.getStockMinimo() != null && cantidad.compareTo(bebida.getStockMinimo()) <= 0) {
            return EstadoStock.BAJO;
        }
        return EstadoStock.OK;
    }

    private List<Ubicacion> activasEnOrden() {
        return ubicaciones.findAllConAbastecimiento().stream().filter(Ubicacion::isActivo)
                .sorted(Comparator.comparing(Ubicacion::getTipo).thenComparing(Ubicacion::getNombre, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static boolean veTodo(UsuarioActual quien) {
        return quien.tieneAlguno(VEN_TODO);
    }
}
