package ar.edu.utn.vastio.agenda.dominio;

import java.time.OffsetDateTime;

/** Estado actual persistido. La auditoría se guarda aparte, solo por inserción. */
public record PublicacionCalendar(long eventoId, String calendarId, String instalacion, String googleId,
        String estado, String pendiente, String huella, String huellaIntento, boolean retiroSolicitado, String etag, OffsetDateTime intento,
        OffsetDateTime exito, String error, Long usuarioId, String avisoClave, int version) { }
