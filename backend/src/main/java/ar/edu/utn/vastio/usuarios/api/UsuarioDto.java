package ar.edu.utn.vastio.usuarios.api;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

public final class UsuarioDto {

    private UsuarioDto() {
    }

    /** Nunca incluye el hash de la contraseña. Los perfiles salen en el orden de la matriz de permisos. */
    public record UsuarioResponse(long id, String nombreCompleto, String nombreUsuario, List<RolCodigo> roles,
            String email, String telefono, boolean activo, boolean debeCambiarContrasena, OffsetDateTime fechaAlta,
            OffsetDateTime fechaBaja) {
        static UsuarioResponse de(Usuario u) {
            List<RolCodigo> roles = Arrays.stream(RolCodigo.values()).filter(u.codigosDeRol()::contains).toList();
            return new UsuarioResponse(u.getId(), u.getNombreCompleto(), u.getNombreUsuario(), roles, u.getEmail(),
                    u.getTelefono(), u.isActivo(), u.isDebeCambiarContrasena(), u.getFechaAlta(), u.getFechaBaja());
        }
    }

    /** Para elegir una persona (p. ej. la vendedora de un evento): sin datos de contacto. */
    public record PersonaResponse(long id, String nombreCompleto) {
        static PersonaResponse de(Usuario u) {
            return new PersonaResponse(u.getId(), u.getNombreCompleto());
        }
    }

    /** Modificar: nombre, perfiles y contacto. El nombre de usuario no se edita. */
    public record UsuarioModificacionRequest(
            @NotBlank(message = "Escribí el nombre y apellido.") @Size(max = 120, message = "Usá hasta 120 caracteres.") String nombreCompleto,
            @NotEmpty(message = "Elegí al menos un perfil.") Set<RolCodigo> roles,
            @Email(message = "Revisá el correo.") @Size(max = 120, message = "Usá hasta 120 caracteres.") String email,
            @Size(max = 30, message = "Usá hasta 30 caracteres.") String telefono) {
    }

    public record UsuarioAltaRequest(
            @NotBlank(message = "Escribí el nombre y apellido.") @Size(max = 120, message = "Usá hasta 120 caracteres.") String nombreCompleto,
            @NotBlank(message = "Escribí el usuario.")
            @Pattern(regexp = "[a-z0-9._-]{3,50}", message = "Usá de 3 a 50 letras minúsculas sin acentos, números, punto, guion o guion bajo.")
            String nombreUsuario,
            @NotEmpty(message = "Elegí al menos un perfil.") Set<RolCodigo> roles,
            @Email(message = "Revisá el correo.") @Size(max = 120, message = "Usá hasta 120 caracteres.") String email,
            @Size(max = 30, message = "Usá hasta 30 caracteres.") String telefono,
            @NotBlank(message = "Escribí la contraseña inicial.")
            @Size(min = 8, max = 72, message = "La contraseña inicial tiene que tener entre 8 y 72 caracteres.")
            String contrasenaInicial) {
    }
}
