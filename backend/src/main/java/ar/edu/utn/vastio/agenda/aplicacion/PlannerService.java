package ar.edu.utn.vastio.agenda.aplicacion;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.dominio.ModificacionEvento;
import ar.edu.utn.vastio.agenda.infraestructura.EventoRepository;
import ar.edu.utn.vastio.agenda.infraestructura.ModificacionEventoRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.usuarios.aplicacion.UsuarioService;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Asignar planner (UI-16): la responsable de la jornada. Coordinación y Dirección, en Contratado y Confirmado.
 * Se puede cambiar por otra; quitarla, solo antes de confirmar (Confirmado exige planner). Si la planner ya tiene
 * otro evento esa fecha, la pantalla lo advierte pero no se impide. Avisa a la nueva, a la anterior y a Administración.
 */
@Service
public class PlannerService {

    /** Estados en los que se asigna planner (docs/maquina-de-estados.md). */
    public static final Set<EstadoEvento> ESTADOS = EnumSet.of(EstadoEvento.CONTRATADO, EstadoEvento.CONFIRMADO);

    private final ConsultaEventoService consultas;
    private final EventoRepository eventos;
    private final ModificacionEventoRepository modificaciones;
    private final UsuarioService usuarios;
    private final AvisosEvento avisos;
    private final DescripcionEvento descripcion;
    private final EntityManager entityManager;

    public PlannerService(ConsultaEventoService consultas, EventoRepository eventos,
            ModificacionEventoRepository modificaciones, UsuarioService usuarios, AvisosEvento avisos,
            DescripcionEvento descripcion, EntityManager entityManager) {
        this.consultas = consultas;
        this.eventos = eventos;
        this.modificaciones = modificaciones;
        this.usuarios = usuarios;
        this.avisos = avisos;
        this.descripcion = descripcion;
        this.entityManager = entityManager;
    }

    /** Una planner activa y los otros eventos activos que ya tiene asignados la misma fecha. */
    public record Candidata(long id, String nombre, List<String> otrosEventos) {
    }

    @Transactional(readOnly = true)
    public List<Candidata> candidatas(long id, UsuarioActual quien) {
        Evento evento = consultas.visible(id, quien);
        if (!quien.accesoTotal()) {
            throw ConsultaEventoService.sinPermiso();
        }
        var fecha = evento.getUnidad().getFecha();
        List<Evento> esaFecha = eventos.activosEntre(fecha, fecha, EstadoEvento.INACTIVOS).stream()
                .filter(e -> !e.getId().equals(evento.getId()) && e.getPlannerId() != null)
                .toList();
        return usuarios.activosConPerfil(RolCodigo.PLANNER).stream()
                .map(p -> new Candidata(p.getId(), p.getNombreCompleto(), esaFecha.stream()
                        .filter(e -> p.getId().equals(e.getPlannerId()))
                        .map(descripcion::de)
                        .toList()))
                .toList();
    }

    /**
     * @param plannerId null quita la planner (solo en Contratado).
     */
    @Transactional
    public Evento asignar(long id, Long plannerId, UsuarioActual quien) {
        Evento evento = consultas.visible(id, quien);
        if (!quien.accesoTotal()) {
            throw ConsultaEventoService.sinPermiso();
        }
        if (!ESTADOS.contains(evento.getEstado())) {
            throw ProblemaException.reglaDeNegocio("ESTADO_NO_PERMITE",
                    "El evento está en %s: la planner se asigna a eventos contratados o confirmados.".formatted(
                            MaquinaDeEstados.nombre(evento.getEstado())));
        }
        if (plannerId == null && evento.getEstado() == EstadoEvento.CONFIRMADO) {
            throw ProblemaException.reglaDeNegocio("PLANNER_REQUERIDA",
                    "El evento está confirmado: necesita una planner. Podés cambiarla por otra.");
        }
        Long anterior = evento.getPlannerId();
        if (Objects.equals(anterior, plannerId)) {
            return evento;
        }
        Usuario nueva = null;
        if (plannerId != null) {
            nueva = usuarios.activosConPerfil(RolCodigo.PLANNER).stream()
                    .filter(u -> u.getId() == plannerId.longValue())
                    .findFirst()
                    .orElseThrow(() -> ProblemaException.reglaDeNegocio("PLANNER_INVALIDA", "Elegí una planner activa."));
        }
        // Una versión nueva: si justo otra persona cancelaba o confirmaba el evento, una de las dos recibe 409.
        entityManager.lock(evento, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        Map<Long, String> nombres = anterior == null ? Map.of() : usuarios.nombres(List.of(anterior));
        modificaciones.save(new ModificacionEvento(id, "planner", anterior == null ? null : nombres.get(anterior),
                nueva == null ? null : nueva.getNombreCompleto(), quien.id(), OffsetDateTime.now()));
        evento.asignarPlanner(plannerId);
        avisos.planner(evento, anterior, nueva == null ? null : nueva.getNombreCompleto(), quien.id());
        return evento;
    }
}
