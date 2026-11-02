package ar.edu.utn.vastio.agenda.aplicacion;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.agenda.dominio.AsistenciaSegmento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.infraestructura.AsistenciaSegmentoRepository;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.TipoSegmentoAsistencia;

/**
 * Lectura de la asistencia por segmento de un evento (docs/sprint-4.md, decisiones 1 a 4). Solo los tipos de evento
 * que la cuentan por partes (egresados) la tienen. El segmento que equivale a los invitados toma como previsto la
 * cantidad de invitados del evento: en egresados, los comensales de la cena.
 */
@Component
@Transactional(readOnly = true)
public class AsistenciaPorSegmento {

    private final AsistenciaSegmentoRepository asistencias;
    private final CatalogoService catalogos;

    public AsistenciaPorSegmento(AsistenciaSegmentoRepository asistencias, CatalogoService catalogos) {
        this.asistencias = asistencias;
        this.catalogos = catalogos;
    }

    /**
     * Un segmento del evento. {@code equivaleInvitados}: su previsto es la cantidad de invitados y no se carga aparte.
     * {@code activo} falso: dado de baja con algo cargado (se ve, no se edita).
     */
    public record Segmento(short id, String nombre, boolean activo, boolean equivaleInvitados, Integer prevista) {
    }

    /** {@code totalPrevisto}: la suma de lo previsto; null si no hay nada. */
    public record Asistencia(List<Segmento> segmentos, Integer totalPrevisto) {
    }

    public boolean usaSegmentos(Evento evento) {
        return catalogos.tipoEvento(evento.getTipoEventoId()).isUsaSegmentos();
    }

    /** Null si el tipo de evento no cuenta la asistencia por segmento. */
    public Asistencia de(Evento evento) {
        if (!usaSegmentos(evento)) {
            return null;
        }
        Map<Short, AsistenciaSegmento> cargada = asistencias.findByEventoId(evento.getId()).stream()
                .collect(Collectors.toMap(AsistenciaSegmento::getSegmentoId, Function.identity()));
        List<Segmento> segmentos = catalogos.segmentos().stream()
                .filter(s -> s.isActivo() || tieneDatos(cargada.get(s.getId())))
                .map(s -> segmento(s, cargada.get(s.getId()), evento))
                .toList();
        List<Integer> previstas = segmentos.stream().map(Segmento::prevista).filter(Objects::nonNull).toList();
        return new Asistencia(segmentos, previstas.isEmpty() ? null : previstas.stream().mapToInt(Integer::intValue).sum());
    }

    /** El segmento activo cuyo previsto es la cantidad de invitados, si hay uno. */
    public static boolean equivaleInvitados(TipoSegmentoAsistencia segmento) {
        return segmento.isActivo() && segmento.isEquivaleInvitados();
    }

    private static Segmento segmento(TipoSegmentoAsistencia s, AsistenciaSegmento cargada, Evento evento) {
        boolean equivale = equivaleInvitados(s);
        Integer prevista = equivale ? evento.getCantidadInvitados() : cargada == null ? null : cargada.getCantidadPrevista();
        return new Segmento(s.getId(), s.getNombre(), s.isActivo(), equivale, prevista);
    }

    private static boolean tieneDatos(AsistenciaSegmento cargada) {
        return cargada != null && (cargada.getCantidadPrevista() != null || cargada.getCantidadReal() != null);
    }
}
