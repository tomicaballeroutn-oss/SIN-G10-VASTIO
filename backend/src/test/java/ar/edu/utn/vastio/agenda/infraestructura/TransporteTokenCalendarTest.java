package ar.edu.utn.vastio.agenda.infraestructura;

import static org.assertj.core.api.Assertions.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.UrlEncodedContent;
import com.google.api.client.testing.http.*;

class TransporteTokenCalendarTest {
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
