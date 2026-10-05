package ar.edu.utn.vastio.agenda.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import ar.edu.utn.vastio.agenda.aplicacion.ServiciosService.Servicio;

/**
 * Registrar servicios contratados (UI-13): la versión de la ficha al abrir la pestaña y un texto por categoría.
 */
public record ServiciosRequest(
        @NotNull(message = "Falta la versión de la ficha. Volvé a abrirla.") Integer version,
        @NotNull(message = "Indicá los servicios.") List<@Valid ServicioRequest> servicios) {

    public record ServicioRequest(
            @NotNull(message = "Indicá la categoría.") Short categoriaId,
            @Size(max = 2000, message = "Usá hasta 2000 caracteres.") String descripcion) {
    }

    List<Servicio> lista() {
        return servicios.stream().map(s -> new Servicio(s.categoriaId(), s.descripcion())).toList();
    }
}
