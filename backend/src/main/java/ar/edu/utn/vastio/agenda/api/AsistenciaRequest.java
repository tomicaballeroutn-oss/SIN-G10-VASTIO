package ar.edu.utn.vastio.agenda.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Asistencia por segmento (UI-19 y UI-20): una cantidad por segmento. {@code version}: la de la ficha al abrir el
 * diálogo.
 */
public record AsistenciaRequest(
        @NotNull(message = "Falta la versión de la ficha. Volvé a abrirla.") Integer version,
        @NotEmpty(message = "Indicá la asistencia de al menos un segmento.") List<@Valid @NotNull Renglon> segmentos) {

    /** {@code cantidad} null: sin dato. */
    public record Renglon(
            @NotNull(message = "Falta el segmento.") Short segmentoId,
            @Min(value = 0, message = "La asistencia no puede ser negativa.") @Max(value = 5000, message = "Revisá la cantidad: el complejo recibe hasta 4.500 personas.") Integer cantidad) {
    }

    @AssertTrue(message = "Hay un segmento repetido.")
    public boolean isSinRepetidos() {
        return segmentos == null || segmentos.stream().map(Renglon::segmentoId).distinct().count() == segmentos.size();
    }

    /** Por segmento; admite cantidades null. */
    Map<Short, Integer> cantidades() {
        Map<Short, Integer> cantidades = new HashMap<>();
        segmentos.forEach(r -> cantidades.put(r.segmentoId(), r.cantidad()));
        return cantidades;
    }
}
