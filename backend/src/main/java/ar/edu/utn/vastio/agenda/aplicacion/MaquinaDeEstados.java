package ar.edu.utn.vastio.agenda.aplicacion;

import static ar.edu.utn.vastio.agenda.dominio.EstadoEvento.CANCELADO;
import static ar.edu.utn.vastio.agenda.dominio.EstadoEvento.CERRADO;
import static ar.edu.utn.vastio.agenda.dominio.EstadoEvento.CONFIRMADO;
import static ar.edu.utn.vastio.agenda.dominio.EstadoEvento.CONTRATADO;
import static ar.edu.utn.vastio.agenda.dominio.EstadoEvento.EN_CURSO;
import static ar.edu.utn.vastio.agenda.dominio.EstadoEvento.LIBERADA;
import static ar.edu.utn.vastio.agenda.dominio.EstadoEvento.PRE_RESERVA;
import static ar.edu.utn.vastio.agenda.dominio.EstadoEvento.REALIZADO;
import static ar.edu.utn.vastio.agenda.dominio.EstadoEvento.SENADO;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.agenda.dominio.CambioEstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.infraestructura.CambioEstadoEventoRepository;
import ar.edu.utn.vastio.agenda.infraestructura.EventoRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;

/**
 * Único lugar que cambia {@code evento.estado} (regla 4 de CLAUDE.md): valida la transición contra
 * docs/maquina-de-estados.md, escribe {@code cambio_estado_evento} y genera los avisos ({@link AvisosEvento}).
 * Quién puede disparar cada transición y sus condiciones de datos las valida el caso de uso que la llama.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class MaquinaDeEstados {

    private static final Map<EstadoEvento, Set<EstadoEvento>> TRANSICIONES = new EnumMap<>(Map.of(
            PRE_RESERVA, EnumSet.of(SENADO, LIBERADA),
            SENADO, EnumSet.of(CONTRATADO, CANCELADO),
            CONTRATADO, EnumSet.of(CONFIRMADO, CANCELADO),
            CONFIRMADO, EnumSet.of(EN_CURSO, CANCELADO),
            EN_CURSO, EnumSet.of(REALIZADO),
            REALIZADO, EnumSet.of(CERRADO)));

    private final EventoRepository eventos;
    private final CambioEstadoEventoRepository historial;
    private final AvisosEvento avisos;

    public MaquinaDeEstados(EventoRepository eventos, CambioEstadoEventoRepository historial, AvisosEvento avisos) {
        this.eventos = eventos;
        this.historial = historial;
        this.avisos = avisos;
    }

    /** — → Pre-reserva. Guarda el evento (hace falta su id para el historial). */
    public Evento registrarCreacion(Evento evento, long usuarioId) {
        if (evento.getId() != null) {
            throw new IllegalStateException("El evento ya existe: usá transicionar");
        }
        evento.cambiarEstado(PRE_RESERVA);
        Evento guardado = eventos.save(evento);
        registrar(guardado, null, usuarioId, null);
        return guardado;
    }

    /**
     * @param usuarioId null si la dispara el sistema (En curso y Realizado automáticos).
     * @throws ProblemaException 422 TRANSICION_INVALIDA si el estado actual no la permite.
     */
    public void transicionar(Evento evento, EstadoEvento nuevo, Long usuarioId, String observacion) {
        EstadoEvento anterior = evento.getEstado();
        if (!TRANSICIONES.getOrDefault(anterior, Set.of()).contains(nuevo)) {
            throw ProblemaException.reglaDeNegocio("TRANSICION_INVALIDA",
                    "El evento está en %s: no puede pasar a %s.".formatted(nombre(anterior), nombre(nuevo)));
        }
        evento.cambiarEstado(nuevo);
        registrar(evento, anterior, usuarioId, observacion);
    }

    public static boolean permite(EstadoEvento desde, EstadoEvento hasta) {
        return TRANSICIONES.getOrDefault(desde, Set.of()).contains(hasta);
    }

    private void registrar(Evento evento, EstadoEvento anterior, Long usuarioId, String observacion) {
        historial.save(new CambioEstadoEvento(evento.getId(), anterior, evento.getEstado(), usuarioId,
                OffsetDateTime.now(), observacion));
        avisos.cambioDeEstado(evento, usuarioId);
    }

    /** Nombre en pantalla: «Pre-reserva», «Señado», … */
    public static String nombre(EstadoEvento estado) {
        return switch (estado) {
            case PRE_RESERVA -> "Pre-reserva";
            case SENADO -> "Señado";
            case CONTRATADO -> "Contratado";
            case CONFIRMADO -> "Confirmado";
            case EN_CURSO -> "En curso";
            case REALIZADO -> "Realizado";
            case CERRADO -> "Cerrado";
            case LIBERADA -> "Liberada";
            case CANCELADO -> "Cancelado";
        };
    }
}
