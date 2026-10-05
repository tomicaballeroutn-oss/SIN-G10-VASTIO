package ar.edu.utn.vastio.comun.seguridad;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Quién hace el pedido, leído del token de acceso. Los controllers lo reciben con
 * {@code @AuthenticationPrincipal Jwt} y se lo pasan al servicio, que lo usa para las reglas
 * «solo sus eventos» y para registrar quién hizo cada cosa.
 *
 * @param roles códigos de perfil (DIRECCION, VENDEDORA, …), sin el prefijo ROLE_.
 */
public record UsuarioActual(long id, Set<String> roles) {

    public static UsuarioActual de(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(TokenService.CLAIM_ROLES);
        return new UsuarioActual(Long.parseLong(jwt.getSubject()), roles == null ? Set.of() : Set.copyOf(roles));
    }

    public boolean tiene(String rol) {
        return roles.contains(rol);
    }

    public boolean tieneAlguno(Collection<String> otros) {
        return otros.stream().anyMatch(roles::contains);
    }

    /** Dirección y Coordinación: acceso total. */
    public boolean accesoTotal() {
        return tiene("DIRECCION") || tiene("COORDINACION");
    }
}
