package ar.edu.utn.vastio.agenda.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ar.edu.utn.vastio.*;
import ar.edu.utn.vastio.agenda.aplicacion.*;
import ar.edu.utn.vastio.agenda.aplicacion.ClienteCalendar.*;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.infraestructura.EventoRepository;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.notificaciones.aplicacion.NotificacionService;
import ar.edu.utn.vastio.usuarios.dominio.*;

/** Transacciones reales e independientes. Google se sustituye: nunca hay red ni credenciales. */
@PruebaDeIntegracion
@TestPropertySource(properties={"vastio.calendar.habilitado=true", "vastio.calendar.calendar-id=calendar-prueba",
    "vastio.calendar.instalacion=m06-it", "vastio.calendar.credenciales=no-se-utiliza.json"})
class CalendarIT {
    @MockitoBean ClienteCalendar google;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    ar.edu.utn.vastio.agenda.infraestructura.PublicacionCalendarRepository publicaciones;
    @Autowired ExportacionCalendarService servicio;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean SeguimientoCalendar seguimiento;
    @Autowired ProcesadorSeguimientoCalendar procesador;
    @Autowired PreReservaService preReservas;
    @Autowired SenaService senas;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired CatalogoService catalogos;
    @Autowired EventoRepository eventos;
    @Autowired ReprogramacionService reprogramaciones;
    @Autowired CancelacionService cancelaciones;
    @Autowired ApplicationEventPublisher cambios;
    @Autowired NotificacionService avisos;
    @Autowired MockMvc mvc;
    @Autowired Personas personas;
    @Autowired Escenario escenario;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    final List<Long> creados=new ArrayList<>();
    final Map<String,Remoto> remotos=new ConcurrentHashMap<>();
    final AtomicInteger numero=new AtomicInteger();
    Usuario direccion, coordinacion, vendedora, planner;
    UsuarioActual quien;
    static final AtomicInteger FECHA=new AtomicInteger();

    @BeforeEach void preparar() {
        direccion=personas.de(RolCodigo.DIRECCION); coordinacion=personas.de(RolCodigo.COORDINACION);
        vendedora=personas.de(RolCodigo.VENDEDORA); planner=personas.de(RolCodigo.PLANNER);
        quien=new UsuarioActual(direccion.getId(),Set.of("DIRECCION"));
        remotos.clear(); numero.set(0);
        when(google.configurado()).thenReturn(true);
        when(google.consultar(anyString(),anyString())).thenAnswer(a->remotos.get(a.getArgument(1)));
        doAnswer(a->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class).getHikariPoolMXBean().getActiveConnections()).isZero();
            var c=(ContenidoCalendar.Contenido)a.getArgument(2);
            var r=new Remoto("etag-"+numero.incrementAndGet(),c.huella(),false);
            remotos.put(a.getArgument(1),r); return r;
        }).when(google).crear(anyString(),anyString(),any());
        when(google.actualizar(anyString(),anyString(),any(),anyString())).thenAnswer(a->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var c=(ContenidoCalendar.Contenido)a.getArgument(2);
            var r=new Remoto("etag-"+numero.incrementAndGet(),c.huella(),false);
            remotos.put(a.getArgument(1),r); return r;
        });
        doAnswer(a->{remotos.remove(a.getArgument(1));return null;}).when(google).retirar(anyString(),anyString(),anyString());
    }
    long evento(String estado) {
        long id=escenario.evento("avril",LocalDate.of(2034,1,1).plusDays(FECHA.incrementAndGet()),"noche",estado,vendedora,planner,"Privado M06");
        creados.add(id); procesador.procesar(id); return id;
    }
    @AfterEach void limpiar() {
        tx.executeWithoutResult(t->{
            jdbc.execute("SET LOCAL session_replication_role = replica");
            for(long id:creados) {
                jdbc.update("DELETE FROM notificacion_destinatario WHERE notificacion_id IN (SELECT notificacion_id FROM notificacion WHERE evento_id=?)",id);
                for(String tabla:List.of("notificacion","operacion_calendar","publicacion_calendar","calendar_seguimiento","reprogramacion","cambio_estado_evento","modificacion_evento")) jdbc.update("DELETE FROM "+tabla+" WHERE evento_id=?",id);
                jdbc.update("DELETE FROM evento WHERE evento_id=?",id);
            }
        });
    }

    @ParameterizedTest @ValueSource(strings={"SENADO","CONTRATADO","CONFIRMADO"})
    void creaUnaSolaPublicacionYRepetirNoLlamaAGoogle(String estado) {
        long id=evento(estado);
        assertThat(servicio.estado(id,quien).pendiente()).isEqualTo("EXPORTAR");
        assertThat(servicio.operar(id,false,quien).estado()).isEqualTo("EXPORTADO");
        servicio.operar(id,false,quien);
        verify(google,times(1)).crear(anyString(),anyString(),any());
        verify(google,times(1)).consultar(anyString(),anyString());
        assertThat(servicio.pendientes(quien)).noneMatch(e->e.eventoId()==id);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE evento_id=? AND tipo='CALENDAR_PENDIENTE' AND NOT resuelta",Integer.class,id)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"PRE_RESERVA","LIBERADA","CANCELADO","EN_CURSO","REALIZADO","CERRADO"})
    void estadosQueNoPuedenCrear(String estado) {
        long id=evento(estado);
        assertThatThrownBy(()->servicio.operar(id,false,quien)).hasMessageContaining("estado del evento");
        verify(google,never()).crear(anyString(),anyString(),any());
    }
    @ParameterizedTest @ValueSource(strings={"EN_CURSO","REALIZADO","CERRADO"})
    void unaPublicacionPuedeConservarseAlAvanzar(String estado) {
        long id=evento("CONFIRMADO"); servicio.operar(id,false,quien);
        jdbc.update("UPDATE evento SET estado=? WHERE evento_id=?",estado,id);
        assertThat(servicio.estado(id,quien).pendiente()).isEqualTo("NINGUNA");
        assertThat(servicio.estado(id,quien).estado()).isEqualTo("EXPORTADO");
    }
    @Test void reprogramarActualizaYCancelarRetiraManualmente() {
        long id=evento("CONTRATADO"); servicio.operar(id,false,quien);
        reprogramaciones.reprogramar(id,new ReprogramacionService.Pedido((short)2,LocalDate.of(2038,12,31),(short)2,(short)4,null),quien);
        assertThat(servicio.estado(id,quien).pendiente()).isEqualTo("ACTUALIZAR");
        verify(google,never()).actualizar(anyString(),anyString(),any(),anyString());
        servicio.operar(id,false,quien);
        verify(google).actualizar(anyString(),anyString(),any(),anyString());
        cancelaciones.cancelar(id,(short)1,null,quien);
        assertThat(servicio.estado(id,quien).pendiente()).isEqualTo("RETIRAR");
        verify(google,never()).retirar(anyString(),anyString(),anyString());
        assertThat(servicio.operar(id,true,quien).estado()).isEqualTo("RETIRADO");
        assertThat(jdbc.queryForObject("SELECT retiro_solicitado FROM publicacion_calendar WHERE evento_id=?",Boolean.class,id)).isFalse();
        servicio.operar(id,true,quien);
        verify(google,times(1)).retirar(anyString(),anyString(),anyString());
    }
    @Test void timeoutTrasCreacionRecuperaLaMismaIdentidad() {
        long id=evento("SENADO");
        doAnswer(a->{
            remotos.put(a.getArgument(1),new Remoto("creado",((ContenidoCalendar.Contenido)a.getArgument(2)).huella(),false));
            throw new Fallo("RED_GOOGLE",true);
        }).when(google).crear(anyString(),anyString(),any());
        assertThatThrownBy(()->servicio.operar(id,false,quien)).hasMessageContaining("RED_GOOGLE");
        assertThat(servicio.estado(id,quien).estado()).isEqualTo("INCIERTO");
        doThrow(new Fallo("RED_GOOGLE",false)).when(google).comprobarDestino(anyString());
        assertThatThrownBy(()->servicio.operar(id,false,quien)).hasMessageContaining("RED_GOOGLE");
        doNothing().when(google).comprobarDestino(anyString());
        assertThat(servicio.operar(id,false,quien).estado()).isEqualTo("EXPORTADO");
        verify(google,times(1)).crear(anyString(),eq(ContenidoCalendar.identidad("m06-it",id)),any());
    }
    @Test void operacionAbandonadaSeRecuperaSinCambiarId() {
        long id=evento("SENADO"); servicio.estado(id,quien);
        jdbc.update("UPDATE publicacion_calendar SET estado='EN_PROCESO',calendar_id='calendar-prueba',instalacion='m06-it',google_id=? WHERE evento_id=?",ContenidoCalendar.identidad("m06-it",id),id);
        assertThat(servicio.estado(id,quien).estado()).isEqualTo("EN_PROCESO");
        procesador.procesar(id);
        assertThat(servicio.estado(id,quien).estado()).isEqualTo("INCIERTO");
        assertThat(servicio.operar(id,false,quien).estado()).isEqualTo("EXPORTADO");
    }
    @Test void falloAlGuardarTrasExitoRemotoSeRecuperaSinDuplicar() {
        long id=evento("SENADO"); var fallar=new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(a->{
            if ("CREACION_CONFIRMADA".equals(a.getArgument(1)) && fallar.getAndSet(false)) throw new org.springframework.dao.DataAccessResourceFailureException("fallo local simulado");
            return a.callRealMethod();
        }).when(publicaciones).auditar(eq(id),anyString(),any(),any());
        assertThatThrownBy(()->servicio.operar(id,false,quien)).hasMessageContaining("RESULTADO_NO_PERSISTIDO");
        assertThat(servicio.estado(id,quien).estado()).isEqualTo("INCIERTO");
        assertThat(servicio.operar(id,false,quien).estado()).isEqualTo("EXPORTADO");
        verify(google,times(1)).crear(anyString(),anyString(),any());
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({
        "salon,nombre,nombre || ' prueba'",
        "tipo_evento,nombre,nombre || ' prueba'",
        "turno,nombre,nombre || ' prueba'",
        "turno,hora_inicio,hora_inicio + interval '1 minute'",
        "turno,hora_fin,hora_fin + interval '1 minute'",
        "turno,cruza_medianoche,NOT cruza_medianoche"
    })
    void catalogoSoloEncolaCambiosRealesYNoLlamaAGoogle(String tabla, String campo, String cambio) {
        long id = evento("SENADO");
        tx.executeWithoutResult(t -> {
            String columna = tabla.equals("tipo_evento") ? "tipo_evento_id" : tabla + "_id";
            String origen = tabla.equals("tipo_evento") ? "evento e" : "evento e JOIN unidad_comercializable u USING(unidad_id)";
            int catalogoId = jdbc.queryForObject("SELECT " + columna + " FROM " + origen + " WHERE evento_id=?", Integer.class, id);
            long antes = jdbc.queryForObject("SELECT revision FROM calendar_seguimiento WHERE evento_id=?", Long.class, id);
            jdbc.update("UPDATE " + tabla + " SET " + campo + "=" + campo + " WHERE " + columna + "=?", catalogoId);
            assertThat(jdbc.queryForObject("SELECT revision FROM calendar_seguimiento WHERE evento_id=?", Long.class, id)).isEqualTo(antes);
            jdbc.update("UPDATE " + tabla + " SET " + campo + "=" + cambio + " WHERE " + columna + "=?", catalogoId);
            assertThat(jdbc.queryForObject("SELECT revision FROM calendar_seguimiento WHERE evento_id=?", Long.class, id)).isEqualTo(antes + 1);
            t.setRollbackOnly();
        });
        verify(google, never()).comprobarDestino(anyString());
        verify(google, never()).crear(anyString(), anyString(), any());
    }

    @Test void inesperadoAntesDeEscribirNoSeAtribuyeALaBase() {
        long id = evento("SENADO");
        doThrow(new NullPointerException("dato privado")).when(google).comprobarDestino(anyString());
        assertThatThrownBy(() -> servicio.operar(id, false, quien))
                .hasMessageContaining("EJECUCION_CALENDAR_FALLIDA").hasMessageNotContaining("dato privado");
        assertThat(servicio.estado(id, quien).estado()).isEqualTo("ERROR");
        assertThat(servicio.estado(id, quien).error()).isEqualTo("EJECUCION_CALENDAR_FALLIDA");
        verify(google, never()).crear(anyString(), anyString(), any());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void inesperadoDuranteEscrituraReconciliaSinSegundoInsert(boolean aplicada) {
        long id = evento("SENADO");
        doAnswer(a -> {
            if (aplicada) remotos.put(a.getArgument(1), new Remoto("creado", ((ContenidoCalendar.Contenido) a.getArgument(2)).huella(), false));
            throw new IllegalStateException("no sabemos si se envió");
        }).when(google).crear(anyString(), anyString(), any());
        assertThatThrownBy(() -> servicio.operar(id, false, quien)).hasMessageContaining("EJECUCION_CALENDAR_FALLIDA");
        assertThat(servicio.estado(id, quien).estado()).isEqualTo("INCIERTO");
        String identidad = jdbc.queryForObject("SELECT google_id FROM publicacion_calendar WHERE evento_id=?", String.class, id);
        doThrow(new Fallo("OAUTH_GOOGLE", false)).when(google).comprobarDestino(anyString());
        assertThatThrownBy(() -> servicio.operar(id, false, quien)).hasMessageContaining("OAUTH_GOOGLE");
        assertThat(servicio.estado(id, quien).estado()).isEqualTo("INCIERTO");
        verify(google, times(1)).crear(anyString(), eq(identidad), any());
        if (aplicada) {
            doNothing().when(google).comprobarDestino(anyString());
            assertThat(servicio.operar(id, false, quien).estado()).isEqualTo("EXPORTADO");
            verify(google, times(1)).crear(anyString(), eq(identidad), any());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void inesperadoTrasActualizarORetirarReconciliaSinRepetir(boolean retirar) {
        long id = evento("SENADO");
        servicio.operar(id, false, quien);
        String identidad = ContenidoCalendar.identidad("m06-it", id);
        if (retirar) {
            doAnswer(a -> {
                remotos.remove(a.getArgument(1));
                throw new IllegalStateException("después de retirar");
            }).when(google).retirar(anyString(), anyString(), anyString());
        } else {
            jdbc.update("UPDATE evento SET hora_inicio='22:30' WHERE evento_id=?", id);
            procesador.procesar(id);
            doAnswer(a -> {
                remotos.put(a.getArgument(1), new Remoto("actualizado", ((ContenidoCalendar.Contenido) a.getArgument(2)).huella(), false));
                throw new IllegalStateException("después de actualizar");
            }).when(google).actualizar(anyString(), anyString(), any(), anyString());
        }
        assertThatThrownBy(() -> servicio.operar(id, retirar, quien)).hasMessageContaining("EJECUCION_CALENDAR_FALLIDA");
        assertThat(servicio.estado(id, quien).estado()).isEqualTo("INCIERTO");
        assertThat(servicio.operar(id, retirar, quien).estado()).isEqualTo(retirar ? "RETIRADO" : "EXPORTADO");
        verify(google, times(1)).crear(anyString(), eq(identidad), any());
        if (retirar) verify(google, times(1)).retirar(anyString(), eq(identidad), anyString());
        else verify(google, times(1)).actualizar(anyString(), eq(identidad), any(), anyString());
    }

    @Test void oauthPrevioAlEnvioEsCiertoPeroNoConfirmaPublicacion() {
        long id = evento("SENADO");
        doThrow(new Fallo("OAUTH_GOOGLE", false)).when(google).crear(anyString(), anyString(), any());
        assertThatThrownBy(() -> servicio.operar(id, false, quien)).hasMessageContaining("OAUTH_GOOGLE");
        assertThat(servicio.estado(id, quien).estado()).isEqualTo("ERROR");
        assertThat(servicio.estado(id, quien).ultimoExito()).isNull();
        assertThat(remotos).isEmpty();
    }

    @Test void inesperadoEnConfirmacionNoSeAtribuyeAPostgresYRecupera() {
        long id = evento("SENADO");
        var primera = new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(a -> {
            if ("CREACION_CONFIRMADA".equals(a.getArgument(1)) && primera.getAndSet(false)) throw new IllegalStateException("programación");
            return a.callRealMethod();
        }).when(publicaciones).auditar(eq(id), anyString(), any(), any());
        assertThatThrownBy(() -> servicio.operar(id, false, quien)).hasMessageContaining("EJECUCION_CALENDAR_FALLIDA");
        assertThat(servicio.estado(id, quien).estado()).isEqualTo("INCIERTO");
        assertThat(servicio.operar(id, false, quien).estado()).isEqualTo("EXPORTADO");
        verify(google, times(1)).crear(anyString(), anyString(), any());
    }

    @Test void respuestaInvalidaNoSeConfirmaNiSeRotulaPersistencia() {
        long id = evento("SENADO");
        doReturn(new Remoto(null, "incorrecta", false)).when(google).crear(anyString(), anyString(), any());
        assertThatThrownBy(() -> servicio.operar(id, false, quien)).hasMessageContaining("RESPUESTA_GOOGLE_INCONSISTENTE");
        assertThat(servicio.estado(id, quien).estado()).isEqualTo("INCIERTO");
        assertThat(servicio.estado(id, quien).ultimoExito()).isNull();
    }

    @Test void noPoderGuardarElFalloConservaEnProcesoYLaIdentidad() {
        long id = evento("SENADO");
        doThrow(new Fallo("RED_GOOGLE", true)).when(google).crear(anyString(), anyString(), any());
        doAnswer(a -> {
            if ("INCIERTO".equals(a.getArgument(1))) throw new org.springframework.dao.DataAccessResourceFailureException("base caída");
            return a.callRealMethod();
        }).when(publicaciones).auditar(eq(id), anyString(), any(), any());
        assertThatThrownBy(() -> servicio.operar(id, false, quien)).hasMessageContaining("RED_GOOGLE");
        assertThat(servicio.estado(id, quien).estado()).isEqualTo("EN_PROCESO");
        assertThat(jdbc.queryForObject("SELECT google_id FROM publicacion_calendar WHERE evento_id=?", String.class, id))
                .isEqualTo(ContenidoCalendar.identidad("m06-it", id));
    }

    @Test void cuotaNoDeshaceCambiosComercialesYRetiradaInciertaEsRepetible() {
        long id=evento("SENADO");
        doThrow(new Fallo("CUOTA_GOOGLE",false)).when(google).comprobarDestino(anyString());
        assertThatThrownBy(()->servicio.operar(id,false,quien)).hasMessageContaining("CUOTA_GOOGLE");
        assertThat(servicio.estado(id,quien).error()).isEqualTo("CUOTA_GOOGLE");
        doNothing().when(google).comprobarDestino(anyString()); servicio.operar(id,false,quien);
        doAnswer(a->{remotos.remove(a.getArgument(1));throw new Fallo("RED_GOOGLE",true);}).when(google).retirar(anyString(),anyString(),anyString());
        assertThatThrownBy(()->servicio.operar(id,true,quien)).hasMessageContaining("RED_GOOGLE");
        assertThat(servicio.estado(id,quien).pendiente()).isEqualTo("RETIRAR");
        assertThatThrownBy(()->servicio.operar(id,false,quien)).hasMessageContaining("Hay una retirada pendiente");
        assertThat(servicio.operar(id,true,quien).estado()).isEqualTo("RETIRADO");
        verify(google,times(1)).retirar(anyString(),anyString(),anyString());
    }
    @Test void desactivadoNoNotificaYActivacionRecuperaSinDuplicar() {
        long id=evento("PRE_RESERVA"); when(google.configurado()).thenReturn(false);
        tx.executeWithoutResult(t->{
            Evento e=eventos.findById(id).orElseThrow();
            e.registrarSena(java.math.BigDecimal.TEN, ar.edu.utn.vastio.agenda.dominio.Moneda.ARS,
                    LocalDate.of(2026,1,1),null,"30111222",null);
            e.cambiarEstado(ar.edu.utn.vastio.agenda.dominio.EstadoEvento.SENADO);
            cambios.publishEvent(new CambioCalendar(id));
        });
        assertThat(servicio.estado(id,quien).pendiente()).isEqualTo("NINGUNA");
        assertThat(servicio.estado(id,quien).puedeExportar()).isFalse();
        assertThat(servicio.pendientes(quien)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE evento_id=? AND NOT resuelta",Integer.class,id)).isZero();
        when(google.configurado()).thenReturn(true);
        procesador.iniciar();procesador.procesar(id);procesador.procesar(id);
        assertThat(servicio.estado(id,quien).pendiente()).isEqualTo("EXPORTAR");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE evento_id=? AND tipo='CALENDAR_PENDIENTE' AND NOT resuelta",Integer.class,id)).isEqualTo(1);
        verify(google,never()).crear(anyString(),anyString(),any());
    }
    @Test void simultaneidadYCambioDuranteLaLlamadaNoQuedaComoActualizado() throws Exception {
        long id=evento("SENADO"); var entro=new CountDownLatch(1); var seguir=new CountDownLatch(1);
        doAnswer(a->{
            entro.countDown(); assertThat(seguir.await(15,TimeUnit.SECONDS)).isTrue();
            var r=new Remoto("creado",((ContenidoCalendar.Contenido)a.getArgument(2)).huella(),false);
            remotos.put(a.getArgument(1),r); return r;
        }).when(google).crear(anyString(),anyString(),any());
        try(var hilos=Executors.newFixedThreadPool(2)) {
            var primero=hilos.submit(()->servicio.operar(id,false,quien));
            assertThat(entro.await(15,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->servicio.operar(id,false,quien)).hasMessageContaining("Ya hay una operación");
            assertThatThrownBy(()->servicio.operar(id,true,quien)).hasMessageContaining("Ya hay una operación");
            reprogramaciones.reprogramar(id,new ReprogramacionService.Pedido((short)2,LocalDate.of(2039,1,1),(short)2,(short)4,null),quien);
            seguir.countDown();
            assertThat(primero.get(15,TimeUnit.SECONDS).pendiente()).isEqualTo("ACTUALIZAR");
        } finally { seguir.countDown(); }
        verify(google,times(1)).crear(anyString(),anyString(),any());
    }
    @Test void lecturaDeAvisosNoResuelvePendienteYSeDeduplican() {
        long id=evento("SENADO"); servicio.estado(id,quien); servicio.estado(id,quien);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE evento_id=? AND tipo='CALENDAR_PENDIENTE' AND NOT resuelta",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT d.usuario_id FROM notificacion_destinatario d JOIN notificacion n USING(notificacion_id) WHERE n.evento_id=?",Long.class,id)).containsExactlyInAnyOrder(direccion.getId(),coordinacion.getId());
        avisos.marcarTodasLeidas(direccion.getId());
        assertThat(servicio.estado(id,quien).pendiente()).isEqualTo("EXPORTAR");
        servicio.operar(id,false,quien);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE evento_id=? AND NOT resuelta",Integer.class,id)).isZero();
    }
    @Test void catalogoYHoraParticularInvalidanLaHuella() {
        long id=evento("SENADO"); servicio.operar(id,false,quien);
        tx.executeWithoutResult(t->{Evento e=eventos.findById(id).orElseThrow();e.actualizarDatos(e.getNombre(),e.getTipoEventoId(),LocalTime.of(22,30),null);cambios.publishEvent(new CambioCalendar(id));});
        assertThat(servicio.estado(id,quien).pendiente()).isEqualTo("ACTUALIZAR");
        servicio.operar(id,false,quien);
        tx.executeWithoutResult(t->{catalogos.actualizarTurno((short)2,"Noche",LocalTime.of(20,0),LocalTime.of(5,0));t.setRollbackOnly();
            assertThat(servicio.estado(id,quien).pendiente()).isEqualTo("NINGUNA");});
        assertThat(servicio.estado(id,quien).pendiente()).isEqualTo("NINGUNA");
    }
    @Test void errorDePermisosNoEs401DeVastioYSePuedeReintentar() throws Exception {
        long id=evento("SENADO"); doThrow(new Fallo("AUTORIZACION_GOOGLE_REVOCADA",false)).when(google).comprobarDestino(anyString());
        mvc.perform(post("/api/v1/eventos/"+id+"/calendar").header("Authorization",personas.bearer(direccion)))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.codigo").value("AUTORIZACION_GOOGLE_REVOCADA"));
        assertThat(servicio.estado(id,quien).estado()).isEqualTo("ERROR");
        doNothing().when(google).comprobarDestino(anyString());
        assertThat(servicio.operar(id,false,quien).estado()).isEqualTo("EXPORTADO");
    }
    @Test void remotoAusenteOModificadoNoSeRecreaSilenciosamente() {
        long id=evento("SENADO"); servicio.operar(id,false,quien);
        reprogramaciones.reprogramar(id,new ReprogramacionService.Pedido((short)2,LocalDate.of(2039,2,1),(short)2,(short)4,null),quien);
        remotos.replaceAll((k,v)->new Remoto("editado","otra-huella",false));
        assertThatThrownBy(()->servicio.operar(id,false,quien)).hasMessageContaining("CONFLICTO_REMOTO");
        remotos.clear();
        assertThatThrownBy(()->servicio.operar(id,false,quien)).hasMessageContaining("EVENTO_REMOTO_AUSENTE");
        verify(google,times(1)).crear(anyString(),anyString(),any());
    }
    @ParameterizedTest @EnumSource(value=RolCodigo.class,names={"DIRECCION","COORDINACION"},mode=EnumSource.Mode.EXCLUDE)
    void otrosRolesNoOperanNiConsultanPendientes(RolCodigo rol) throws Exception {
        String bearer=personas.bearer(rol);
        for(var request:List.of(get("/api/v1/eventos/1/calendar"),post("/api/v1/eventos/1/calendar"),put("/api/v1/eventos/1/calendar"),delete("/api/v1/eventos/1/calendar"),post("/api/v1/eventos/1/calendar/verificacion"),post("/api/v1/eventos/1/calendar/reconciliacion").contentType("application/json").content("{\"etag\":\"prueba\"}"),get("/api/v1/calendar/pendientes")))
            mvc.perform(request.header("Authorization",bearer)).andExpect(status().isForbidden());
        verify(google,never()).crear(anyString(),anyString(),any());
    }
    void fallarSeguimiento(long id) {
        doThrow(new IllegalStateException("fallo de proyección inyectado")).when(seguimiento).revisar(id);
    }
    void verificarFalloYRecuperar(long id) {
        assertThat(jdbc.queryForObject("SELECT ultimo_error FROM calendar_seguimiento WHERE evento_id=?",String.class,id)).isEqualTo("SEGUIMIENTO_CALENDAR_FALLIDO");
        assertThat(servicio.estado(id,quien).seguimientoPendiente()).isTrue();
        doCallRealMethod().when(seguimiento).revisar(id);
        assertThat(procesador.procesar(id)).isTrue();
        assertThat(servicio.estado(id,quien).seguimientoPendiente()).isFalse();
        assertThat(servicio.estado(id,quien).errorSeguimiento()).isNull();
    }
    @Test void falloDurantePreReservaNoRevierteAltaNiHistorial() {
        doThrow(new IllegalStateException("fallo de proyección inyectado")).when(seguimiento).revisar(anyLong());
        var e=preReservas.registrar(new PreReservaService.PreReserva((short)1,LocalDate.of(2041,1,1).plusDays(FECHA.incrementAndGet()),(short)2,(short)2,
                "Ficticio",vendedora.getId(),new ClienteService.DatosCliente(null,"Ficticio",null,null,null)),quien);
        long id=e.getId();creados.add(id);
        assertThat(jdbc.queryForObject("SELECT estado FROM evento WHERE evento_id=?",String.class,id)).isEqualTo("PRE_RESERVA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cambio_estado_evento WHERE evento_id=?",Integer.class,id)).isEqualTo(1);
        verificarFalloYRecuperar(id);
        verify(google,never()).comprobarDestino(anyString());
    }
    @Test void falloDuranteSenaNoRevierteImporteEstadoNiHistorial() {
        long id=evento("PRE_RESERVA");fallarSeguimiento(id);
        senas.registrar(id,new SenaService.Sena(java.math.BigDecimal.TEN,ar.edu.utn.vastio.agenda.dominio.Moneda.ARS,LocalDate.of(2026,1,1),null,"30111222",null),quien);
        assertThat(jdbc.queryForObject("SELECT estado FROM evento WHERE evento_id=?",String.class,id)).isEqualTo("SENADO");
        assertThat(jdbc.queryForObject("SELECT importe_sena FROM evento WHERE evento_id=?",java.math.BigDecimal.class,id)).isEqualByComparingTo("10");
        verificarFalloYRecuperar(id);
        assertThat(servicio.estado(id,quien).pendiente()).isEqualTo("EXPORTAR");
    }
    @Test void falloDuranteCancelacionConservaOperacionYRecuperaRetirada() {
        long id=evento("SENADO");servicio.operar(id,false,quien);fallarSeguimiento(id);
        cancelaciones.cancelar(id,(short)1,null,quien);
        assertThat(jdbc.queryForObject("SELECT estado FROM evento WHERE evento_id=?",String.class,id)).isEqualTo("CANCELADO");
        verificarFalloYRecuperar(id);
        assertThat(servicio.estado(id,quien).pendiente()).isEqualTo("RETIRAR");
        verify(google,never()).retirar(anyString(),anyString(),anyString());
    }
    @Test void falloDuranteReprogramacionConservaDestinoYRecuperaActualizacion() {
        long id=evento("SENADO");servicio.operar(id,false,quien);fallarSeguimiento(id);
        LocalDate destino=LocalDate.of(2042,1,1).plusDays(FECHA.incrementAndGet());
        reprogramaciones.reprogramar(id,new ReprogramacionService.Pedido((short)2,destino,(short)2,(short)4,null),quien);
        assertThat(servicio.estado(id,quien).fecha()).isEqualTo(destino);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reprogramacion WHERE evento_id=?",Integer.class,id)).isEqualTo(1);
        verificarFalloYRecuperar(id);
        assertThat(servicio.estado(id,quien).pendiente()).isEqualTo("ACTUALIZAR");
        verify(google,never()).actualizar(anyString(),anyString(),any(),anyString());
    }
    @Test void falloDeCatalogoNoRevierteCambioYSeRecupera() {
        long id=evento("SENADO");servicio.operar(id,false,quien);fallarSeguimiento(id);
        var original=jdbc.queryForMap("SELECT nombre,hora_inicio,hora_fin FROM turno WHERE turno_id=2");
        try {
            catalogos.actualizarTurno((short)2,"Noche de prueba",LocalTime.of(20,0),LocalTime.of(5,0));
            assertThat(jdbc.queryForObject("SELECT nombre FROM turno WHERE turno_id=2",String.class)).isEqualTo("Noche de prueba");
            verificarFalloYRecuperar(id);
            assertThat(servicio.estado(id,quien).pendiente()).isEqualTo("ACTUALIZAR");
        } finally {
            doCallRealMethod().when(seguimiento).revisar(id);
            catalogos.actualizarTurno((short)2,(String)original.get("nombre"),((java.sql.Time)original.get("hora_inicio")).toLocalTime(),((java.sql.Time)original.get("hora_fin")).toLocalTime());
        }
    }
    @Test void colaSobreviveSinEventoSpringYRollbackNoDejaTrabajoFalso() {
        long id=evento("SENADO");servicio.operar(id,false,quien);
        var antes=jdbc.queryForMap("SELECT * FROM calendar_seguimiento WHERE evento_id=?",id);
        tx.executeWithoutResult(t->{jdbc.update("UPDATE evento SET hora_inicio='22:30' WHERE evento_id=?",id);t.setRollbackOnly();});
        assertThat(jdbc.queryForMap("SELECT * FROM calendar_seguimiento WHERE evento_id=?",id)).isEqualTo(antes);
        jdbc.update("UPDATE evento SET hora_inicio='22:30' WHERE evento_id=?",id);
        var publicacion=jdbc.queryForMap("SELECT * FROM publicacion_calendar WHERE evento_id=?",id);
        servicio.estado(id,quien);servicio.pendientes(quien);
        assertThat(jdbc.queryForMap("SELECT * FROM publicacion_calendar WHERE evento_id=?",id)).isEqualTo(publicacion);
        assertThat(servicio.estado(id,quien).seguimientoPendiente()).isTrue();
        procesador.procesarPendientes();
        assertThat(servicio.estado(id,quien).pendiente()).isEqualTo("ACTUALIZAR");
        assertThat(servicio.estado(id,quien).seguimientoPendiente()).isFalse();
    }
    @Test void desactivarConservaIdentidadYActivarRecuperaRetiradaSinDuplicarAvisos() {
        long id=evento("SENADO");servicio.operar(id,false,quien);
        String identidad=jdbc.queryForObject("SELECT google_id FROM publicacion_calendar WHERE evento_id=?",String.class,id);
        when(google.configurado()).thenReturn(false);procesador.iniciar();
        cancelaciones.cancelar(id,(short)1,null,quien);
        assertThat(servicio.pendientes(quien)).isEmpty();
        assertThat(servicio.estado(id,quien).puedeRetirar()).isFalse();
        assertThat(jdbc.queryForObject("SELECT google_id FROM publicacion_calendar WHERE evento_id=?",String.class,id)).isEqualTo(identidad);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE evento_id=? AND tipo='CALENDAR_PENDIENTE' AND NOT resuelta",Integer.class,id)).isZero();
        when(google.configurado()).thenReturn(true);procesador.iniciar();procesador.iniciar();procesador.procesar(id);
        assertThat(servicio.estado(id,quien).pendiente()).isEqualTo("RETIRAR");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE evento_id=? AND tipo='CALENDAR_PENDIENTE' AND NOT resuelta",Integer.class,id)).isEqualTo(1);
        verify(google,never()).retirar(anyString(),anyString(),anyString());
    }
    @Test void conflictoRequiereRevisionExplicitaYEtagsVigentesSinImportarDatos() {
        long id=evento("SENADO");servicio.operar(id,false,quien);
        jdbc.update("UPDATE evento SET hora_inicio='22:30' WHERE evento_id=?",id);procesador.procesar(id);
        remotos.replaceAll((k,v)->new Remoto("externo","huella-externa",false));
        assertThatThrownBy(()->servicio.operar(id,false,quien)).hasMessageContaining("CONFLICTO_REMOTO");
        var revision=servicio.verificarConflicto(id,quien);
        assertThat(revision.coincideConVastio()).isFalse();
        remotos.replaceAll((k,v)->new Remoto("externo-2","huella-externa",false));
        assertThatThrownBy(()->servicio.aceptarRevision(id,revision.etag(),quien)).hasMessageContaining("CONFLICTO_REMOTO");
        var fuente=jdbc.queryForMap("SELECT * FROM evento WHERE evento_id=?",id);
        assertThat(servicio.aceptarRevision(id,"externo-2",quien).error()).isNull();
        assertThat(jdbc.queryForMap("SELECT * FROM evento WHERE evento_id=?",id)).isEqualTo(fuente);
        verify(google,never()).actualizar(anyString(),anyString(),any(),anyString());
        assertThat(servicio.operar(id,false,quien).estado()).isEqualTo("EXPORTADO");
        verify(google).actualizar(anyString(),anyString(),any(),eq("externo-2"));
    }
    @Test void sinSesionYDesactivado() throws Exception {
        mvc.perform(get("/api/v1/calendar/pendientes")).andExpect(status().isUnauthorized());
        long id=evento("SENADO"); when(google.configurado()).thenReturn(false);
        assertThatThrownBy(()->servicio.operar(id,false,quien)).hasMessageContaining("CALENDAR_NO_CONFIGURADO");
        assertThat(servicio.estado(id,quien).pendiente()).isEqualTo("NINGUNA");
        verify(google,never()).crear(anyString(),anyString(),any());
    }
}
