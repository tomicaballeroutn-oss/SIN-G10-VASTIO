package ar.edu.utn.vastio.agenda.aplicacion;

import java.time.LocalDate;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.UnidadComercializable;
import ar.edu.utn.vastio.agenda.infraestructura.BloqueoRepository;
import ar.edu.utn.vastio.agenda.infraestructura.EventoRepository;
import ar.edu.utn.vastio.agenda.infraestructura.UnidadComercializableRepository;
import ar.edu.utn.vastio.comun.errores.Mensajes;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;

/**
 * Exclusividad al ocupar una unidad (pre-reservar o reprogramar): crea la unidad si hace falta, toma
 * {@code SELECT … FOR UPDATE} sobre ella y, con el bloqueo tomado, verifica que no haya evento activo ni bloqueo.
 * Si aun así dos transacciones llegaran a escribir, el índice {@code ux_evento_unidad_activa} rechaza la segunda.
 * En ambos casos: 409 «Esa fecha ya está tomada…». Fecha y salón los valida cada caso de uso.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class TomaDeUnidad {

    private final UnidadComercializableRepository unidades;
    private final EventoRepository eventos;
    private final BloqueoRepository bloqueos;
    private final CatalogoService catalogos;

    public TomaDeUnidad(UnidadComercializableRepository unidades, EventoRepository eventos, BloqueoRepository bloqueos,
            CatalogoService catalogos) {
        this.unidades = unidades;
        this.eventos = eventos;
        this.bloqueos = bloqueos;
        this.catalogos = catalogos;
    }

    /**
     * @throws ProblemaException 409 FECHA_TOMADA o UNIDAD_BLOQUEADA.
     */
    public UnidadComercializable tomar(short salonId, LocalDate fecha, short turnoId) {
        unidades.asegurar(salonId, fecha, turnoId);
        UnidadComercializable unidad = unidades.bloquear(salonId, fecha, turnoId).orElseThrow();
        if (eventos.hayActivoEn(unidad.getId(), EstadoEvento.INACTIVOS)) {
            throw ProblemaException.conflicto(Mensajes.CODIGO_FECHA_TOMADA, Mensajes.FECHA_TOMADA);
        }
        var bloqueo = bloqueos.activosEn(unidad.getId()).stream().findFirst();
        if (bloqueo.isPresent()) {
            String motivo = catalogos.motivo(bloqueo.get().getMotivoId()).getNombre().toLowerCase();
            throw ProblemaException.conflicto("UNIDAD_BLOQUEADA",
                    "Esa fecha está bloqueada (" + motivo + "). Elegí otro salón, otra fecha u otro turno.");
        }
        return unidad;
    }
}
