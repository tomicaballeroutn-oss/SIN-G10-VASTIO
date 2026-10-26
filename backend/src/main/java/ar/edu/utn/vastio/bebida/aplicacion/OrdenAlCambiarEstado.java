package ar.edu.utn.vastio.bebida.aplicacion;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import ar.edu.utn.vastio.agenda.aplicacion.CambioDeEstadoEvento;

/**
 * La orden de preparación sigue al evento (docs/maquina-de-estados.md): se genera al confirmarlo y se descarta al
 * cancelarlo, en la misma transacción que el cambio de estado. El cierre la cierra desde {@link CierreService}.
 */
@Component
class OrdenAlCambiarEstado {

    private final OrdenPreparacionService ordenes;

    OrdenAlCambiarEstado(OrdenPreparacionService ordenes) {
        this.ordenes = ordenes;
    }

    @EventListener
    void alCambiar(CambioDeEstadoEvento cambio) {
        switch (cambio.nuevo()) {
            case CONFIRMADO -> ordenes.generarAlConfirmar(cambio.eventoId());
            case CANCELADO -> ordenes.descartarAlCancelar(cambio.eventoId());
            default -> {
                // El resto de las transiciones no toca la orden.
            }
        }
    }
}
