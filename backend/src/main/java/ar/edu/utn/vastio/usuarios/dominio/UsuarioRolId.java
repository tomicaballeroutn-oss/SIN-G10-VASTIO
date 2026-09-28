package ar.edu.utn.vastio.usuarios.dominio;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class UsuarioRolId implements Serializable {

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "rol_id")
    private Short rolId;

    protected UsuarioRolId() {
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof UsuarioRolId otro
                && Objects.equals(usuarioId, otro.usuarioId)
                && Objects.equals(rolId, otro.rolId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(usuarioId, rolId);
    }
}
