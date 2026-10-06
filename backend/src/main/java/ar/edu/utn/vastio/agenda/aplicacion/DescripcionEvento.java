package ar.edu.utn.vastio.agenda.aplicacion;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.dominio.UnidadComercializable;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;

/**
 * Texto corto de un evento para avisos: «Quince de Delfina Ríos · Avril · sáb 10/10 · Noche».
 */
@Component
public class DescripcionEvento {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("EEE d/M", Locale.forLanguageTag("es-AR"));
    private static final int LARGO_MAXIMO = 230;

    private final CatalogoService catalogos;

    public DescripcionEvento(CatalogoService catalogos) {
        this.catalogos = catalogos;
    }

    public String de(Evento evento) {
        String texto = evento.getNombre() + " · " + unidad(evento.getUnidad());
        return texto.length() <= LARGO_MAXIMO ? texto : texto.substring(0, LARGO_MAXIMO - 1) + "…";
    }

    /** «Avril · sáb 10/10 · Noche». */
    public String unidad(UnidadComercializable unidad) {
        return String.join(" · ", catalogos.salon(unidad.getSalonId()).getNombre(),
                FECHA.format(unidad.getFecha()).replace(".", ""), catalogos.turno(unidad.getTurnoId()).getNombre());
    }
}
