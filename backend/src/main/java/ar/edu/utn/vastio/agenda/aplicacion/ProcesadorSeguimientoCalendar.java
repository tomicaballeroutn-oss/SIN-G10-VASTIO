package ar.edu.utn.vastio.agenda.aplicacion;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.infraestructura.PublicacionCalendarRepository;
import ar.edu.utn.vastio.agenda.infraestructura.SeguimientoCalendarRepository;
import ar.edu.utn.vastio.notificaciones.aplicacion.NotificacionService;

/** Proyección local recuperable. Ningún error de proyección se propaga al commit comercial. */
@Service
public class ProcesadorSeguimientoCalendar {
    private static final Logger log=LoggerFactory.getLogger(ProcesadorSeguimientoCalendar.class);
    private final SeguimientoCalendar seguimiento;
    private final SeguimientoCalendarRepository cola;
    private final PublicacionCalendarRepository publicaciones;
    private final ClienteCalendar cliente;
    private final NotificacionService avisos;
    private final EntityManager em;
    private final TransactionTemplate nueva;

    public ProcesadorSeguimientoCalendar(SeguimientoCalendar seguimiento, SeguimientoCalendarRepository cola,
            PublicacionCalendarRepository publicaciones, ClienteCalendar cliente, NotificacionService avisos,
            EntityManager em, TransactionTemplate tx) {
        this.seguimiento=seguimiento;this.cola=cola;this.publicaciones=publicaciones;this.cliente=cliente;
        this.avisos=avisos;this.em=em;
        nueva=new TransactionTemplate(tx.getTransactionManager());
        nueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        nueva.setTimeout(10);
    }

    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void cambio(CambioCalendar cambio) { if(cambio.eventoId()!=null) procesar(cambio.eventoId()); }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void catalogo(ar.edu.utn.vastio.comun.CatalogoCambiado cambio) { procesarPendientes(); }

    @EventListener(ApplicationReadyEvent.class)
    public void iniciar() {
        try {
            nueva.executeWithoutResult(t->{
                if(cliente.configurado()) cola.encolarExistentes();
                else { avisos.silenciarCalendar(); publicaciones.jdbc().update("UPDATE publicacion_calendar SET aviso_clave=NULL WHERE aviso_clave IS NOT NULL"); }
            });
            procesarPendientes();
        } catch(RuntimeException e) { log.warn("Calendar: no se pudo iniciar el seguimiento local; se reintentará desde la cola durable."); }
    }
    public void procesarPendientes() {
        try {
            if(!cliente.configurado()) {
                nueva.executeWithoutResult(t->{
                    avisos.silenciarCalendar();
                    publicaciones.jdbc().update("UPDATE publicacion_calendar SET aviso_clave=NULL WHERE aviso_clave IS NOT NULL");
                });
                return;
            }
            cola.pendientes().forEach(this::procesar);
        }
        catch(RuntimeException e) { log.warn("Calendar: cola local no disponible; no se realizaron llamadas externas."); }
    }
    public boolean procesar(long id) {
        try {
            if(!cliente.configurado()) return false;
            nueva.executeWithoutResult(t->{
                // Mismo orden que el trigger: primero evento, luego cola y publicación.
                if(em.find(Evento.class,id,LockModeType.PESSIMISTIC_WRITE)==null) return;
                if(cola.consultar(id)==null) return;
                long revision=cola.bloquear(id);
                var p=seguimiento.revisar(id);
                if(p!=null && p.estado().equals("EN_PROCESO") && Boolean.TRUE.equals(publicaciones.jdbc().queryForObject(
                        "SELECT pg_try_advisory_xact_lock(6006, ?)",Boolean.class,Long.hashCode(id)))) {
                    publicaciones.jdbc().update("UPDATE publicacion_calendar SET estado='INCIERTO',error='OPERACION_ABANDONADA' WHERE evento_id=?",id);
                    publicaciones.auditar(id,"INCIERTO",null,"OPERACION_ABANDONADA");
                    seguimiento.revisar(id);
                }
                cola.confirmar(id,revision);
            });
            return true;
        } catch(RuntimeException e) {
            // La transacción de origen ya terminó; rollback solo de la proyección. No incluir mensajes privados.
            log.warn("Calendar: seguimiento del evento {} falló; permanece en cola para reintento.",id);
            try { nueva.executeWithoutResult(t->cola.fallar(id)); }
            catch(RuntimeException registro) { log.error("Calendar: no se pudo registrar el fallo del evento {}; la revisión sigue sin confirmar.",id); }
            return false;
        }
    }
}
