package ar.edu.utn.vastio.usuarios.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Perfil de usuario. Los perfiles vienen de V2 y no se crean desde la aplicación.
 */
@Entity
@Table(name = "rol")
public class Rol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rol_id")
    private Short id;

    @Enumerated(EnumType.STRING)
    @Column(name = "codigo", nullable = false, unique = true, length = 20)
    private RolCodigo codigo;

    @Column(name = "nombre", nullable = false, length = 50)
    private String nombre;

    protected Rol() {
    }

    public Short getId() {
        return id;
    }

    public RolCodigo getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }
}
