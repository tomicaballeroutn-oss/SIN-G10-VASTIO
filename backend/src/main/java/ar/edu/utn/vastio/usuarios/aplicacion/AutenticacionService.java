package ar.edu.utn.vastio.usuarios.aplicacion;

import java.time.Duration;
import java.util.List;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.comun.errores.SesionVencidaException;
import ar.edu.utn.vastio.comun.seguridad.TokenService;
import ar.edu.utn.vastio.comun.seguridad.TokenService.TokenEmitido;
import ar.edu.utn.vastio.configuracion.aplicacion.ParametroService;
import ar.edu.utn.vastio.configuracion.dominio.ParametroClave;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;
import ar.edu.utn.vastio.usuarios.infraestructura.UsuarioRepository;

/**
 * Inicio de sesión y renovación. La vida del token de refresco es MINUTOS_EXPIRACION_SESION y se renueva
 * en cada refresco: si la persona no usa el sistema durante ese tiempo, tiene que volver a ingresar (UI-02).
 */
@Service
@Transactional(readOnly = true)
public class AutenticacionService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final ParametroService parametros;
    private final String hashDeRelleno;

    public AutenticacionService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, TokenService tokens,
            ParametroService parametros) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.parametros = parametros;
        this.hashDeRelleno = passwordEncoder.encode("relleno-para-igualar-tiempos");
    }

    /**
     * Usuario inexistente, contraseña incorrecta y usuario dado de baja responden igual,
     * para no revelar qué nombres de usuario existen.
     */
    public Sesion iniciarSesion(String nombreUsuario, String contrasena) {
        Usuario usuario = usuarios.findByNombreUsuario(nombreUsuario.trim()).orElse(null);
        if (usuario == null) {
            passwordEncoder.matches(contrasena, hashDeRelleno); // mismo costo que un usuario existente
            throw new BadCredentialsException("Usuario inexistente");
        }
        if (!passwordEncoder.matches(contrasena, usuario.getHashContrasena())) {
            throw new BadCredentialsException("Contraseña incorrecta");
        }
        if (!usuario.isActivo()) {
            throw new BadCredentialsException("Usuario dado de baja");
        }
        return abrirSesion(usuario);
    }

    public Sesion renovar(String tokenRefresco) {
        long usuarioId = tokens.leerRefresco(tokenRefresco);
        Usuario usuario = usuarios.findById(usuarioId)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new SesionVencidaException("Usuario inexistente o dado de baja"));
        return abrirSesion(usuario);
    }

    private Sesion abrirSesion(Usuario usuario) {
        List<String> roles = usuario.codigosDeRol().stream().map(RolCodigo::name).sorted().toList();
        int minutosSesion = parametros.entero(ParametroClave.MINUTOS_EXPIRACION_SESION);
        int minutosAviso = parametros.entero(ParametroClave.MINUTOS_AVISO_EXPIRACION);
        TokenEmitido acceso = tokens.emitirAcceso(usuario.getId(), usuario.getNombreUsuario(), roles);
        TokenEmitido refresco = tokens.emitirRefresco(usuario.getId(), Duration.ofMinutes(minutosSesion));
        return new Sesion(acceso, refresco, minutosSesion, minutosAviso,
                new UsuarioSesion(usuario.getId(), usuario.getNombreCompleto(), usuario.getNombreUsuario(), roles,
                        usuario.isDebeCambiarContrasena()));
    }

    public record Sesion(TokenEmitido acceso, TokenEmitido refresco, int minutosExpiracionSesion,
            int minutosAvisoExpiracion, UsuarioSesion usuario) {
    }

    public record UsuarioSesion(long id, String nombreCompleto, String nombreUsuario, List<String> roles,
            boolean debeCambiarContrasena) {
    }
}
