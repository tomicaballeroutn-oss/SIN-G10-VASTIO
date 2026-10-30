package ar.edu.utn.vastio.agenda.infraestructura;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("vastio.calendar")
public record CalendarProperties(@DefaultValue("false") boolean habilitado, @DefaultValue("") String calendarId,
        @DefaultValue("") String instalacion, @DefaultValue("") String credenciales,
        @DefaultValue("5000") int conexionMs, @DefaultValue("10000") int lecturaMs) {
    public CalendarProperties {
        if (habilitado && (calendarId.isBlank() || "primary".equalsIgnoreCase(calendarId)
                || instalacion.isBlank() || instalacion.length() > 120 || calendarId.length() > 255 || credenciales.isBlank()
                || conexionMs < 100 || conexionMs > 30000 || lecturaMs < 100 || lecturaMs > 30000)) {
            throw new IllegalArgumentException("Configuración Calendar incompleta: destino explícito, instalación, archivo y timeouts válidos.");
        }
    }
}
