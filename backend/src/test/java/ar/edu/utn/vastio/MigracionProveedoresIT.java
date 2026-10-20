package ar.edu.utn.vastio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Migraciones reales de Flyway, exclusivamente en bases nuevas de un contenedor de prueba. */
@Testcontainers
class MigracionProveedoresIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private record Base(String url, JdbcTemplate jdbc) {
        Flyway flyway(String target) {
            return Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                    .locations("classpath:db/migration").target(target).load();
        }
    }

    private Base nueva() throws Exception {
        String nombre = "v9_" + UUID.randomUUID().toString().replace("-", "");
        try (var conexion = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var sentencia = conexion.createStatement()) {
            sentencia.execute("CREATE DATABASE " + nombre);
        }
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + nombre;
        return new Base(url, new JdbcTemplate(new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())));
    }

    @Test
    void respaldaRestauraYAutorizaConElMecanismoReal() throws Exception {
        String python = System.getenv("VASTIO_TEST_PYTHON");
        Assumptions.assumeTrue(python != null && !python.isBlank(), "Definir VASTIO_TEST_PYTHON para probar el script externo con Docker");
        Base base = poblada();
        String antes = preservados(base.jdbc());
        Path script = Path.of("../scripts/probar_resguardo_proveedores.py").toAbsolutePath().normalize();
        Path output = Path.of("target/resguardo-v9").toAbsolutePath();
        Files.createDirectories(output);
        Path log = output.resolve("ejecucion.log");
        var comando = new ProcessBuilder(python, script.toString(), "--container", POSTGRES.getContainerId(),
                "--database", base.url().substring(base.url().lastIndexOf('/') + 1), "--user", POSTGRES.getUsername(),
                "--output", output.toString()).redirectErrorStream(true).redirectOutput(log.toFile());
        comando.environment().put("PYTHONIOENCODING", "utf-8");
        var proceso = comando.start();
        try {
            assertThat(proceso.waitFor(5, TimeUnit.MINUTES)).as("prueba de resguardo termina").isTrue();
            assertThat(proceso.exitValue()).as("resguardo: %s", Files.readString(log)).isZero();
        } finally {
            if (proceso.isAlive()) proceso.destroyForcibly();
        }
        base.flyway("9").migrate();
        ausencia(base.jdbc());
        assertThat(preservados(base.jdbc())).isEqualTo(antes);
    }

    @Test
    void instalaDesdeCeroYDesdeV8SinDatosAfectados() throws Exception {
        Base vacia = nueva();
        vacia.flyway("9").migrate();
        vacia.flyway("9").validate();
        ausencia(vacia.jdbc());
        Base v8 = nueva();
        v8.flyway("8").migrate();
        v8.flyway("9").migrate();
        ausencia(v8.jdbc());
    }

    @Test
    void sinProveedoresConservaIngresosYBebidasSinExigirArchivo() throws Exception {
        Base base = poblada();
        base.jdbc().execute("UPDATE bebida SET proveedor_habitual_id=NULL; UPDATE ingreso SET proveedor_id=NULL; DELETE FROM proveedor");
        String antes = preservados(base.jdbc());
        base.flyway("9").migrate();
        ausencia(base.jdbc());
        assertThat(preservados(base.jdbc())).isEqualTo(antes);
    }

    @Test
    void bloqueaSinRespaldoYConReciboObsoletoSinPerderDatos() throws Exception {
        Base base = poblada();
        String antes = preservados(base.jdbc());
        assertThatThrownBy(() -> base.flyway("9").migrate()).hasStackTraceContaining("V9 bloqueada");
        assertThat(base.jdbc().queryForObject("SELECT count(*) FROM proveedor", Integer.class)).isEqualTo(3);
        assertThat(base.jdbc().queryForObject("SELECT proveedor_id FROM ingreso WHERE ingreso_id = 1", Long.class)).isEqualTo(2);
        recibo(base.jdbc());
        base.jdbc().update("UPDATE proveedor SET telefono = 'cambio posterior' WHERE proveedor_id = 3");
        assertThatThrownBy(() -> base.flyway("9").migrate()).hasStackTraceContaining("V9 bloqueada");
        assertThat(preservados(base.jdbc())).isEqualTo(antes);
    }

    @Test
    void noAceptaRecibosDeOtraBaseNiHashesInvalidos() throws Exception {
        Base base = poblada();
        recibo(base.jdbc());
        base.jdbc().update("UPDATE resguardo_proveedores_v9 SET base_nombre='otra_base'");
        assertThatThrownBy(() -> base.flyway("9").migrate()).hasStackTraceContaining("V9 bloqueada");
        base.jdbc().update("UPDATE resguardo_proveedores_v9 SET base_nombre=current_database(), archivo_sha256='invalido'");
        assertThatThrownBy(() -> base.flyway("9").migrate()).hasStackTraceContaining("V9 bloqueada");
        assertThat(base.jdbc().queryForObject("SELECT count(*) FROM proveedor", Integer.class)).isEqualTo(3);
    }

    @Test
    void migraV8PobladaConReciboVigenteYConservaFilasHistoricas() throws Exception {
        Base base = poblada();
        String antes = preservados(base.jdbc());
        recibo(base.jdbc());
        base.flyway("9").migrate();
        base.flyway("9").validate();
        ausencia(base.jdbc());
        assertThat(preservados(base.jdbc())).isEqualTo(antes);
    }

    @Test
    void noEliminaDependenciasInesperadasEnCascada() throws Exception {
        Base base = poblada();
        recibo(base.jdbc());
        base.jdbc().execute("CREATE VIEW informe_externo AS SELECT proveedor_id FROM proveedor");
        assertThatThrownBy(() -> base.flyway("9").migrate()).hasStackTraceContaining("informe_externo");
        assertThat(base.jdbc().queryForObject("SELECT count(*) FROM proveedor", Integer.class)).isEqualTo(3);
        assertThat(base.jdbc().queryForObject("SELECT proveedor_habitual_id FROM bebida WHERE bebida_id = 1", Long.class)).isEqualTo(1);
    }

    private Base poblada() throws Exception {
        Base base = nueva();
        base.flyway("8").migrate();
        base.jdbc().execute("""
                INSERT INTO usuario (usuario_id,nombre_completo,nombre_usuario,hash_contrasena) VALUES (1,'Prueba','prueba','no-login');
                INSERT INTO proveedor (proveedor_id,razon_social,cuit,telefono,email,activo) VALUES
                  (1,'Activo','30711222339','123','activo@example.test',true),
                  (2,'Inactivo',NULL,NULL,NULL,false),(3,'Sin referencias',NULL,NULL,NULL,true);
                INSERT INTO bebida (bebida_id,nombre,presentacion,tipo_bebida_id,unidad_manipulacion_id,unidades_por_bulto,activo,proveedor_habitual_id)
                  VALUES (1,'Ron','750 ml',3,1,6,true,1),(2,'Gin','750 ml',3,1,6,true,NULL);
                INSERT INTO ingreso (ingreso_id,ubicacion_destino_id,proveedor_id,numero_remito,es_inventario_inicial,usuario_id)
                  VALUES (1,1,2,'R-historico',false,1),(2,1,NULL,NULL,true,1);
                INSERT INTO movimiento_stock (tipo,bebida_id,cantidad,ubicacion_destino_id,ingreso_id,usuario_id)
                  VALUES ('INGRESO',1,12,1,1,1),('INVENTARIO_INICIAL',2,6,1,2,1);
                INSERT INTO stock_ubicacion VALUES (1,1,12,now()),(1,2,6,now());
                """);
        return base;
    }

    /** Recibo simulado para probar exclusivamente el guard SQL; el script de respaldo se prueba aparte. */
    private void recibo(JdbcTemplate jdbc) {
        jdbc.execute("""
                CREATE TABLE resguardo_proveedores_v9 (id boolean, base_oid oid, base_nombre text, huella text,
                  archivo_sha256 text, manifiesto_sha256 text, verificado_en timestamptz);
                INSERT INTO resguardo_proveedores_v9
                SELECT true,(SELECT oid FROM pg_database WHERE datname=current_database()),current_database(),
                  encode(sha256(convert_to(jsonb_build_object(
                    'proveedores',(SELECT coalesce(jsonb_agg(to_jsonb(p) ORDER BY proveedor_id),'[]'::jsonb) FROM proveedor p),
                    'bebidas',(SELECT coalesce(jsonb_agg(jsonb_build_array(bebida_id,proveedor_habitual_id) ORDER BY bebida_id),'[]'::jsonb) FROM bebida),
                    'ingresos',(SELECT coalesce(jsonb_agg(jsonb_build_array(ingreso_id,proveedor_id) ORDER BY ingreso_id),'[]'::jsonb) FROM ingreso)
                  )::text,'UTF8')),'hex'),repeat('a',64),repeat('b',64),now();
                """);
    }

    private String preservados(JdbcTemplate jdbc) {
        return jdbc.queryForObject("""
                SELECT jsonb_build_object(
                  'bebidas',(SELECT jsonb_agg(to_jsonb(b)-'proveedor_habitual_id' ORDER BY bebida_id) FROM bebida b),
                  'ingresos',(SELECT jsonb_agg(to_jsonb(i)-'proveedor_id' ORDER BY ingreso_id) FROM ingreso i),
                  'movimientos',(SELECT jsonb_agg(to_jsonb(m) ORDER BY movimiento_id) FROM movimiento_stock m),
                  'stock',(SELECT jsonb_agg(to_jsonb(s) ORDER BY ubicacion_id,bebida_id) FROM stock_ubicacion s),
                  'preparaciones',(SELECT jsonb_agg(to_jsonb(p) ORDER BY preparacion_id) FROM preparacion_evento p),
                  'entregas',(SELECT jsonb_agg(to_jsonb(e) ORDER BY entrega_id) FROM entrega e),
                  'consumos',(SELECT jsonb_agg(to_jsonb(c) ORDER BY evento_id,bebida_id) FROM consumo_evento c)
                )::text
                """, String.class);
    }

    private void ausencia(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.proveedor') IS NULL", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.resguardo_proveedores_v9') IS NULL", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND column_name IN ('proveedor_id','proveedor_habitual_id')", Integer.class)).isZero();
    }
}
