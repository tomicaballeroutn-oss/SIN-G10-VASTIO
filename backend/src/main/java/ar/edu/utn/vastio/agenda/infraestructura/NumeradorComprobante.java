package ar.edu.utn.vastio.agenda.infraestructura;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.EmptyResultDataAccessException;
import ar.edu.utn.vastio.comun.errores.ProblemaException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Número correlativo de comprobante de seña por año (tabla {@code numerador_comprobante}, V12). Funciona como
 * {@link NumeradorEvento}. La asignación requiere una transacción existente.
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
    @Transactional(propagation = Propagation.MANDATORY)
    public int siguiente(int anio) {
        if (anio < 1 || anio > 9999) throw ProblemaException.reglaDeNegocio("ANIO_COMPROBANTE_INVALIDO", "El año no admite el formato del comprobante.");
        try {
            Integer numero = jdbc.queryForObject("""
                INSERT INTO numerador_comprobante (anio, ultimo) VALUES (?, 1)
                ON CONFLICT (anio) DO UPDATE SET ultimo = numerador_comprobante.ultimo + 1
                WHERE numerador_comprobante.ultimo < 99999
                RETURNING ultimo""", Integer.class, anio);
            return numero;
        } catch (EmptyResultDataAccessException e) {
            throw ProblemaException.reglaDeNegocio("NUMERADOR_COMPROBANTE_AGOTADO", "Se agotaron los números de comprobante de ese año.");
        }
    }

    /** CS-AAAA-NNNNN */
    public static String numero(int anio, int numero) {
        if (anio < 1 || anio > 9999 || numero < 1 || numero > 99999) throw new IllegalArgumentException("Número de comprobante fuera de rango");
        return "CS-%04d-%05d".formatted(anio, numero);
    }
}
