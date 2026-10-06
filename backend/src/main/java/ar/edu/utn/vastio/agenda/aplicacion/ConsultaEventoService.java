package ar.edu.utn.vastio.agenda.aplicacion;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.agenda.dominio.CambioEstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.ContactoEvento;
import ar.edu.utn.vastio.agenda.dominio.DocumentoEvento;
import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.dominio.ModificacionEvento;
import ar.edu.utn.vastio.agenda.dominio.ServicioContratado;
import ar.edu.utn.vastio.agenda.infraestructura.CambioEstadoEventoRepository;
import ar.edu.utn.vastio.agenda.infraestructura.DocumentoEventoRepository;
import ar.edu.utn.vastio.agenda.infraestructura.EventoRepository;
import ar.edu.utn.vastio.agenda.infraestructura.ModificacionEventoRepository;
import ar.edu.utn.vastio.agenda.infraestructura.ServicioContratadoRepository;
import ar.edu.utn.vastio.comun.errores.Mensajes;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.usuarios.aplicacion.UsuarioService;

/**
 * Consultar evento (UI-09): la ficha con su historial y la lista de próximos eventos.
 */
@Service
@Transactional(readOnly = true)
public class ConsultaEventoService {

    private static final ZoneId ZONA = ZoneId.of(VastioApplication.ZONA_HORARIA);

    /** Ven todos los eventos en la lista. La vendedora, solo los suyos («Mis eventos»). */
    private static final Set<String> VEN_TODOS = Set.of("DIRECCION", "COORDINACION", "ADMINISTRACION", "PLANNER", "COMPRAS");

    /** Estados en los que se registran o modifican los datos del evento. */
    public static final Set<EstadoEvento> EDITABLES = EnumSet.of(EstadoEvento.PRE_RESERVA, EstadoEvento.SENADO,
            EstadoEvento.CONTRATADO, EstadoEvento.CONFIRMADO);

    private final EventoRepository eventos;
    private final CambioEstadoEventoRepository historial;
    private final ModificacionEventoRepository modificaciones;
    private final DocumentoEventoRepository documentos;
    private final ServicioContratadoRepository servicios;
    private final RequisitosConfirmacion requisitos;
    private final UsuarioService usuarios;

    public ConsultaEventoService(EventoRepository eventos, CambioEstadoEventoRepository historial,
            ModificacionEventoRepository modificaciones, DocumentoEventoRepository documentos,
            ServicioContratadoRepository servicios, RequisitosConfirmacion requisitos, UsuarioService usuarios) {
        this.requisitos = requisitos;
        this.eventos = eventos;
        this.historial = historial;
        this.modificaciones = modificaciones;
        this.documentos = documentos;
        this.servicios = servicios;
        this.usuarios = usuarios;
    }

    /** Lo que la persona puede hacer con el evento según su perfil y el estado actual. */
    public record Acciones(boolean modificar, boolean liberar, boolean registrarSena, boolean registrarFirma,
            boolean asignarPlanner, boolean confirmar, boolean cancelar) {
    }

    /**
     * Contactos y modificaciones se copian dentro de la transacción: la respuesta se arma después.
     *
     * @param documentos null si la persona no ve el legajo (el contrato tiene importes).
     * @param requisitos condiciones para confirmar; null salvo en Contratado.
     */
    public record Ficha(Evento evento, boolean veImportes, Acciones acciones, List<ContactoEvento> contactos,
            List<CambioEstadoEvento> cambios, List<ModificacionEvento> modificaciones, List<DocumentoEvento> documentos,
            List<ServicioContratado> servicios, List<RequisitosConfirmacion.Requisito> requisitos, Map<Long, String> nombres) {
    }

    /**
     * @throws ProblemaException 404 si no existe; 403 si es de otra vendedora y el perfil no ve todos.
     */
    public Ficha ficha(long id, UsuarioActual quien) {
        Evento evento = visible(id, quien);
        List<CambioEstadoEvento> cambios = historial.findByEventoIdOrderByFechaHoraAscIdAsc(id);
        List<ModificacionEvento> datos = modificaciones.findByEventoIdOrderByFechaHoraAscIdAsc(id);
        boolean veImportes = AccesoEvento.veImportes(quien, evento);
        List<DocumentoEvento> legajo = veImportes ? documentos.findByEventoIdOrderByIdAsc(id) : null;
        List<ServicioContratado> contratados = servicios.findByEventoId(id);
        Set<Long> personas = new HashSet<>();
        personas.add(evento.getVendedoraId());
        if (evento.getPlannerId() != null) {
            personas.add(evento.getPlannerId());
        }
        cambios.stream().map(CambioEstadoEvento::getUsuarioId).filter(u -> u != null).forEach(personas::add);
        datos.forEach(m -> personas.add(m.getUsuarioId()));
        if (legajo != null) {
            legajo.forEach(d -> personas.add(d.getUsuarioId()));
        }
        contratados.forEach(s -> personas.add(s.getUsuarioId()));
        return new Ficha(evento, veImportes, acciones(evento, quien), List.copyOf(evento.getContactos()), cambios, datos,
                legajo, contratados, evento.getEstado() == EstadoEvento.CONTRATADO ? requisitos.de(evento) : null,
                usuarios.nombres(personas));
    }

    public static Acciones acciones(Evento evento, UsuarioActual quien) {
        boolean preReserva = evento.getEstado() == EstadoEvento.PRE_RESERVA;
        boolean titular = AccesoEvento.puedeOperarComoTitular(quien, evento);
        return new Acciones(EDITABLES.contains(evento.getEstado()) && AccesoEvento.puedeModificar(quien, evento),
                preReserva && titular, preReserva && titular,
                evento.getEstado() == EstadoEvento.SENADO && quien.accesoTotal(),
                PlannerService.ESTADOS.contains(evento.getEstado()) && quien.accesoTotal(),
                evento.getEstado() == EstadoEvento.CONTRATADO && AccesoEvento.puedeConfirmar(quien, evento),
                CancelacionService.ESTADOS.contains(evento.getEstado()) && quien.accesoTotal());
    }

    /** El evento, si existe y la persona puede ver su detalle. */
    public Evento visible(long id, UsuarioActual quien) {
        Evento evento = eventos.conDetalle(id).orElseThrow(() -> ProblemaException.noEncontrado("EVENTO_INEXISTENTE",
                "No encontramos ese evento."));
        if (!AccesoEvento.veDetalle(quien, evento)) {
            throw new ProblemaException(HttpStatus.FORBIDDEN, "EVENTO_DE_OTRA_VENDEDORA", "Sin permiso",
                    "Ese evento es de otra vendedora: solo podés ver quién tiene la fecha en la agenda.");
        }
        return evento;
    }

    /**
     * Eventos activos desde hoy. La vendedora ve los suyos («Mis eventos»); el resto de los perfiles con acceso,
     * incluida la planner, todos.
     */
    public Proximos proximos(UsuarioActual quien) {
        LocalDate hoy = LocalDate.now(ZONA);
        List<Evento> lista = eventos.activosDesde(hoy, EstadoEvento.INACTIVOS).stream()
                .filter(e -> quien.tieneAlguno(VEN_TODOS) || AccesoEvento.esTitular(quien, e))
                .toList();
        Set<Long> personas = new HashSet<>();
        lista.forEach(e -> {
            personas.add(e.getVendedoraId());
            if (e.getPlannerId() != null) {
                personas.add(e.getPlannerId());
            }
        });
        return new Proximos(lista, usuarios.nombres(personas));
    }

    public record Proximos(List<Evento> eventos, Map<Long, String> nombres) {
    }

    /** Para mensajes: el evento no está en un estado que permita la operación. */
    public static ProblemaException noPermiteEnEsteEstado(Evento evento, String operacion) {
        return ProblemaException.reglaDeNegocio("ESTADO_NO_PERMITE",
                "El evento está en %s: no se puede %s.".formatted(MaquinaDeEstados.nombre(evento.getEstado()), operacion));
    }

    /** Para mensajes: la persona no puede operar sobre este evento. */
    public static ProblemaException sinPermiso() {
        return new ProblemaException(HttpStatus.FORBIDDEN, "SIN_PERMISO", "Sin permiso", Mensajes.SIN_PERMISO);
    }
}
