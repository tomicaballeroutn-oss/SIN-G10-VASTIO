package ar.edu.utn.vastio.agenda.infraestructura;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.ServiceAccountCredentials;
import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.agenda.aplicacion.ClienteCalendar;
import ar.edu.utn.vastio.agenda.aplicacion.ContenidoCalendar;
import ar.edu.utn.vastio.agenda.aplicacion.ContenidoCalendar.Contenido;

/** Cliente oficial. Carga perezosa, sin red al arrancar y sin credenciales en el navegador o logs. */
@Component
@EnableConfigurationProperties(CalendarProperties.class)
public class GoogleCalendarClient implements ClienteCalendar {
    private final CalendarProperties config;
    private volatile Calendar api;
    @org.springframework.beans.factory.annotation.Autowired
    public GoogleCalendarClient(CalendarProperties config) { this.config = config; }
    GoogleCalendarClient(CalendarProperties config, Calendar api) { this.config = config; this.api = api; }
    public boolean configurado() { return config.habilitado(); }

    private Calendar api() {
        if (!config.habilitado()) throw new Fallo("CALENDAR_NO_CONFIGURADO", false);
        if (api == null) synchronized (this) {
            if (api == null) {
                try (var archivo = Files.newInputStream(Path.of(config.credenciales()))) {
                    var credenciales = ServiceAccountCredentials.fromStream(archivo)
                            .toBuilder().setHttpTransportFactory(() -> new TransporteTokenCalendar(
                                    new NetHttpTransport(), config.conexionMs(), config.lecturaMs()))
                            .setDefaultRetriesEnabled(false).build()
                            .createScoped(List.of("https://www.googleapis.com/auth/calendar.events"));
                    var auth = new HttpCredentialsAdapter(credenciales);
                    api = new Calendar.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), inicializador(auth, config))
                            .setApplicationName("Vastio M-06").build();
                } catch (IOException | RuntimeException e) {
                    DiagnosticoCalendar.registrar(DiagnosticoCalendar.Fase.CREDENCIALES, e);
                    throw new Fallo("CREDENCIALES_NO_DISPONIBLES", false);
                }
            }
        }
        return api;
    }

    static com.google.api.client.http.HttpRequestInitializer inicializador(HttpCredentialsAdapter auth, CalendarProperties config) {
        return request -> {
            // El adaptador autentica en initialize(); no instala un HttpExecuteInterceptor.
            try { auth.initialize(request); }
            catch (IOException | RuntimeException e) { throw new DiagnosticoCalendar.FalloOAuth(e); }
            request.setConnectTimeout(config.conexionMs());
            request.setReadTimeout(config.lecturaMs());
            request.setNumberOfRetries(0); // Reconciliar antes de volver a escribir.
            request.setLoggingEnabled(false);
            request.setCurlLoggingEnabled(false);
        };
    }

    public void comprobarDestino(String calendario) {
        try { api().events().list(calendario).setMaxResults(1).setFields("items(id)").execute(); }
        catch (IOException e) { throw fallo(DiagnosticoCalendar.Fase.CALENDAR_LISTAR,e, false); }
    }
    public Remoto consultar(String calendario, String id) {
        try { return remoto(api().events().get(calendario, id).execute(), id); }
        catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404 || e.getStatusCode() == 410) return null;
            throw fallo(DiagnosticoCalendar.Fase.CALENDAR_CONSULTAR,e, false);
        } catch (IOException e) { throw fallo(DiagnosticoCalendar.Fase.CALENDAR_CONSULTAR,e, false); }
    }
    public Remoto crear(String calendario, String id, Contenido contenido) {
        try { return interpretarRespuesta(api().events().insert(calendario, evento(id, contenido)).execute(), id); }
        catch (IOException e) { throw fallo(DiagnosticoCalendar.Fase.CALENDAR_CREAR,e, true); }
    }
    public Remoto actualizar(String calendario, String id, Contenido contenido, String etag) {
        try {
            var pedido = api().events().update(calendario, id, evento(id, contenido));
            pedido.getRequestHeaders().setIfMatch(etag);
            return interpretarRespuesta(pedido.execute(), id);
        } catch (IOException e) { throw fallo(DiagnosticoCalendar.Fase.CALENDAR_ACTUALIZAR,e, true); }
    }
    public void retirar(String calendario, String id, String etag) {
        try {
            var pedido = api().events().delete(calendario, id);
            pedido.getRequestHeaders().setIfMatch(etag);
            pedido.execute();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() != 410) throw fallo(DiagnosticoCalendar.Fase.CALENDAR_RETIRAR,e, true);
        } catch (IOException e) { throw fallo(DiagnosticoCalendar.Fase.CALENDAR_RETIRAR,e, true); }
    }

    static Event evento(String id, Contenido c) {
        return new Event().setId(id).setSummary(c.titulo()).setDescription(c.descripcion()).setLocation(c.salon())
                .setStart(new EventDateTime().setDateTime(new DateTime(c.inicio().toOffsetDateTime().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME))).setTimeZone(VastioApplication.ZONA_HORARIA))
                .setEnd(new EventDateTime().setDateTime(new DateTime(c.fin().toOffsetDateTime().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME))).setTimeZone(VastioApplication.ZONA_HORARIA))
                .setExtendedProperties(new Event.ExtendedProperties().setPrivate(Map.of("vastio-id", id)))
                .setReminders(new Event.Reminders().setUseDefault(false));
    }
    static Remoto remoto(Event e, String id) {
        if ("cancelled".equals(e.getStatus())) return new Remoto(e.getEtag(), null, true);
        if (e.getExtendedProperties() == null || e.getExtendedProperties().getPrivate() == null
                || !id.equals(e.getExtendedProperties().getPrivate().get("vastio-id"))) throw new Fallo("IDENTIDAD_REMOTA_AJENA", false);
        try {
            var zona = ZoneId.of(VastioApplication.ZONA_HORARIA);
            var inicio = OffsetDateTime.parse(e.getStart().getDateTime().toStringRfc3339()).atZoneSameInstant(zona);
            var fin = OffsetDateTime.parse(e.getEnd().getDateTime().toStringRfc3339()).atZoneSameInstant(zona);
            String hash = ContenidoCalendar.hash(String.join("\u0000", e.getSummary(), e.getDescription(), e.getLocation(), inicio.toString(), fin.toString()));
            return new Remoto(e.getEtag(), hash, false);
        } catch (RuntimeException ex) { throw new Fallo("CONTENIDO_REMOTO_MODIFICADO", false); }
    }

    private static Remoto interpretarRespuesta(Event respuesta, String id) {
        // execute() ya devolvió una respuesta de escritura. Un fallo al interpretarla no prueba ausencia.
        try {
            return remoto(respuesta, id);
        } catch (Fallo fallo) {
            throw new Fallo(fallo.codigo(), true);
        } catch (RuntimeException inesperado) {
            DiagnosticoCalendar.registrar(DiagnosticoCalendar.Fase.CALENDAR_RESPUESTA, inesperado);
            throw new Fallo("EJECUCION_CALENDAR_FALLIDA", true);
        }
    }
    private static Fallo fallo(DiagnosticoCalendar.Fase fase, IOException e, boolean escritura) {
        DiagnosticoCalendar.registrar(fase, e);
        return traducir(e, escritura);
    }
    static Fallo traducir(IOException e, boolean escritura) {
        // initialize() autentica antes de que el transporte envíe la petición Calendar.
        if (e instanceof DiagnosticoCalendar.FalloOAuth) return new Fallo("OAUTH_GOOGLE", false);
        if (e instanceof GoogleJsonResponseException g) {
            int status = g.getStatusCode();
            String reason = g.getDetails() != null && g.getDetails().getErrors() != null && !g.getDetails().getErrors().isEmpty()
                    ? g.getDetails().getErrors().getFirst().getReason() : "";
            String codigo = switch (status) {
                case 400 -> "DATOS_CALENDAR_INVALIDOS";
                case 401 -> "AUTORIZACION_GOOGLE_REVOCADA";
                case 403 -> reason != null && (reason.contains("Limit") || reason.contains("quota")) ? "CUOTA_GOOGLE" : "PERMISO_GOOGLE_INSUFICIENTE";
                case 404 -> "DESTINO_NO_ACCESIBLE";
                case 409 -> "ID_EXISTENTE";
                case 412 -> "CONFLICTO_REMOTO";
                case 429 -> "CUOTA_GOOGLE";
                default -> status >= 500 ? "GOOGLE_NO_DISPONIBLE" : "ERROR_GOOGLE";
            };
            return new Fallo(codigo, escritura && status >= 500);
        }
        return new Fallo("RED_GOOGLE", escritura);
    }
}
