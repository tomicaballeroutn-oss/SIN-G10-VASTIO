package ar.edu.utn.vastio;

import java.util.Arrays;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import ar.edu.utn.vastio.comun.seguridad.TokenService;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;
import ar.edu.utn.vastio.usuarios.infraestructura.RolRepository;
import ar.edu.utn.vastio.usuarios.infraestructura.UsuarioRepository;

/**
 * Usuarios de prueba y sus tokens de acceso, sin pasar por el login.
 * Si el test es {@code @Transactional}, los usuarios se descartan con el resto de los datos.
 */
@Component
public class Personas {

    public static final String CONTRASENA = "una-clave-larga";

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    Personas(UsuarioRepository usuarios, RolRepository roles, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    /** Busca el usuario o lo crea con esos perfiles. */
    public Usuario usuario(String nombreUsuario, String nombreCompleto, RolCodigo... codigos) {
        return usuarios.findByNombreUsuario(nombreUsuario).orElseGet(() -> {
            Usuario usuario = new Usuario(nombreCompleto, nombreUsuario, passwordEncoder.encode(CONTRASENA));
            // Las pruebas comerciales parten de cuentas con el primer ingreso completado.
            // Los casos de contraseña inicial activan la marca explícitamente.
            usuario.setDebeCambiarContrasena(false);
            for (RolCodigo codigo : codigos) {
                usuario.asignarRol(roles.findByCodigo(codigo).orElseThrow());
            }
            return usuarios.save(usuario);
        });
    }

    /** Un usuario genérico del perfil («prueba.vendedora»). */
    public Usuario de(RolCodigo rol) {
        String nombre = rol.name().toLowerCase(Locale.ROOT);
        return usuario("prueba." + nombre, "Prueba " + nombre, rol);
    }

    /** Encabezado {@code Authorization} para ese usuario. */
    public String bearer(Usuario usuario) {
        var codigos = Arrays.stream(RolCodigo.values()).filter(usuario.codigosDeRol()::contains).map(RolCodigo::name).toList();
        return "Bearer " + tokens.emitirAcceso(usuario.getId(), usuario.getNombreUsuario(), codigos, usuario.isDebeCambiarContrasena()).valor();
    }

    /**
     * Adaptador optativo para @WithMockUser: emite un JWT real con identidad y marca del fixture,
     * conservando las autoridades simuladas. Solo existe en src/test; el filtro no admite UserDetails.
     * La solicitud sigue pasando por firma, decoder y autorización de producción.
     */
    public RequestPostProcessor jwtDeUsuarioSimulado() {
        var autenticacion = TestSecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null) throw new IllegalStateException("Falta @WithMockUser en la prueba");
        var usuario = usuarios.findByNombreUsuario(autenticacion.getName())
                .orElseThrow(() -> new IllegalStateException("El usuario simulado necesita un fixture persistido"));
        var codigos = autenticacion.getAuthorities().stream()
                .map(a -> a.getAuthority()).filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length())).toList();
        String token = tokens.emitirAcceso(usuario.getId(), usuario.getNombreUsuario(), codigos,
                usuario.isDebeCambiarContrasena()).valor();
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return request;
        };
    }

    public String bearer(RolCodigo rol) {
        return bearer(de(rol));
    }
}
