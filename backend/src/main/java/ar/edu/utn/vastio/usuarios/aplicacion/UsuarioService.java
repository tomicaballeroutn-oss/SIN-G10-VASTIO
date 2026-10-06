package ar.edu.utn.vastio.usuarios.aplicacion;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;
import ar.edu.utn.vastio.usuarios.infraestructura.RolRepository;
import ar.edu.utn.vastio.usuarios.infraestructura.UsuarioRepository;

/**
 * Alta mínima de usuarios (adelantada de «Administrar usuarios», UI-05): listar, dar de alta y dar de baja.
 * También es la puerta de los otros módulos a los usuarios (p. ej. elegir la vendedora de un evento).
 */
@Service
@Transactional(readOnly = true)
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarios, RolRepository roles, PasswordEncoder passwordEncoder) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    /** Activos primero y después por nombre. */
    public List<Usuario> todos() {
        return usuarios.findAll().stream()
                .sorted(Comparator.comparing(Usuario::isActivo).reversed()
                        .thenComparing(Usuario::getNombreCompleto, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Usuarios activos con ese perfil, por nombre. */
    public List<Usuario> activosConPerfil(RolCodigo rol) {
        return usuarios.findAll().stream()
                .filter(u -> u.isActivo() && u.codigosDeRol().contains(rol))
                .sorted(Comparator.comparing(Usuario::getNombreCompleto, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** De esos ids, los que siguen activos (p. ej. para no avisarle a alguien dado de baja). */
    public Set<Long> activos(Collection<Long> ids) {
        return usuarios.findAllById(ids).stream().filter(Usuario::isActivo).map(Usuario::getId)
                .collect(Collectors.toSet());
    }

    public Optional<Usuario> buscar(long id) {
        return usuarios.findById(id);
    }

    /** Nombre y apellido de cada id, para mostrar quién hizo o tiene algo sin cargar el usuario entero. */
    public Map<Long, String> nombres(Collection<Long> ids) {
        return usuarios.findAllById(ids).stream().collect(Collectors.toMap(Usuario::getId, Usuario::getNombreCompleto));
    }

    /**
     * La contraseña inicial la elige quien da el alta y se la pasa a la persona; queda
     * {@code debe_cambiar_contrasena = true} para que la cambie al ingresar (UI-03).
     */
    @Transactional
    public Usuario crear(String nombreCompleto, String nombreUsuario, Set<RolCodigo> perfiles, String email,
            String telefono, String contrasenaInicial) {
        if (usuarios.existsByNombreUsuario(nombreUsuario)) {
            throw ProblemaException.conflicto("USUARIO_REPETIDO", "Ya hay un usuario «" + nombreUsuario + "». Elegí otro.");
        }
        Usuario usuario = new Usuario(nombreCompleto, nombreUsuario, passwordEncoder.encode(contrasenaInicial));
        usuario.setContacto(email, telefono);
        perfiles.stream().sorted().forEach(codigo -> usuario.asignarRol(roles.findByCodigo(codigo).orElseThrow()));
        return usuarios.save(usuario);
    }

    /** Baja lógica: se conserva todo lo que registró. Nadie se da de baja a sí mismo. */
    @Transactional
    public Usuario darDeBaja(long id, long quienDaLaBaja) {
        if (id == quienDaLaBaja) {
            throw ProblemaException.reglaDeNegocio("BAJA_PROPIA",
                    "No podés darte de baja a vos mismo. Pedíselo a otra persona de Dirección o Coordinación.");
        }
        Usuario usuario = usuarios.findById(id).orElseThrow(() -> ProblemaException.noEncontrado("USUARIO_INEXISTENTE",
                "No encontramos ese usuario."));
        usuario.darDeBaja();
        return usuario;
    }
}
