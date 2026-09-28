package ar.edu.utn.vastio.usuarios.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Escribí tu usuario.") @Size(max = 50) String nombreUsuario,
        @NotBlank(message = "Escribí tu contraseña.") @Size(max = 200) String contrasena) {
}
