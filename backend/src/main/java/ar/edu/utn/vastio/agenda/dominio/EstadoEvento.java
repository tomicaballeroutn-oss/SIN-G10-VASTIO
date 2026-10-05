package ar.edu.utn.vastio.agenda.dominio;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estados del evento (docs/maquina-de-estados.md). Mismos valores que el CHECK {@code ck_evento_estado}.
 * Las transiciones las decide {@code MaquinaDeEstados}; acá solo está qué estados ocupan la unidad.
 */
public enum EstadoEvento {
    PRE_RESERVA,
    SENADO,
    CONTRATADO,
    CONFIRMADO,
    EN_CURSO,
    REALIZADO,
    CERRADO,
    LIBERADA,
    CANCELADO;

    /** Liberada y cancelado no ocupan la unidad: es la condición del índice {@code ux_evento_unidad_activa}. */
    public static final Set<EstadoEvento> INACTIVOS = EnumSet.of(LIBERADA, CANCELADO);

    public boolean esActivo() {
        return !INACTIVOS.contains(this);
    }
}
