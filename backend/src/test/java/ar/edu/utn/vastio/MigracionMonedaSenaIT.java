package ar.edu.utn.vastio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Actualización real a V11 sobre señas existentes, sin modificar migraciones publicadas. */
@Testcontainers
class MigracionMonedaSenaIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private record Base(String url, JdbcTemplate jdbc) {
        Flyway flyway(String target) {
            return Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                    .locations("classpath:db/migration").target(target).outOfOrder(false).load();
        }
    }

    private Base nueva() throws Exception {
        String nombre = "moneda_" + UUID.randomUUID().toString().replace("-", "");
        try (var conexion = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var sentencia = conexion.createStatement()) {
            sentencia.execute("CREATE DATABASE " + nombre);
        }
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + nombre;
        return new Base(url, new JdbcTemplate(new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"9", "10"})
    void conservaSenasPreexistentesYAplicaV10AntesDeV11(String versionInicial) throws Exception {
        Base base = nueva();
        base.flyway(versionInicial).migrate();
        base.flyway(versionInicial).validate();
        poblar(base.jdbc());
        var antes = base.jdbc().queryForList("SELECT * FROM evento ORDER BY evento_id");
        var historialAntes = base.jdbc().queryForList("SELECT version, checksum FROM flyway_schema_history WHERE success ORDER BY installed_rank");

        var migracion = base.flyway("11");
        migracion.migrate();
        migracion.validate();

        var despues = base.jdbc().queryForList("SELECT * FROM evento ORDER BY evento_id");
        assertThat(despues).hasSize(antes.size());
        for (int i = 0; i < antes.size(); i++) {
            var fila = despues.get(i);
            assertThat(fila.remove("moneda_sena")).isEqualTo(antes.get(i).get("importe_sena") == null ? null : "ARS");
            assertThat(fila).as("todos los datos anteriores de evento %s", antes.get(i).get("evento_id")).isEqualTo(antes.get(i));
        }
        assertThat(base.jdbc().queryForList("SELECT version, checksum FROM flyway_schema_history WHERE success ORDER BY installed_rank"))
                .containsAll(historialAntes);
        assertThat(base.jdbc().queryForList("SELECT version FROM flyway_schema_history WHERE version IN ('10','11') AND success ORDER BY installed_rank", String.class))
                .containsExactly("10", "11");
        assertThat(migracion.info().current().getVersion().toString()).isEqualTo("11");
        assertThat(base.jdbc().queryForObject("SELECT count(*) FROM servicio_renglon", Integer.class)).isZero();
        assertThat(migracion.migrate().migrationsExecuted).isZero();
    }

    @Test
    void instalaDesdeCeroYRespetaMonedasParesYRestriccionesExistentes() throws Exception {
        Base base = nueva();
        base.flyway("11").migrate();
        base.flyway("11").validate();
        // Datos anteriores se cargan con V11 ya aplicada, indicando ARS donde corresponde.
        poblarConMoneda(base.jdbc());
        for (String moneda : List.of("ARS", "USD")) {
            base.jdbc().update("UPDATE evento SET importe_sena = ?, moneda_sena = ? WHERE evento_id = 1", new BigDecimal("1500.75"), moneda);
            assertThat(base.jdbc().queryForObject("SELECT importe_sena FROM evento WHERE evento_id = 1", BigDecimal.class))
                    .isEqualByComparingTo("1500.75");
            assertThat(base.jdbc().queryForObject("SELECT moneda_sena FROM evento WHERE evento_id = 1", String.class)).isEqualTo(moneda);
        }
        assertThatThrownBy(() -> base.jdbc().update("UPDATE evento SET moneda_sena = 'EUR' WHERE evento_id = 1"))
                .hasMessageContaining("ck_evento_moneda_sena");
        assertThatThrownBy(() -> base.jdbc().update("UPDATE evento SET moneda_sena = NULL WHERE evento_id = 1"))
                .hasMessageContaining("ck_evento_sena_con_moneda");
        assertThatThrownBy(() -> base.jdbc().update("UPDATE evento SET moneda_sena = 'USD' WHERE evento_id = 4"))
                .hasMessageContaining("ck_evento_sena_con_moneda");
        assertThatThrownBy(() -> base.jdbc().update("UPDATE evento SET importe_sena = -1 WHERE evento_id = 1"))
                .hasMessageContaining("ck_evento_2");
        assertThatThrownBy(() -> base.jdbc().update("UPDATE evento SET importe_sena = NULL, moneda_sena = NULL WHERE evento_id = 1"))
                .hasMessageContaining("ck_evento_3");
        assertThat(base.jdbc().queryForObject("SELECT moneda_sena FROM evento WHERE evento_id = 4", String.class)).isNull();
    }

    @Test
    void elFixtureDeEstadisticasSigueFuncionandoConV11() throws Exception {
        Base base = nueva();
        base.flyway("11").migrate();
        base.jdbc().execute("""
                INSERT INTO usuario (nombre_completo, nombre_usuario, hash_contrasena)
                VALUES ('Vendedora sintética', 'lucia.ferreyra', 'no-login'), ('Planner sintética', 'ana.sosa', 'no-login')
                """);
        base.jdbc().execute(java.nio.file.Files.readString(java.nio.file.Path.of("../frontend/e2e/fixtures/estadisticas-consumo.sql")));
        assertThat(base.jdbc().queryForObject("SELECT count(*) FROM evento", Integer.class)).isEqualTo(4);
        assertThat(base.jdbc().queryForList("SELECT DISTINCT moneda_sena FROM evento", String.class)).containsExactly("ARS");
        assertThat(base.jdbc().queryForList("SELECT DISTINCT importe_sena FROM evento", BigDecimal.class)).containsExactly(new BigDecimal("100.00"));
        assertThat(base.jdbc().queryForObject("SELECT count(*) FROM consumo_evento", Integer.class)).isEqualTo(4);
    }

    private void poblar(JdbcTemplate jdbc) {
        poblar(jdbc, false);
    }

    private void poblarConMoneda(JdbcTemplate jdbc) {
        poblar(jdbc, true);
    }

    private void poblar(JdbcTemplate jdbc, boolean conMoneda) {
        jdbc.execute("INSERT INTO usuario (usuario_id, nombre_completo, nombre_usuario, hash_contrasena) VALUES (1, 'Prueba', 'migracion.moneda', 'no-login')");
        jdbc.execute("INSERT INTO cliente (cliente_id, nombre) VALUES (1, 'Cliente de prueba')");
        jdbc.execute("INSERT INTO unidad_comercializable (unidad_id, salon_id, fecha, turno_id) SELECT i, 1, DATE '2032-06-01' + i, 1 FROM generate_series(1, 5) i");
        for (int i = 1; i <= 5; i++) {
            String estado = switch (i) {
                case 3 -> "CANCELADO";
                case 4 -> "PRE_RESERVA";
                case 5 -> "CERRADO";
                default -> "SENADO";
            };
            BigDecimal importe = switch (i) {
                case 1 -> new BigDecimal("150000.50");
                case 2 -> new BigDecimal("0.01");
                case 3 -> new BigDecimal("9876.54");
                case 4 -> null;
                default -> new BigDecimal("1234.56");
            };
            jdbc.update("""
                    INSERT INTO evento (evento_id, codigo, unidad_id, cliente_id, tipo_evento_id, vendedora_id, planner_id,
                        estado, nombre, invitados_definitivos, importe_sena, fecha_sena, firmante_dni, fecha_firma_contrato,
                        motivo_cancelacion_id, version%s)
                    VALUES (?, ?, ?, 1, 1, 1, 1, ?, 'Prueba de migración', true, ?, DATE '2032-05-01', '30111222',
                        DATE '2032-05-02', ?, 0%s)
                    """.formatted(conMoneda ? ", moneda_sena" : "", conMoneda ? (importe == null ? ", NULL" : ", 'ARS'") : ""),
                    i, "EV-2032-%05d".formatted(i), i, estado, importe, i == 3 ? 1 : null);
        }
    }
}
