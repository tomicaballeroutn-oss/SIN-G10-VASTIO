package ar.edu.utn.vastio;

import static org.assertj.core.api.Assertions.*;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class MigracionCalendarIT {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17");
    @ParameterizedTest @ValueSource(booleans={false,true})
    void instalaYActualizaDesdeV13SinCambiarDatosComerciales(boolean actualizar) throws Exception {
        String nombre="calendar_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());var s=c.createStatement()){s.execute("CREATE DATABASE "+nombre);}
        String url="jdbc:postgresql://"+POSTGRES.getHost()+":"+POSTGRES.getMappedPort(5432)+"/"+nombre;
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,POSTGRES.getUsername(),POSTGRES.getPassword()));
        if(actualizar) {
            flyway(url,"13").migrate();
            jdbc.execute("""
                INSERT INTO usuario(usuario_id,nombre_completo,nombre_usuario,hash_contrasena) VALUES(1,'Prueba','calendar','no-login');
                INSERT INTO cliente(cliente_id,nombre) VALUES(1,'Privado');
                INSERT INTO unidad_comercializable(unidad_id,salon_id,fecha,turno_id) VALUES(1,1,'2040-01-01',2);
                INSERT INTO evento(evento_id,codigo,unidad_id,cliente_id,tipo_evento_id,vendedora_id,estado,nombre,invitados_definitivos,importe_sena,moneda_sena,fecha_sena,firmante_dni,version)
                VALUES(1,'EV-2040-00001',1,1,1,1,'SENADO','Privado',false,10,'ARS','2039-12-01','30111222',0);
                """);
        }
        java.util.List<java.util.Map<String,Object>> antes=actualizar?jdbc.queryForList("SELECT * FROM evento"):java.util.List.of();
        var migrador=flyway(url,"14");migrador.migrate();migrador.validate();
        assertThat(jdbc.queryForList("SELECT * FROM evento")).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name <> 'flyway_schema_history'",Integer.class)).isEqualTo(44);
        assertThat(migrador.migrate().migrationsExecuted).isZero();
        if(actualizar) {
            assertThat(jdbc.queryForMap("SELECT pendiente,google_id FROM publicacion_calendar WHERE evento_id=1")).containsEntry("pendiente","EXPORTAR").containsEntry("google_id",null);
            jdbc.update("INSERT INTO operacion_calendar(evento_id,accion) VALUES(1,'SOLICITUD')");
            assertThatThrownBy(()->jdbc.update("UPDATE operacion_calendar SET accion='OTRA' WHERE evento_id=1")).hasMessageContaining("solo inserción");
            assertThatThrownBy(()->jdbc.update("DELETE FROM operacion_calendar WHERE evento_id=1")).hasMessageContaining("solo inserción");
            assertThatThrownBy(()->jdbc.update("INSERT INTO publicacion_calendar(evento_id) VALUES(1)")).hasMessageContaining("duplicate");
        }
    }
    private Flyway flyway(String url,String target) {
        return Flyway.configure().dataSource(url,POSTGRES.getUsername(),POSTGRES.getPassword()).locations("classpath:db/migration").target(target).load();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void colaV15DesdeCeroODesdeV14PreservaHistorial(boolean actualizar) throws Exception {
        String nombre="cola_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());var s=c.createStatement()){s.execute("CREATE DATABASE "+nombre);}
        String url="jdbc:postgresql://"+POSTGRES.getHost()+":"+POSTGRES.getMappedPort(5432)+"/"+nombre;
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,POSTGRES.getUsername(),POSTGRES.getPassword()));
        if(actualizar) {
            flyway(url,"14").migrate();
            jdbc.execute("""
                INSERT INTO usuario(usuario_id,nombre_completo,nombre_usuario,hash_contrasena) VALUES(1,'Prueba','calendar','no-login');
                INSERT INTO cliente(cliente_id,nombre) VALUES(1,'Privado');
                INSERT INTO unidad_comercializable(unidad_id,salon_id,fecha,turno_id) VALUES(1,1,'2040-01-01',2);
                INSERT INTO evento(evento_id,codigo,unidad_id,cliente_id,tipo_evento_id,vendedora_id,estado,nombre,invitados_definitivos,version)
                VALUES(1,'EV-2040-00001',1,1,1,1,'PRE_RESERVA','Privado',false,0);
                INSERT INTO publicacion_calendar(evento_id,calendar_id,instalacion,google_id,estado,pendiente,retiro_solicitado)
                VALUES(1,'aislado','prueba','id-conservado','RETIRADO','NINGUNA',true);
                INSERT INTO operacion_calendar(evento_id,accion) VALUES(1,'RETIRADA_CONFIRMADA');
                """);
        }
        var eventos=actualizar?jdbc.queryForList("SELECT * FROM evento"):java.util.List.of();
        var historial=actualizar?jdbc.queryForList("SELECT * FROM operacion_calendar"):java.util.List.of();
        var migrador=flyway(url,"15");migrador.migrate();migrador.validate();
        assertThat(jdbc.queryForList("SELECT * FROM evento")).isEqualTo(eventos);
        assertThat(jdbc.queryForList("SELECT * FROM operacion_calendar")).isEqualTo(historial);
        assertThat(migrador.migrate().migrationsExecuted).isZero();
        if(actualizar) {
            assertThat(jdbc.queryForObject("SELECT google_id FROM publicacion_calendar WHERE evento_id=1",String.class)).isEqualTo("id-conservado");
            assertThat(jdbc.queryForObject("SELECT retiro_solicitado FROM publicacion_calendar WHERE evento_id=1",Boolean.class)).isFalse();
            assertThat(jdbc.queryForObject("SELECT revision-procesada FROM calendar_seguimiento WHERE evento_id=1",Long.class)).isEqualTo(1);
            jdbc.update("UPDATE evento SET hora_inicio='22:30' WHERE evento_id=1");
            assertThat(jdbc.queryForObject("SELECT revision FROM calendar_seguimiento WHERE evento_id=1",Long.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE tipo='CALENDAR_PENDIENTE'",Integer.class)).isZero();
        }
    }
}
