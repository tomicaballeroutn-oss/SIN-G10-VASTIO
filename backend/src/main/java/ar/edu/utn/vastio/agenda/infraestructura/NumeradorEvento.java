package ar.edu.utn.vastio.agenda.infraestructura;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Número correlativo de evento por año (tabla {@code numerador_evento}, V3).
 */
@Repository
public class NumeradorEvento {

    private final JdbcTemplate jdbc;

    public NumeradorEvento(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Toma el siguiente número del año. La fila del año queda bloqueada hasta el final de la transacción:
     * dos pre-reservas simultáneas nunca comparten número y, si una se deshace, su número no se pierde.
     */
    public int siguiente(int anio) {
        Integer numero = jdbc.queryForObject("""
                INSERT INTO numerador_evento (anio, ultimo) VALUES (?, 1)
                ON CONFLICT (anio) DO UPDATE SET ultimo = numerador_evento.ultimo + 1
                RETURNING ultimo""", Integer.class, anio);
        return numero;
    }

    /** EV-AAAA-NNNNN */
    public static String codigo(int anio, int numero) {
        return "EV-%04d-%05d".formatted(anio, numero);
    }
}
