package ar.edu.utn.vastio.agenda.infraestructura;

import static org.assertj.core.api.Assertions.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.UrlEncodedContent;
import com.google.api.client.testing.http.*;

class TransporteTokenCalendarTest {
    static byte[] gzip(String texto) throws IOException {
        var bytes=new java.io.ByteArrayOutputStream();
        try(var salida=new java.util.zip.GZIPOutputStream(bytes)) { salida.write(texto.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        return bytes.toByteArray();
    }
    @Test void respuestaOAuthComprimidaSeDescomprimeUnaSolaVez() throws Exception {
        byte[] cuerpo=gzip("{\"expires_in\":3600}");
        var respuesta=new MockLowLevelHttpResponse().setStatusCode(200).setContentType("application/json")
                .setContentEncoding("gzip").setContent(cuerpo).setContentLength(cuerpo.length)
                .addHeader("Content-Encoding","gzip").addHeader("Content-Length",String.valueOf(cuerpo.length));
        var red=new MockHttpTransport.Builder().setLowLevelHttpResponse(respuesta).build();
        var transporte=new TransporteTokenCalendar(red,1234,2345);
        var pedido=transporte.createRequestFactory().buildGetRequest(new GenericUrl("https://oauth2.invalid/token"));
        pedido.setLoggingEnabled(false);
        var recibida=pedido.execute();
        try {
            assertThat(recibida.getContentEncoding()).isNull();
            assertThat(recibida.getHeaders().getContentEncoding()).isNull();
            assertThat(recibida.getHeaders().getContentLength()).isNull();
            assertThat(recibida.parseAsString()).isEqualTo("{\"expires_in\":3600}");
        }
        finally { recibida.disconnect(); }
    }
    @Test void errorOAuthComprimidoConservaEstadoHttpYSinDobleDescompresion() throws Exception {
        var respuesta=new MockLowLevelHttpResponse().setStatusCode(400).setContentType("application/json")
                .setContentEncoding("gzip").setContent(gzip("{\"error\":\"invalid_grant\"}"));
        var red=new MockHttpTransport.Builder().setLowLevelHttpResponse(respuesta).build();
        var transporte=new TransporteTokenCalendar(red,1234,2345);
        var pedido=transporte.createRequestFactory().buildGetRequest(new GenericUrl("https://oauth2.invalid/token"));
        pedido.setLoggingEnabled(false);
        assertThatThrownBy(pedido::execute).isInstanceOfSatisfying(com.google.api.client.http.HttpResponseException.class,error->{
            assertThat(error.getStatusCode()).isEqualTo(400);
            assertThat(error.getContent()).isEqualTo("{\"error\":\"invalid_grant\"}");
        });
    }
    @Test void bibliotecaOAuthPuedeLeerTokenComprimidoSinCalendarNiCredencialesReales() throws Exception {
        var respuesta=new MockLowLevelHttpResponse().setStatusCode(200).setContentType("application/json")
                .setContentEncoding("gzip").setContent(gzip("{\"access_token\":\"sintetico-no-utilizable\",\"expires_in\":3600,\"token_type\":\"Bearer\"}"));
        var red=new MockHttpTransport.Builder().setLowLevelHttpResponse(respuesta).build();
        var generador=java.security.KeyPairGenerator.getInstance("RSA");generador.initialize(2048);
        var credenciales=com.google.auth.oauth2.ServiceAccountCredentials.newBuilder()
                .setClientEmail("prueba@example.invalid").setPrivateKey(generador.generateKeyPair().getPrivate())
                .setPrivateKeyId("sintetica").setScopes(java.util.List.of("https://www.googleapis.com/auth/calendar.events"))
                .setHttpTransportFactory(()->new TransporteTokenCalendar(red,1234,2345))
                .setDefaultRetriesEnabled(false).build();
        assertThat(credenciales.refreshAccessToken().getExpirationTime()).isAfter(new java.util.Date());
    }
    @Test void limitaConexionLecturaYReintentosInclusoConDefaultsDeAuth() throws Exception {
        var cantidad=new AtomicInteger();
        var red=new MockHttpTransport() {
            @Override public MockLowLevelHttpRequest buildRequest(String metodo,String url) {
                cantidad.incrementAndGet();
                return new MockLowLevelHttpRequest(url) {
                    @Override public void setTimeout(int conexion,int lectura) {
                        assertThat(conexion).isEqualTo(1234);assertThat(lectura).isEqualTo(2345);
                    }
                    @Override public MockLowLevelHttpResponse execute() throws IOException {
                        assertThat(getContentAsString()).contains("grant_type=prueba");
                        return new MockLowLevelHttpResponse().setStatusCode(503).setContent("fallo simulado");
                    }
                };
            }
        };
        var transporte=new TransporteTokenCalendar(red,1234,2345);
        var pedido=transporte.createRequestFactory().buildPostRequest(new GenericUrl("https://oauth2.invalid/token"),new UrlEncodedContent(java.util.Map.of("grant_type","prueba")));
        pedido.setConnectTimeout(20000);pedido.setReadTimeout(20000);pedido.setThrowExceptionOnExecuteError(false);
        pedido.setLoggingEnabled(false);
        var respuesta=pedido.execute();
        try { assertThat(respuesta.getStatusCode()).isEqualTo(503); }
        finally { respuesta.disconnect(); }
        assertThat(cantidad).hasValue(1);
    }
}
