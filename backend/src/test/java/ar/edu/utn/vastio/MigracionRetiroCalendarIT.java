package ar.edu.utn.vastio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import ar.edu.utn.vastio.notificaciones.dominio.TipoNotificacion;

@Testcontainers
class MigracionRetiroCalendarIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Test
    void actualizaV20YConservaTodasLasFilasComercialesYLosChecksums() throws Exception {
        var base = nuevaBase();
        var v20 = base.flyway("20");
        v20.migrate();
        v20.validate();

        JdbcTemplate jdbc = base.jdbc();
        cargarDatos(jdbc);
        var datosComerciales = datosComerciales(jdbc);
        var checksums = jdbc.queryForList("SELECT version, checksum FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(checksums).hasSize(20);
        assertThat(avisos(jdbc)).containsEntry("avisos", 7L).containsEntry("destinatarios", 9L);
        assertThat(jdbc.queryForMap("""
                SELECT (SELECT count(*) FROM publicacion_calendar) AS publicaciones,
                       (SELECT count(*) FROM operacion_calendar) AS operaciones,
                       (SELECT count(*) FROM calendar_seguimiento) AS seguimientos,
                       (SELECT count(*) FROM notificacion WHERE tipo = 'CALENDAR_PENDIENTE') AS avisos_calendar,
                       (SELECT count(*) FROM notificacion_destinatario d JOIN notificacion n USING (notificacion_id)
                        WHERE n.tipo = 'CALENDAR_PENDIENTE') AS destinatarios_calendar
                """)).containsExactlyInAnyOrderEntriesOf(Map.of(
                        "publicaciones", 2L, "operaciones", 3L, "seguimientos", 2L,
                        "avisos_calendar", 2L, "destinatarios_calendar", 3L));

        var v21 = base.flyway("21");
        assertThat(v21.migrate().migrationsExecuted).isEqualTo(1);
        v21.validate();

        assertThat(jdbc.queryForList("""
                SELECT version, checksum FROM flyway_schema_history WHERE version <> '21' ORDER BY installed_rank
                """)).isEqualTo(checksums);
        assertThat(datosComerciales(jdbc)).isEqualTo(datosComerciales);
        assertThat(avisos(jdbc)).containsEntry("avisos", 5L).containsEntry("destinatarios", 6L);
        verificarEsquemaFinal(jdbc);
        assertThat(v21.migrate().migrationsExecuted).isZero();
    }

    @Test
    void instalaDesdeCeroHastaV21YPermiteRegistrarYModificarEventos() throws Exception {
        var base = nuevaBase();
        var flyway = base.flyway("21");
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(21);
        flyway.validate();

        JdbcTemplate jdbc = base.jdbc();
        verificarEsquemaFinal(jdbc);
        jdbc.execute("""
                INSERT INTO usuario(usuario_id,nombre_completo,nombre_usuario,hash_contrasena)
                VALUES (101,'Vendedora de prueba','vendedora.v21','no-login');
                INSERT INTO cliente(cliente_id,nombre) VALUES (101,'Cliente de prueba');
                INSERT INTO unidad_comercializable(unidad_id,salon_id,fecha,turno_id) VALUES (101,1,'2040-03-01',2);
                INSERT INTO evento(evento_id,codigo,unidad_id,cliente_id,tipo_evento_id,vendedora_id,estado,nombre,invitados_definitivos,version)
                VALUES (101,'EV-2040-00101',101,101,1,101,'PRE_RESERVA','Evento de prueba',false,0);
                INSERT INTO cambio_estado_evento(evento_id,estado_nuevo,usuario_id) VALUES (101,'PRE_RESERVA',101);
                UPDATE evento SET hora_inicio = '21:30', tipo_evento_id = 2 WHERE evento_id = 101;
                INSERT INTO modificacion_evento(evento_id,campo,valor_nuevo,usuario_id) VALUES (101,'hora_inicio','21:30',101);
                UPDATE turno SET hora_inicio = '20:45' WHERE turno_id = 2;
                UPDATE salon SET nombre = 'Salón actualizado' WHERE salon_id = 1;
                UPDATE tipo_evento SET nombre = 'Tipo actualizado' WHERE tipo_evento_id = 2;
                """);
        assertThat(jdbc.queryForMap("SELECT estado, hora_inicio::text AS hora, tipo_evento_id::int AS tipo_evento_id FROM evento WHERE evento_id = 101"))
                .containsEntry("estado", "PRE_RESERVA").containsEntry("hora", "21:30:00")
                .containsEntry("tipo_evento_id", 2);
        assertThat(jdbc.queryForObject("SELECT estado_nuevo FROM cambio_estado_evento WHERE evento_id = 101", String.class))
                .isEqualTo("PRE_RESERVA");
        assertThat(jdbc.queryForObject("SELECT valor_nuevo FROM modificacion_evento WHERE evento_id = 101", String.class))
                .isEqualTo("21:30");
        assertThat(jdbc.queryForObject("SELECT hora_inicio::text FROM turno WHERE turno_id = 2", String.class))
                .isEqualTo("20:45:00");
        assertThat(jdbc.queryForObject("SELECT nombre FROM salon WHERE salon_id = 1", String.class))
                .isEqualTo("Salón actualizado");
        assertThat(jdbc.queryForObject("SELECT nombre FROM tipo_evento WHERE tipo_evento_id = 2", String.class))
                .isEqualTo("Tipo actualizado");
    }

    private static void cargarDatos(JdbcTemplate jdbc) {
        jdbc.execute("""
                INSERT INTO usuario(usuario_id,nombre_completo,nombre_usuario,hash_contrasena)
                VALUES (101,'Vendedora de prueba','vendedora.v20','no-login'), (102,'Planner de prueba','planner.v20','no-login');
                INSERT INTO usuario_rol(usuario_id,rol_id) SELECT 101,rol_id FROM rol WHERE codigo = 'VENDEDORA';
                INSERT INTO usuario_rol(usuario_id,rol_id) SELECT 102,rol_id FROM rol WHERE codigo = 'PLANNER';
                INSERT INTO cliente(cliente_id,nombre) VALUES (101,'Cliente de prueba');
                INSERT INTO unidad_comercializable(unidad_id,salon_id,fecha,turno_id)
                VALUES (101,1,'2040-01-01',2),(102,2,'2040-01-02',2),(103,1,'2040-01-02',2);
                INSERT INTO evento(evento_id,codigo,unidad_id,cliente_id,tipo_evento_id,vendedora_id,planner_id,
                                   estado,nombre,cantidad_invitados,invitados_definitivos,
                                   importe_sena,moneda_sena,fecha_sena,firmante_dni,fecha_firma_contrato,version)
                VALUES (101,'EV-2040-00101',101,101,1,101,NULL,'SENADO','Seña de prueba',90,false,
                        1200.75,'USD','2039-12-01','30111222',NULL,2),
                       (102,'EV-2040-00102',102,101,2,101,102,'CONFIRMADO','Confirmado de prueba',120,true,
                        180000.00,'ARS','2039-11-15','30999888','2039-12-01',4);
                INSERT INTO servicio_contratado(evento_id,categoria_id,descripcion,usuario_id)
                VALUES (101,2,'Menú de prueba',101),(101,6,'Bodega de prueba',101);
                INSERT INTO cambio_estado_evento(evento_id,estado_anterior,estado_nuevo,usuario_id)
                VALUES (101,'PRE_RESERVA','SENADO',101),(102,'CONTRATADO','CONFIRMADO',101);
                INSERT INTO modificacion_evento(evento_id,campo,valor_anterior,valor_nuevo,usuario_id)
                VALUES (101,'cantidad_invitados','80','90',101);
                INSERT INTO reprogramacion(evento_id,unidad_anterior_id,unidad_nueva_id,motivo_id,usuario_id)
                VALUES (102,103,102,4,101);
                INSERT INTO publicacion_calendar(evento_id,calendar_id,instalacion,google_id,estado,pendiente)
                VALUES (101,'prueba','v20','publicado','EXPORTADO','NINGUNA'),
                       (102,'prueba','v20','incierto','INCIERTO','EXPORTAR');
                INSERT INTO operacion_calendar(evento_id,accion,usuario_id)
                VALUES (101,'SOLICITUD',101),(101,'CREACION_CONFIRMADA',101),(102,'INCIERTO',102);
                INSERT INTO notificacion(notificacion_id,evento_id,tipo,mensaje,usuario_origen_id,resuelta)
                VALUES (101,101,'SENA','Seña de prueba',101,false),
                       (102,102,'CONFIRMACION','Confirmación de prueba',101,false),
                       (103,102,'ASISTENCIA_REAL','Asistencia de prueba',101,false),
                       (104,102,'ORDEN_PREPARACION','Orden de prueba',101,false),
                       (105,102,'REPROGRAMACION','Reprogramación resuelta de prueba',101,true),
                       (106,101,'CALENDAR_PENDIENTE','Calendar de prueba activo',101,false),
                       (107,102,'CALENDAR_PENDIENTE','Calendar de prueba resuelto',101,true);
                INSERT INTO notificacion_destinatario(notificacion_id,usuario_id,leida,fecha_lectura)
                VALUES (101,101,false,NULL),(101,102,true,'2039-12-01 10:00-03'),(102,102,false,NULL),
                       (103,102,false,NULL),(104,102,false,NULL),(105,101,false,NULL),
                       (106,101,false,NULL),(106,102,true,'2039-12-01 10:00-03'),(107,102,false,NULL);
                """);
    }

    private static Map<String, Object> avisos(JdbcTemplate jdbc) {
        return jdbc.queryForMap("""
                SELECT (SELECT count(*) FROM notificacion) AS avisos,
                       (SELECT count(*) FROM notificacion_destinatario) AS destinatarios
                """);
    }

    private static Map<String, String> datosComerciales(JdbcTemplate jdbc) {
        var tablas = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                  AND table_name NOT IN ('flyway_schema_history', 'calendar_seguimiento', 'operacion_calendar', 'publicacion_calendar')
                ORDER BY table_name
                """, String.class);
        var datos = new LinkedHashMap<String, String>();
        for (String tabla : tablas) {
            String filas = switch (tabla) {
                case "notificacion" -> "SELECT * FROM notificacion WHERE tipo <> 'CALENDAR_PENDIENTE'";
                case "notificacion_destinatario" -> """
                        SELECT d.* FROM notificacion_destinatario d JOIN notificacion n USING (notificacion_id)
                        WHERE n.tipo <> 'CALENDAR_PENDIENTE'
                        """;
                default -> "SELECT * FROM public." + tabla;
            };
            datos.put(tabla, jdbc.queryForObject("""
                    SELECT coalesce(jsonb_agg(to_jsonb(fila) ORDER BY to_jsonb(fila)), '[]'::jsonb)::text
                    FROM (%s) fila
                    """.formatted(filas), String.class));
        }
        assertThat(datos).hasSize(42);
        return datos;
    }

    private static void verificarEsquemaFinal(JdbcTemplate jdbc) {
        var objetos = jdbc.queryForList("""
                SELECT relname FROM pg_class WHERE relnamespace = 'public'::regnamespace AND relname LIKE '%calendar%'
                """, String.class);
        assertThat(objetos).isEmpty();
        var triggers = jdbc.queryForList("SELECT tgname FROM pg_trigger WHERE NOT tgisinternal", String.class);
        assertThat(triggers).doesNotContain("tg_calendar_evento", "tg_calendar_turno", "tg_calendar_salon",
                "tg_calendar_tipo", "tg_operacion_calendar_solo_insercion")
                .contains("tg_cambio_estado_evento_solo_insercion", "tg_reprogramacion_solo_insercion");
        var funciones = jdbc.queryForList("SELECT proname FROM pg_proc WHERE pronamespace = 'public'::regnamespace", String.class);
        assertThat(funciones).doesNotContain("fn_calendar_encolar_evento", "fn_calendar_encolar_catalogo")
                .contains("fn_solo_insercion");

        String regla = jdbc.queryForObject("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conname = 'ck_notificacion_tipo' AND conrelid = 'public.notificacion'::regclass
                """, String.class);
        assertThat(regla).doesNotContain("CALENDAR_PENDIENTE")
                .contains(Arrays.stream(TipoNotificacion.values()).map(Enum::name).toArray(String[]::new));
        assertThatThrownBy(() -> jdbc.update("INSERT INTO notificacion(tipo,mensaje) VALUES ('CALENDAR_PENDIENTE','Inválido')"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_notificacion_tipo");
    }

    private static Base nuevaBase() throws Exception {
        String nombre = "retiro_calendar_" + UUID.randomUUID().toString().replace("-", "");
        try (var conexion = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var sentencia = conexion.createStatement()) {
            sentencia.execute("CREATE DATABASE " + nombre);
        }
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + nombre;
        return new Base(new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    private record Base(DriverManagerDataSource fuente) {
        JdbcTemplate jdbc() {
            return new JdbcTemplate(fuente);
        }

        Flyway flyway(String version) {
            return Flyway.configure().dataSource(fuente).locations("classpath:db/migration").target(version).load();
        }
    }
}
