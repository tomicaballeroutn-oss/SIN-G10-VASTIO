package ar.edu.utn.vastio.bebida.api;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Consultar movimientos de stock (UI-37, docs/sprint-4.md, decisiones 26 a 28). Las bebidas son propias de la prueba:
 * filtrar por ellas aísla los asientos de otros datos de la base.
 */
@PruebaDeIntegracion
@Transactional
class MovimientosIT {

    private static final LocalDate HOY = LocalDate.now(ZoneId.of(VastioApplication.ZONA_HORARIA));
    private static final int DEPOSITO = 1;

    @Autowired
    MockMvc mvc;

    @Autowired
    Personas personas;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    Usuario nadir;
    long ron;
    long gin;

    @BeforeEach
    void ingresoYRecuento() throws Exception {
        nadir = personas.usuario("nadir.movimientos", "Nadir Movimientos", RolCodigo.COMPRAS);
        ron = bebida("Ron de movimientos");
        gin = bebida("Gin de movimientos");
        mvc.perform(post("/api/v1/ingresos").header(HttpHeaders.AUTHORIZATION, personas.bearer(nadir))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"fecha":"%s","numeroRemito":"R-1","renglones":[{"bebidaId":%d,"cantidad":24},{"bebidaId":%d,"cantidad":12}]}"""
                                .formatted(HOY, ron, gin)))
                .andExpect(status().isCreated());
        entityManager.flush();
        mvc.perform(post("/api/v1/ajustes/recuento").header(HttpHeaders.AUTHORIZATION, personas.bearer(nadir))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"ubicacionId":%d,"bebidaId":%d,"cantidadContada":22,"motivoId":12}""".formatted(DEPOSITO, ron)))
                .andExpect(status().isOk());
        entityManager.flush();
    }

    @Test
    void lasOperacionesDeUnaBebidaDeLaMasNuevaALaMasVieja() throws Exception {
        consultar("bebidaId=" + ron, RolCodigo.COMPRAS)
                .andExpect(jsonPath("$.operaciones.length()").value(2))
                .andExpect(jsonPath("$.hayMas").value(false))
                .andExpect(jsonPath("$.operaciones[0].tipo").value("AJUSTE"))
                .andExpect(jsonPath("$.operaciones[0].origen.nombre").value("Depósito principal"))
                .andExpect(jsonPath("$.operaciones[0].motivo").isNotEmpty())
                .andExpect(jsonPath("$.operaciones[0].asientos[0].cantidad").value(2.0))
                .andExpect(jsonPath("$.operaciones[1].tipo").value("INGRESO"))
                .andExpect(jsonPath("$.operaciones[1].destino.nombre").value("Depósito principal"))
                .andExpect(jsonPath("$.operaciones[1].usuario.nombre").value("Nadir Movimientos"))
                // Con el filtro de bebida, la operación muestra solo esa bebida.
                .andExpect(jsonPath("$.operaciones[1].asientos.length()").value(1))
                .andExpect(jsonPath("$.operaciones[1].asientos[0].unidadesPorBulto").value(6));
    }

    @Test
    void unIngresoEsUnaSolaOperacionConSusBebidas() throws Exception {
        consultar("tipo=INGRESO&ubicacionId=" + DEPOSITO, RolCodigo.ADMINISTRACION)
                .andExpect(jsonPath("$.operaciones[?(@.usuario.nombre == 'Nadir Movimientos')].asientos.length()").value(hasItem(2)));
    }

    @Test
    void filtraPorFechasYPagina() throws Exception {
        for (int i = 0; i < 21; i++) {
            merma(OffsetDateTime.parse("2030-01-05T10:00:00-03:00").plusMinutes(i));
        }
        merma(OffsetDateTime.parse("2030-02-01T10:00:00-03:00"));

        consultar("bebidaId=%d&desde=2030-01-01&hasta=2030-01-31".formatted(gin), RolCodigo.COMPRAS)
                .andExpect(jsonPath("$.operaciones.length()").value(20))
                .andExpect(jsonPath("$.hayMas").value(true))
                .andExpect(jsonPath("$.operaciones[0].fechaHora").value("2030-01-05T13:20:00Z"));
        consultar("bebidaId=%d&desde=2030-01-01&hasta=2030-01-31&pagina=1".formatted(gin), RolCodigo.COMPRAS)
                .andExpect(jsonPath("$.operaciones.length()").value(1))
                .andExpect(jsonPath("$.hayMas").value(false));
        consultar("bebidaId=%d&desde=2030-02-01&hasta=2030-02-01".formatted(gin), RolCodigo.COMPRAS)
                .andExpect(jsonPath("$.operaciones.length()").value(1))
                .andExpect(jsonPath("$.operaciones[0].tipo").value("MERMA"));
    }

    @Test
    void unRangoAlRevesSeRechaza() throws Exception {
        mvc.perform(get("/api/v1/movimientos?desde=2030-02-01&hasta=2030-01-01")
                        .header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.COMPRAS)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FILTROS_INVALIDOS"));
    }

    @Test
    void losEventosDelFiltroSonLosQueTienenMovimientos() throws Exception {
        mvc.perform(get("/api/v1/movimientos/eventos").header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.COMPRAS)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"BARRA", "VENDEDORA", "PLANNER", "COCINA"})
    void elRestoDeLosPerfilesNoLosVe(RolCodigo rol) throws Exception {
        mvc.perform(get("/api/v1/movimientos").header(HttpHeaders.AUTHORIZATION, personas.bearer(rol)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/movimientos/eventos").header(HttpHeaders.AUTHORIZATION, personas.bearer(rol)))
                .andExpect(status().isForbidden());
    }

    private void merma(OffsetDateTime momento) {
        jdbc.update("""
                INSERT INTO movimiento_stock (tipo, bebida_id, cantidad, ubicacion_origen_id, motivo_id, usuario_id, fecha_hora)
                VALUES ('MERMA', ?, 1, ?, 11, ?, ?)""", gin, DEPOSITO, nadir.getId(), momento);
    }

    private long bebida(String nombre) {
        return jdbc.queryForObject("""
                INSERT INTO bebida (nombre, presentacion, tipo_bebida_id, unidad_manipulacion_id, unidades_por_bulto, activo)
                VALUES (?, '750 ml', 3, 1, 6, true) RETURNING bebida_id""", Long.class, nombre);
    }

    private ResultActions consultar(String filtros, RolCodigo rol) throws Exception {
        return mvc.perform(get("/api/v1/movimientos?" + filtros).header(HttpHeaders.AUTHORIZATION, personas.bearer(rol)))
                .andExpect(status().isOk());
    }
}
