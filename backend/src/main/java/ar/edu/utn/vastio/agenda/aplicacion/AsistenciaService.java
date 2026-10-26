package ar.edu.utn.vastio.agenda.aplicacion;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.agenda.dominio.AsistenciaSegmento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.dominio.ModificacionEvento;
import ar.edu.utn.vastio.agenda.infraestructura.AsistenciaSegmentoRepository;
import ar.edu.utn.vastio.agenda.infraestructura.ModificacionEventoRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.TipoSegmentoAsistencia;

/**
 * Registrar tipo de asistencia (UI-19, docs/sprint-4.md, decisiones 1 a 5): la asistencia prevista por segmento de un
 * evento de egresados. La registran la planner asignada, Coordinación y Dirección, entre Pre-reserva y Confirmado.
 * Cada segmento que cambia queda en {@code modificacion_evento} como {@code asistencia_prevista.<segmento>}; en un
 * evento confirmado avisa a Compras, porque cambia la bebida a preparar.
 */
@Service
public class AsistenciaService {

    /** Prefijo de {@code modificacion_evento.campo}. */
    public static final String CAMPO_PREVISTA = "asistencia_prevista.";

    private final ConsultaEventoService consultas;
    private final AsistenciaPorSegmento lectura;
    private final AsistenciaSegmentoRepository asistencias;
    private final ModificacionEventoRepository modificaciones;
    private final CatalogoService catalogos;
    private final AvisosEvento avisos;
    private final EntityManager entityManager;

    public AsistenciaService(ConsultaEventoService consultas, AsistenciaPorSegmento lectura,
            AsistenciaSegmentoRepository asistencias, ModificacionEventoRepository modificaciones, CatalogoService catalogos,
            AvisosEvento avisos, EntityManager entityManager) {
        this.consultas = consultas;
        this.lectura = lectura;
        this.asistencias = asistencias;
        this.modificaciones = modificaciones;
        this.catalogos = catalogos;
        this.avisos = avisos;
        this.entityManager = entityManager;
    }

    /**
     * @param version    la que tenía la ficha al abrir el diálogo: si otra persona guardó en el medio, 409.
     * @param cantidades por segmento; null lo deja sin previsto. Los segmentos que no vienen no cambian.
     */
    @Transactional
    public Evento registrarPrevista(long id, int version, Map<Short, Integer> cantidades, UsuarioActual quien) {
        Evento evento = consultas.visible(id, quien);
        if (!AccesoEvento.puedeRegistrarAsistencia(quien, evento)) {
            throw ConsultaEventoService.sinPermiso();
        }
        if (!lectura.usaSegmentos(evento)) {
            throw sinSegmentos();
        }
        if (!ConsultaEventoService.EDITABLES.contains(evento.getEstado())) {
            throw ConsultaEventoService.noPermiteEnEsteEstado(evento, "registrar la asistencia prevista");
        }
        if (evento.getVersion() != version) {
            throw ProblemaException.conflicto("EVENTO_MODIFICADO",
                    "Otra persona modificó el evento mientras lo editabas. Volvé a abrir la ficha para ver los cambios.");
        }
        Map<Short, TipoSegmentoAsistencia> segmentos = catalogos.segmentos().stream()
                .collect(Collectors.toMap(TipoSegmentoAsistencia::getId, Function.identity()));
        cantidades.keySet().forEach(segmentoId -> validar(segmentos.get(segmentoId)));
        entityManager.lock(evento, LockModeType.PESSIMISTIC_FORCE_INCREMENT);

        Map<Short, AsistenciaSegmento> cargadas = asistencias.findByEventoId(id).stream()
                .collect(Collectors.toMap(AsistenciaSegmento::getSegmentoId, Function.identity()));
        OffsetDateTime momento = OffsetDateTime.now();
        List<ModificacionEvento> filas = new ArrayList<>();
        cantidades.forEach((segmentoId, cantidad) -> {
            AsistenciaSegmento fila = cargadas.get(segmentoId);
            Integer antes = fila == null ? null : fila.getCantidadPrevista();
            if (Objects.equals(antes, cantidad)) {
                return;
            }
            if (fila == null) {
                fila = asistencias.save(new AsistenciaSegmento(id, segmentoId));
            }
            fila.setCantidadPrevista(cantidad);
            filas.add(new ModificacionEvento(id, CAMPO_PREVISTA + segmentos.get(segmentoId).getNombre(), texto(antes),
                    texto(cantidad), quien.id(), momento));
        });
        modificaciones.saveAll(filas);
        avisos.modificacion(evento, filas.stream().map(ModificacionEvento::getCampo).toList(), false, true, quien.id());
        return evento;
    }

    private static void validar(TipoSegmentoAsistencia segmento) {
        if (segmento == null || !segmento.isActivo()) {
            throw ProblemaException.reglaDeNegocio("SEGMENTO_INVALIDO",
                    "Ese segmento no existe o está dado de baja. Volvé a abrir la ficha.");
        }
        if (AsistenciaPorSegmento.equivaleInvitados(segmento)) {
            throw ProblemaException.reglaDeNegocio("SEGMENTO_INVALIDO",
                    "«%s» es la cantidad de invitados del evento: cambiala desde «Cantidad de invitados»."
                            .formatted(segmento.getNombre()));
        }
    }

    static ProblemaException sinSegmentos() {
        return ProblemaException.reglaDeNegocio("SIN_SEGMENTOS",
                "Este tipo de evento no cuenta la asistencia por segmento.");
    }

    private static String texto(Integer cantidad) {
        return cantidad == null ? null : cantidad.toString();
    }
}
