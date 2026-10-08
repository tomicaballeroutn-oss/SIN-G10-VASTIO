package ar.edu.utn.vastio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Verifica que las migraciones dejan la base como indica el diccionario de datos.
 */
@PruebaDeIntegracion
class MigracionesIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transacciones;

    @Test
    void creaLas39Tablas() {
        Integer tablas = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """, Integer.class);

        assertThat(tablas).isEqualTo(39); // 38 de V1 + numerador_evento (V3)
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
            "tipo_bebida, 5",
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

    @ParameterizedTest(name = "{0} no admite UPDATE ni DELETE")
    @CsvSource({"cambio_estado_evento", "modificacion_evento", "reprogramacion", "movimiento_stock"})
    void lasTablasDeSoloInsercionRechazanCambios(String tabla) {
        for (String sentencia : List.of("UPDATE " + tabla + " SET fecha_hora = fecha_hora", "DELETE FROM " + tabla)) {
            // Con la tabla vacía el trigger por fila no llega a correr: se inserta una fila en una transacción que se descarta.
            assertThatThrownBy(() -> transacciones.executeWithoutResult(t -> {
                insertarFilaDePrueba(tabla);
                jdbc.update(sentencia);
            })).as(sentencia).hasMessageContaining("es de solo inserción");
        }
    }

    /** Fila mínima para el trigger; sin restricciones de clave porque se inserta con los triggers de FK desactivados. */
    private void insertarFilaDePrueba(String tabla) {
        jdbc.execute("SET LOCAL session_replication_role = replica");
        switch (tabla) {
            case "cambio_estado_evento" -> jdbc.update("INSERT INTO cambio_estado_evento (evento_id, estado_nuevo) VALUES (-1, 'PRE_RESERVA')");
            case "modificacion_evento" -> jdbc.update("INSERT INTO modificacion_evento (evento_id, campo, usuario_id) VALUES (-1, 'nombre', -1)");
            case "reprogramacion" -> jdbc.update("INSERT INTO reprogramacion (evento_id, unidad_anterior_id, unidad_nueva_id, motivo_id, usuario_id) VALUES (-1, -1, -2, 4, -1)");
            default -> jdbc.update("INSERT INTO movimiento_stock (tipo, bebida_id, cantidad, usuario_id, ubicacion_destino_id) VALUES ('INGRESO', -1, 1, -1, 1)");
        }
        jdbc.execute("SET LOCAL session_replication_role = DEFAULT");
    }
}
