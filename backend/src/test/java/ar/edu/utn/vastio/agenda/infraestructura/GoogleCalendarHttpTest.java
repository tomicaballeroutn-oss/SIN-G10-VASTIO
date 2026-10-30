package ar.edu.utn.vastio.agenda.infraestructura;

import static org.assertj.core.api.Assertions.*;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import ar.edu.utn.vastio.agenda.aplicacion.ContenidoCalendar;

/** Transporte Google oficial simulado: valida HTTP real del adaptador sin sockets ni claves. */
class GoogleCalendarHttpTest {
    @Test void inicializadorRealAutenticaSinDependerDeUnInterceptor() throws Exception {
        var t=new Transporte();t.respuesta(200,"{\"items\":[]}");
        var credenciales=com.google.auth.oauth2.OAuth2Credentials.create(new com.google.auth.oauth2.AccessToken("token-sintetico",new java.util.Date(System.currentTimeMillis()+3600000)));
        var auth=new com.google.auth.http.HttpCredentialsAdapter(credenciales);
        var config=new CalendarProperties(true,"prueba","instalacion","archivo-inexistente",1234,2345);
        var api=new Calendar.Builder(t,GsonFactory.getDefaultInstance(),GoogleCalendarClient.inicializador(auth,config)).setApplicationName("prueba-sin-red").build();
        new GoogleCalendarClient(config,api).comprobarDestino("prueba");
        assertThat(t.pedidos).hasSize(1);
        assertThat(t.pedidos.getFirst().getFirstHeaderValue("Authorization")).isEqualTo("Bearer token-sintetico");
    }
    @Test void inicializadorClasificaIOExceptionOAuthSinMensajesPrivados() throws Exception {
        var auth=org.mockito.Mockito.mock(com.google.auth.http.HttpCredentialsAdapter.class);
        org.mockito.Mockito.doThrow(new IOException("secreto-no-imprimir")).when(auth).initialize(org.mockito.ArgumentMatchers.any());
        var config=new CalendarProperties(true,"prueba","instalacion","archivo-inexistente",1234,2345);
        var t=new Transporte();
        var api=new Calendar.Builder(t,GsonFactory.getDefaultInstance(),GoogleCalendarClient.inicializador(auth,config)).setApplicationName("prueba-sin-red").build();
        assertThatThrownBy(()->new GoogleCalendarClient(config,api).comprobarDestino("prueba")).hasMessage("OAUTH_GOOGLE").hasMessageNotContaining("secreto-no-imprimir");
        assertThat(t.pedidos).isEmpty();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"CREAR", "ACTUALIZAR", "RETIRAR"})
    void oauthImpideCadaEscrituraAntesDeEnviar(String operacion) throws Exception {
        var auth = org.mockito.Mockito.mock(com.google.auth.http.HttpCredentialsAdapter.class);
        org.mockito.Mockito.doThrow(new IOException("dato privado")).when(auth).initialize(org.mockito.ArgumentMatchers.any());
        var config = new CalendarProperties(true, "prueba", "instalacion", "no-utilizado", 1234, 2345);
        var t = new Transporte();
        var api = new Calendar.Builder(t, GsonFactory.getDefaultInstance(), GoogleCalendarClient.inicializador(auth, config))
                .setApplicationName("sin-red").build();
        var cliente = new GoogleCalendarClient(config, api);
        var inicio = ZonedDateTime.parse("2032-01-01T20:00:00-03:00[America/Argentina/Cordoba]");
        var contenido = new ContenidoCalendar.Contenido("EV", "Público", "Salón", inicio, inicio.plusHours(10), "huella");
        var fallo = catchThrowableOfType(ar.edu.utn.vastio.agenda.aplicacion.ClienteCalendar.Fallo.class, () -> {
            switch (operacion) {
                case "CREAR" -> cliente.crear("prueba", "id123", contenido);
                case "ACTUALIZAR" -> cliente.actualizar("prueba", "id123", contenido, "etag");
                case "RETIRAR" -> cliente.retirar("prueba", "id123", "etag");
                default -> throw new AssertionError(operacion);
            }
        });
        assertThat(fallo.codigo()).isEqualTo("OAUTH_GOOGLE");
        assertThat(fallo.incierto()).isFalse();
        assertThat(t.pedidos).isEmpty();
    }

    @Test void runtimeEnAutenticacionTambienOcurreAntesDelEnvio() throws Exception {
        var auth = org.mockito.Mockito.mock(com.google.auth.http.HttpCredentialsAdapter.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("dato privado")).when(auth).initialize(org.mockito.ArgumentMatchers.any());
        var config = new CalendarProperties(true, "prueba", "instalacion", "no-utilizado", 1234, 2345);
        var t = new Transporte();
        var api = new Calendar.Builder(t, GsonFactory.getDefaultInstance(), GoogleCalendarClient.inicializador(auth, config))
                .setApplicationName("sin-red").build();
        var fallo = catchThrowableOfType(ar.edu.utn.vastio.agenda.aplicacion.ClienteCalendar.Fallo.class,
                () -> new GoogleCalendarClient(config, api).retirar("prueba", "id123", "etag"));
        assertThat(fallo.codigo()).isEqualTo("OAUTH_GOOGLE");
        assertThat(fallo.incierto()).isFalse();
        assertThat(t.pedidos).isEmpty();
    }

    static class Transporte extends MockHttpTransport {
        final ArrayDeque<MockLowLevelHttpResponse> respuestas=new ArrayDeque<>();
        final List<MockLowLevelHttpRequest> pedidos=new ArrayList<>();
        @Override public MockLowLevelHttpRequest buildRequest(String metodo,String url) throws IOException {
            var r=new MockLowLevelHttpRequest(url).setResponse(respuestas.removeFirst()); pedidos.add(r); return r;
        }
        void respuesta(int status,String cuerpo) { respuestas.add(new MockLowLevelHttpResponse().setStatusCode(status).setContentType("application/json").setContent(cuerpo)); }
    }
    private GoogleCalendarClient cliente(Transporte transporte) {
        var api=new Calendar.Builder(transporte,GsonFactory.getDefaultInstance(),r->{r.setNumberOfRetries(0);r.setLoggingEnabled(false);}).setApplicationName("prueba-sin-red").build();
        return new GoogleCalendarClient(new CalendarProperties(true,"prueba","instalacion","archivo-inexistente",5000,10000),api);
    }
    @Test void scopeYDestinoNoSeConfundenConAusenciaDeEvento() {
        var t=new Transporte(); var cliente=cliente(t);
        t.respuesta(404,"{\"error\":{\"code\":404,\"message\":\"no disponible\"}}");
        assertThatThrownBy(()->cliente.comprobarDestino("calendario")).hasMessage("DESTINO_NO_ACCESIBLE");
        t.respuesta(200,"{\"items\":[]}");cliente.comprobarDestino("calendario");
        t.respuesta(404,"{\"error\":{\"code\":404}}");assertThat(cliente.consultar("calendario","id123")).isNull();
        assertThat(t.pedidos).hasSize(3);
        assertThat(t.pedidos.getFirst().getUrl()).contains("calendars/calendario/events");
    }
    @Test void retiradaUsaIfMatchYNoPropagaEl401ComoSesionVastio() {
        var t=new Transporte();var cliente=cliente(t);
        t.respuesta(204,"");cliente.retirar("prueba","id123","\"etag\"");
        assertThat(t.pedidos.getFirst().getFirstHeaderValue("If-Match")).isEqualTo("\"etag\"");
        t.respuesta(401,"{\"error\":{\"code\":401,\"message\":\"secret-do-not-expose\"}}");
        assertThatThrownBy(()->cliente.comprobarDestino("prueba")).hasMessage("AUTORIZACION_GOOGLE_REVOCADA")
                .hasMessageNotContaining("secret-do-not-expose");
    }
    @Test void actualizacionUsaIfMatchYElModeloMinimo() throws Exception {
        var t=new Transporte();var cliente=cliente(t);
        var inicio=ZonedDateTime.parse("2032-01-01T20:00:00-03:00[America/Argentina/Cordoba]");
        var c=new ContenidoCalendar.Contenido("EV · Avril · Noche","Código Vastio: EV.","Avril",inicio,inicio.plusHours(10),"huella");
        // La respuesta inválida no se interpreta como una publicación propia.
        t.respuesta(200,"{\"id\":\"id123\",\"etag\":\"etag\"}");
        assertThatThrownBy(()->cliente.actualizar("prueba","id123",c,"anterior")).hasMessage("IDENTIDAD_REMOTA_AJENA");
        assertThat(t.pedidos.getFirst().getFirstHeaderValue("If-Match")).isEqualTo("anterior");
        assertThat(t.pedidos.getFirst().getContentAsString()).contains("America/Argentina/Cordoba", "vastio-id")
                .doesNotContain("attendees", "attachments", "importeSena");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"200,IDENTIDAD_REMOTA_AJENA", "204,EJECUCION_CALENDAR_FALLIDA"})
    void falloAlInterpretarRespuestaDeEscrituraConservaIncertidumbre(int status, String codigo) {
        var t = new Transporte();
        var cliente = cliente(t);
        var inicio = ZonedDateTime.parse("2032-01-01T20:00:00-03:00[America/Argentina/Cordoba]");
        var c = new ContenidoCalendar.Contenido("EV", "Público", "Salón", inicio, inicio.plusHours(10), "huella");
        // JSON null usa un sentinel de GenericJson; una respuesta sin cuerpo sí devuelve null Java.
        t.respuesta(status, status == 204 ? "" : "null");
        var fallo = catchThrowableOfType(ar.edu.utn.vastio.agenda.aplicacion.ClienteCalendar.Fallo.class,
                () -> cliente.crear("prueba", "id123", c));
        assertThat(fallo.codigo()).isEqualTo(codigo);
        assertThat(fallo.incierto()).isTrue();
        assertThat(t.pedidos).hasSize(1);
    }
}
