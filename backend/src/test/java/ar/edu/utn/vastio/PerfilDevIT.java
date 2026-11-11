package ar.edu.utn.vastio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.infraestructura.UsuarioRepository;

/**
 * Solo en dev: usuario inicial de Dirección, Swagger UI (Sprint 0) y datos de demostración (Sprints 1 y 2).
 */
@SpringBootTest(properties = {
        "vastio.usuario-inicial.usuario=direccion.test",
        "vastio.usuario-inicial.contrasena=clave-inicial-de-prueba",
        "vastio.demo.contrasena=clave-de-demostracion",
        // Los datos básicos: el escenario completo se prueba en EscenarioCompletoIT.
        "vastio.demo.completo=false"
})
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
@Import(TestcontainersConfig.class)
class PerfilDevIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void alArrancarCreaElUsuarioInicialDeDireccion() {
        var usuario = usuarios.findByNombreUsuario("direccion.test").orElseThrow();

        assertThat(usuario.codigosDeRol()).containsExactly(RolCodigo.DIRECCION);
        assertThat(usuario.getHashContrasena()).startsWith("$2").doesNotContain("clave-inicial-de-prueba");
    }

    @Test
    void conElUsuarioInicialSePuedeIniciarSesion() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreUsuario\":\"direccion.test\",\"contrasena\":\"clave-inicial-de-prueba\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.roles[0]").value("DIRECCION"));
    }

    @Test
    void elDocumentoOpenApiEstaPublicadoEnDev() throws Exception {
        mvc.perform(get("/api/docs/openapi.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Vastio API"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/proveedores']").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ProveedorResponse").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.BebidaResponse.properties.proveedorHabitual").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.IngresoRequest.properties.proveedorId").doesNotExist());
    }

    @Test
    void swaggerUiEstaEnApiDocs() throws Exception {
        mvc.perform(get("/api/docs"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void cargaUnUsuarioPorPerfilYEventosConSuHistorial() {
        assertThat(usuarios.findByNombreUsuario("lucia.ferreyra")).get()
                .satisfies(u -> assertThat(u.codigosDeRol()).containsExactly(RolCodigo.VENDEDORA))
                .satisfies(u -> assertThat(u.isDebeCambiarContrasena()).isFalse());
        for (RolCodigo rol : RolCodigo.values()) {
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM usuario_rol ur JOIN rol r USING (rol_id) WHERE r.codigo = ?""", Integer.class, rol.name()))
                    .as(rol.name()).isPositive();
        }
        // El volumen depende de la fecha de carga; las etapas y su trazabilidad deben mantenerse.
        var etapas = List.of("PRE_RESERVA", "SENADO", "CONTRATADO", "CONFIRMADO");
        assertThat(jdbc.queryForList("SELECT DISTINCT estado FROM evento", String.class))
                .containsExactlyInAnyOrderElementsOf(etapas);
        var eventos = jdbc.queryForList("SELECT evento_id, estado FROM evento");
        for (var evento : eventos) {
            var id = evento.get("evento_id");
            int etapa = etapas.indexOf(evento.get("estado"));
            var historial = jdbc.queryForList("""
                    SELECT estado_anterior, estado_nuevo, usuario_id FROM cambio_estado_evento
                    WHERE evento_id = ? ORDER BY cambio_id""", id);
            assertThat(historial).as("Historial del evento %s", id).hasSize(etapa + 1);
            for (int i = 0; i <= etapa; i++) {
                assertThat(historial.get(i).get("estado_anterior")).as("Origen del cambio %s del evento %s", i, id)
                        .isEqualTo(i == 0 ? null : etapas.get(i - 1));
                assertThat(historial.get(i).get("estado_nuevo")).as("Destino del cambio %s del evento %s", i, id)
                        .isEqualTo(etapas.get(i));
                assertThat(historial.get(i).get("usuario_id")).as("Autor del cambio %s del evento %s", i, id)
                        .isNotNull();
            }
        }
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT unidad_id FROM evento GROUP BY unidad_id HAVING count(*) > 1
                ) duplicados""", Integer.class)).as("Una reserva por unidad comercializable").isZero();
        // El del objetivo del Sprint 3: confirmado para hoy al mediodía en Avril.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM evento e JOIN unidad_comercializable u USING (unidad_id)
                WHERE e.estado = 'CONFIRMADO' AND u.fecha = ? AND u.salon_id = 1 AND u.turno_id = 1""",
                Integer.class, LocalDate.now(ZoneId.of(VastioApplication.ZONA_HORARIA)))).isEqualTo(1);
    }

    @Test
    void cargaUnCatalogoDeBebidasConSusCodigos() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM codigo_barra c JOIN bebida b USING (bebida_id)
                WHERE b.nombre = 'Fernet Branca' AND b.presentacion = '750 ml' AND b.activo""", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT unidades_por_bulto FROM bebida WHERE nombre = 'Vodka Smirnoff' AND activo""", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT s.cantidad FROM stock_ubicacion s JOIN bebida b USING (bebida_id)
                WHERE b.nombre = 'Fernet Branca' AND b.activo AND s.ubicacion_id = 2""", Integer.class)).isEqualTo(9);
    }
}
