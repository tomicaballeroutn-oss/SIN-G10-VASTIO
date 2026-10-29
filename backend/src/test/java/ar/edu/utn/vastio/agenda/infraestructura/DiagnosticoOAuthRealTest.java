package ar.edu.utn.vastio.agenda.infraestructura;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.oauth2.ServiceAccountCredentials;

/** Opt-in: token y, con un segundo permiso, GET de identidad. Nunca escribe ni imprime credenciales/token. */
@EnabledIfEnvironmentVariable(named="VASTIO_DIAGNOSTICO_OAUTH",matches="true")
class DiagnosticoOAuthRealTest {
    @Test
    @EnabledIfEnvironmentVariable(named="VASTIO_DIAGNOSTICO_IDENTIDAD",matches="true")
    void consultaIdentidadGuardadaSinInsertarNiRecuperar() {
        String ruta=System.getenv("VASTIO_CALENDAR_CREDENCIALES");
        String destino=System.getenv("VASTIO_CALENDAR_ID");
        String identidad=System.getenv("VASTIO_DIAGNOSTICO_GOOGLE_ID");
        if(ruta==null || destino==null || identidad==null || !identidad.matches("[0-9a-f]{64}"))
            throw new AssertionError("Configuración de diagnóstico incompleta; copiar identidad y destino persistidos, sin regenerarlos.");
        var cliente=new GoogleCalendarClient(new CalendarProperties(true,destino,"diagnostico-solo-lectura",ruta,5000,10000));
        try {
            cliente.comprobarDestino(destino); // GET, antes de interpretar ausencia.
            var remoto=cliente.consultar(destino,identidad); // GET exclusivamente.
            System.out.println(remoto==null?"Identidad: AUSENTE en la consulta; no se creó nada.":
                    remoto.eliminado()?"Identidad: ELIMINADA; no se creó nada.":"Identidad: PRESENTE y propia; no se modificó nada.");
        } catch(ar.edu.utn.vastio.agenda.aplicacion.ClienteCalendar.Fallo error) {
            throw new AssertionError("Consulta de identidad no confirmada. Código sanitizado: "+error.codigo());
        }
    }
    @Test void obtieneTokenSinOperarCalendar() {
        var fase=DiagnosticoCalendar.Fase.CREDENCIALES;
        try {
            String ruta=System.getenv("VASTIO_CALENDAR_CREDENCIALES");
            if(ruta==null || ruta.isBlank()) throw new IllegalStateException("CONFIGURACION_AUSENTE");
            ServiceAccountCredentials credenciales;
            try(var archivo=Files.newInputStream(Path.of(ruta))) {
                credenciales=ServiceAccountCredentials.fromStream(archivo).toBuilder()
                        .setHttpTransportFactory(()->new TransporteTokenCalendar(new NetHttpTransport(),5000,10000))
                        .setDefaultRetriesEnabled(false)
                        .setScopes(List.of("https://www.googleapis.com/auth/calendar.events")).build();
            }
            // El diagnóstico solo permite el endpoint oficial: no enviar la aserción a destinos arbitrarios.
            if(!credenciales.getTokenServerUri().toString().equals("https://oauth2.googleapis.com/token"))
                throw new IllegalStateException("DESTINO_OAUTH_NO_ADMITIDO_POR_DIAGNOSTICO");
            fase=DiagnosticoCalendar.Fase.OAUTH_TOKEN;
            credenciales.refreshAccessToken();
            System.out.println("Diagnóstico OAuth: OK; token obtenido y no mostrado. Calendar no fue invocado.");
        } catch(Exception error) {
            var detalle=DiagnosticoCalendar.detalle(fase,error);
            boolean firmaZip=false;
            for(Throwable causa=error;causa!=null;causa=causa.getCause())
                if(causa instanceof java.util.zip.ZipException && "Not in GZIP format".equals(causa.getMessage())) { firmaZip=true;break; }
            // No adjuntar causa: los errores de bibliotecas pueden contener cuerpo HTTP/credenciales.
            throw new AssertionError("Diagnóstico OAuth sanitizado: "+detalle+"; firma ZipException Not in GZIP format="+firmaZip);
        }
    }
}
