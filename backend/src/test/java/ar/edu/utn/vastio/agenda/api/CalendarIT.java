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
    @Autowired SeguimientoCalendar seguimiento;
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
        creados.add(id); return id;
    }
    @AfterEach void limpiar() {
        tx.executeWithoutResult(t->{
            jdbc.execute("SET LOCAL session_replication_role = replica");
            for(long id:creados) {
                jdbc.update("DELETE FROM notificacion_destinatario WHERE notificacion_id IN (SELECT notificacion_id FROM notificacion WHERE evento_id=?)",id);
                for(String tabla:List.of("notificacion","operacion_calendar","publicacion_calendar","reprogramacion","cambio_estado_evento","modificacion_evento")) jdbc.update("DELETE FROM "+tabla+" WHERE evento_id=?",id);
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
        assertThat(servicio.operar(id,false,quien).estado()).isEqualTo("EXPORTADO");
        verify(google,times(1)).crear(anyString(),eq(ContenidoCalendar.identidad("m06-it",id)),any());
    }
    @Test void operacionAbandonadaSeRecuperaSinCambiarId() {
        long id=evento("SENADO"); servicio.estado(id,quien);
        jdbc.update("UPDATE publicacion_calendar SET estado='EN_PROCESO',calendar_id='calendar-prueba',instalacion='m06-it',google_id=? WHERE evento_id=?",ContenidoCalendar.identidad("m06-it",id),id);
        assertThat(servicio.estado(id,quien).estado()).isEqualTo("INCIERTO");
        assertThat(servicio.operar(id,false,quien).estado()).isEqualTo("EXPORTADO");
    }
    @Test void falloAlGuardarTrasExitoRemotoSeRecuperaSinDuplicar() {
        long id=evento("SENADO"); var fallar=new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(a->{
            if ("CREACION_CONFIRMADA".equals(a.getArgument(1)) && fallar.getAndSet(false)) throw new IllegalStateException("fallo local simulado");
            return a.callRealMethod();
        }).when(publicaciones).auditar(eq(id),anyString(),any(),any());
        assertThatThrownBy(()->servicio.operar(id,false,quien)).hasMessageContaining("RESULTADO_NO_PERSISTIDO");
        assertThat(servicio.estado(id,quien).estado()).isEqualTo("INCIERTO");
        assertThat(servicio.operar(id,false,quien).estado()).isEqualTo("EXPORTADO");
        verify(google,times(1)).crear(anyString(),anyString(),any());
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
    @Test void seNotificaAlLlegarASenadoAunqueCalendarEsteDesactivado() {
        long id=evento("PRE_RESERVA"); when(google.configurado()).thenReturn(false);
        tx.executeWithoutResult(t->{
            Evento e=eventos.findById(id).orElseThrow();
            e.registrarSena(java.math.BigDecimal.TEN, ar.edu.utn.vastio.agenda.dominio.Moneda.ARS,
                    LocalDate.of(2026,1,1),null,"30111222",null);
            e.cambiarEstado(ar.edu.utn.vastio.agenda.dominio.EstadoEvento.SENADO);
            cambios.publishEvent(new CambioCalendar(id));
        });
        assertThat(servicio.estado(id,quien).pendiente()).isEqualTo("EXPORTAR");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE evento_id=? AND NOT resuelta",Integer.class,id)).isEqualTo(1);
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
            assertThat(servicio.estado(id,quien).pendiente()).isEqualTo("ACTUALIZAR");});
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
        for(var request:List.of(get("/api/v1/eventos/1/calendar"),post("/api/v1/eventos/1/calendar"),put("/api/v1/eventos/1/calendar"),delete("/api/v1/eventos/1/calendar"),get("/api/v1/calendar/pendientes")))
            mvc.perform(request.header("Authorization",bearer)).andExpect(status().isForbidden());
        verify(google,never()).crear(anyString(),anyString(),any());
    }
    @Test void sinSesionYDesactivado() throws Exception {
        mvc.perform(get("/api/v1/calendar/pendientes")).andExpect(status().isUnauthorized());
        long id=evento("SENADO"); when(google.configurado()).thenReturn(false);
        assertThatThrownBy(()->servicio.operar(id,false,quien)).hasMessageContaining("CALENDAR_NO_CONFIGURADO");
        assertThat(servicio.estado(id,quien).pendiente()).isEqualTo("EXPORTAR");
        verify(google,never()).crear(anyString(),anyString(),any());
    }
}
