package ar.edu.utn.vastio.bebida.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Proveedor de bebida (datos maestros de la puesta en marcha). Las bebidas que provee son las que lo tienen como
 * proveedor habitual. La baja es lógica.
 */
@Entity
@Table(name = "proveedor")
public class Proveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "proveedor_id")
    private Long id;

    @Column(name = "razon_social", nullable = false, length = 120)
    private String razonSocial;

    /** 11 dígitos sin guiones, con dígito verificador válido. Único. */
    @Column(name = "cuit", unique = true, length = 11)
    private String cuit;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "email", length = 120)
    private String email;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    protected Proveedor() {
    }

    public Proveedor(String razonSocial) {
        this.razonSocial = razonSocial;
    }

    public void actualizar(String razonSocial, String cuit, String telefono, String email) {
        this.razonSocial = razonSocial;
        this.cuit = cuit;
        this.telefono = telefono;
        this.email = email;
    }

    public void darDeBaja() {
        activo = false;
    }

    public void reactivar() {
        activo = true;
    }

    /**
     * Dígito verificador del CUIT (módulo 11, pesos 5-4-3-2-7-6-5-4-3-2). Resto 0 → 0; resto 1 → no hay dígito válido.
     */
    public static boolean cuitValido(String cuit) {
        if (cuit == null || !cuit.matches("\\d{11}")) {
            return false;
        }
        int[] pesos = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
        int suma = 0;
        for (int i = 0; i < pesos.length; i++) {
            suma += (cuit.charAt(i) - '0') * pesos[i];
        }
        int resto = suma % 11;
        if (resto == 1) {
            return false;
        }
        int verificador = resto == 0 ? 0 : 11 - resto;
        return verificador == cuit.charAt(10) - '0';
    }

    public Long getId() {
        return id;
    }

    public String getRazonSocial() {
        return razonSocial;
    }

    public String getCuit() {
        return cuit;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getEmail() {
        return email;
    }

    public boolean isActivo() {
        return activo;
    }
}
