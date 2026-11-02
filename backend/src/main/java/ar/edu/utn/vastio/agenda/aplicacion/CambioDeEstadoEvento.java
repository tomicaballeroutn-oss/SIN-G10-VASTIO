package ar.edu.utn.vastio.agenda.aplicacion;

import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;

/**
 * Lo publica {@link MaquinaDeEstados} después de cada transición, dentro de la misma transacción. Así otros módulos
 * reaccionan sin que la agenda dependa de ellos: bebida genera la orden de preparación al confirmar y la descarta al
 * cancelar (docs/sprint-4.md, decisiones 11 y 19).
 *
 * @param anterior  null al crear la pre-reserva.
 * @param usuarioId null si la transición es automática.
 */
public record CambioDeEstadoEvento(long eventoId, EstadoEvento anterior, EstadoEvento nuevo, Long usuarioId) {
}
