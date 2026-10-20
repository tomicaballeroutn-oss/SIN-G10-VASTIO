package ar.edu.utn.vastio.bebida.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import ar.edu.utn.vastio.*;
import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.bebida.aplicacion.*;
import ar.edu.utn.vastio.bebida.aplicacion.BebidaService.DatosBebida;
import ar.edu.utn.vastio.bebida.aplicacion.EntregaService.DatosRetiro;
import ar.edu.utn.vastio.bebida.aplicacion.EntregaService.DatosDevolucion;
import ar.edu.utn.vastio.bebida.aplicacion.EntregaService.Renglon;
import ar.edu.utn.vastio.bebida.aplicacion.EntregaService.RenglonDevolucion;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;

@PruebaDeIntegracion
@Transactional
class PreparacionIT {
    @Autowired MockMvc mvc;
    @Autowired Personas personas;
    @Autowired Escenario escenario;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired BebidaService bebidas;
    @Autowired EntregaService entregas;
    @Autowired CierreService cierres;
    @Autowired ConsultaConsumoService consultas;
    long evento, bebida, otra;
    short destino;
    @BeforeEach void datos() {
        evento = escenario.evento("avril", LocalDate.of(2035, 2, 6), "noche", "CONFIRMADO", personas.de(RolCodigo.VENDEDORA), personas.de(RolCodigo.PLANNER), "Preparación IT");
        bebida = bebidas.crear(new DatosBebida("Ron preparación", "750 ml", (short) 3, (short) 1, (short) 6, null,
                List.of(new BebidaService.Codigo("9988776655441", (short) 6)), null)).getId();
        otra = bebidas.crear(new DatosBebida("Gin preparación", "750 ml", (short) 3, (short) 1, (short) 6, null, List.of(), null)).getId();
        em.flush();
        destino = jdbc.queryForObject("INSERT INTO ubicacion(nombre,tipo,activo) VALUES ('Transición IT','TRANSICION',true) RETURNING ubicacion_id", Short.class);
        jdbc.update("UPDATE ubicacion SET ubicacion_abastecimiento_id=? WHERE ubicacion_id=2", destino);
        jdbc.update("INSERT INTO stock_ubicacion(ubicacion_id,bebida_id,cantidad,fecha_actualizacion) VALUES (1,?,30,now())", bebida);
        em.clear();
    }
    String cuerpo(int barra, int destino, String renglones) { return "{\"barraId\":%d,\"destinoId\":%d,\"renglones\":[%s]}".formatted(barra, destino, renglones); }
    String renglon(long b, String n) { return "{\"bebidaId\":%d,\"cantidad\":%s}".formatted(b, n); }
    String valido() { return cuerpo(2, destino, renglon(bebida, "12")); }
    ResultActions registrar(String clave, String cuerpo, RolCodigo rol) throws Exception {
        return mvc.perform(post("/api/v1/eventos/{id}/preparaciones", evento).header("Authorization", personas.bearer(rol))
                .header("Idempotency-Key", clave).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }
    ResultActions registrar() throws Exception { return registrar(UUID.randomUUID().toString(), valido(), RolCodigo.COMPRAS); }
    int cuenta(String tabla) { em.flush(); return jdbc.queryForObject("SELECT count(*) FROM " + tabla + " WHERE evento_id=?", Integer.class, evento); }
    void sinEfectos() { assertThat(cuenta("preparacion_evento")).isZero(); assertThat(cuenta("movimiento_stock")).isZero(); }
    void estado(String s) {
        em.flush();
        jdbc.update("UPDATE evento SET estado=?, motivo_cancelacion_id=CASE WHEN ?='CANCELADO' THEN 1 ELSE NULL END WHERE evento_id=?", s, s, evento);
        em.clear();
    }

    @ParameterizedTest @EnumSource(value=RolCodigo.class, names={"COMPRAS","ADMINISTRACION","COORDINACION","DIRECCION"})
    void permitidos(RolCodigo rol) throws Exception {
        registrar("clave", valido(), rol).andExpect(status().isCreated()).andExpect(jsonPath("$.eventoId").value(evento))
                .andExpect(jsonPath("$.origen.id").value(1)).andExpect(jsonPath("$.destino.id").value((int) destino))
                .andExpect(jsonPath("$.usuario.nombre").isNotEmpty()).andExpect(jsonPath("$.fechaHora").isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT cantidad FROM stock_ubicacion WHERE ubicacion_id=1 AND bebida_id=?", BigDecimal.class, bebida)).isEqualByComparingTo("18");
        assertThat(jdbc.queryForObject("SELECT cantidad FROM stock_ubicacion WHERE ubicacion_id=? AND bebida_id=?", BigDecimal.class, destino, bebida)).isEqualByComparingTo("12");
        assertThat(jdbc.queryForList("SELECT tipo FROM movimiento_stock WHERE evento_id=?", String.class, evento)).containsExactly("PREPARACION");
    }
    @ParameterizedTest @EnumSource(value=RolCodigo.class, names={"VENDEDORA","PLANNER","BARRA","COCINA"})
    void prohibidos(RolCodigo rol) throws Exception {
        registrar("clave", valido(), rol).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/eventos/{id}/preparaciones",evento).header("Authorization", personas.bearer(rol))).andExpect(status().isForbidden());
        sinEfectos();
    }
    @Test void sinSesion() throws Exception {
        mvc.perform(get("/api/v1/eventos/{id}/preparaciones",evento)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/eventos/{id}/preparaciones",evento).contentType(MediaType.APPLICATION_JSON).content(valido())).andExpect(status().isUnauthorized());
    }
    @ParameterizedTest @EnumSource(value=EstadoEvento.class, names={"CONFIRMADO","EN_CURSO"})
    void estadosPermitidos(EstadoEvento e) throws Exception { estado(e.name()); registrar().andExpect(status().isCreated()); }
    @ParameterizedTest @EnumSource(value=EstadoEvento.class, names={"CONFIRMADO","EN_CURSO"}, mode=EnumSource.Mode.EXCLUDE)
    void estadosRechazados(EstadoEvento e) throws Exception {
        estado(e.name()); registrar().andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ESTADO_NO_PERMITE")); sinEfectos();
    }
    @Test void inexistente() throws Exception { evento = 99999999; registrar().andExpect(status().isNotFound()); }
    @Test void variasPreparacionesEHistorial() throws Exception {
        registrar().andExpect(status().isCreated()); registrar().andExpect(status().isCreated());
        assertThat(cuenta("preparacion_evento")).isEqualTo(2);
        mvc.perform(get("/api/v1/eventos/{id}/preparaciones",evento).header("Authorization", personas.bearer(RolCodigo.ADMINISTRACION)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.historial.length()").value(2)).andExpect(jsonPath("$.circuitos[0].requierePreparacion").value(true));
    }
    @Test void reintentoInclusoCerradoYCatalogoInactivo() throws Exception {
        registrar("clave",valido(),RolCodigo.COMPRAS).andExpect(status().isCreated());
        estado("CERRADO"); jdbc.update("UPDATE bebida SET activo=false WHERE bebida_id=?", bebida); em.clear();
        registrar("clave",valido(),RolCodigo.COMPRAS).andExpect(status().isOk());
        assertThat(cuenta("preparacion_evento")).isOne(); assertThat(cuenta("movimiento_stock")).isOne();
    }
    @Test void mismaClaveOtraCargaEsConflicto() throws Exception {
        registrar("clave",valido(),RolCodigo.COMPRAS).andExpect(status().isCreated());
        registrar("clave",cuerpo(2,destino,renglon(bebida,"13")),RolCodigo.COMPRAS).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CLAVE_REUTILIZADA"));
        assertThat(cuenta("movimiento_stock")).isOne();
    }
    @Test void ordenDeRenglonesNoDuplica() throws Exception {
        registrar("clave",cuerpo(2,destino,renglon(bebida,"12")+","+renglon(otra,"2")),RolCodigo.COMPRAS).andExpect(status().isCreated());
        registrar("clave",cuerpo(2,destino,renglon(otra,"2.00")+","+renglon(bebida,"12.00")),RolCodigo.COMPRAS).andExpect(status().isOk());
        assertThat(cuenta("movimiento_stock")).isEqualTo(2);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","1.5","100000000","null"})
    void cantidadesInvalidas(String n) throws Exception {
        registrar("clave",cuerpo(2,destino,renglon(bebida,n)),RolCodigo.COMPRAS).andExpect(status().is4xxClientError()); sinEfectos();
    }
    @Test void repetida() throws Exception {
        registrar("clave",cuerpo(2,destino,renglon(bebida,"1")+","+renglon(bebida,"2")),RolCodigo.COMPRAS).andExpect(status().isUnprocessableContent()); sinEfectos();
    }
    @Test void todosLosRenglonesSeValidanAntesDeMover() throws Exception {
        registrar("clave",cuerpo(2,destino,renglon(bebida,"1")+","+renglon(999999,"2")),RolCodigo.COMPRAS).andExpect(status().isUnprocessableContent()); sinEfectos();
    }
    @Test void inactiva() throws Exception {
        jdbc.update("UPDATE bebida SET activo=false WHERE bebida_id=?",bebida); em.clear(); registrar().andExpect(status().isUnprocessableContent()); sinEfectos();
    }
    @Test void destinoIncorrecto() throws Exception {
        registrar("clave",cuerpo(2,3,renglon(bebida,"1")),RolCodigo.COMPRAS).andExpect(status().isConflict()); sinEfectos();
    }
    @Test void destinoInactivo() throws Exception {
        jdbc.update("UPDATE ubicacion SET activo=false WHERE ubicacion_id=?",destino); em.clear(); registrar().andExpect(status().isConflict()); sinEfectos();
    }
    @Test void otraBarraDelSalon() throws Exception {
        short segunda = jdbc.queryForObject("INSERT INTO ubicacion(nombre,tipo,salon_id,ubicacion_abastecimiento_id,activo) VALUES ('Segunda IT','BARRA',1,?,true) RETURNING ubicacion_id", Short.class,destino);
        registrar("clave",cuerpo(segunda,destino,renglon(bebida,"1")),RolCodigo.COMPRAS).andExpect(status().isCreated()).andExpect(jsonPath("$.barra.id").value((int)segunda));
    }
    @Test void barraAjena() throws Exception {
        registrar("clave",cuerpo(3,destino,renglon(bebida,"1")),RolCodigo.COMPRAS).andExpect(status().isUnprocessableContent()); sinEfectos();
    }
    @Test void barraInactiva() throws Exception {
        jdbc.update("UPDATE ubicacion SET activo=false WHERE ubicacion_id=2"); em.clear(); registrar().andExpect(status().isUnprocessableContent()); sinEfectos();
    }
    @Test void abastecimientoDirecto() throws Exception {
        jdbc.update("UPDATE ubicacion SET ubicacion_abastecimiento_id=1 WHERE ubicacion_id=2"); em.clear();
        registrar().andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.codigo").value("NO_REQUIERE_PREPARACION")); sinEfectos();
    }
    @Test void negativoYAlertaUnica() throws Exception {
        personas.de(RolCodigo.ADMINISTRACION); personas.de(RolCodigo.COMPRAS); em.flush();
        String c=cuerpo(2,destino,renglon(bebida,"40"));
        int antes=jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE tipo='ALERTA_STOCK'",Integer.class);
        registrar("clave",c,RolCodigo.COMPRAS).andExpect(status().isCreated()); registrar("clave",c,RolCodigo.COMPRAS).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT cantidad FROM stock_ubicacion WHERE ubicacion_id=1 AND bebida_id=?",BigDecimal.class,bebida)).isEqualByComparingTo("-10");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE tipo='ALERTA_STOCK'",Integer.class)).isEqualTo(antes+1);
    }
    @Test void claveObligatoria() throws Exception {
        mvc.perform(post("/api/v1/eventos/{id}/preparaciones",evento).header("Authorization",personas.bearer(RolCodigo.COMPRAS))
                .contentType(MediaType.APPLICATION_JSON).content(valido())).andExpect(status().isBadRequest()); sinEfectos();
    }
    @ParameterizedTest @ValueSource(strings={"UPDATE preparacion_evento SET clave='otra' WHERE evento_id=?", "DELETE FROM preparacion_evento WHERE evento_id=?",
        "UPDATE movimiento_stock SET cantidad=1 WHERE evento_id=?", "DELETE FROM movimiento_stock WHERE evento_id=?"})
    void inmutableEnLaBase(String sql) throws Exception {
        registrar().andExpect(status().isCreated()); em.flush();
        assertThatThrownBy(() -> jdbc.update(sql,evento)).hasMessageContaining("solo inserción");
    }
    @Test void auditoriaConsistente() throws Exception {
        registrar().andExpect(status().isCreated()); em.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM preparacion_evento p JOIN movimiento_stock m USING(preparacion_id) WHERE p.evento_id=? AND m.evento_id=p.evento_id AND m.usuario_id=p.usuario_id AND m.fecha_hora=p.fecha_hora AND m.entrega_id IS NULL AND m.ingreso_id IS NULL",Integer.class,evento)).isOne();
    }
    @Test void prepararNoConstituyeConsumo() throws Exception {
        registrar().andExpect(status().isCreated()); estado("EN_CURSO");
        UsuarioActual quien = new UsuarioActual(personas.de(RolCodigo.COMPRAS).getId(),java.util.Set.of("COMPRAS"));
        entregas.retirar(evento,(short)2,new DatosRetiro(false,List.of(new Renglon(bebida,new BigDecimal("6")))),quien);
        entregas.devolver(evento,(short)2,new DatosDevolucion(List.of(new RenglonDevolucion(bebida,new BigDecimal("2"),BigDecimal.ONE)),List.of(new Renglon(bebida,new BigDecimal("6")))),quien);
        em.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movimiento_stock WHERE evento_id=? AND tipo='CONSUMO'",Integer.class,evento)).isOne();
        estado("REALIZADO"); cierres.cerrar(evento,quien); em.flush();
        assertThat(jdbc.queryForObject("SELECT cantidad_consumida FROM consumo_evento WHERE evento_id=? AND bebida_id=?",BigDecimal.class,evento,bebida)).isEqualByComparingTo("3");
        assertThat(cuenta("preparacion_evento")).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movimiento_stock WHERE evento_id=? AND tipo='CONSUMO'",Integer.class,evento)).isOne();
        var saldosAntes = jdbc.queryForList("SELECT * FROM stock_ubicacion WHERE bebida_id=? ORDER BY ubicacion_id",bebida);
        assertThat(consultas.consultar(evento).consumoTotal()).isEqualByComparingTo("3");
        assertThat(jdbc.queryForList("SELECT * FROM stock_ubicacion WHERE bebida_id=? ORDER BY ubicacion_id",bebida)).isEqualTo(saldosAntes);
    }
}
