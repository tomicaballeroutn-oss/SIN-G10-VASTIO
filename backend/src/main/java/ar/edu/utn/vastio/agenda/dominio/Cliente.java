package ar.edu.utn.vastio.agenda.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Persona u organización que contrata el evento. El documento no es obligatorio.
 */
@Entity
@Table(name = "cliente")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cliente_id")
    private Long id;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    /** DNI o CUIT, sin guiones. */
    @Column(name = "documento", length = 13)
    private String documento;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "email", length = 120)
    private String email;

    protected Cliente() {
    }

    public Cliente(String nombre, String documento, String telefono, String email) {
        actualizar(nombre, documento, telefono, email);
    }

    public void actualizar(String nombre, String documento, String telefono, String email) {
        this.nombre = nombre;
        this.documento = documento;
        this.telefono = telefono;
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDocumento() {
        return documento;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getEmail() {
        return email;
    }
}
