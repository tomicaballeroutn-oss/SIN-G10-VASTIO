package ar.edu.utn.vastio.usuarios.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.comun.seguridad.Permisos;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.usuarios.api.UsuarioDto.PersonaResponse;
import ar.edu.utn.vastio.usuarios.api.UsuarioDto.UsuarioAltaRequest;
import ar.edu.utn.vastio.usuarios.api.UsuarioDto.UsuarioResponse;
import ar.edu.utn.vastio.usuarios.aplicacion.UsuarioService;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Alta mínima de usuarios (UI-05). Solo Dirección y Coordinación.
 */
@RestController
@RequestMapping("/api/v1/usuarios")
@PreAuthorize(Permisos.ACCESO_TOTAL)
@Tag(name = "Usuarios", description = "Listado, alta y baja lógica")
public class UsuarioController {

    private final UsuarioService usuarios;

    public UsuarioController(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping
    @Operation(summary = "Listar usuarios", description = "Activos primero; los dados de baja con activo = false.")
    public List<UsuarioResponse> listar() {
        return usuarios.todos().stream().map(UsuarioResponse::de).toList();
    }

    @GetMapping("/personas")
    @Operation(summary = "Usuarios activos de un perfil", description = "Para elegir, p. ej., la vendedora interviniente de un evento.")
    public List<PersonaResponse> personas(@RequestParam RolCodigo perfil) {
        return usuarios.activosConPerfil(perfil).stream().map(PersonaResponse::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dar de alta un usuario", description = "Queda con debe_cambiar_contrasena = true.")
    public UsuarioResponse crear(@Valid @RequestBody UsuarioAltaRequest pedido) {
        return UsuarioResponse.de(usuarios.crear(pedido.nombreCompleto().trim(), pedido.nombreUsuario(), pedido.roles(),
                vacioANulo(pedido.email()), vacioANulo(pedido.telefono()), pedido.contrasenaInicial()));
    }

    @PostMapping("/{id}/baja")
    @Operation(summary = "Dar de baja un usuario", description = "Baja lógica: no puede ingresar y se conserva lo que registró.")
    public UsuarioResponse darDeBaja(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return UsuarioResponse.de(usuarios.darDeBaja(id, UsuarioActual.de(jwt).id()));
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
