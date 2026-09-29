package ar.edu.utn.vastio.usuarios.aplicacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;
import ar.edu.utn.vastio.usuarios.infraestructura.RolRepository;
import ar.edu.utn.vastio.usuarios.infraestructura.UsuarioRepository;

/**
 * Solo en el perfil dev: crea el primer usuario de Dirección si todavía no existe.
 * La contraseña sale de {@code VASTIO_ADMIN_CONTRASENA} (en el .env de la raíz o en el entorno); sin ella no se crea nada.
 * V2 no versiona hashes a propósito.
 */
@Component
@Profile("dev")
public class UsuarioInicialDev implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UsuarioInicialDev.class);

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final String nombreUsuario;
    private final String contrasena;

    public UsuarioInicialDev(UsuarioRepository usuarios, RolRepository roles, PasswordEncoder passwordEncoder,
            @Value("${vastio.dev.admin-usuario:direccion}") String nombreUsuario,
            @Value("${vastio.dev.admin-contrasena:}") String contrasena) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.nombreUsuario = nombreUsuario;
        this.contrasena = contrasena;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarios.existsByNombreUsuario(nombreUsuario)) {
            return;
        }
        if (contrasena.isBlank()) {
            log.warn("No se creó el usuario inicial '{}': definí VASTIO_ADMIN_CONTRASENA en el .env de la raíz.", nombreUsuario);
            return;
        }
        Usuario direccion = new Usuario("Dirección (usuario inicial)", nombreUsuario, passwordEncoder.encode(contrasena));
        direccion.asignarRol(roles.findByCodigo(RolCodigo.DIRECCION).orElseThrow());
        direccion.setDebeCambiarContrasena(false);
        usuarios.save(direccion);
        log.info("Usuario inicial de Dirección creado: '{}'", nombreUsuario);
    }
}
