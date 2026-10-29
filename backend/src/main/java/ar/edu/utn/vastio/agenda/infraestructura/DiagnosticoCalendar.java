package ar.edu.utn.vastio.agenda.infraestructura;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.zip.ZipException;
import javax.net.ssl.SSLException;
import com.google.api.client.http.HttpResponseException;

/** Solo clases, fases cerradas y estado HTTP. Nunca mensajes, cuerpos, URLs ni stack traces. */
final class DiagnosticoCalendar {
    enum Fase { CREDENCIALES, OAUTH_TOKEN, CALENDAR_LISTAR, CALENDAR_CONSULTAR, CALENDAR_CREAR, CALENDAR_ACTUALIZAR, CALENDAR_RETIRAR }
    record Detalle(Fase fase,String categoria,String excepciones,Integer http) { }
    static final class FalloOAuth extends IOException {
        FalloOAuth(IOException causa) { super("Fallo en obtención OAuth",causa); }
    }
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(DiagnosticoCalendar.class);

    static Detalle detalle(Fase fase,Throwable error) {
        var clases=new ArrayList<String>();
        var vistos=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        String categoria="IO_NO_CLASIFICADA";Integer http=null;
        for(Throwable causa=error;causa!=null && clases.size()<12 && vistos.add(causa);causa=causa.getCause()) {
            clases.add(causa.getClass().getName());
            if(causa instanceof FalloOAuth) fase=Fase.OAUTH_TOKEN;
            if(causa instanceof HttpResponseException respuesta) http=respuesta.getStatusCode();
            if(causa instanceof ZipException) categoria="FORMATO_GZIP";
            else if(causa instanceof UnknownHostException) categoria="DNS";
            else if(causa instanceof SSLException) categoria="TLS";
            else if(causa instanceof SocketTimeoutException || causa instanceof java.net.http.HttpTimeoutException) categoria="TIMEOUT";
            else if(causa instanceof ConnectException) categoria="CONEXION";
        }
        if(categoria.equals("IO_NO_CLASIFICADA") && http!=null)
            categoria=fase==Fase.OAUTH_TOKEN?"OAUTH_HTTP":http==401 || http==403?"AUTORIZACION_O_PERMISOS_CALENDAR":"CALENDAR_HTTP";
        return new Detalle(fase,categoria,String.join(" -> ",clases),http);
    }
    static void registrar(Fase fase,Throwable error) {
        var d=detalle(fase,error);
        log.warn("Calendar diagnóstico: fase={} categoria={} excepciones={} http={}",d.fase(),d.categoria(),d.excepciones(),d.http());
    }
    private DiagnosticoCalendar() { }
}
