package ar.edu.utn.vastio.bebida.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService;
import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService.EventoParaOrden;
import ar.edu.utn.vastio.bebida.aplicacion.OrdenPreparacionService.Consulta;
import ar.edu.utn.vastio.bebida.aplicacion.OrdenPreparacionService.Renglon;
import ar.edu.utn.vastio.bebida.aplicacion.OrdenPreparacionService.Resumen;
import ar.edu.utn.vastio.bebida.dominio.Bebida;
import ar.edu.utn.vastio.bebida.dominio.EstadoOrden;
import ar.edu.utn.vastio.bebida.dominio.FuenteSugerencia;
import ar.edu.utn.vastio.bebida.dominio.OrdenPreparacion;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.configuracion.dominio.Salon;

/**
 * Respuestas de la orden de preparación (UI-35). Cantidades en botellas; la pantalla las muestra en cajas y botellas
 * con {@code unidadesPorBulto}. Sin importes: el sistema no maneja dinero.
 */
public final class OrdenDto {

    /** Gestionan la orden (decisión 22); Administración solo consulta. */
    static final Set<String> GESTIONAN = Set.of("DIRECCION", "COORDINACION", "COMPRAS");

    private OrdenDto() {
    }

    public record Persona(long id, String nombre) {
        static Persona de(Long id, Map<Long, String> nombres) {
            return id == null ? null : new Persona(id, nombres.get(id));
        }
    }

    /** Quién y cuándo; null si no pasó. */
    public record Hecho(Persona usuario, OffsetDateTime fechaHora) {
        static Hecho de(Long usuarioId, OffsetDateTime fechaHora, Map<Long, String> nombres) {
            return fechaHora == null ? null : new Hecho(Persona.de(usuarioId, nombres), fechaHora);
        }
    }

    public record SalonResumen(short id, String codigo, String nombre) {
        static SalonResumen de(Salon s) {
            return new SalonResumen(s.getId(), s.getCodigo(), s.getNombre());
        }
    }

    /** {@code asistentes}: los previstos hoy (total por segmento en egresados; si no, los invitados). */
    public record EventoOrden(long id, String codigo, String nombre, String estado, LocalDate fecha, SalonResumen salon,
            Integer asistentes) {
        static EventoOrden de(EventoParaOrden e, Salon salon) {
            return new EventoOrden(e.evento().eventoId(), e.evento().codigo(), e.evento().nombre(), e.evento().estado().name(),
                    e.fecha(), SalonResumen.de(salon), e.asistentesPrevistos());
        }
    }

    /** {@code comprometido}: lo que tienen a retirar las órdenes vigentes de otros eventos próximos. */
    public record RenglonOrden(long bebidaId, String nombre, String presentacion, String unidad, short unidadesPorBulto,
            boolean activa, BigDecimal sugerida, BigDecimal aRetirar, BigDecimal comprometido) {
        static RenglonOrden de(Renglon r) {
            Bebida b = r.detalle().getBebida();
            return new RenglonOrden(b.getId(), b.getNombre(), b.getPresentacion(), b.getUnidad().getNombre(),
                    b.getUnidadesPorBulto(), b.isActivo(), r.detalle().getCantidadSugerida(), r.detalle().getCantidadARetirar(),
                    r.comprometido());
        }
    }

    /**
     * {@code responsable} null: la generó el sistema al confirmar el evento. {@code asistentesBase}: los asistentes sobre
     * los que se calculó; {@code eventosBase}: los eventos cerrados (0 con proporciones o sin datos).
     */
    public record Orden(long id, EstadoOrden estado, FuenteSugerencia fuente, int eventosBase, Integer asistentesBase,
            short margenPorcentaje, String origen, Persona responsable, OffsetDateTime fechaCreacion,
            OffsetDateTime fechaCalculo, Hecho modificacion, Hecho confirmacion, Hecho reapertura, int version,
            List<RenglonOrden> renglones) {
        static Orden de(OrdenPreparacion o, List<Renglon> renglones, Map<Long, String> nombres) {
            return new Orden(o.getId(), o.getEstado(), o.getFuente(), o.getEventosBase(), o.getAsistentesBase(),
                    o.getMargenPorcentaje(), o.getOrigen().getNombre(), Persona.de(o.getResponsableId(), nombres),
                    o.getFechaCreacion(), o.getFechaCalculo(),
                    Hecho.de(o.getUsuarioModificacionId(), o.getFechaModificacion(), nombres),
                    Hecho.de(o.getUsuarioConfirmacionId(), o.getFechaConfirmacion(), nombres),
                    Hecho.de(o.getUsuarioReaperturaId(), o.getFechaReapertura(), nombres), o.getVersion(),
                    renglones.stream().map(RenglonOrden::de).toList());
        }
    }

    /** Lo que puede hacer quien consulta, según su perfil, el estado del evento y el de la orden. */
    public record Acciones(boolean generar, boolean recalcular) {
        static Acciones de(Consulta c, UsuarioActual quien) {
            boolean gestiona = quien.tieneAlguno(GESTIONAN)
                    && ConsultaEventoService.CON_ORDEN.contains(c.evento().evento().estado());
            boolean vigente = c.orden() != null && c.orden().isVigente();
            return new Acciones(gestiona && !vigente, gestiona && vigente && EstadoOrden.EDITABLES.contains(c.orden().getEstado()));
        }
    }

    /**
     * La orden del evento: la vigente o, si no hay, la última (cerrada o descartada); null si nunca tuvo.
     * {@code asistentesCambiaron}: desde que se calculó la sugerencia cambiaron los invitados o la asistencia prevista.
     */
    public record OrdenResponse(EventoOrden evento, Orden orden, boolean asistentesCambiaron, Acciones acciones) {
        static OrdenResponse de(Consulta c, Salon salon, Map<Long, String> nombres, UsuarioActual quien) {
            return new OrdenResponse(EventoOrden.de(c.evento(), salon),
                    c.orden() == null ? null : Orden.de(c.orden(), c.renglones(), nombres), c.asistentesCambiaron(),
                    Acciones.de(c, quien));
        }
    }

    /** Un evento Confirmado o En curso y el estado de su orden vigente (null: no tiene). */
    public record OrdenDeLaLista(EventoOrden evento, EstadoOrden estado, Integer bebidas, boolean asistentesCambiaron) {
        static OrdenDeLaLista de(Resumen r, Salon salon) {
            OrdenPreparacion o = r.orden();
            return new OrdenDeLaLista(EventoOrden.de(r.evento(), salon), o == null ? null : o.getEstado(),
                    o == null ? null : o.getRenglones().size(),
                    o != null && !java.util.Objects.equals(o.getAsistentesBase(), r.evento().asistentesPrevistos()));
        }
    }
}
