package ar.edu.utn.vastio.agenda.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import ar.edu.utn.vastio.agenda.aplicacion.DatosEventoService.DatosCliente;
import ar.edu.utn.vastio.agenda.aplicacion.DatosEventoService.DatosContacto;
import ar.edu.utn.vastio.agenda.aplicacion.DatosEventoService.DatosEvento;

/**
 * Registrar evento: el estado completo de los datos editables. Los contactos que no vienen se quitan.
 * La cantidad de invitados se registra aparte ({@link InvitadosRequest}).
 * {@code version}: la de la ficha que se abrió, para no pisar cambios de otra persona.
 */
public record DatosEventoRequest(
        @NotNull(message = "Falta la versión de la ficha. Volvé a abrirla.") Integer version,
        @NotBlank(message = "Escribí el nombre del evento.") @Size(max = 120, message = "Usá hasta 120 caracteres.") String nombre,
        @NotNull(message = "Elegí el tipo de evento.") Short tipoEventoId,
        @Size(max = 2000, message = "Usá hasta 2000 caracteres.") String observacionesInternas,
        @NotNull(message = "Faltan los datos del cliente.") @Valid ClienteDatos cliente,
        @NotNull List<@Valid ContactoDatos> contactos) {

    public record ClienteDatos(
            @NotBlank(message = "Escribí el nombre del cliente.") @Size(max = 120, message = "Usá hasta 120 caracteres.") String nombre,
            @Pattern(regexp = "\\d{7,8}|\\d{11}|", message = "Escribí el DNI (7 u 8 números) o el CUIT (11), sin puntos ni guiones.") String documento,
            @Size(max = 30, message = "Usá hasta 30 caracteres.") String telefono,
            @Email(message = "Revisá el correo.") @Size(max = 120, message = "Usá hasta 120 caracteres.") String email) {
    }

    public record ContactoDatos(
            Long id,
            @NotBlank(message = "Escribí el nombre del contacto.") @Size(max = 120, message = "Usá hasta 120 caracteres.") String nombre,
            @Size(max = 40, message = "Usá hasta 40 caracteres.") String vinculo,
            @Size(max = 30, message = "Usá hasta 30 caracteres.") String telefono,
            @Email(message = "Revisá el correo.") @Size(max = 120, message = "Usá hasta 120 caracteres.") String email) {
    }

    DatosEvento datos() {
        return new DatosEvento(version, nombre.trim(), tipoEventoId, limpio(observacionesInternas),
                new DatosCliente(cliente.nombre().trim(), limpio(cliente.documento()), limpio(cliente.telefono()),
                        limpio(cliente.email())),
                contactos.stream().map(c -> new DatosContacto(c.id(), c.nombre().trim(), limpio(c.vinculo()),
                        limpio(c.telefono()), limpio(c.email()))).toList());
    }

    /** Vacío es «sin dato»: así borrar un campo queda registrado como cambio a nada. */
    private static String limpio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
