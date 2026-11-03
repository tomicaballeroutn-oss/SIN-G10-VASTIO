package ar.edu.utn.vastio.bebida.aplicacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService;
import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService.EventoOperativo;
import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.bebida.dominio.Bebida;
import ar.edu.utn.vastio.bebida.dominio.Entrega;
import ar.edu.utn.vastio.bebida.dominio.EstadoOrden;
import ar.edu.utn.vastio.bebida.dominio.MovimientoStock;
import ar.edu.utn.vastio.bebida.dominio.TipoMovimiento;
import ar.edu.utn.vastio.bebida.dominio.TipoUbicacion;
import ar.edu.utn.vastio.bebida.dominio.Ubicacion;
import ar.edu.utn.vastio.bebida.infraestructura.BebidaRepository;
import ar.edu.utn.vastio.bebida.infraestructura.BebidaRepository.SaldoBebida;
import ar.edu.utn.vastio.bebida.infraestructura.EntregaRepository;
import ar.edu.utn.vastio.bebida.infraestructura.MovimientoStockRepository;
import ar.edu.utn.vastio.bebida.infraestructura.OrdenPreparacionRepository;
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
    private final OrdenPreparacionRepository ordenes;
    private final EntityManager entityManager;
    private final Clock reloj;

    public EntregaService(EntregaRepository entregas, UbicacionRepository ubicaciones, BebidaRepository bebidas,
            MovimientoStockRepository movimientos, MovimientoService movimientoService, ConsultaEventoService eventos,
            StockService stock, CatalogoService catalogos, NotificacionService notificaciones, OrdenPreparacionRepository ordenes,
            EntityManager entityManager, Clock reloj) {
        this.ordenes = ordenes;
        this.entityManager = entityManager;
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
     * @param aDevolver       con la entrega abierta, las bebidas de la barra (solo los nombres: se declara a ciegas).
     * @param enTransicion    con la entrega abierta y una barra abastecida por una transición, las bebidas que hay ahí.
     */
    public record PantallaEntrega(EventoOperativo evento, Salon salon, Ubicacion barra, Ubicacion origen,
            Ubicacion depositoMadre, Entrega entrega, List<Retiro> retiros, List<Entregado> yaEntregado, List<Bebida> bebidas,
            List<Bebida> aDevolver, List<Bebida> enTransicion) {
    }

    /** Una bebida del retiro, en botellas enteras mayores a 0. */
    public record Renglon(long bebidaId, BigDecimal cantidad) {
    }

    /** {@code directo}: sale del depósito madre como contingencia, si la barra lo permite. */
    public record DatosRetiro(boolean directo, List<Renglon> renglones) {
    }

    /** Lo que vuelve y lo que queda de una bebida, en botellas enteras ≥ 0. Si falta, 0 y 0: se consumió todo. */
    public record RenglonDevolucion(long bebidaId, BigDecimal devuelto, BigDecimal remanente) {
    }

    /** {@code transicion}: lo que queda en el depósito de transición y vuelve al madre (si la barra se abastece de uno). */
    public record DatosDevolucion(List<RenglonDevolucion> renglones, List<Renglon> transicion) {
    }

    /** El consumo real de una bebida en la barra: entregado − devuelto − remanente, en botellas. */
    public record Consumo(Bebida bebida, BigDecimal entregado, BigDecimal devuelto, BigDecimal remanente, BigDecimal consumo) {
    }

    /** Recién después de registrar se muestra el consumo (decisión 10). */
    public record DevolucionRegistrada(PantallaEntrega pantalla, List<Consumo> consumo, List<Entregado> desdeTransicion) {
    }

    /**
     * Los eventos de la jornada con sus barras, y los que todavía esperan una devolución aunque ya no sean de la jornada
     * (p. ej. después de las 06:00).
     */
    public List<EventoConBarras> jornada() {
        Set<Long> ids = new LinkedHashSet<>();
        eventos.eventosDeLaJornada(stock.jornada()).forEach(e -> ids.add(e.eventoId()));
        entregas.abiertas().forEach(e -> ids.add(e.getEventoId()));
        return ids.stream()
                .map(eventos::operativo)
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
            Entrega nueva = Entrega.abrir(eventoId, barra, quien.id(), momento);
            // La salida se hace según la orden confirmada, si hay (docs/sprint-4.md, decisión 25).
            ordenes.vigente(eventoId).filter(o -> o.getEstado() == EstadoOrden.CONFIRMADA)
                    .ifPresent(o -> nueva.vincularOrden(o.getId()));
            entrega = entregas.save(nueva);
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

    /**
     * Devolución de bebida (UI-32, decisiones 8 a 11): por cada bebida de la barra, lo que vuelve al depósito madre
     * (DEVOLUCION) y lo que queda para el evento siguiente (REMANENTE). Si la barra se abastece de una transición, lo que
     * queda ahí también vuelve al madre. Lo consumido sale de la barra (CONSUMO), así su saldo queda en el remanente y el
     * evento siguiente arranca de lo que de verdad hay. La entrega queda CERRADA. La encargada declara sin ver lo
     * esperado: el consumo se devuelve recién ahora.
     */
    @Transactional
    public DevolucionRegistrada devolver(long eventoId, short barraId, DatosDevolucion datos, UsuarioActual quien) {
        Ubicacion barra = barra(barraId);
        EventoOperativo evento = evento(eventoId, barra, quien);
        if (evento.estado() != EstadoEvento.EN_CURSO && evento.estado() != EstadoEvento.REALIZADO) {
            throw ProblemaException.conflicto("ESTADO_NO_PERMITE",
                    "El evento está en %s: la devolución se registra con el evento en curso o realizado."
                            .formatted(evento.nombreDelEstado()));
        }
        Ubicacion madre = depositoMadre();
        Ubicacion transicion = barra.getAbastecimiento().getTipo() == TipoUbicacion.TRANSICION ? barra.getAbastecimiento() : null;
        // Mismo orden que MovimientoService (por id). Después se vuelve a leer todo: lo leído antes puede estar viejo.
        Stream.of(madre, barra, transicion).filter(Objects::nonNull).map(Ubicacion::getId).distinct().sorted()
                .forEach(movimientos::bloquearUbicacion);
        Entrega entrega = entregas.findByEventoIdAndBarraId(eventoId, barraId).orElseThrow(() -> ProblemaException.conflicto(
                "SIN_ENTREGA", "No hubo retiros a %s para este evento: no hay nada que devolver.".formatted(barra.getNombre())));
        entityManager.refresh(entrega);
        if (!entrega.isAbierta()) {
            throw ProblemaException.conflicto("DEVOLUCION_REGISTRADA",
                    "La devolución de %s ya se registró.".formatted(barra.getNombre()));
        }
        OffsetDateTime momento = OffsetDateTime.now(reloj);
        Map<Long, BigDecimal> saldos = saldos(barra.getId());
        List<Bebida> enLaBarra = paraDevolver(barra.getId(), entrega);
        Map<Long, RenglonDevolucion> declarado = declarado(datos.renglones(), enLaBarra);
        List<Consumo> consumo = new ArrayList<>();
        for (Bebida bebida : enLaBarra) {
            RenglonDevolucion r = declarado.getOrDefault(bebida.getId(),
                    new RenglonDevolucion(bebida.getId(), BigDecimal.ZERO, BigDecimal.ZERO));
            BigDecimal devuelto = enBotellas(r.devuelto());
            BigDecimal remanente = enBotellas(r.remanente());
            BigDecimal entregado = saldos.getOrDefault(bebida.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.UNNECESSARY);
            BigDecimal suma = devuelto.add(remanente);
            // Cero también supera un saldo negativo: omitir la bebida no puede producir consumo negativo.
            if (suma.compareTo(entregado) > 0) {
                throw ProblemaException.reglaDeNegocio("SUPERA_LO_DE_LA_BARRA",
                        "%s: lo que vuelve y lo que queda suman más de lo que hay en la barra. Revisá el conteo."
                                .formatted(bebida.descripcion()));
            }
            if (devuelto.signum() > 0) {
                movimientoService.registrar(MovimientoStock.devolucion(bebida, devuelto, barra, madre, entrega, quien.id(), momento));
            }
            if (remanente.signum() > 0) {
                movimientoService.registrar(MovimientoStock.remanente(bebida, remanente, entrega, quien.id(), momento));
            }
            BigDecimal consumido = entregado.subtract(suma);
            // Sale de la barra lo que se tomó: su saldo queda en el remanente, que es lo que hay (decisiones 12 y 13).
            if (consumido.signum() > 0) {
                movimientoService.registrar(MovimientoStock.consumo(bebida, consumido, entrega, quien.id(), momento));
            }
            consumo.add(new Consumo(bebida, entregado, devuelto, remanente, consumido));
        }
        List<Entregado> desdeTransicion = devolverTransicion(transicion, madre, datos.transicion(), entrega, quien.id(), momento);
        entrega.cerrar(quien.id(), momento);
        return new DevolucionRegistrada(pantalla(evento, barra, entrega), consumo, desdeTransicion);
    }

    /** Lo que queda en la transición vuelve al madre. Nunca se rechaza por saldo (regla 5): si queda negativo, se avisa. */
    private List<Entregado> devolverTransicion(Ubicacion transicion, Ubicacion madre, List<Renglon> renglones, Entrega entrega,
            long usuarioId, OffsetDateTime momento) {
        List<Renglon> lista = renglones == null ? List.of() : renglones;
        if (transicion == null) {
            if (!lista.isEmpty()) {
                throw ProblemaException.reglaDeNegocio("SIN_TRANSICION",
                        "%s no se abastece de un depósito de transición.".formatted(entrega.getBarra().getNombre()));
            }
            return List.of();
        }
        Map<Long, Bebida> permitidas = enTransicion(transicion.getId()).stream()
                .collect(Collectors.toMap(Bebida::getId, Function.identity()));
        Set<Long> vistas = new HashSet<>();
        List<Entregado> devuelto = new ArrayList<>();
        List<String> negativos = new ArrayList<>();
        for (Renglon r : lista) {
            if (!vistas.add(r.bebidaId())) {
                throw ProblemaException.reglaDeNegocio("BEBIDA_REPETIDA_EN_DEVOLUCION",
                        "Una bebida aparece dos veces en la transición. Sumá las cantidades en un solo renglón.");
            }
            Bebida bebida = permitidas.get(r.bebidaId());
            if (bebida == null) {
                throw ProblemaException.reglaDeNegocio("BEBIDA_FUERA_DE_LA_TRANSICION",
                        "Una de las bebidas no está en %s. Volvé a cargar la pantalla.".formatted(transicion.getNombre()));
            }
            BigDecimal cantidad = enBotellas(r.cantidad());
            if (cantidad.signum() > 0) {
                var registrado = movimientoService.registrar(MovimientoStock.devolucion(bebida, cantidad, transicion, madre, entrega,
                        usuarioId, momento));
                if (registrado.saldoOrigen().signum() < 0) {
                    negativos.add(bebida.descripcion());
                }
                devuelto.add(new Entregado(bebida, cantidad));
            }
        }
        if (!negativos.isEmpty()) {
            notificaciones.notificar(null, TipoNotificacion.ALERTA_STOCK, recortar(
                    "Saldo negativo en %s después de una devolución: %s. Falta registrar un movimiento."
                            .formatted(transicion.getNombre(), String.join(", ", negativos))),
                    usuarioId, List.of(RolCodigo.COMPRAS, RolCodigo.ADMINISTRACION));
        }
        return devuelto;
    }

    /** Cada bebida una sola vez y de las que están en la barra. */
    private static Map<Long, RenglonDevolucion> declarado(List<RenglonDevolucion> renglones, List<Bebida> enLaBarra) {
        Set<Long> permitidas = enLaBarra.stream().map(Bebida::getId).collect(Collectors.toSet());
        Map<Long, RenglonDevolucion> porBebida = new HashMap<>();
        for (RenglonDevolucion r : renglones == null ? List.<RenglonDevolucion>of() : renglones) {
            if (!permitidas.contains(r.bebidaId())) {
                throw ProblemaException.reglaDeNegocio("BEBIDA_FUERA_DE_LA_BARRA",
                        "Una de las bebidas no está en la barra. Volvé a cargar la pantalla.");
            }
            if (porBebida.put(r.bebidaId(), r) != null) {
                throw ProblemaException.reglaDeNegocio("BEBIDA_REPETIDA_EN_DEVOLUCION",
                        "Una bebida aparece dos veces. Cargá lo que vuelve y lo que queda en un solo renglón.");
            }
        }
        return porBebida;
    }

    private static BigDecimal enBotellas(BigDecimal cantidad) {
        return (cantidad == null ? BigDecimal.ZERO : cantidad).setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Saldo teórico de la ubicación por bebida, en botellas. Lo lee el backend; nunca va a la barra. */
    private Map<Long, BigDecimal> saldos(short ubicacionId) {
        return bebidas.saldosDe(ubicacionId).stream()
                .collect(Collectors.toMap(SaldoBebida::getBebidaId, SaldoBebida::getCantidad));
    }

    /** Las bebidas con saldo en la barra y las retiradas en esta entrega, por nombre. Sin cantidades. */
    private List<Bebida> paraDevolver(short barraId, Entrega entrega) {
        Set<Long> ids = new HashSet<>();
        bebidas.saldosDe(barraId).stream().filter(s -> s.getCantidad().signum() != 0).forEach(s -> ids.add(s.getBebidaId()));
        movimientos.deEntrega(entrega.getId()).forEach(m -> ids.add(m.getBebida().getId()));
        return bebidas.findAllById(ids).stream().sorted(POR_NOMBRE).toList();
    }

    /** Las bebidas con saldo en la transición, por nombre. Sin cantidades. */
    private List<Bebida> enTransicion(short transicionId) {
        Set<Long> ids = bebidas.saldosDe(transicionId).stream().filter(s -> s.getCantidad().signum() != 0)
                .map(SaldoBebida::getBebidaId).collect(Collectors.toSet());
        return bebidas.findAllById(ids).stream().sorted(POR_NOMBRE).toList();
    }

    private PantallaEntrega pantalla(EventoOperativo evento, Ubicacion barra, Entrega entrega) {
        List<MovimientoStock> lista = entrega == null ? List.of() : movimientos.deEntrega(entrega.getId());
        boolean devolviendo = entrega != null && entrega.isAbierta();
        Ubicacion origen = barra.getAbastecimiento();
        return new PantallaEntrega(evento, catalogos.salon(evento.salonId()), barra, origen,
                barra.isPermiteRetiroDirecto() ? depositoMadre() : null, entrega, retiros(lista), entregado(lista),
                bebidas.findAllConCodigos().stream().filter(Bebida::isActivo).sorted(POR_NOMBRE).toList(),
                devolviendo ? paraDevolver(barra.getId(), entrega) : List.of(),
                devolviendo && origen.getTipo() == TipoUbicacion.TRANSICION ? enTransicion(origen.getId()) : List.of());
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
            // Fuera de la jornada, solo si en esa barra espera la devolución (p. ej. pasadas las 06:00).
            boolean enLaJornada = eventos.eventosDeLaJornada(stock.jornada()).stream().anyMatch(e -> e.eventoId() == eventoId)
                    || entregas.findByEventoIdAndBarraId(eventoId, barra.getId()).filter(Entrega::isAbierta).isPresent();
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
