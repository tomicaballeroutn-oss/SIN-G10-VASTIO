package ar.edu.utn.vastio.agenda.aplicacion;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService.EventoOperativo;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.infraestructura.EventoRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;

/** Puerto de Agenda: conserva el evento estable durante una operación de bebida. */
@Service
public class OperacionEventoService {
    private final EventoRepository eventos;
    private final EntityManager em;
    public OperacionEventoService(EventoRepository eventos, EntityManager em) {
        this.eventos = eventos;
        this.em = em;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public EventoOperativo bloquear(long id) {
        Evento e = eventos.findById(id).orElseThrow(() -> ProblemaException.noEncontrado(
                "EVENTO_INEXISTENTE", "No encontramos ese evento."));
        em.refresh(e, LockModeType.PESSIMISTIC_WRITE);
        return new EventoOperativo(e.getId(), e.getCodigo(), e.getNombre(), e.getUnidad().getSalonId(), e.getEstado());
    }
}
