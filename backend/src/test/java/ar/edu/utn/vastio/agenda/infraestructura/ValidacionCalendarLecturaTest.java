package ar.edu.utn.vastio.agenda.infraestructura;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.ServiceAccountCredentials;

/** Aceptación optativa: SOLO GET de calendario aislado/identidad guardada; sin Spring ni DB. */
@EnabledIfEnvironmentVariable(named="VASTIO_VALIDACION_SOLO_LECTURA",matches="true")
class ValidacionCalendarLecturaTest {
    static String textoOpcional(com.google.api.client.json.GenericJson fuente,String campo) {
        Object valor=fuente.get(campo);
        return valor==null || com.google.api.client.util.Data.isNull(valor)?null:(String)valor;
    }
    @Test void compruebaDestinoIdentidadYContenidoSinEscribir() {
        var fase=DiagnosticoCalendar.Fase.CREDENCIALES;
        try {
            String destino=System.getenv("VASTIO_CALENDAR_ID"),id=System.getenv("VASTIO_DIAGNOSTICO_GOOGLE_ID");
            if(destino==null || id==null || !id.matches("[0-9a-f]{64}")) throw new AssertionError("IDENTIDAD_INCOMPLETA");
            ServiceAccountCredentials credenciales;
            try(var entrada=Files.newInputStream(Path.of(System.getenv("VASTIO_CALENDAR_CREDENCIALES")))) {
                credenciales=ServiceAccountCredentials.fromStream(entrada).toBuilder()
                    .setHttpTransportFactory(()->new TransporteTokenCalendar(new NetHttpTransport(),5000,10000))
                    .setDefaultRetriesEnabled(false).setScopes(List.of("https://www.googleapis.com/auth/calendar.events")).build();
            }
            if(!credenciales.getTokenServerUri().toString().equals("https://oauth2.googleapis.com/token")) throw new AssertionError("DESTINO_OAUTH_NO_ADMITIDO");
            fase=DiagnosticoCalendar.Fase.OAUTH_TOKEN;credenciales.refresh();
            var auth=new HttpCredentialsAdapter(credenciales);
            var api=new Calendar.Builder(new NetHttpTransport(),GsonFactory.getDefaultInstance(),r->{
                auth.initialize(r);r.setConnectTimeout(5000);r.setReadTimeout(10000);r.setNumberOfRetries(0);
                r.setLoggingEnabled(false);r.setCurlLoggingEnabled(false);
            }).setApplicationName("Vastio M-06 validación solo lectura").build();
            fase=DiagnosticoCalendar.Fase.CALENDAR_LISTAR;
            var calendario=api.events().list(destino).setMaxResults(1).setFields("summary,accessRole,timeZone").execute();
            if(!"Vastio - Pruebas".equals(calendario.getSummary())) throw new AssertionError("DESTINO_NO_ES_EL_CALENDARIO_DE_PRUEBA");
            var resultado=new LinkedHashMap<String,Object>();
            resultado.put("calendario",calendario.getSummary());resultado.put("rol",calendario.getAccessRole());
            resultado.put("puedeEscribirEventosNoPrivados",Set.of("owner","writer","writerWithoutPrivateAccess").contains(calendario.getAccessRole()));
            fase=DiagnosticoCalendar.Fase.CALENDAR_CONSULTAR;
            Event remoto=null;String estado="INCIERTO";
            try {
                remoto=api.events().get(destino,id).setFields("id,status,summary,description,location,start,end,extendedProperties,attendees,attachments").execute();
                estado="cancelled".equals(remoto.getStatus())?"ELIMINADA":"PRESENTE";
            } catch(GoogleJsonResponseException e) {
                if(e.getStatusCode()==404) estado="AUSENTE";
                else if(e.getStatusCode()==410) estado="ELIMINADA";
                else throw e;
            }
            resultado.put("identidad",estado);
            if("PRESENTE".equals(estado)) {
                if(remoto.getExtendedProperties()==null || remoto.getExtendedProperties().getPrivate()==null
                        || !id.equals(remoto.getExtendedProperties().getPrivate().get("vastio-id"))) throw new AssertionError("IDENTIDAD_AJENA");
                var fuente=GsonFactory.getDefaultInstance().fromString(Files.readString(Path.of(System.getenv("VASTIO_VALIDACION_FUENTE"))),com.google.api.client.json.GenericJson.class);
                var zona=ZoneId.of("America/Argentina/Cordoba");
                var fecha=LocalDate.parse((String)fuente.get("fecha"));
                var inicio=fecha.atTime(LocalTime.parse((String)fuente.get("inicio"))).atZone(zona);
                var fin=fecha.plusDays(Boolean.TRUE.equals(fuente.get("cruza"))?1:0).atTime(LocalTime.parse((String)fuente.get("fin"))).atZone(zona);
                String titulo=fuente.get("codigo")+" · "+fuente.get("salon")+" · "+fuente.get("turno");
                String particular=textoOpcional(fuente,"particular");
                String descripcion="Tipo de evento: "+fuente.get("tipo")+".\nSalón: "+fuente.get("salon")+".\nTurno: "+fuente.get("turno")+".\n"
                    +(particular==null?"":"Hora particular de inicio: "+LocalTime.parse(particular).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))+".\n")
                    +"Código Vastio: "+fuente.get("codigo")+".";
                boolean coincide=titulo.equals(remoto.getSummary()) && descripcion.equals(remoto.getDescription()) && fuente.get("salon").equals(remoto.getLocation())
                    && inicio.toInstant().toEpochMilli()==remoto.getStart().getDateTime().getValue() && fin.toInstant().toEpochMilli()==remoto.getEnd().getDateTime().getValue()
                    && zona.getId().equals(remoto.getStart().getTimeZone()) && zona.getId().equals(remoto.getEnd().getTimeZone())
                    && (remoto.getAttendees()==null || remoto.getAttendees().isEmpty()) && (remoto.getAttachments()==null || remoto.getAttachments().isEmpty());
                resultado.put("contenidoPublicoExacto",coincide);resultado.put("intervalo",inicio+" / "+fin);
                if(!coincide) throw new AssertionError("CONTENIDO_DIFIERE_DE_PROYECCION_PUBLICA");
            }
            fase=DiagnosticoCalendar.Fase.CALENDAR_LISTAR;
            var coincidencias=api.events().list(destino).setPrivateExtendedProperty(List.of("vastio-id="+id)).setShowDeleted(false)
                .setMaxResults(2500).setFields("items(id),nextPageToken").execute();
            if(coincidencias.getNextPageToken()!=null) throw new AssertionError("LECTURA_DE_IDENTIDADES_INCOMPLETA");
            resultado.put("entradasPropiasActivas",coincidencias.getItems()==null?0:coincidencias.getItems().size());
            String json=GsonFactory.getDefaultInstance().toString(resultado);
            Files.writeString(Path.of(System.getenv("VASTIO_VALIDACION_EVIDENCIA")),json);
            System.out.println("Lectura Calendar sanitizada: "+json);
        } catch(Exception error) {
            throw new AssertionError("Lectura Calendar no confirmada: "+DiagnosticoCalendar.detalle(fase,error));
        }
    }
}
