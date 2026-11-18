package ar.edu.utn.vastio.agenda.aplicacion;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import ar.edu.utn.vastio.agenda.aplicacion.RequisitosConfirmacion.Requisito;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.dominio.ServicioContratado;
import ar.edu.utn.vastio.agenda.infraestructura.ServicioContratadoRepository;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.CategoriaServicio;

/**
 * Condiciones de «Registrar seña» (A3-01): la cantidad de invitados —que puede ser tentativa y editable— y los servicios
 * cargados en las categorías marcadas como requeridas para señar (el menú y la bodega). Son los datos a partir de los cuales
 * la organización determina el precio y, por lo tanto, la seña. El sistema no calcula el precio: solo valida que el dato esté
 * antes de dejar señar. La ficha las muestra en el diálogo (UI-11) y el caso de uso las vuelve a verificar.
 */
@Component
public class RequisitosSena {

    private final ServicioContratadoRepository servicios;
    private final CatalogoService catalogos;

    public RequisitosSena(ServicioContratadoRepository servicios, CatalogoService catalogos) {
        this.servicios = servicios;
        this.catalogos = catalogos;
    }

    public List<Requisito> de(Evento evento) {
        return List.of(invitados(evento), servicios(evento));
    }

    /** A diferencia de confirmar, en la seña alcanza con un número mayor a 0: puede ser tentativo y seguir editándose. */
    private static Requisito invitados(Evento evento) {
        Integer cantidad = evento.getCantidadInvitados();
        if (cantidad == null || cantidad <= 0) {
            return new Requisito("INVITADOS", "Cantidad de invitados", false, "Falta registrar la cantidad de invitados.");
        }
        return new Requisito("INVITADOS", "Cantidad de invitados", true, String.valueOf(cantidad));
    }

    private Requisito servicios(Evento evento) {
        List<CategoriaServicio> requeridas = catalogos.categorias().stream()
                .filter(c -> c.isActivo() && c.isRequeridaParaSenar())
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
}
