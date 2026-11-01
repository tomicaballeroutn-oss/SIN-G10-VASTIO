package ar.edu.utn.vastio.agenda.aplicacion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;

/** Proyección explícita: nunca serializa la ficha ni los datos del cliente. */
@Component
public class ContenidoCalendar {
    private final CatalogoService catalogos;
    public ContenidoCalendar(CatalogoService catalogos) { this.catalogos = catalogos; }

    public record Contenido(String titulo, String descripcion, String salon, ZonedDateTime inicio,
            ZonedDateTime fin, String huella) { }

    public Contenido de(Evento evento) {
        var unidad = evento.getUnidad();
        var turno = catalogos.turno(unidad.getTurnoId());
        String salon = catalogos.salon(unidad.getSalonId()).getNombre();
        String tipo = catalogos.tipoEvento(evento.getTipoEventoId()).getNombre();
        var zona = ZoneId.of(VastioApplication.ZONA_HORARIA);
        var inicio = unidad.getFecha().atTime(turno.getHoraInicio()).atZone(zona);
        var fin = (turno.isCruzaMedianoche() ? unidad.getFecha().plusDays(1) : unidad.getFecha())
                .atTime(turno.getHoraFin()).atZone(zona);
        if (!fin.isAfter(inicio)) throw new IllegalStateException("TURNO_SIN_DURACION");
        String titulo = evento.getCodigo() + " · " + salon + " · " + turno.getNombre();
        String descripcion = "Tipo de evento: " + tipo + ".\nSalón: " + salon + ".\nTurno: " + turno.getNombre() + ".\n"
                + (evento.getHoraInicio() == null ? "" : "Hora particular de inicio: "
                    + evento.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm")) + ".\n")
                + "Código Vastio: " + evento.getCodigo() + ".";
        String huella = hash(String.join("\u0000", titulo, descripcion, salon, inicio.toString(), fin.toString()));
        return new Contenido(titulo, descripcion, salon, inicio, fin, huella);
    }

    public static String hash(String contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    /** SHA-256 hexadecimal: solo 0-9/a-f, admitidos por el ID base32hex de Calendar. */
    public static String identidad(String instalacion, long eventoId) {
        return hash(instalacion + "\u0000evento\u0000" + eventoId);
    }
}
