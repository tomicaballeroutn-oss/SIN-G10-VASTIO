package ar.edu.utn.vastio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifica que V1 y V2 dejan la base como indica el diccionario de datos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfig.class)
class MigracionesIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void creaLas38Tablas() {
        Integer tablas = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """, Integer.class);

        assertThat(tablas).isEqualTo(38);
    }

    @ParameterizedTest(name = "{0} tiene {1} filas")
    @CsvSource({
            "rol, 8",
            "salon, 3",
            "turno, 2",
            "tipo_evento, 5",
            "tipo_segmento_asistencia, 3",
            "categoria_servicio, 10",
            "motivo, 13",
            "parametro, 3",
            "tipo_bebida, 7",
            "unidad_manipulacion, 3",
            "ubicacion, 4"
    })
    void cargaLosCatalogos(String tabla, int filasEsperadas) {
        Integer filas = jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class);

        assertThat(filas).isEqualTo(filasEsperadas);
    }

    @Test
    void creaLosIndicesDeExclusividad() {
        var indices = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class);

        assertThat(indices).contains("ux_evento_unidad_activa", "ux_orden_evento_vigente");
    }

    @Test
    void noHayUsuariosCargados() {
        // V2 no versiona hashes: el primer usuario lo crea la aplicación (tarea 7).
        Integer usuarios = jdbc.queryForObject("SELECT count(*) FROM usuario", Integer.class);

        assertThat(usuarios).isZero();
    }
}
