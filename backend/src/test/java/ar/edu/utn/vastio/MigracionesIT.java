package ar.edu.utn.vastio;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifica que V1 y V2 dejan la base como indica el diccionario de datos.
 */
@PruebaDeIntegracion
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
    void lasMigracionesNoCarganUsuarios() throws Exception {
        // No se versionan hashes: el primer usuario lo crea la aplicación (UsuarioInicialDev).
        // Se revisa el script porque la base es compartida con tests que crean usuarios.
        for (Resource script : new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/*.sql")) {
            assertThat(script.getContentAsString(StandardCharsets.UTF_8))
                    .as(script.getFilename())
                    .doesNotContainIgnoringCase("INSERT INTO usuario");
        }
    }
}
