package ar.edu.utn.vastio.agenda.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import ar.edu.utn.vastio.agenda.aplicacion.ServiciosService.Renglon;
import ar.edu.utn.vastio.agenda.aplicacion.ServiciosService.Servicio;

/**
 * Registrar servicios contratados (UI-13): la versión de la ficha al abrir la pestaña y, por categoría, los renglones
 * (texto libre y cantidad opcional) y el detalle libre (M-09). Sin {@code renglones}, la categoría queda sin renglones.
 */
public record ServiciosRequest(
        @NotNull(message = "Falta la versión de la ficha. Volvé a abrirla.") Integer version,
        @NotNull(message = "Indicá los servicios.") List<@Valid ServicioRequest> servicios) {

    public record ServicioRequest(
            @NotNull(message = "Indicá la categoría.") Short categoriaId,
            @Size(max = 2000, message = "Usá hasta 2000 caracteres.") String descripcion,
            @Size(max = 30, message = "Cargá hasta 30 renglones por categoría.") List<@Valid RenglonRequest> renglones) {
    }

    public record RenglonRequest(
            @Size(max = 120, message = "Usá hasta 120 caracteres.") String texto,
            @Positive(message = "La cantidad tiene que ser mayor a 0.")
            @Max(value = 100_000, message = "Revisá la cantidad.") Integer cantidad) {
    }

    List<Servicio> lista() {
        return servicios.stream()
                .map(s -> new Servicio(s.categoriaId(), s.descripcion(), s.renglones() == null ? List.of()
                        : s.renglones().stream().map(r -> new Renglon(r.texto(), r.cantidad())).toList()))
                .toList();
    }
}
