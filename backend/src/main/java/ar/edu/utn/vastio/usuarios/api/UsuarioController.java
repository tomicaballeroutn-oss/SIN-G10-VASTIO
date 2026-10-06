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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.comun.seguridad.Permisos;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.usuarios.api.UsuarioDto.PersonaResponse;
import ar.edu.utn.vastio.usuarios.api.UsuarioDto.UsuarioAltaRequest;
import ar.edu.utn.vastio.usuarios.api.UsuarioDto.UsuarioModificacionRequest;
import ar.edu.utn.vastio.usuarios.api.UsuarioDto.UsuarioResponse;
import ar.edu.utn.vastio.usuarios.aplicacion.UsuarioService;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Administrar usuarios (UI-05): listar, alta, modificación, baja y reactivación. Solo Dirección y Coordinación.
 */
@RestController
@RequestMapping("/api/v1/usuarios")
@PreAuthorize(Permisos.ACCESO_TOTAL)
@Tag(name = "Usuarios", description = "Listado, alta, modificación, baja lógica y reactivación")
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

    @PutMapping("/{id}")
    @Operation(summary = "Modificar un usuario", description = """
            Nombre, perfiles y contacto; el nombre de usuario no cambia. Nadie se quita a sí mismo Dirección o
            Coordinación, y siempre queda un usuario activo de Dirección.""")
    public UsuarioResponse modificar(@PathVariable long id, @Valid @RequestBody UsuarioModificacionRequest pedido,
            @AuthenticationPrincipal Jwt jwt) {
        return UsuarioResponse.de(usuarios.modificar(id, pedido.nombreCompleto().trim(), pedido.roles(),
                vacioANulo(pedido.email()), vacioANulo(pedido.telefono()), UsuarioActual.de(jwt).id()));
    }

    @PostMapping("/{id}/reactivacion")
    @Operation(summary = "Reactivar un usuario", description = "Vuelve a poder ingresar con su contraseña y a aparecer para elegir.")
    public UsuarioResponse reactivar(@PathVariable long id) {
        return UsuarioResponse.de(usuarios.reactivar(id));
    }

    @PostMapping("/{id}/baja")
    @Operation(summary = "Dar de baja un usuario", description = """
            Baja lógica: no puede ingresar y se conserva lo que registró. Nadie se da de baja a sí mismo y siempre queda un
            usuario activo de Dirección. Los eventos que tenía como vendedora o planner se consultan antes en
            /api/v1/eventos/afectados.""")
    public UsuarioResponse darDeBaja(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return UsuarioResponse.de(usuarios.darDeBaja(id, UsuarioActual.de(jwt).id()));
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
