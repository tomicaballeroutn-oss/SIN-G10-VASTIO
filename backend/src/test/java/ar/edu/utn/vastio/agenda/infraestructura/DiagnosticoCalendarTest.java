package ar.edu.utn.vastio.agenda.infraestructura;

import static org.assertj.core.api.Assertions.*;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.zip.ZipException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpHeaders;

class DiagnosticoCalendarTest {
    @Test void identificaCausaOAuthSinExponerMensajeCuerpoNiCambiarIncertidumbre() {
        var error=new DiagnosticoCalendar.FalloOAuth(new IOException("token-secreto",new ZipException("contenido-privado")));
        var d=DiagnosticoCalendar.detalle(DiagnosticoCalendar.Fase.CALENDAR_LISTAR,error);
        assertThat(d.fase()).isEqualTo(DiagnosticoCalendar.Fase.OAUTH_TOKEN);
        assertThat(d.categoria()).isEqualTo("FORMATO_GZIP");
        assertThat(d.excepciones()).contains("java.util.zip.ZipException");
        assertThat(d.toString()).doesNotContain("token-secreto","contenido-privado");
        assertThat(GoogleCalendarClient.traducir(error,true).incierto()).isTrue();
        assertThat(GoogleCalendarClient.traducir(error,false).incierto()).isFalse();
        assertThat(GoogleCalendarClient.traducir(error,false).codigo()).isEqualTo("RED_GOOGLE");
    }
    @ParameterizedTest @CsvSource({"DNS,DNS","TLS,TLS","TIMEOUT,TIMEOUT","CONEXION,CONEXION"})
    void distingueFallosDeTransporte(String tipo,String esperado) {
        IOException causa=switch(tipo) {
            case "DNS" -> new UnknownHostException("dominio-privado");
            case "TLS" -> new SSLHandshakeException("certificado-privado");
            case "TIMEOUT" -> new SocketTimeoutException("url-privada");
            default -> new ConnectException("ip-privada");
        };
        var d=DiagnosticoCalendar.detalle(DiagnosticoCalendar.Fase.CALENDAR_LISTAR,new IOException("token-secreto",causa));
        assertThat(d.categoria()).isEqualTo(esperado);
        assertThat(d.toString()).doesNotContain("privad","token-secreto");
    }
    @ParameterizedTest @CsvSource({"400,OAUTH_TOKEN,OAUTH_HTTP","403,CALENDAR_LISTAR,AUTORIZACION_O_PERMISOS_CALENDAR","500,CALENDAR_LISTAR,CALENDAR_HTTP"})
    void distingueHttpOAuthPermisosYApiSinImprimirCuerpo(int estado,DiagnosticoCalendar.Fase fase,String categoria) {
        var error=new HttpResponseException.Builder(estado,"mensaje-privado",new HttpHeaders()).setContent("token-secreto").build();
        var d=DiagnosticoCalendar.detalle(fase,error);
        assertThat(d.http()).isEqualTo(estado);assertThat(d.categoria()).isEqualTo(categoria);
        assertThat(d.toString()).doesNotContain("mensaje-privado","token-secreto");
    }
}
