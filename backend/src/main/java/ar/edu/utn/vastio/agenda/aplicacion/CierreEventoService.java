package ar.edu.utn.vastio.agenda.aplicacion;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService.EventoOperativo;
import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.infraestructura.EventoRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;

/**
 * Registrar cierre de evento, del lado de la agenda (docs/sprint-3-operacion.md, decisión 12): el evento Realizado pasa
 * a Cerrado por la máquina de estados. Lo que se congela del consumo lo escribe bebida, en la misma transacción.
 * Quién puede cerrar lo valida el caso de uso de bebida.
 */
@Service
@Transactional(readOnly = true)
public class CierreEventoService {

    private final EventoRepository eventos;
    private final MaquinaDeEstados maquina;
    private final CatalogoService catalogos;
    private final ConsultaEventoService consultas;
    private final AsistenciaPorSegmento asistencias;
    private final EntityManager entityManager;

    public CierreEventoService(EventoRepository eventos, MaquinaDeEstados maquina, CatalogoService catalogos,
            ConsultaEventoService consultas, AsistenciaPorSegmento asistencias, EntityManager entityManager) {
        this.asistencias = asistencias;
        this.eventos = eventos;
        this.maquina = maquina;
        this.catalogos = catalogos;
        this.consultas = consultas;
        this.entityManager = entityManager;
    }

    /**
     * Para el consumo por asistente (docs/sprint-3-operacion.md, decisión 14, y docs/sprint-4.md, decisión 8).
     * {@code usaSegmentos}: el tipo de evento cuenta la asistencia por segmento. {@code asistenciaReal}: su total, si ya
     * se cargó la de todos los segmentos; si no, null y se divide por la cantidad de invitados.
     */
    public record DatosDeCierre(Integer cantidadInvitados, boolean usaSegmentos, Integer asistenciaReal) {

        /** El divisor del consumo por asistente: la asistencia real si está; si no, los invitados. */
        public Integer asistentes() {
            return asistenciaReal != null ? asistenciaReal : cantidadInvitados;
        }
    }

    public DatosDeCierre datos(long eventoId) {
        Evento e = evento(eventoId);
        AsistenciaPorSegmento.Asistencia asistencia = asistencias.de(e);
        return new DatosDeCierre(e.getCantidadInvitados(), asistencia != null, asistencia == null ? null : asistencia.totalReal());
    }

    /** Los eventos realizados que esperan su cierre, del más viejo al más nuevo. */
    public List<EventoOperativo> realizados() {
        return eventos.enEstado(EstadoEvento.REALIZADO).stream().map(e -> consultas.operativo(e.getId())).toList();
    }

    /**
     * Realizado → Cerrado. Bloquea el evento: dos cierres simultáneos no pasan los dos.
     *
     * @throws ProblemaException 409 si el evento no está Realizado (o ya se cerró).
     */
    @Transactional
    public void cerrar(long eventoId, long usuarioId) {
        Evento evento = evento(eventoId);
        entityManager.refresh(evento, LockModeType.PESSIMISTIC_WRITE);
        if (evento.getEstado() == EstadoEvento.CERRADO) {
            throw ProblemaException.conflicto("EVENTO_CERRADO", "El evento ya está cerrado: su consumo quedó fijado.");
        }
        if (evento.getEstado() != EstadoEvento.REALIZADO) {
            throw ProblemaException.conflicto("ESTADO_NO_PERMITE", "El evento está en %s: se cierra un evento realizado."
                    .formatted(MaquinaDeEstados.nombre(evento.getEstado())));
        }
        maquina.transicionar(evento, EstadoEvento.CERRADO, usuarioId, null);
    }

    private Evento evento(long id) {
        return eventos.findById(id).orElseThrow(() -> ProblemaException.noEncontrado("EVENTO_INEXISTENTE",
                "No encontramos ese evento."));
    }
}
