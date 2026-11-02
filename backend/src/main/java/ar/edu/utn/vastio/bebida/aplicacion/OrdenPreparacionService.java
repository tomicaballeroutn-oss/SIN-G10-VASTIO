package ar.edu.utn.vastio.bebida.aplicacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService;
import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService.EventoParaOrden;
import ar.edu.utn.vastio.bebida.dominio.DetalleOrden;
import ar.edu.utn.vastio.bebida.dominio.EstadoOrden;
import ar.edu.utn.vastio.bebida.dominio.OrdenPreparacion;
import ar.edu.utn.vastio.bebida.dominio.TipoUbicacion;
import ar.edu.utn.vastio.bebida.dominio.Ubicacion;
import ar.edu.utn.vastio.bebida.infraestructura.OrdenPreparacionRepository;
import ar.edu.utn.vastio.bebida.infraestructura.UbicacionRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.notificaciones.aplicacion.NotificacionService;
import ar.edu.utn.vastio.notificaciones.dominio.TipoNotificacion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;

/**
 * Generar orden de preparación (UI-35, docs/sprint-4.md, decisiones 11 a 22). Se genera sola al confirmar el evento y,
 * si no tiene una vigente, a mano con el evento Confirmado o En curso. Una por evento, con los totales de todas sus
 * barras, desde el depósito madre. La cancelación la descarta; el cierre la cierra. Compras, Coordinación y Dirección
 * la gestionan; Administración la consulta (la autorización por perfil está en el controller).
 */
@Service
@Transactional(readOnly = true)
public class OrdenPreparacionService {

    /** «Ya comprometido por otros eventos próximos»: las órdenes vigentes de esta cantidad de días desde hoy. */
    public static final int DIAS_COMPROMETIDO = 15;

    private static final ZoneId ZONA = ZoneId.of(VastioApplication.ZONA_HORARIA);

    private final OrdenPreparacionRepository ordenes;
    private final ConsultaEventoService eventos;
    private final SugerenciaOrden sugerencias;
    private final UbicacionRepository ubicaciones;
    private final NotificacionService notificaciones;

    public OrdenPreparacionService(OrdenPreparacionRepository ordenes, ConsultaEventoService eventos,
            SugerenciaOrden sugerencias, UbicacionRepository ubicaciones, NotificacionService notificaciones) {
        this.ordenes = ordenes;
        this.eventos = eventos;
        this.sugerencias = sugerencias;
        this.ubicaciones = ubicaciones;
        this.notificaciones = notificaciones;
    }

    /** Un renglón con lo que ya tienen comprometido las órdenes vigentes de otros eventos próximos, en botellas. */
    public record Renglon(DetalleOrden detalle, BigDecimal comprometido) {
    }

    /**
     * @param orden la vigente o, si no hay, la última (cerrada o descartada); null si el evento nunca tuvo.
     * @param renglones vacío si no hay orden.
     */
    public record Consulta(EventoParaOrden evento, OrdenPreparacion orden, List<Renglon> renglones) {

        /** Los asistentes cambiaron desde que se calculó la sugerencia (decisión 19). */
        public boolean asistentesCambiaron() {
            return orden != null && orden.isVigente() && !Objects.equals(orden.getAsistentesBase(), evento.asistentesPrevistos());
        }
    }

    /** Un evento Confirmado o En curso y su orden vigente (null si no tiene), para la lista de órdenes. */
    public record Resumen(EventoParaOrden evento, OrdenPreparacion orden) {
    }

    public Consulta consultar(long eventoId) {
        EventoParaOrden evento = eventos.paraOrden(eventoId);
        Optional<OrdenPreparacion> orden = ordenes.vigente(eventoId).or(() -> ordenes.findFirstByEventoIdOrderByIdDesc(eventoId));
        if (orden.isEmpty()) {
            return new Consulta(evento, null, List.of());
        }
        Map<Long, BigDecimal> comprometido = orden.get().isVigente() ? comprometido(eventoId) : Map.of();
        return new Consulta(evento, orden.get(), orden.get().getRenglones().stream()
                .map(r -> new Renglon(r, comprometido.getOrDefault(r.getBebida().getId(), BigDecimal.ZERO)))
                .toList());
    }

    /** Los eventos Confirmados y En curso, del más próximo al más lejano, con su orden vigente. */
    public List<Resumen> listado() {
        List<EventoParaOrden> lista = eventos.paraOrdenes();
        Map<Long, OrdenPreparacion> porEvento = ordenes.vigentes(lista.stream().map(e -> e.evento().eventoId()).toList())
                .stream().collect(Collectors.toMap(OrdenPreparacion::getEventoId, Function.identity()));
        return lista.stream().map(e -> new Resumen(e, porEvento.get(e.evento().eventoId()))).toList();
    }

    /**
     * Generar orden a mano: el evento Confirmado o En curso sin orden vigente.
     *
     * @throws ProblemaException 409 ORDEN_EXISTENTE; 422 si el estado no lo permite o no hay depósito madre.
     */
    @Transactional
    public OrdenPreparacion generar(long eventoId, UsuarioActual quien) {
        EventoParaOrden evento = eventos.paraOrden(eventoId);
        if (!ConsultaEventoService.CON_ORDEN.contains(evento.evento().estado())) {
            throw ProblemaException.reglaDeNegocio("ESTADO_NO_PERMITE", "El evento está en %s: la orden se genera con el evento confirmado o en curso."
                    .formatted(evento.evento().nombreDelEstado()));
        }
        if (ordenes.vigente(eventoId).isPresent()) {
            throw ProblemaException.conflicto("ORDEN_EXISTENTE", "El evento ya tiene su orden de preparación. Volvé a abrirla.");
        }
        Ubicacion madre = madre().orElseThrow(() -> ProblemaException.reglaDeNegocio("SIN_DEPOSITO_MADRE",
                "No hay un depósito madre activo: configuralo en Ubicaciones de stock antes de generar la orden."));
        return crear(evento, madre, quien.id());
    }

    /** Al confirmar el evento (decisión 11). Si ya tiene una vigente o no hay depósito madre, no hace nada. */
    @Transactional
    public void generarAlConfirmar(long eventoId) {
        if (ordenes.vigente(eventoId).isPresent()) {
            return;
        }
        madre().ifPresent(madre -> crear(eventos.paraOrden(eventoId), madre, null));
    }

    /**
     * Recalcular sugerencia (decisión 19): mientras no esté confirmada. Pisa lo ajustado.
     *
     * @param version la de la orden que se veía: si otra persona la cambió en el medio, 409.
     */
    @Transactional
    public OrdenPreparacion recalcular(long eventoId, int version, UsuarioActual quien) {
        EventoParaOrden evento = eventos.paraOrden(eventoId);
        OrdenPreparacion orden = editable(eventoId, version, evento, "recalcular");
        orden.recalcular(sugerencias.calcular(evento.tipoEventoId(), evento.asistentesPrevistos()), quien.id(),
                OffsetDateTime.now());
        return ordenes.saveAndFlush(orden);
    }

    /** Al cancelar el evento: la orden vigente queda Descartada (docs/maquina-de-estados.md). */
    @Transactional
    public void descartarAlCancelar(long eventoId) {
        ordenes.vigente(eventoId).ifPresent(OrdenPreparacion::descartar);
    }

    /**
     * La orden vigente, Sugerida o Modificada, de un evento Confirmado o En curso, en la versión que se veía.
     *
     * @param operacion para el mensaje: «recalcular», «ajustar».
     */
    OrdenPreparacion editable(long eventoId, int version, EventoParaOrden evento, String operacion) {
        if (!ConsultaEventoService.CON_ORDEN.contains(evento.evento().estado())) {
            throw ProblemaException.reglaDeNegocio("ESTADO_NO_PERMITE", "El evento está en %s: no se puede %s la orden."
                    .formatted(evento.evento().nombreDelEstado(), operacion));
        }
        OrdenPreparacion orden = vigente(eventoId, version);
        if (!EstadoOrden.EDITABLES.contains(orden.getEstado())) {
            throw ProblemaException.reglaDeNegocio("ORDEN_CONFIRMADA",
                    "La orden está confirmada: reabrila para %s.".formatted(operacion));
        }
        return orden;
    }

    /** La orden vigente en la versión que se veía. */
    OrdenPreparacion vigente(long eventoId, int version) {
        OrdenPreparacion orden = ordenes.vigente(eventoId).orElseThrow(() -> ProblemaException.noEncontrado("SIN_ORDEN",
                "El evento no tiene una orden de preparación vigente."));
        if (orden.getVersion() != version) {
            throw ProblemaException.conflicto("ORDEN_MODIFICADA",
                    "Otra persona cambió la orden mientras la mirabas. Volvé a abrirla para ver los cambios.");
        }
        return orden;
    }

    private OrdenPreparacion crear(EventoParaOrden evento, Ubicacion madre, Long usuarioId) {
        OrdenPreparacion orden = ordenes.save(OrdenPreparacion.generar(evento.evento().eventoId(), madre, usuarioId,
                sugerencias.calcular(evento.tipoEventoId(), evento.asistentesPrevistos()), OffsetDateTime.now()));
        notificaciones.notificar(evento.evento().eventoId(), TipoNotificacion.ORDEN_PREPARACION,
                recortar("Orden de preparación sugerida: %s (%s)".formatted(evento.evento().nombre(), evento.evento().codigo())),
                usuarioId, List.of(RolCodigo.COMPRAS));
        return orden;
    }

    /** Por bebida, lo que tienen a retirar las órdenes vigentes de los otros eventos de los próximos días. */
    private Map<Long, BigDecimal> comprometido(long eventoId) {
        LocalDate hoy = LocalDate.now(ZONA);
        List<Long> proximos = eventos.paraOrdenes().stream()
                .filter(e -> e.evento().eventoId() != eventoId && !e.fecha().isBefore(hoy)
                        && !e.fecha().isAfter(hoy.plusDays(DIAS_COMPROMETIDO)))
                .map(e -> e.evento().eventoId()).toList();
        Map<Long, BigDecimal> suma = new HashMap<>();
        ordenes.vigentes(proximos).forEach(o -> o.getRenglones().forEach(r ->
                suma.merge(r.getBebida().getId(), r.getCantidadARetirar(), BigDecimal::add)));
        return suma;
    }

    private Optional<Ubicacion> madre() {
        return ubicaciones.findFirstByTipoAndActivoTrue(TipoUbicacion.DEPOSITO);
    }

    /** {@code notificacion.mensaje} es varchar(255). */
    private static String recortar(String mensaje) {
        return mensaje.length() <= 255 ? mensaje : mensaje.substring(0, 254) + "…";
    }
}
