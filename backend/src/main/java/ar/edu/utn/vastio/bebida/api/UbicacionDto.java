package ar.edu.utn.vastio.bebida.api;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import ar.edu.utn.vastio.bebida.aplicacion.UbicacionService.DatosUbicacion;
import ar.edu.utn.vastio.bebida.dominio.TipoUbicacion;
import ar.edu.utn.vastio.bebida.dominio.Ubicacion;
import ar.edu.utn.vastio.configuracion.dominio.Salon;

public final class UbicacionDto {

    private UbicacionDto() {
    }

    /** El código da el color del salón (tokens del sistema de diseño). */
    public record SalonResumen(short id, String codigo, String nombre) {
        static SalonResumen de(Salon s) {
            return new SalonResumen(s.getId(), s.getCodigo(), s.getNombre());
        }
    }

    public record UbicacionResumen(short id, String nombre, TipoUbicacion tipo) {
        static UbicacionResumen de(Ubicacion u) {
            return new UbicacionResumen(u.getId(), u.getNombre(), u.getTipo());
        }
    }

    /** {@code abastecimiento} y {@code permiteRetiroDirecto} solo en las barras; {@code salon}, en barras y transiciones. */
    public record UbicacionResponse(short id, String nombre, TipoUbicacion tipo, SalonResumen salon,
            UbicacionResumen abastecimiento, boolean permiteRetiroDirecto, boolean activo) {
        static UbicacionResponse de(Ubicacion u, Map<Short, Salon> salones) {
            Salon salon = u.getSalonId() == null ? null : salones.get(u.getSalonId());
            return new UbicacionResponse(u.getId(), u.getNombre(), u.getTipo(), salon == null ? null : SalonResumen.de(salon),
                    u.getAbastecimiento() == null ? null : UbicacionResumen.de(u.getAbastecimiento()),
                    u.isPermiteRetiroDirecto(), u.isActivo());
        }
    }

    /**
     * Alta y modificación. El tipo es obligatorio en el alta y no cambia después. Sin {@code abastecimientoId}, la barra
     * se abastece del depósito madre.
     */
    public record UbicacionRequest(
            @NotBlank(message = "Escribí el nombre.") @Size(max = 60, message = "Usá hasta 60 caracteres.") String nombre,
            TipoUbicacion tipo,
            Short salonId,
            Short abastecimientoId,
            boolean permiteRetiroDirecto) {

        DatosUbicacion datos() {
            return new DatosUbicacion(nombre.trim(), salonId, abastecimientoId, permiteRetiroDirecto);
        }
    }
}
