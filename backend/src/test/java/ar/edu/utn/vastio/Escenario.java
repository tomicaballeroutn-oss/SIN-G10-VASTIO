package ar.edu.utn.vastio;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Arma datos de agenda directo en la base, para probar lecturas sin depender de los casos de uso de alta.
 * Salones y turnos por código (avril, club, santa-barbara; mediodia, noche). Tipo de evento 2 = Quince.
 */
@Component
public class Escenario {

    private static final AtomicInteger NUMERO = new AtomicInteger();

    private final JdbcTemplate jdbc;

    Escenario(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long unidad(String salon, LocalDate fecha, String turno) {
        Long existente = jdbc.query("""
                SELECT u.unidad_id FROM unidad_comercializable u
                JOIN salon s ON s.salon_id = u.salon_id JOIN turno t ON t.turno_id = u.turno_id
                WHERE s.codigo = ? AND u.fecha = ? AND t.codigo = ?""",
                rs -> rs.next() ? rs.getLong(1) : null, salon, fecha, turno);
        if (existente != null) {
            return existente;
        }
        return jdbc.queryForObject("""
                INSERT INTO unidad_comercializable (salon_id, fecha, turno_id)
                SELECT s.salon_id, ?, t.turno_id FROM salon s, turno t WHERE s.codigo = ? AND t.codigo = ?
                RETURNING unidad_id""", Long.class, fecha, salon, turno);
    }

    public long cliente(String nombre) {
        return jdbc.queryForObject("INSERT INTO cliente (nombre) VALUES (?) RETURNING cliente_id", Long.class, nombre);
    }

    /** Evento en ese estado, con los datos que exige el CHECK de cada estado. */
    public long evento(String salon, LocalDate fecha, String turno, String estado, Usuario vendedora, String nombre) {
        return evento(salon, fecha, turno, estado, vendedora, null, nombre);
    }

    /**
     * Evento en ese estado, con los datos que exige el CHECK de cada estado: seña desde Señado, fecha de firma desde
     * Contratado y, desde Confirmado, planner e invitados definitivos (150).
     *
     * @param planner obligatoria desde Confirmado; antes, opcional.
     */
    public long evento(String salon, LocalDate fecha, String turno, String estado, Usuario vendedora, Usuario planner,
            String nombre) {
        long unidad = unidad(salon, fecha, turno);
        long cliente = cliente("Cliente de " + nombre);
        boolean senado = !List.of("PRE_RESERVA", "LIBERADA").contains(estado);
        boolean contratado = List.of("CONTRATADO", "CONFIRMADO", "EN_CURSO", "REALIZADO", "CERRADO").contains(estado);
        boolean confirmado = List.of("CONFIRMADO", "EN_CURSO", "REALIZADO", "CERRADO").contains(estado);
        return jdbc.queryForObject("""
                INSERT INTO evento (codigo, unidad_id, cliente_id, tipo_evento_id, vendedora_id, planner_id, estado, nombre,
                    cantidad_invitados, invitados_definitivos, importe_sena, fecha_sena, firmante_dni, fecha_firma_contrato,
                    motivo_cancelacion_id, version)
                VALUES (?, ?, ?, 2, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                RETURNING evento_id""", Long.class,
                "EV-9999-%05d".formatted(NUMERO.incrementAndGet()), unidad, cliente, vendedora.getId(),
                planner == null ? null : planner.getId(), estado, nombre, confirmado ? 150 : null, confirmado,
                senado ? 100000 : null, senado ? fecha.minusMonths(1) : null, senado ? "30111222" : null,
                contratado ? fecha.minusWeeks(3) : null, estado.equals("CANCELADO") ? 1 : null);
    }

    /** Bloqueo activo de una unidad; motivo 7 = Mantenimiento. */
    public long bloqueo(String salon, LocalDate fecha, String turno, Usuario quien, String detalle) {
        long unidad = unidad(salon, fecha, turno);
        long bloqueo = jdbc.queryForObject("""
                INSERT INTO bloqueo (motivo_id, detalle, usuario_id, activo) VALUES (7, ?, ?, true)
                RETURNING bloqueo_id""", Long.class, detalle, quien.getId());
        jdbc.update("INSERT INTO bloqueo_unidad (bloqueo_id, unidad_id) VALUES (?, ?)", bloqueo, unidad);
        return bloqueo;
    }
}
