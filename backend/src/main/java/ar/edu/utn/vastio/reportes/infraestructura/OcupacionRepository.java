package ar.edu.utn.vastio.reportes.infraestructura;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;

/** Consulta de solo lectura de eventos que ocupan una unidad para el reporte comercial. */
@Repository
public class OcupacionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public OcupacionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<EventoOcupacion> consultar(LocalDate desde, LocalDate hasta, Short salonId, Short tipoEventoId,
            Long vendedoraId, String regimen) {
        StringBuilder sql = new StringBuilder("""
                select e.evento_id, u.salon_id, e.tipo_evento_id, e.vendedora_id, e.estado
                from evento e
                join unidad_comercializable u on u.unidad_id = e.unidad_id
                where u.fecha between :desde and :hasta
                  and e.estado in ('SENADO', 'CONTRATADO', 'CONFIRMADO', 'EN_CURSO', 'REALIZADO', 'CERRADO')
                """);
        MapSqlParameterSource parametros = new MapSqlParameterSource().addValue("desde", desde).addValue("hasta", hasta);
        if (salonId != null) {
            sql.append(" and u.salon_id = :salonId");
            parametros.addValue("salonId", salonId);
        }
        if (tipoEventoId != null) {
            sql.append(" and e.tipo_evento_id = :tipoEventoId");
            parametros.addValue("tipoEventoId", tipoEventoId);
        }
        if (vendedoraId != null) {
            sql.append(" and e.vendedora_id = :vendedoraId");
            parametros.addValue("vendedoraId", vendedoraId);
        }
        if ("ALTA".equals(regimen)) {
            sql.append(" and extract(month from u.fecha) between 10 and 12");
        } else if ("BAJA".equals(regimen)) {
            sql.append(" and extract(month from u.fecha) between 1 and 9");
        }
        sql.append(" order by u.salon_id, e.tipo_evento_id, e.vendedora_id, e.evento_id");
        return jdbc.query(sql.toString(), parametros, (rs, fila) -> new EventoOcupacion(
                rs.getLong("evento_id"), rs.getShort("salon_id"), rs.getShort("tipo_evento_id"),
                rs.getLong("vendedora_id"), EstadoEvento.valueOf(rs.getString("estado"))));
    }

    /** Unidades bloqueadas en su fecha, sin restar también las ocupadas por un evento del reporte.
     * Como en la agenda, el evento tiene prioridad; los filtros comerciales no cambian esta disponibilidad. */
    public Map<Short, Long> bloqueadasPorSalon(LocalDate desde, LocalDate hasta, Short salonId, String regimen) {
        StringBuilder sql = new StringBuilder("""
                select u.salon_id, count(distinct u.unidad_id) as unidades
                from bloqueo b
                join bloqueo_unidad bu on bu.bloqueo_id = b.bloqueo_id
                join unidad_comercializable u on u.unidad_id = bu.unidad_id
                where u.fecha between :desde and :hasta
                  and (b.fecha_registro at time zone 'America/Argentina/Cordoba')::date <= u.fecha
                  and (b.fecha_desbloqueo is null
                    or (b.fecha_desbloqueo at time zone 'America/Argentina/Cordoba')::date >= u.fecha)
                  and not exists (
                    select 1 from evento e where e.unidad_id = u.unidad_id
                      and e.estado in ('SENADO', 'CONTRATADO', 'CONFIRMADO', 'EN_CURSO', 'REALIZADO', 'CERRADO')
                  )
                """);
        MapSqlParameterSource parametros = new MapSqlParameterSource().addValue("desde", desde).addValue("hasta", hasta);
        if (salonId != null) {
            sql.append(" and u.salon_id = :salonId");
            parametros.addValue("salonId", salonId);
        }
        if ("ALTA".equals(regimen)) {
            sql.append(" and extract(month from u.fecha) between 10 and 12");
        } else if ("BAJA".equals(regimen)) {
            sql.append(" and extract(month from u.fecha) between 1 and 9");
        }
        sql.append(" group by u.salon_id");
        Map<Short, Long> resultado = new LinkedHashMap<>();
        jdbc.query(sql.toString(), parametros, rs -> {
            resultado.put(rs.getShort("salon_id"), rs.getLong("unidades"));
        });
        return resultado;
    }

    public record EventoOcupacion(long eventoId, short salonId, short tipoEventoId, long vendedoraId, EstadoEvento estado) { }
}
