package ar.edu.utn.vastio.agenda.aplicacion;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.dominio.ServicioContratado;
import ar.edu.utn.vastio.agenda.infraestructura.ServicioContratadoRepository;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.CategoriaServicio;
import ar.edu.utn.vastio.usuarios.aplicacion.UsuarioService;

/**
 * Condiciones de «Confirmar evento» (docs/sprint-2.md, decisión 7): planner asignada, cantidad de invitados mayor a 0
 * y definitiva, servicios cargados en las categorías requeridas para confirmar y que la fecha no haya pasado.
 * La ficha las muestra en el diálogo (UI-15) y el caso de uso las vuelve a verificar.
 */
@Component
public class RequisitosConfirmacion {

    private static final ZoneId ZONA = ZoneId.of(VastioApplication.ZONA_HORARIA);

    private final ServicioContratadoRepository servicios;
    private final CatalogoService catalogos;
    private final UsuarioService usuarios;

    public RequisitosConfirmacion(ServicioContratadoRepository servicios, CatalogoService catalogos, UsuarioService usuarios) {
        this.servicios = servicios;
        this.catalogos = catalogos;
        this.usuarios = usuarios;
    }

    /**
     * @param codigo  PLANNER · INVITADOS · SERVICIOS · FECHA.
     * @param detalle lo que hay («Ana Sosa», «180») o lo que falta («Falta cargar Tipo de barra.»).
     */
    public record Requisito(String codigo, String titulo, boolean cumplido, String detalle) {
    }

    public List<Requisito> de(Evento evento) {
        return List.of(planner(evento), invitados(evento), servicios(evento), fecha(evento));
    }

    private Requisito planner(Evento evento) {
        if (evento.getPlannerId() == null) {
            return new Requisito("PLANNER", "Planner asignada", false, "Falta asignar la planner.");
        }
        String nombre = usuarios.nombres(List.of(evento.getPlannerId())).get(evento.getPlannerId());
        return new Requisito("PLANNER", "Planner asignada", true, nombre);
    }

    private static Requisito invitados(Evento evento) {
        Integer cantidad = evento.getCantidadInvitados();
        if (cantidad == null || cantidad <= 0) {
            return new Requisito("INVITADOS", "Cantidad definitiva de invitados", false, "Falta registrar la cantidad de invitados.");
        }
        if (!evento.isInvitadosDefinitivos()) {
            return new Requisito("INVITADOS", "Cantidad definitiva de invitados", false,
                    "Hay %d previstos: falta marcar la cantidad como definitiva.".formatted(cantidad));
        }
        return new Requisito("INVITADOS", "Cantidad definitiva de invitados", true, String.valueOf(cantidad));
    }

    private Requisito servicios(Evento evento) {
        List<CategoriaServicio> requeridas = catalogos.categorias().stream()
                .filter(c -> c.isActivo() && c.isRequeridaParaConfirmar())
                .toList();
        if (requeridas.isEmpty()) {
            return new Requisito("SERVICIOS", "Servicios requeridos", true, "Ninguna categoría es requerida.");
        }
        String titulo = requeridas.stream().map(CategoriaServicio::getNombre).collect(Collectors.joining(" y "));
        Set<Short> cargadas = servicios.findByEventoId(evento.getId()).stream()
                .map(ServicioContratado::getCategoriaId).collect(Collectors.toSet());
        Set<String> faltan = requeridas.stream().filter(c -> !cargadas.contains(c.getId()))
                .map(CategoriaServicio::getNombre).collect(Collectors.toCollection(LinkedHashSet::new));
        return faltan.isEmpty()
                ? new Requisito("SERVICIOS", titulo, true, "Cargado")
                : new Requisito("SERVICIOS", titulo, false, "Falta cargar " + String.join(" y ", faltan) + ".");
    }

    private static Requisito fecha(Evento evento) {
        boolean pasada = evento.getUnidad().getFecha().isBefore(LocalDate.now(ZONA));
        return new Requisito("FECHA", "Fecha del evento", !pasada, pasada ? "La fecha del evento ya pasó." : "Todavía no pasó.");
    }
}
