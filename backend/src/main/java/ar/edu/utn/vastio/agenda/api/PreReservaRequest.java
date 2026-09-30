package ar.edu.utn.vastio.agenda.api;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import ar.edu.utn.vastio.agenda.aplicacion.ClienteService.DatosCliente;
import ar.edu.utn.vastio.agenda.aplicacion.PreReservaService.PreReserva;

/**
 * Registrar pre-reserva. {@code nombre} vacío: se arma con el tipo y el cliente («Quince de Delfina Ríos»).
 * {@code vendedoraId}: solo lo eligen Coordinación y Dirección; la vendedora pre-reserva a su nombre.
 */
public record PreReservaRequest(
        @NotNull(message = "Elegí el salón.") Short salonId,
        @NotNull(message = "Elegí la fecha.") LocalDate fecha,
        @NotNull(message = "Elegí el turno.") Short turnoId,
        @NotNull(message = "Elegí el tipo de evento.") Short tipoEventoId,
        @Size(max = 120, message = "Usá hasta 120 caracteres.") String nombre,
        Long vendedoraId,
        @NotNull(message = "Elegí o cargá el cliente.") @Valid ClienteRequest cliente) {

    /** Un cliente existente ({@code id}) o uno nuevo ({@code nombre}). */
    public record ClienteRequest(
            Long id,
            @Size(max = 120, message = "Usá hasta 120 caracteres.") String nombre,
            @Pattern(regexp = "\\d{7,8}|\\d{11}|", message = "Escribí el DNI (7 u 8 números) o el CUIT (11), sin puntos ni guiones.") String documento,
            @Size(max = 30, message = "Usá hasta 30 caracteres.") String telefono,
            @Email(message = "Revisá el correo.") @Size(max = 120, message = "Usá hasta 120 caracteres.") String email) {

        @AssertTrue(message = "Elegí un cliente de la lista o escribí el nombre del nuevo.")
        public boolean isIdentificado() {
            return id != null || (nombre != null && !nombre.isBlank());
        }

        DatosCliente datos() {
            return new DatosCliente(id, nombre, documento == null || documento.isBlank() ? null : documento,
                    telefono, email);
        }
    }

    PreReserva pedido() {
        return new PreReserva(salonId, fecha, turnoId, tipoEventoId, nombre, vendedoraId, cliente.datos());
    }
}
