package ar.edu.utn.vastio.agenda.aplicacion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.dominio.ModificacionEvento;
import ar.edu.utn.vastio.agenda.dominio.Reprogramacion;
import ar.edu.utn.vastio.agenda.dominio.UnidadComercializable;
import ar.edu.utn.vastio.agenda.infraestructura.ModificacionEventoRepository;
import ar.edu.utn.vastio.agenda.infraestructura.ReprogramacionRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.AmbitoMotivo;
import ar.edu.utn.vastio.configuracion.dominio.Motivo;
import ar.edu.utn.vastio.configuracion.dominio.Salon;

/**
 * Reprogramar (UI-18, parte de «Modificar evento»): cambia salón, fecha o turno sin cambiar el estado. Lo hacen la
 * vendedora titular, Coordinación y Dirección, entre Pre-reserva y Confirmado. Con motivo obligatorio del catálogo y
 * una fila en {@code reprogramacion} que conserva la unidad original; en Pre-reserva es una modificación más (sin
 * motivo, en {@code modificacion_evento}). La exclusividad de la unidad nueva la garantiza {@link TomaDeUnidad}.
 */
@Service
public class ReprogramacionService {

    private static final ZoneId ZONA = ZoneId.of(VastioApplication.ZONA_HORARIA);

    private final ConsultaEventoService consultas;
    private final TomaDeUnidad tomaDeUnidad;
    private final ReprogramacionRepository reprogramaciones;
    private final ModificacionEventoRepository modificaciones;
    private final CatalogoService catalogos;
    private final DescripcionEvento descripcion;
    private final AvisosEvento avisos;
    private final EntityManager entityManager;

    public ReprogramacionService(ConsultaEventoService consultas, TomaDeUnidad tomaDeUnidad,
            ReprogramacionRepository reprogramaciones, ModificacionEventoRepository modificaciones, CatalogoService catalogos,
            DescripcionEvento descripcion, AvisosEvento avisos, EntityManager entityManager) {
        this.consultas = consultas;
        this.tomaDeUnidad = tomaDeUnidad;
        this.reprogramaciones = reprogramaciones;
        this.modificaciones = modificaciones;
        this.catalogos = catalogos;
        this.descripcion = descripcion;
        this.avisos = avisos;
        this.entityManager = entityManager;
    }

    /** La unidad nueva, el motivo (obligatorio salvo en Pre-reserva) y un detalle opcional. */
    public record Pedido(short salonId, LocalDate fecha, short turnoId, Short motivoId, String detalle) {
    }

    @Transactional
    public Evento reprogramar(long id, Pedido pedido, UsuarioActual quien) {
        Evento evento = consultas.visible(id, quien);
        if (!AccesoEvento.puedeOperarComoTitular(quien, evento)) {
            throw ConsultaEventoService.sinPermiso();
        }
        if (!ConsultaEventoService.EDITABLES.contains(evento.getEstado())) {
            throw ConsultaEventoService.noPermiteEnEsteEstado(evento, "reprogramar");
        }
        boolean preReserva = evento.getEstado() == EstadoEvento.PRE_RESERVA;
        Motivo motivo = preReserva ? null : motivo(pedido.motivoId());
        UnidadComercializable anterior = evento.getUnidad();
        if (anterior.getSalonId() == pedido.salonId() && anterior.getFecha().equals(pedido.fecha())
                && anterior.getTurnoId() == pedido.turnoId()) {
            throw ProblemaException.reglaDeNegocio("MISMA_UNIDAD", "Elegí un salón, una fecha o un turno distintos de los actuales.");
        }
        if (pedido.fecha().isBefore(LocalDate.now(ZONA))) {
            throw ProblemaException.reglaDeNegocio("FECHA_PASADA", "No se puede reprogramar a una fecha pasada. Elegí otra fecha.");
        }
        Salon salon = catalogos.salon(pedido.salonId());
        if (!salon.isActivo()) {
            throw ProblemaException.reglaDeNegocio("SALON_DADO_DE_BAJA",
                    "El salón " + salon.getNombre() + " está dado de baja: no acepta eventos nuevos.");
        }
        catalogos.turno(pedido.turnoId());

        // Primero el evento y después la unidad nueva, en ese orden siempre: dos reprogramaciones no se traban.
        entityManager.lock(evento, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        UnidadComercializable nueva = tomaDeUnidad.tomar(pedido.salonId(), pedido.fecha(), pedido.turnoId());
        String antes = descripcion.unidad(anterior);
        OffsetDateTime ahora = OffsetDateTime.now();
        evento.reprogramar(nueva);
        if (preReserva) {
            modificaciones.save(new ModificacionEvento(id, "unidad", antes, descripcion.unidad(nueva), quien.id(), ahora));
            avisos.modificacion(evento, List.of("unidad"), quien.id());
        } else {
            reprogramaciones.save(new Reprogramacion(id, anterior, nueva, motivo.getId(), pedido.detalle(), quien.id(), ahora));
            avisos.reprogramacion(evento, antes, quien.id());
        }
        // Si otra transacción ocupó la unidad nueva sin pasar por el bloqueo, el índice de exclusividad salta acá (409).
        entityManager.flush();
        return evento;
    }

    private Motivo motivo(Short motivoId) {
        if (motivoId == null) {
            throw ProblemaException.reglaDeNegocio("FALTA_MOTIVO", "Elegí el motivo de la reprogramación.");
        }
        Motivo motivo = catalogos.motivo(motivoId);
        if (motivo.getAmbito() != AmbitoMotivo.REPROGRAMACION || !motivo.isActivo()) {
            throw ProblemaException.reglaDeNegocio("MOTIVO_INVALIDO", "Elegí un motivo de reprogramación de la lista.");
        }
        return motivo;
    }
}
