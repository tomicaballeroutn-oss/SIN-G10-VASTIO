package ar.edu.utn.vastio.usuarios.dominio;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * Persona que opera el sistema. La baja es lógica: se conserva el historial de lo que registró.
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "usuario_id")
    private Long id;

    @Column(name = "nombre_completo", nullable = false, length = 120)
    private String nombreCompleto;

    @Column(name = "nombre_usuario", nullable = false, unique = true, length = 50)
    private String nombreUsuario;

    /** Hash BCrypt. Nunca la contraseña en claro. */
    @Column(name = "hash_contrasena", nullable = false, length = 100)
    private String hashContrasena;

    @Column(name = "email", length = 120)
    private String email;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "debe_cambiar_contrasena", nullable = false)
    private boolean debeCambiarContrasena = true;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_alta", nullable = false)
    private OffsetDateTime fechaAlta;

    @Column(name = "fecha_baja")
    private OffsetDateTime fechaBaja;

    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<UsuarioRol> roles = new LinkedHashSet<>();

    protected Usuario() {
    }

    public Usuario(String nombreCompleto, String nombreUsuario, String hashContrasena) {
        this.nombreCompleto = nombreCompleto;
        this.nombreUsuario = nombreUsuario;
        this.hashContrasena = hashContrasena;
        this.fechaAlta = OffsetDateTime.now();
    }

    public void asignarRol(Rol rol) {
        if (!codigosDeRol().contains(rol.getCodigo())) {
            roles.add(new UsuarioRol(this, rol));
        }
    }

    /** Deja exactamente esos perfiles: quita los que no están y agrega los nuevos. */
    public void fijarRoles(Collection<Rol> nuevos) {
        Set<RolCodigo> codigos = nuevos.stream().map(Rol::getCodigo).collect(Collectors.toSet());
        roles.removeIf(ur -> !codigos.contains(ur.getRol().getCodigo()));
        nuevos.forEach(this::asignarRol);
    }

    public void cambiarNombre(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto;
    }

    /** Vuelve a poder ingresar y a aparecer para elegir. */
    public void reactivar() {
        activo = true;
        fechaBaja = null;
    }

    public void darDeBaja() {
        if (activo) {
            activo = false;
            fechaBaja = OffsetDateTime.now();
        }
    }

    public Set<RolCodigo> codigosDeRol() {
        return roles.stream().map(ur -> ur.getRol().getCodigo()).collect(Collectors.toUnmodifiableSet());
    }

    public void setContacto(String email, String telefono) {
        this.email = email;
        this.telefono = telefono;
    }

    public void setDebeCambiarContrasena(boolean debeCambiarContrasena) {
        this.debeCambiarContrasena = debeCambiarContrasena;
    }

    public Long getId() {
        return id;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public String getHashContrasena() {
        return hashContrasena;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefono() {
        return telefono;
    }

    public boolean isDebeCambiarContrasena() {
        return debeCambiarContrasena;
    }

    public boolean isActivo() {
        return activo;
    }

    public OffsetDateTime getFechaAlta() {
        return fechaAlta;
    }

    public OffsetDateTime getFechaBaja() {
        return fechaBaja;
    }

    public Set<UsuarioRol> getRoles() {
        return Set.copyOf(roles);
    }
}
