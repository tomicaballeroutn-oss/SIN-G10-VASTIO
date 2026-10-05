package ar.edu.utn.vastio.agenda.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Registrar cantidad de invitados (UI-14). {@code definitivos}: la cantidad quedó cerrada (condición de Confirmar).
 */
public record InvitadosRequest(
        @NotNull(message = "Falta la versión de la ficha. Volvé a abrirla.") Integer version,
        @NotNull(message = "Indicá la cantidad de invitados.") @Min(value = 1, message = "La cantidad de invitados tiene que ser mayor a 0.") @Max(value = 5000, message = "Revisá la cantidad de invitados: el complejo recibe hasta 4.500 personas.") Integer cantidad,
        boolean definitivos) {
}
