package ar.edu.utn.vastio.usuarios.aplicacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;
import ar.edu.utn.vastio.usuarios.infraestructura.RolRepository;
import ar.edu.utn.vastio.usuarios.infraestructura.UsuarioRepository;

/**
 * Crea el primer usuario de Dirección si todavía no existe. Corre solo en los perfiles
 * {@code dev} y {@code puesta-en-marcha} (primer arranque del ambiente de prueba, ver docs/despliegue.md).
 * La contraseña sale de {@code VASTIO_ADMIN_CONTRASENA}; sin ella no se crea nada. V2 no versiona hashes a propósito.
 */
@Component
@Profile({"dev", "puesta-en-marcha"})
@Order(1)
public class UsuarioInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UsuarioInicial.class);

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final String nombreUsuario;
    private final String contrasena;

    public UsuarioInicial(UsuarioRepository usuarios, RolRepository roles, PasswordEncoder passwordEncoder,
            @Value("${vastio.usuario-inicial.usuario:direccion}") String nombreUsuario,
            @Value("${vastio.usuario-inicial.contrasena:}") String contrasena) {
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
            log.warn("No se creó el usuario inicial '{}': definí VASTIO_ADMIN_CONTRASENA.", nombreUsuario);
            return;
        }
        Usuario direccion = new Usuario("Dirección (usuario inicial)", nombreUsuario, passwordEncoder.encode(contrasena));
        direccion.asignarRol(roles.findByCodigo(RolCodigo.DIRECCION).orElseThrow());
        direccion.setDebeCambiarContrasena(false);
        usuarios.save(direccion);
        log.info("Usuario inicial de Dirección creado: '{}'", nombreUsuario);
    }
}
