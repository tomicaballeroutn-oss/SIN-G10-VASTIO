package ar.edu.utn.vastio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.sql.DriverManager;
import java.math.BigDecimal;
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

@Testcontainers
class MigracionComprobanteSenaIT {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");
    private record Base(String url, JdbcTemplate jdbc) {
        Flyway flyway(String target) {
            return Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                    .locations("classpath:db/migration").target(target).outOfOrder(false).load();
        }
    }

    private Base nueva() throws Exception {
        String nombre = "comprobante_" + UUID.randomUUID().toString().replace("-", "");
        try (var conexion = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var sentencia = conexion.createStatement()) {
            sentencia.execute("CREATE DATABASE " + nombre);
        }
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + nombre;
        return new Base(url, new JdbcTemplate(new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())));
    }


    @Test
    void instalaDesdeCeroCon42Tablas11CategoriasYOrdenCorrecto() throws Exception {
        Base base = nueva();
        base.flyway("13").migrate();
        base.flyway("13").validate();
        assertThat(base.jdbc().queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name <> 'flyway_schema_history'", Integer.class)).isEqualTo(42);
        assertThat(base.jdbc().queryForObject("SELECT count(*) FROM categoria_servicio", Integer.class)).isEqualTo(11);
        assertThat(base.jdbc().queryForList("SELECT version FROM flyway_schema_history WHERE version IN ('10','11','12','13') ORDER BY installed_rank", String.class)).containsExactly("10", "11", "12", "13");
    }

    @ParameterizedTest
    @ValueSource(strings = {"11", "12"})
    void actualizaSinAlterarSenasNiInventarOriginalesHistoricos(String desde) throws Exception {
        Base base = nueva();
        base.flyway(desde).migrate();
        base.jdbc().execute("""
                INSERT INTO usuario (usuario_id,nombre_completo,nombre_usuario,hash_contrasena) VALUES (1,'Emisor original','prueba','no-login');
                INSERT INTO cliente (cliente_id,nombre) VALUES (1,'Cliente original');
                INSERT INTO unidad_comercializable (unidad_id,salon_id,fecha,turno_id) VALUES (1,1,'2040-06-01',2);
                INSERT INTO evento (evento_id,codigo,unidad_id,cliente_id,tipo_evento_id,vendedora_id,estado,nombre,invitados_definitivos,importe_sena,moneda_sena,fecha_sena,firmante_dni,version)
                VALUES (1,'EV-2040-00001',1,1,1,1,'SENADO','Evento original',false,1500.75,'USD','2040-01-01','30111222',0);
                """);
        if (desde.equals("12")) base.jdbc().execute("""
                INSERT INTO numerador_comprobante(anio,ultimo) VALUES (2040,7);
                INSERT INTO comprobante_sena(evento_id,numero,fecha_emision,usuario_id) VALUES (1,'CS-2040-00007','2040-01-02T10:30:00-03:00',1);
                """);
        var legado = desde.equals("12") ? base.jdbc().queryForMap("SELECT numero, fecha_emision, usuario_id FROM comprobante_sena WHERE evento_id=1") : null;
        var evento = base.jdbc().queryForMap("SELECT * FROM evento WHERE evento_id=1");
        var historial = base.jdbc().queryForList("SELECT version, checksum FROM flyway_schema_history ORDER BY installed_rank");
        base.flyway("13").migrate();
        base.flyway("13").validate();
        assertThat(base.jdbc().queryForMap("SELECT * FROM evento WHERE evento_id=1")).isEqualTo(evento);
        assertThat(base.jdbc().queryForList("SELECT version, checksum FROM flyway_schema_history ORDER BY installed_rank")).containsAll(historial);
        assertThat(base.flyway("13").migrate().migrationsExecuted).isZero();
        if (desde.equals("12")) {
            assertThat(base.jdbc().queryForMap("SELECT numero, usuario_id, pdf_original FROM comprobante_sena WHERE evento_id=1"))
                    .containsEntry("numero", "CS-2040-00007").containsEntry("usuario_id", 1L).containsEntry("pdf_original", null);
            assertThat(base.jdbc().queryForMap("SELECT numero, fecha_emision, usuario_id FROM comprobante_sena WHERE evento_id=1")).isEqualTo(legado);
            assertThat(base.jdbc().queryForObject("SELECT ultimo FROM numerador_comprobante WHERE anio=2040", Integer.class)).isEqualTo(7);
            assertThatThrownBy(() -> base.jdbc().update("UPDATE comprobante_sena SET numero='CS-2040-00008' WHERE evento_id=1")).hasMessageContaining("es inmutable");
        } else {
            assertThatThrownBy(() -> base.jdbc().update("INSERT INTO comprobante_sena(evento_id,numero,usuario_id) VALUES (1,'CS-2040-00001',1)"))
                    .hasMessageContaining("requiere su PDF original");
        }
        assertThat(base.jdbc().queryForObject("SELECT importe_sena FROM evento WHERE evento_id=1", BigDecimal.class)).isEqualByComparingTo("1500.75");
    }
}
