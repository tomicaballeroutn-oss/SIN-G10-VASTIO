package ar.edu.utn.vastio.agenda.infraestructura;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Número correlativo de comprobante de seña por año (tabla {@code numerador_comprobante}, V12). Funciona como
 * {@link NumeradorEvento}.
 */
@Repository
public class NumeradorComprobante {

    private final JdbcTemplate jdbc;

    public NumeradorComprobante(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Toma el siguiente número del año. La fila del año queda bloqueada hasta el final de la transacción: dos emisiones
     * simultáneas nunca comparten número y, si una se deshace, su número no se pierde.
     */
    public int siguiente(int anio) {
        Integer numero = jdbc.queryForObject("""
                INSERT INTO numerador_comprobante (anio, ultimo) VALUES (?, 1)
                ON CONFLICT (anio) DO UPDATE SET ultimo = numerador_comprobante.ultimo + 1
                RETURNING ultimo""", Integer.class, anio);
        return numero;
    }

    /** CS-AAAA-NNNNN */
    public static String numero(int anio, int numero) {
        return "CS-%04d-%05d".formatted(anio, numero);
    }
}
