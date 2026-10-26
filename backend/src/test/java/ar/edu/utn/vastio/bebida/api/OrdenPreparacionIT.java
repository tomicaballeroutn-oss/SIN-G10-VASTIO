package ar.edu.utn.vastio.bebida.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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

import ar.edu.utn.vastio.Escenario;
import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Generar orden de preparación (UI-35, docs/sprint-4.md, decisiones 11 a 22). Cada prueba usa un tipo de evento propio,
 * así el consumo histórico no se mezcla con otros datos de la base de pruebas.
 */
@PruebaDeIntegracion
@Transactional
class OrdenPreparacionIT {

    private static final LocalDate HOY = LocalDate.now(ZoneId.of(VastioApplication.ZONA_HORARIA));

    @Autowired
    MockMvc mvc;

    @Autowired
    Personas personas;

    @Autowired
    Escenario escenario;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    Usuario lucia;
    Usuario ana;
    Usuario compras;
    short tipo;
    long fernet;
    long vino;

    @BeforeEach
    void preparar() {
        lucia = personas.usuario("lucia.orden", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        ana = personas.usuario("ana.orden", "Ana Sosa", RolCodigo.PLANNER);
        compras = personas.usuario("tomas.orden", "Tomás Caballero", RolCodigo.COMPRAS);
        tipo = jdbc.queryForObject("""
                INSERT INTO tipo_evento (nombre, usa_segmentos, activo) VALUES ('Orden de prueba', false, true)
                RETURNING tipo_evento_id""", Short.class);
        fernet = bebida("Fernet orden", 6);
        vino = bebida("Vino orden", 1);
    }

    @Test
    void conTresEventosCerradosDelTipoSugiereSobreSuConsumoEnCajasCompletas() throws Exception {
        // 3 eventos de 100 invitados: 10 botellas de fernet y 5 de vino cada uno → 0,1 y 0,05 por asistente.
        for (int i = 1; i <= 3; i++) {
            long cerrado = evento(HOY.minusMonths(i), "CERRADO", 100);
            consumo(cerrado, fernet, 10);
            consumo(cerrado, vino, 5);
        }
        long evento = evento(HOY.plusDays(20), "CONFIRMADO", 150);

        generar(compras, evento)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orden.estado").value("SUGERIDA"))
                .andExpect(jsonPath("$.orden.fuente").value("HISTORICO_TIPO"))
                .andExpect(jsonPath("$.orden.eventosBase").value(3))
                .andExpect(jsonPath("$.orden.asistentesBase").value(150))
                .andExpect(jsonPath("$.orden.margenPorcentaje").value(10))
                .andExpect(jsonPath("$.orden.origen").value("Depósito principal"))
                .andExpect(jsonPath("$.orden.responsable.nombre").value("Tomás Caballero"))
                // 0,1 × 150 × 1,1 = 16,5 botellas → 3 cajas de 6.
                .andExpect(jsonPath("$.orden.renglones[0].nombre").value("Fernet orden"))
                .andExpect(jsonPath("$.orden.renglones[0].sugerida").value(18))
                .andExpect(jsonPath("$.orden.renglones[0].aRetirar").value(18))
                // 0,05 × 150 × 1,1 = 8,25 → 9 botellas (de a una).
                .andExpect(jsonPath("$.orden.renglones[1].sugerida").value(9))
                .andExpect(jsonPath("$.acciones.generar").value(false))
                .andExpect(jsonPath("$.acciones.recalcular").value(true));
    }

    @Test
    void conPocosEventosDelTipoUsaLasProporcionesCargadas() throws Exception {
        consumo(evento(HOY.minusMonths(1), "CERRADO", 100), fernet, 50);
        long evento = evento(HOY.plusDays(20), "CONFIRMADO", 200);
        proporcion(fernet, "0.0500").andExpect(status().isOk());
        jdbc.update("UPDATE parametro SET valor = '0' WHERE clave = 'MARGEN_ORDEN_PORCENTAJE'");

        generar(compras, evento)
                .andExpect(jsonPath("$.orden.fuente").value("PROPORCIONES"))
                .andExpect(jsonPath("$.orden.eventosBase").value(0))
                .andExpect(jsonPath("$.orden.renglones.length()").value(1))
                // 0,05 × 200 = 10 botellas → 2 cajas de 6.
                .andExpect(jsonPath("$.orden.renglones[0].aRetirar").value(12));
    }

    @Test
    void sinNadaDelTipoUsaElHistoricoGeneral() throws Exception {
        consumo(evento(HOY.minusMonths(1), "CERRADO", 100), fernet, 10);
        jdbc.update("UPDATE evento SET tipo_evento_id = 2 WHERE tipo_evento_id = ?", tipo);
        long evento = evento(HOY.plusDays(20), "CONFIRMADO", 100);

        generar(compras, evento)
                .andExpect(jsonPath("$.orden.fuente").value("HISTORICO_GENERAL"))
                .andExpect(jsonPath("$.orden.renglones[?(@.bebidaId == %d)].sugerida".formatted(fernet)).value(hasItem(12)));
    }

    @Test
    void confirmarElEventoGeneraLaOrdenYAvisaACompras() throws Exception {
        long evento = evento(HOY.plusDays(30), "CONTRATADO", 150);
        jdbc.update("UPDATE evento SET planner_id = ?, invitados_definitivos = true WHERE evento_id = ?", ana.getId(), evento);
        jdbc.update("""
                INSERT INTO servicio_contratado (evento_id, categoria_id, descripcion, usuario_id)
                SELECT ?, categoria_id, 'Cargado', ? FROM categoria_servicio WHERE requerida_para_confirmar AND activo""",
                evento, ana.getId());

        mvc.perform(post("/api/v1/eventos/{id}/confirmacion", evento).header(HttpHeaders.AUTHORIZATION, personas.bearer(ana)))
                .andExpect(status().isOk());
        entityManager.flush();

        consultar(compras, evento)
                .andExpect(jsonPath("$.orden.estado").value("SUGERIDA"))
                .andExpect(jsonPath("$.orden.responsable").value(nullValue()))
                .andExpect(jsonPath("$.orden.asistentesBase").value(150));
        assertThat(jdbc.queryForList("""
                SELECT n.mensaje FROM notificacion_destinatario d JOIN notificacion n USING (notificacion_id)
                WHERE n.evento_id = ? AND n.tipo = 'ORDEN_PREPARACION' AND d.usuario_id = ?""", String.class, evento, compras.getId()))
                .singleElement().asString().startsWith("Orden de preparación sugerida: ");
    }

    @Test
    void enEgresadosCalculaSobreElTotalPrevisto() throws Exception {
        for (int i = 1; i <= 3; i++) {
            consumo(evento(HOY.minusMonths(i), "CERRADO", 100), vino, 10);
        }
        jdbc.update("UPDATE tipo_evento SET usa_segmentos = true WHERE tipo_evento_id = ?", tipo);
        long evento = evento(HOY.plusDays(20), "CONFIRMADO", 140);
        jdbc.update("INSERT INTO asistencia_segmento (evento_id, tipo_segmento_id, cantidad_prevista) VALUES (?, 2, 60)", evento);
        jdbc.update("UPDATE parametro SET valor = '0' WHERE clave = 'MARGEN_ORDEN_PORCENTAJE'");

        generar(compras, evento)
                .andExpect(jsonPath("$.orden.asistentesBase").value(200))
                .andExpect(jsonPath("$.orden.renglones[0].aRetirar").value(20));
    }

    @Test
    void sinHistoriaNiProporcionesSaleVacia() throws Exception {
        long evento = evento(HOY.plusDays(20), "CONFIRMADO", 150);
        // Ningún cerrado en toda la base: se apartan los de otras pruebas.
        jdbc.update("UPDATE evento SET estado = 'REALIZADO' WHERE estado = 'CERRADO'");

        generar(compras, evento)
                .andExpect(jsonPath("$.orden.fuente").value("SIN_DATOS"))
                .andExpect(jsonPath("$.orden.renglones").isEmpty());
    }

    @Test
    void siCambianLosAsistentesLoAvisaYRecalcula() throws Exception {
        for (int i = 1; i <= 3; i++) {
            consumo(evento(HOY.minusMonths(i), "CERRADO", 100), fernet, 10);
        }
        long evento = evento(HOY.plusDays(20), "CONFIRMADO", 150);
        int version = version(generar(compras, evento));
        jdbc.update("UPDATE evento SET cantidad_invitados = 300 WHERE evento_id = ?", evento);
        entityManager.clear();

        consultar(compras, evento).andExpect(jsonPath("$.asistentesCambiaron").value(true));
        recalcular(compras, evento, version + 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ORDEN_MODIFICADA"));
        recalcular(compras, evento, version)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asistentesCambiaron").value(false))
                .andExpect(jsonPath("$.orden.asistentesBase").value(300))
                .andExpect(jsonPath("$.orden.modificacion.usuario.nombre").value("Tomás Caballero"))
                // 0,1 × 300 × 1,1 = 33 → 6 cajas.
                .andExpect(jsonPath("$.orden.renglones[0].aRetirar").value(36));
    }

    @Test
    void lasOrdenesDeOtrosEventosProximosCuentanComoComprometido() throws Exception {
        for (int i = 1; i <= 3; i++) {
            consumo(evento(HOY.minusMonths(i), "CERRADO", 100), fernet, 10);
        }
        long cercano = evento(HOY.plusDays(5), "CONFIRMADO", 150);
        long lejano = evento(HOY.plusDays(40), "CONFIRMADO", 150);
        long evento = evento(HOY.plusDays(12), "CONFIRMADO", 150);
        generar(compras, cercano).andExpect(status().isCreated());
        generar(compras, lejano).andExpect(status().isCreated());

        generar(compras, evento)
                .andExpect(jsonPath("$.orden.renglones[0].comprometido").value(18));
    }

    @Test
    void unaOrdenVigentePorEventoYLaCancelacionLaDescarta() throws Exception {
        long evento = evento(HOY.plusDays(20), "CONFIRMADO", 150);
        generar(compras, evento).andExpect(status().isCreated());
        generar(compras, evento)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ORDEN_EXISTENTE"));

        mvc.perform(post("/api/v1/eventos/{id}/cancelacion", evento).header(HttpHeaders.AUTHORIZATION,
                        personas.bearer(RolCodigo.COORDINACION))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"motivoId":1,"detalle":"Se suspendió"}"""))
                .andExpect(status().isOk());
        entityManager.flush();

        consultar(compras, evento)
                .andExpect(jsonPath("$.orden.estado").value("DESCARTADA"))
                .andExpect(jsonPath("$.acciones.generar").value(false));
    }

    @Test
    void soloSeGeneraConElEventoConfirmadoOEnCurso() throws Exception {
        long evento = evento(HOY.plusDays(20), "CONTRATADO", 150);

        generar(compras, evento)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ESTADO_NO_PERMITE"));
    }

    @Test
    void laListaMuestraLosConfirmadosConElEstadoDeSuOrden() throws Exception {
        long conOrden = evento(HOY.plusDays(6), "CONFIRMADO", 150);
        long sinOrden = evento(HOY.plusDays(7), "CONFIRMADO", 150);
        generar(compras, conOrden).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/ordenes-preparacion").header(HttpHeaders.AUTHORIZATION, personas.bearer(compras)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.evento.id == %d)].estado".formatted(conOrden)).value(hasItem("SUGERIDA")))
                .andExpect(jsonPath("$[?(@.evento.id == %d)].estado".formatted(sinOrden)).value(hasItem(nullValue())));
    }

    @Test
    void administracionConsultaPeroNoGenera() throws Exception {
        long evento = evento(HOY.plusDays(20), "CONFIRMADO", 150);
        Usuario administracion = personas.de(RolCodigo.ADMINISTRACION);

        generar(administracion, evento).andExpect(status().isForbidden());
        consultar(administracion, evento)
                .andExpect(jsonPath("$.orden").value(nullValue()))
                .andExpect(jsonPath("$.acciones.generar").value(false));
        generar(personas.de(RolCodigo.COORDINACION), evento).andExpect(status().isCreated());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"VENDEDORA", "PLANNER", "BARRA", "COCINA"})
    void elRestoDeLosPerfilesNoVeLaOrden(RolCodigo rol) throws Exception {
        long evento = evento(HOY.plusDays(20), "CONFIRMADO", 150);

        mvc.perform(get("/api/v1/eventos/{id}/orden-preparacion", evento).header(HttpHeaders.AUTHORIZATION, personas.bearer(rol)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/ordenes-preparacion").header(HttpHeaders.AUTHORIZATION, personas.bearer(rol)))
                .andExpect(status().isForbidden());
    }

    @Test
    void lasProporcionesSeAgreganCambianYQuitan() throws Exception {
        proporcion(fernet, "0.0500").andExpect(status().isOk()).andExpect(jsonPath("$.botellasPorAsistente").value(0.05));
        proporcion(fernet, "0.0800").andExpect(status().isOk());
        String lista = mvc.perform(get("/api/v1/proporciones-consumo?tipoEventoId={t}", tipo)
                        .header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.ADMINISTRACION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].botellasPorAsistente").value(0.08))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(lista, "$[0].id")).longValue();

        mvc.perform(delete("/api/v1/proporciones-consumo/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.ADMINISTRACION)))
                .andExpect(status().isNoContent());
        proporcion(fernet, "0").andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/proporciones-consumo").header(HttpHeaders.AUTHORIZATION, personas.bearer(compras))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"tipoEventoId":%d,"bebidaId":%d,"botellasPorAsistente":0.05}""".formatted(tipo, fernet)))
                .andExpect(status().isForbidden());
    }

    private long evento(LocalDate fecha, String estado, int invitados) {
        String salon = new String[] {"avril", "club", "santa-barbara"}[(int) (Math.abs(fecha.toEpochDay()) % 3)];
        long id = escenario.evento(salon, fecha, "mediodia", estado, lucia, ana, "Evento " + fecha);
        jdbc.update("UPDATE evento SET tipo_evento_id = ?, cantidad_invitados = ? WHERE evento_id = ?", tipo, invitados, id);
        return id;
    }

    private long bebida(String nombre, int porBulto) {
        return jdbc.queryForObject("""
                INSERT INTO bebida (nombre, presentacion, tipo_bebida_id, unidad_manipulacion_id, unidades_por_bulto, activo)
                VALUES (?, '750 ml', 3, ?, ?, true) RETURNING bebida_id""", Long.class, nombre, porBulto == 1 ? 3 : 1, porBulto);
    }

    private void consumo(long evento, long bebida, int botellas) {
        jdbc.update("""
                INSERT INTO consumo_evento (evento_id, bebida_id, cantidad_entregada, cantidad_devuelta, cantidad_remanente, cantidad_consumida)
                VALUES (?, ?, ?, 0, 0, ?)""", evento, bebida, botellas, botellas);
    }

    private ResultActions proporcion(long bebida, String botellas) throws Exception {
        return mvc.perform(put("/api/v1/proporciones-consumo").header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.ADMINISTRACION))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"tipoEventoId":%d,"bebidaId":%d,"botellasPorAsistente":%s}""".formatted(tipo, bebida, botellas)));
    }

    private ResultActions consultar(Usuario quien, long evento) throws Exception {
        return mvc.perform(get("/api/v1/eventos/{id}/orden-preparacion", evento).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)))
                .andExpect(status().isOk());
    }

    private ResultActions generar(Usuario quien, long evento) throws Exception {
        ResultActions r = mvc.perform(post("/api/v1/eventos/{id}/orden-preparacion", evento)
                .header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)));
        entityManager.flush();
        return r;
    }

    private ResultActions recalcular(Usuario quien, long evento, int version) throws Exception {
        ResultActions r = mvc.perform(post("/api/v1/eventos/{id}/orden-preparacion/recalculo", evento)
                .header(HttpHeaders.AUTHORIZATION, personas.bearer(quien))
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":%d}".formatted(version)));
        entityManager.flush();
        return r;
    }

    private static int version(ResultActions r) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.orden.version");
    }
}
