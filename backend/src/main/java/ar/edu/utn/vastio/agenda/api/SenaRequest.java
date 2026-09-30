package ar.edu.utn.vastio.agenda.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import ar.edu.utn.vastio.agenda.aplicacion.SenaService.Sena;

/**
 * Registrar seña (UI-11). Importe en pesos, mayor a 0. El DNI del firmante es obligatorio;
 * su nombre y contacto, opcionales. El turno de firma no se guarda.
 */
public record SenaRequest(
        @NotNull(message = "Escribí el importe de la seña.")
        @DecimalMin(value = "0", inclusive = false, message = "El importe tiene que ser mayor a 0.")
        @Digits(integer = 12, fraction = 2, message = "Revisá el importe: hasta 2 decimales.")
        BigDecimal importe,
        @NotNull(message = "Elegí la fecha del pago.") LocalDate fechaPago,
        @Size(max = 120, message = "Usá hasta 120 caracteres.") String firmanteNombre,
        @NotBlank(message = "Escribí el DNI del firmante.")
        @Pattern(regexp = "\\d{7,8}", message = "Escribí el DNI con 7 u 8 números, sin puntos.")
        String firmanteDni,
        @Size(max = 120, message = "Usá hasta 120 caracteres.") String firmanteContacto) {

    Sena sena() {
        return new Sena(importe, fechaPago, limpio(firmanteNombre), firmanteDni, limpio(firmanteContacto));
    }

    private static String limpio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
