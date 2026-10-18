package ar.edu.utn.vastio.bebida.aplicacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService;
import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService.EventoOperativo;
import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.bebida.dominio.Bebida;
import ar.edu.utn.vastio.bebida.dominio.Entrega;
import ar.edu.utn.vastio.bebida.dominio.MovimientoStock;
import ar.edu.utn.vastio.bebida.dominio.TipoMovimiento;
import ar.edu.utn.vastio.bebida.dominio.TipoUbicacion;
import ar.edu.utn.vastio.bebida.dominio.Ubicacion;
import ar.edu.utn.vastio.bebida.infraestructura.BebidaRepository;
import ar.edu.utn.vastio.bebida.infraestructura.EntregaRepository;
import ar.edu.utn.vastio.bebida.infraestructura.MovimientoStockRepository;
import ar.edu.utn.vastio.bebida.infraestructura.UbicacionRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.Salon;
import ar.edu.utn.vastio.notificaciones.aplicacion.NotificacionService;
import ar.edu.utn.vastio.notificaciones.dominio.TipoNotificacion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;

/**
 * Registrar salida a barra (UI-31), con el retiro adicional (docs/sprint-3-operacion.md, decisiones 3 a 7). El primer
 * retiro de un evento hacia una barra abre la entrega (SALIDA_BARRA); los siguientes son RETIRO_ADICIONAL sobre la misma.
 * Sale de la ubicación de abastecimiento de la barra o, como contingencia, del depósito madre. Opera a ciegas: nunca
 * devuelve el saldo del origen; si queda negativo, se acepta y se avisa a Compras y Administración.
 */
@Service
@Transactional(readOnly = true)
public class EntregaService {

    /** Operan cualquier evento en curso. La encargada de barra, solo los de la jornada. */
    private static final Set<String> OPERAN_CUALQUIER_EVENTO = Set.of("DIRECCION", "COORDINACION", "COMPRAS");

    /** Largo de {@code notificacion.mensaje}. */
    private static final int LARGO_AVISO = 255;

    private static final Comparator<Bebida> POR_NOMBRE = Comparator.comparing(Bebida::getNombre, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Bebida::getPresentacion, String.CASE_INSENSITIVE_ORDER);

    private final EntregaRepository entregas;
    private final UbicacionRepository ubicaciones;
    private final BebidaRepository bebidas;
    private final MovimientoStockRepository movimientos;
    private final MovimientoService movimientoService;
    private final ConsultaEventoService eventos;
    private final StockService stock;
    private final CatalogoService catalogos;
    private final NotificacionService notificaciones;
    private final Clock reloj;

    public EntregaService(EntregaRepository entregas, UbicacionRepository ubicaciones, BebidaRepository bebidas,
            MovimientoStockRepository movimientos, MovimientoService movimientoService, ConsultaEventoService eventos,
            StockService stock, CatalogoService catalogos, NotificacionService notificaciones, Clock reloj) {
        this.entregas = entregas;
        this.ubicaciones = ubicaciones;
        this.bebidas = bebidas;
        this.movimientos = movimientos;
        this.movimientoService = movimientoService;
        this.eventos = eventos;
        this.stock = stock;
        this.catalogos = catalogos;
        this.notificaciones = notificaciones;
        this.reloj = reloj;
    }

    /** Un evento de la jornada con las barras activas de su salón. */
    public record EventoConBarras(EventoOperativo evento, Salon salon, List<Ubicacion> barras) {
    }

    /** Un retiro: los renglones registrados juntos, con quién, cuándo y desde dónde. */
    public record Retiro(TipoMovimiento tipo, Ubicacion origen, long usuarioId, OffsetDateTime momento,
            List<MovimientoStock> renglones) {
    }

    /** Lo entregado a la barra para el evento, en botellas. */
    public record Entregado(Bebida bebida, BigDecimal cantidad) {
    }

    /**
     * Lo que ve la pantalla. Sin saldos de ninguna ubicación (operación a ciegas).
     *
     * @param entrega         null si todavía no hubo retiros.
     * @param depositoMadre   solo si la barra permite el retiro directo.
     * @param bebidas         las activas, para el buscador y el lector.
     */
    public record PantallaEntrega(EventoOperativo evento, Salon salon, Ubicacion barra, Ubicacion origen,
            Ubicacion depositoMadre, Entrega entrega, List<Retiro> retiros, List<Entregado> yaEntregado, List<Bebida> bebidas) {
    }

    /** Una bebida del retiro, en botellas enteras mayores a 0. */
    public record Renglon(long bebidaId, BigDecimal cantidad) {
    }

    /** {@code directo}: sale del depósito madre como contingencia, si la barra lo permite. */
    public record DatosRetiro(boolean directo, List<Renglon> renglones) {
    }

    /** Los eventos de la jornada que la persona puede operar, con sus barras. */
    public List<EventoConBarras> jornada() {
        return eventos.eventosDeLaJornada(stock.jornada()).stream()
                .map(e -> eventos.operativo(e.eventoId()))
                .map(e -> new EventoConBarras(e, catalogos.salon(e.salonId()), barrasDelSalon(e.salonId())))
                .toList();
    }

    public PantallaEntrega pantalla(long eventoId, short barraId, UsuarioActual quien) {
        Ubicacion barra = barra(barraId);
        EventoOperativo evento = evento(eventoId, barra, quien);
        return pantalla(evento, barra, entregas.findByEventoIdAndBarraId(eventoId, barraId).orElse(null));
    }

    @Transactional
    public PantallaEntrega retirar(long eventoId, short barraId, DatosRetiro datos, UsuarioActual quien) {
        Ubicacion barra = barra(barraId);
        EventoOperativo evento = evento(eventoId, barra, quien);
        if (evento.estado() != EstadoEvento.EN_CURSO) {
            throw ProblemaException.conflicto("ESTADO_NO_PERMITE",
                    "El evento está en %s: la salida a barra se registra con el evento en curso.".formatted(evento.nombreDelEstado()));
        }
        Ubicacion origen = origen(barra, datos.directo());
        Map<Long, Bebida> porId = bebidasActivas(datos.renglones());
        // Mismo orden que MovimientoService (por id): dos retiros a la misma barra no abren dos entregas.
        Stream.of(origen.getId(), barra.getId()).distinct().sorted().forEach(movimientos::bloquearUbicacion);
        entregas.abiertasDeOtroEvento(barraId, eventoId).stream().findFirst().ifPresent(otra -> {
            throw ProblemaException.conflicto("ENTREGA_ABIERTA_EN_LA_BARRA",
                    "Primero registrá la devolución de %s.".formatted(eventos.operativo(otra.getEventoId()).nombre()));
        });
        OffsetDateTime momento = OffsetDateTime.now(reloj);
        Entrega entrega = entregas.findByEventoIdAndBarraId(eventoId, barraId).orElse(null);
        TipoMovimiento tipo;
        if (entrega == null) {
            entrega = entregas.save(Entrega.abrir(eventoId, barra, quien.id(), momento));
            tipo = TipoMovimiento.SALIDA_BARRA;
        } else if (!entrega.isAbierta()) {
            throw ProblemaException.conflicto("ENTREGA_CERRADA",
                    "La entrega a %s ya se cerró con la devolución: no admite más retiros.".formatted(barra.getNombre()));
        } else {
            tipo = TipoMovimiento.RETIRO_ADICIONAL;
        }
        List<String> negativos = new ArrayList<>();
        for (Renglon r : datos.renglones()) {
            Bebida bebida = porId.get(r.bebidaId());
            // Con la escala de la columna (numeric(10,2)): igual que si se leyera de la base.
            var registrado = movimientoService.registrar(MovimientoStock.retiroABarra(tipo, bebida,
                    r.cantidad().setScale(2, RoundingMode.UNNECESSARY), origen, entrega, quien.id(), momento));
            if (registrado.saldoOrigen().signum() < 0) {
                negativos.add(bebida.descripcion());
            }
        }
        if (!negativos.isEmpty()) {
            notificaciones.notificar(null, TipoNotificacion.ALERTA_STOCK, recortar(
                    "Saldo negativo en %s después de una salida a %s: %s. Falta registrar un movimiento."
                            .formatted(origen.getNombre(), barra.getNombre(), String.join(", ", negativos))),
                    quien.id(), List.of(RolCodigo.COMPRAS, RolCodigo.ADMINISTRACION));
        }
        return pantalla(evento, barra, entrega);
    }

    private PantallaEntrega pantalla(EventoOperativo evento, Ubicacion barra, Entrega entrega) {
        List<MovimientoStock> lista = entrega == null ? List.of() : movimientos.deEntrega(entrega.getId());
        return new PantallaEntrega(evento, catalogos.salon(evento.salonId()), barra, barra.getAbastecimiento(),
                barra.isPermiteRetiroDirecto() ? depositoMadre() : null, entrega, retiros(lista), entregado(lista),
                bebidas.findAllConCodigos().stream().filter(Bebida::isActivo).sorted(POR_NOMBRE).toList());
    }

    /** Agrupa por retiro: mismo tipo, origen, persona y momento. */
    private static List<Retiro> retiros(List<MovimientoStock> lista) {
        // El instante, no el OffsetDateTime: el leído de la base puede venir con otro desplazamiento que el recién creado.
        record Clave(TipoMovimiento tipo, Short origen, long usuario, Instant momento) {
        }
        Map<Clave, List<MovimientoStock>> grupos = new LinkedHashMap<>();
        lista.stream().filter(m -> esRetiro(m.getTipo())).forEach(m -> grupos.computeIfAbsent(
                new Clave(m.getTipo(), m.getOrigen().getId(), m.getUsuarioId(), m.getFechaHora().toInstant()),
                k -> new ArrayList<>()).add(m));
        return grupos.values().stream().map(ms -> {
            MovimientoStock primero = ms.getFirst();
            return new Retiro(primero.getTipo(), primero.getOrigen(), primero.getUsuarioId(), primero.getFechaHora(), ms);
        }).toList();
    }

    private static List<Entregado> entregado(List<MovimientoStock> lista) {
        Map<Long, Bebida> porId = new LinkedHashMap<>();
        Map<Long, BigDecimal> cantidades = new LinkedHashMap<>();
        lista.stream().filter(m -> esRetiro(m.getTipo())).forEach(m -> {
            porId.putIfAbsent(m.getBebida().getId(), m.getBebida());
            cantidades.merge(m.getBebida().getId(), m.getCantidad(), BigDecimal::add);
        });
        return porId.values().stream().sorted(POR_NOMBRE).map(b -> new Entregado(b, cantidades.get(b.getId()))).toList();
    }

    private static boolean esRetiro(TipoMovimiento tipo) {
        return tipo == TipoMovimiento.SALIDA_BARRA || tipo == TipoMovimiento.RETIRO_ADICIONAL;
    }

    /**
     * El evento, si la persona puede operarlo en esa barra. La encargada de barra solo opera los eventos de la jornada,
     * en las barras de su salón: lo demás es 403. Para el resto, la barra tiene que ser del salón del evento.
     */
    private EventoOperativo evento(long eventoId, Ubicacion barra, UsuarioActual quien) {
        EventoOperativo evento = eventos.operativo(eventoId);
        boolean delSalon = Objects.equals(barra.getSalonId(), evento.salonId());
        if (!quien.tieneAlguno(OPERAN_CUALQUIER_EVENTO)) {
            boolean enLaJornada = eventos.eventosDeLaJornada(stock.jornada()).stream().anyMatch(e -> e.eventoId() == eventoId);
            if (!enLaJornada || !delSalon) {
                throw new AccessDeniedException("La barra solo opera los eventos de la jornada, en las barras de su salón.");
            }
        }
        if (!delSalon) {
            throw ProblemaException.reglaDeNegocio("BARRA_DE_OTRO_SALON",
                    "%s no es una barra del salón del evento. Elegí una barra de ese salón.".formatted(barra.getNombre()));
        }
        return evento;
    }

    private Ubicacion barra(short id) {
        Ubicacion barra = ubicaciones.findById(id).orElseThrow(() -> ProblemaException.noEncontrado("UBICACION_INEXISTENTE",
                "No encontramos esa barra."));
        if (barra.getTipo() != TipoUbicacion.BARRA) {
            throw ProblemaException.reglaDeNegocio("NO_ES_BARRA", "%s no es una barra.".formatted(barra.getNombre()));
        }
        if (!barra.isActivo()) {
            throw ProblemaException.reglaDeNegocio("UBICACION_INACTIVA", "%s está dada de baja.".formatted(barra.getNombre()));
        }
        return barra;
    }

    /** La ubicación de abastecimiento; con {@code directo}, el depósito madre (solo si la barra lo permite). */
    private Ubicacion origen(Ubicacion barra, boolean directo) {
        if (!directo) {
            return barra.getAbastecimiento();
        }
        if (!barra.isPermiteRetiroDirecto()) {
            throw ProblemaException.reglaDeNegocio("SIN_RETIRO_DIRECTO",
                    "%s no permite retirar del depósito madre: se abastece de %s.".formatted(barra.getNombre(),
                            barra.getAbastecimiento().getNombre()));
        }
        return depositoMadre();
    }

    private Ubicacion depositoMadre() {
        return ubicaciones.findFirstByTipoAndActivoTrue(TipoUbicacion.DEPOSITO)
                .orElseThrow(() -> ProblemaException.reglaDeNegocio("SIN_DEPOSITO_MADRE",
                        "No hay un depósito madre activo. Configuralo en Parámetros, Ubicaciones de stock."));
    }

    private List<Ubicacion> barrasDelSalon(short salonId) {
        return ubicaciones.findByTipoAndSalonIdAndActivoTrue(TipoUbicacion.BARRA, salonId).stream()
                .sorted(Comparator.comparing(Ubicacion::getNombre, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    /** Cada bebida una sola vez y activa (decisión 6). */
    private Map<Long, Bebida> bebidasActivas(List<Renglon> renglones) {
        if (renglones == null || renglones.isEmpty()) {
            throw ProblemaException.reglaDeNegocio("RETIRO_VACIO", "Cargá al menos una bebida con su cantidad.");
        }
        Set<Long> vistas = new HashSet<>();
        for (Renglon r : renglones) {
            if (!vistas.add(r.bebidaId())) {
                throw ProblemaException.reglaDeNegocio("BEBIDA_REPETIDA_EN_RETIRO",
                        "Una bebida aparece dos veces. Sumá las cantidades en un solo renglón.");
            }
        }
        Map<Long, Bebida> porId = bebidas.findAllById(vistas).stream().collect(Collectors.toMap(Bebida::getId, Function.identity()));
        for (Renglon r : renglones) {
            Bebida bebida = porId.get(r.bebidaId());
            if (bebida == null) {
                throw ProblemaException.reglaDeNegocio("BEBIDA_INEXISTENTE",
                        "Una de las bebidas ya no está en el catálogo. Volvé a cargar la pantalla.");
            }
            if (!bebida.isActivo()) {
                throw ProblemaException.reglaDeNegocio("BEBIDA_DADA_DE_BAJA",
                        "%s está dada de baja: sacala del retiro.".formatted(bebida.descripcion()));
            }
        }
        return porId;
    }

    private static String recortar(String mensaje) {
        return mensaje.length() <= LARGO_AVISO ? mensaje : mensaje.substring(0, LARGO_AVISO - 1) + "…";
    }
}
