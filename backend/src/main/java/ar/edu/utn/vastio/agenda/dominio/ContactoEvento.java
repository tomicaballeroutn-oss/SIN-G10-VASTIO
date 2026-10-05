package ar.edu.utn.vastio.agenda.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Contacto del evento además del cliente (madre, padre, organizador).
 */
@Entity
@Table(name = "contacto_evento")
public class ContactoEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contacto_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id")
    private Evento evento;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    /** Madre, padre, organizador… */
    @Column(name = "vinculo", length = 40)
    private String vinculo;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "email", length = 120)
    private String email;

    protected ContactoEvento() {
    }

    ContactoEvento(Evento evento, String nombre, String vinculo, String telefono, String email) {
        this.evento = evento;
        actualizar(nombre, vinculo, telefono, email);
    }

    void actualizar(String nombre, String vinculo, String telefono, String email) {
        this.nombre = nombre;
        this.vinculo = vinculo;
        this.telefono = telefono;
        this.email = email;
    }

    /** «María Ríos (madre) · 351 555-0000»: para el registro de modificaciones. */
    public String descripcion() {
        StringBuilder texto = new StringBuilder(nombre);
        if (vinculo != null) {
            texto.append(" (").append(vinculo).append(')');
        }
        for (String dato : new String[] {telefono, email}) {
            if (dato != null) {
                texto.append(" · ").append(dato);
            }
        }
        return texto.toString();
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getVinculo() {
        return vinculo;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getEmail() {
        return email;
    }
}
