package ar.edu.utn.vastio.agenda.api;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.agenda.api.EventoDto.RenglonDto;
import ar.edu.utn.vastio.agenda.aplicacion.CocinaService;
import ar.edu.utn.vastio.agenda.aplicacion.CocinaService.EventoCocina;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.dominio.ServicioContratado;
import ar.edu.utn.vastio.comun.seguridad.Permisos;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.CategoriaServicio;
import ar.edu.utn.vastio.configuracion.dominio.Salon;
import ar.edu.utn.vastio.configuracion.dominio.Turno;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Consultar vista de cocina (A7-02, UI-23). La consultan Dirección, Coordinación, Planner, Compras y Cocina, según la
 * matriz de docs/perfiles-y-permisos.md. Las respuestas llevan solo lo gastronómico: ni estado, ni importes, ni
 * observaciones internas, ni datos del cliente.
 */
@RestController
@RequestMapping("/api/v1/cocina")
@Tag(name = "Vista de cocina", description = "Eventos confirmados con lo que cocina tiene que preparar")
public class CocinaController {

    private final CocinaService cocina;
    private final CatalogoService catalogos;

    public CocinaController(CocinaService cocina, CatalogoService catalogos) {
        this.cocina = cocina;
        this.catalogos = catalogos;
    }

    @GetMapping
    @PreAuthorize(Permisos.VISTA_COCINA)
    @Operation(summary = "Eventos de cocina de un mes", description = """
            Eventos Confirmados y En curso del mes, con hora, salón, invitados y los servicios de las categorías visibles
            en cocina. Para el perfil Cocina, solo desde hoy hasta HORIZONTE_COCINA_DIAS adelante (`hasta`); para el resto,
            `hasta` es null.""")
    public CocinaResponse mes(@Parameter(example = "2026-11") @RequestParam(required = false) YearMonth mes,
            @AuthenticationPrincipal Jwt jwt) {
        UsuarioActual quien = UsuarioActual.de(jwt);
        CocinaService.Cocina resultado = cocina.mes(mes != null ? mes : YearMonth.from(cocina.horizonte(quien).hoy()), quien);
        Armado armado = new Armado();
        return new CocinaResponse(resultado.mes(), resultado.horizonte().hoy(), resultado.horizonte().hasta(),
                catalogos.salones().stream().map(SalonCocina::de).toList(),
                resultado.eventos().stream().map(armado::tarjeta).toList());
    }

    @GetMapping("/eventos/{id}")
    @PreAuthorize(Permisos.VISTA_COCINA)
    @Operation(summary = "Tarjeta de cocina de un evento", description = """
            Para abrir la tarjeta desde un aviso. 404 FUERA_DE_COCINA si el evento ya no está en la vista (cancelado,
            reprogramado fuera del período de Cocina o sin confirmar).""")
    public TarjetaCocina evento(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return new Armado().tarjeta(cocina.evento(id, UsuarioActual.de(jwt)));
    }

    /** {@code hasta}: último día que puede ver quien consulta; null si no tiene límite. */
    public record CocinaResponse(YearMonth mes, LocalDate hoy, LocalDate hasta, List<SalonCocina> salones,
            List<TarjetaCocina> eventos) {
    }

    public record SalonCocina(String codigo, String nombre) {
        static SalonCocina de(Salon s) {
            return new SalonCocina(s.getCodigo(), s.getNombre());
        }
    }

    /** {@code hora}: la de inicio del evento o, si no se cargó, la del turno. {@code turno}: su código (mediodia, noche). */
    public record TarjetaCocina(long id, String nombre, String tipo, LocalDate fecha, SalonCocina salon, String turno,
            String turnoNombre, LocalTime hora, Integer invitados, List<ServicioCocina> servicios) {
    }

    /** Lo cargado en una categoría visible en cocina. */
    public record ServicioCocina(String categoria, List<RenglonDto> renglones, String detalle) {
    }

    /** Arma las tarjetas con los catálogos leídos una sola vez por pedido. */
    private final class Armado {

        private final Map<Short, Salon> salones = catalogos.salones().stream().collect(Collectors.toMap(Salon::getId, Function.identity()));
        private final Map<Short, Turno> turnos = catalogos.turnos().stream().collect(Collectors.toMap(Turno::getId, Function.identity()));
        private final Map<Short, CategoriaServicio> enCocina = catalogos.categorias().stream()
                .filter(CategoriaServicio::isVisibleEnCocina)
                .collect(Collectors.toMap(CategoriaServicio::getId, Function.identity()));

        TarjetaCocina tarjeta(EventoCocina ec) {
            Evento e = ec.evento();
            Turno turno = turnos.get(e.getUnidad().getTurnoId());
            List<ServicioCocina> servicios = ec.servicios().stream()
                    .filter(s -> enCocina.containsKey(s.getCategoriaId()))
                    .sorted(Comparator.comparing(s -> enCocina.get(s.getCategoriaId()).getOrden()))
                    .map(this::servicio)
                    .toList();
            return new TarjetaCocina(e.getId(), e.getNombre(), catalogos.tipoEvento(e.getTipoEventoId()).getNombre(),
                    e.getUnidad().getFecha(), SalonCocina.de(salones.get(e.getUnidad().getSalonId())), turno.getCodigo(),
                    turno.getNombre(), e.getHoraInicio() != null ? e.getHoraInicio() : turno.getHoraInicio(),
                    e.getCantidadInvitados(), servicios);
        }

        private ServicioCocina servicio(ServicioContratado s) {
            return new ServicioCocina(enCocina.get(s.getCategoriaId()).getNombre(), RenglonDto.de(s), s.getDescripcion());
        }
    }
}
