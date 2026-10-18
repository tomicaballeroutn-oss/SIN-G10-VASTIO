package ar.edu.utn.vastio.agenda.aplicacion;

import static ar.edu.utn.vastio.agenda.dominio.EstadoEvento.CONFIRMADO;
import static ar.edu.utn.vastio.agenda.dominio.EstadoEvento.EN_CURSO;
import static ar.edu.utn.vastio.agenda.dominio.EstadoEvento.REALIZADO;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.dominio.UnidadComercializable;
import ar.edu.utn.vastio.agenda.infraestructura.EventoRepository;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.Turno;

/**
 * Registrar realización de evento (docs/sprint-3-operacion.md, decisión 1): un evento Confirmado pasa solo a En curso
 * cuando llega la hora de inicio de su turno, y a Realizado cuando el turno termina (el día siguiente si cruza la
 * medianoche). Lo dispara el sistema (usuario null, «Sistema» en el historial) y no genera avisos.
 * Es idempotente: si estuvo apagado, se pone al día y un Confirmado cuyo turno ya terminó pasa por los dos estados.
 */
@Service
public class RealizacionService {

    private static final Logger log = LoggerFactory.getLogger(RealizacionService.class);
    private static final ZoneId ZONA = ZoneId.of(VastioApplication.ZONA_HORARIA);

    private final EventoRepository eventos;
    private final MaquinaDeEstados maquina;
    private final CatalogoService catalogos;
    private final TransactionTemplate transaccion;
    private final Clock reloj;

    public RealizacionService(EventoRepository eventos, MaquinaDeEstados maquina, CatalogoService catalogos,
            TransactionTemplate transaccion, Clock reloj) {
        this.eventos = eventos;
        this.maquina = maquina;
        this.catalogos = catalogos;
        this.transaccion = transaccion;
        this.reloj = reloj;
    }

    /**
     * Avanza los eventos que corresponda según la hora actual. Cada evento va en su propia transacción: si uno falla
     * (por ejemplo, alguien lo cancela en ese momento), los demás avanzan igual y ese se reintenta en la próxima pasada.
     *
     * @return cuántos eventos cambiaron de estado.
     */
    public synchronized int ponerAlDia() {
        LocalDateTime ahora = ZonedDateTime.now(reloj).withZoneSameInstant(ZONA).toLocalDateTime();
        Map<Short, Turno> turnos = catalogos.turnos().stream().collect(Collectors.toMap(Turno::getId, Function.identity()));
        // Un turno noche de ayer puede seguir en curso: alcanza con mirar hasta hoy.
        List<Long> pendientes = eventos.idsHasta(ahora.toLocalDate(), EnumSet.of(CONFIRMADO, EN_CURSO));
        int cambiados = 0;
        for (long id : pendientes) {
            try {
                if (Boolean.TRUE.equals(transaccion.execute(estado -> avanzar(id, ahora, turnos)))) {
                    cambiados++;
                }
            } catch (RuntimeException e) {
                log.warn("No se pudo poner al día el evento {}; se reintenta en la próxima pasada.", id, e);
            }
        }
        if (cambiados > 0) {
            log.info("Realización de eventos: {} eventos cambiaron de estado.", cambiados);
        }
        return cambiados;
    }

    private boolean avanzar(long id, LocalDateTime ahora, Map<Short, Turno> turnos) {
        Evento evento = eventos.findById(id).orElseThrow();
        UnidadComercializable unidad = evento.getUnidad();
        Turno turno = turnos.get(unidad.getTurnoId());
        LocalDate fecha = unidad.getFecha();
        LocalDateTime inicio = fecha.atTime(turno.getHoraInicio());
        LocalDateTime fin = (turno.isCruzaMedianoche() ? fecha.plusDays(1) : fecha).atTime(turno.getHoraFin());
        boolean cambio = false;
        if (evento.getEstado() == CONFIRMADO && !ahora.isBefore(inicio)) {
            maquina.transicionar(evento, EN_CURSO, null, null);
            cambio = true;
        }
        if (evento.getEstado() == EN_CURSO && !ahora.isBefore(fin)) {
            maquina.transicionar(evento, REALIZADO, null, null);
            cambio = true;
        }
        return cambio;
    }
}
