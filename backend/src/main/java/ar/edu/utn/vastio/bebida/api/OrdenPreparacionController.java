package ar.edu.utn.vastio.bebida.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.bebida.api.OrdenDto.OrdenDeLaLista;
import ar.edu.utn.vastio.bebida.api.OrdenDto.OrdenResponse;
import ar.edu.utn.vastio.bebida.aplicacion.OrdenPreparacionService;
import ar.edu.utn.vastio.bebida.aplicacion.OrdenPreparacionService.Consulta;
import ar.edu.utn.vastio.bebida.dominio.OrdenPreparacion;
import ar.edu.utn.vastio.comun.seguridad.Permisos;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.usuarios.aplicacion.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Orden de preparación (UI-35, docs/sprint-4.md, decisiones 11 a 22). La consultan Dirección, Coordinación,
 * Administración y Compras; la gestionan todos ellos menos Administración. La barra nunca la ve (operación a ciegas).
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize(Permisos.CONSULTAR_ORDEN)
@Tag(name = "Orden de preparación", description = "Bebida sugerida para cada evento confirmado, a partir del consumo histórico")
public class OrdenPreparacionController {

    private final OrdenPreparacionService ordenes;
    private final CatalogoService catalogos;
    private final UsuarioService usuarios;

    public OrdenPreparacionController(OrdenPreparacionService ordenes, CatalogoService catalogos, UsuarioService usuarios) {
        this.ordenes = ordenes;
        this.catalogos = catalogos;
        this.usuarios = usuarios;
    }

    /** {@code version}: la de la orden que se estaba viendo. */
    public record VersionRequest(@NotNull(message = "Falta la versión de la orden. Volvé a abrirla.") Integer version) {
    }

    @GetMapping("/ordenes-preparacion")
    @Operation(summary = "Órdenes de preparación", description = """
            Los eventos Confirmados y En curso, del más próximo al más lejano, con el estado de su orden vigente (null si
            no tiene) y si cambiaron los asistentes desde que se calculó.""")
    public List<OrdenDeLaLista> listado() {
        return ordenes.listado().stream()
                .map(r -> OrdenDeLaLista.de(r, catalogos.salon(r.evento().evento().salonId()))).toList();
    }

    @GetMapping("/eventos/{eventoId}/orden-preparacion")
    @Operation(summary = "Orden de preparación del evento", description = """
            La vigente o, si no hay, la última (cerrada o descartada); null si el evento nunca tuvo. Por bebida, lo
            sugerido, lo que se va a retirar y lo comprometido por las órdenes vigentes de otros eventos de los próximos
            15 días, en botellas. Dice de dónde salió la sugerencia y si cambiaron los asistentes.""")
    public OrdenResponse consultar(@PathVariable long eventoId, @AuthenticationPrincipal Jwt jwt) {
        return respuesta(ordenes.consultar(eventoId), UsuarioActual.de(jwt));
    }

    @PostMapping("/eventos/{eventoId}/orden-preparacion")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permisos.GESTIONAR_ORDEN)
    @Operation(summary = "Generar orden de preparación", description = """
            Para un evento Confirmado o En curso sin orden vigente (las que se confirmaron antes o con la orden
            descartada): al confirmar se genera sola. Calcula la sugerencia desde el consumo histórico y avisa a Compras.""")
    @ApiResponse(responseCode = "409", description = "ORDEN_EXISTENTE")
    @ApiResponse(responseCode = "422", description = "Estado que no lo permite o SIN_DEPOSITO_MADRE")
    public OrdenResponse generar(@PathVariable long eventoId, @AuthenticationPrincipal Jwt jwt) {
        UsuarioActual quien = UsuarioActual.de(jwt);
        ordenes.generar(eventoId, quien);
        return respuesta(ordenes.consultar(eventoId), quien);
    }

    @PostMapping("/eventos/{eventoId}/orden-preparacion/recalculo")
    @PreAuthorize(Permisos.GESTIONAR_ORDEN)
    @Operation(summary = "Recalcular sugerencia", description = """
            Vuelve a calcular la orden con los asistentes y el consumo histórico de hoy. Queda Sugerida y pisa lo
            ajustado. No se puede con la orden confirmada (primero hay que reabrirla).""")
    @ApiResponse(responseCode = "409", description = "ORDEN_MODIFICADA: otra persona la cambió en el medio")
    @ApiResponse(responseCode = "422", description = "ORDEN_CONFIRMADA o estado del evento que no lo permite")
    public OrdenResponse recalcular(@PathVariable long eventoId, @Valid @RequestBody VersionRequest pedido,
            @AuthenticationPrincipal Jwt jwt) {
        UsuarioActual quien = UsuarioActual.de(jwt);
        ordenes.recalcular(eventoId, pedido.version(), quien);
        return respuesta(ordenes.consultar(eventoId), quien);
    }

    private OrdenResponse respuesta(Consulta consulta, UsuarioActual quien) {
        Set<Long> personas = new HashSet<>();
        OrdenPreparacion o = consulta.orden();
        if (o != null) {
            Stream.of(o.getResponsableId(), o.getUsuarioModificacionId(), o.getUsuarioConfirmacionId(), o.getUsuarioReaperturaId())
                    .filter(Objects::nonNull).forEach(personas::add);
        }
        return OrdenResponse.de(consulta, catalogos.salon(consulta.evento().evento().salonId()), usuarios.nombres(personas),
                quien);
    }
}
