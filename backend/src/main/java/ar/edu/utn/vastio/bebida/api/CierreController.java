package ar.edu.utn.vastio.bebida.api;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.bebida.api.EntregaDto.EventoResumen;
import ar.edu.utn.vastio.bebida.api.UbicacionDto.SalonResumen;
import ar.edu.utn.vastio.bebida.api.UbicacionDto.UbicacionResumen;
import ar.edu.utn.vastio.bebida.aplicacion.CierreService;
import ar.edu.utn.vastio.bebida.aplicacion.CierreService.EventoPorCerrar;
import ar.edu.utn.vastio.bebida.aplicacion.CierreService.PantallaCierre;
import ar.edu.utn.vastio.bebida.aplicacion.CierreService.Renglon;
import ar.edu.utn.vastio.bebida.dominio.Bebida;
import ar.edu.utn.vastio.comun.seguridad.Permisos;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Registrar cierre de evento (UI-33). La encargada de barra, Compras, Coordinación y Dirección. Cantidades en botellas;
 * sin importes: el sistema no maneja dinero.
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize(Permisos.OPERAR_BARRA)
@Tag(name = "Cierre de evento", description = "Conciliación final del consumo de bebida de un evento")
public class CierreController {

    private final CierreService cierres;

    public CierreController(CierreService cierres) {
        this.cierres = cierres;
    }

    /** Consumo de una bebida en el evento, en botellas. {@code porAsistente} null si no hay invitados cargados. */
    public record RenglonCierre(long bebidaId, String nombre, String presentacion, String unidad, short unidadesPorBulto,
            BigDecimal entregado, BigDecimal devuelto, BigDecimal remanente, BigDecimal consumo, BigDecimal porAsistente) {
        static RenglonCierre de(Renglon r) {
            Bebida b = r.bebida();
            return new RenglonCierre(b.getId(), b.getNombre(), b.getPresentacion(), b.getUnidad().getNombre(),
                    b.getUnidadesPorBulto(), r.entregado(), r.devuelto(), r.remanente(), r.consumo(), r.porAsistente());
        }
    }

    /**
     * {@code pendientes}: barras sin devolución (no se puede cerrar). {@code usaSegmentos}: el tipo pide asistencia por
     * segmento, que todavía no se registra: se divide por la cantidad de invitados. {@code cerrado}: lo congelado.
     */
    public record CierreResponse(EventoResumen evento, SalonResumen salon, Integer invitados, boolean usaSegmentos,
            List<UbicacionResumen> pendientes, List<RenglonCierre> renglones, BigDecimal consumoTotal, BigDecimal porAsistenteTotal,
            boolean cerrado) {
        static CierreResponse de(PantallaCierre p) {
            return new CierreResponse(EventoResumen.de(p.evento()), SalonResumen.de(p.salon()), p.invitados(), p.usaSegmentos(),
                    p.pendientes().stream().map(UbicacionResumen::de).toList(),
                    p.renglones().stream().map(RenglonCierre::de).toList(), p.consumoTotal(), p.porAsistenteTotal(), p.cerrado());
        }
    }

    public record EventoPorCerrarResponse(EventoResumen evento, SalonResumen salon) {
        static EventoPorCerrarResponse de(EventoPorCerrar e) {
            return new EventoPorCerrarResponse(EventoResumen.de(e.evento()), SalonResumen.de(e.salon()));
        }
    }

    @GetMapping("/cierres/pendientes")
    @Operation(summary = "Eventos por cerrar", description = "Los eventos realizados que todavía no se cerraron, del más viejo al más nuevo.")
    public List<EventoPorCerrarResponse> pendientes() {
        return cierres.porCerrar().stream().map(EventoPorCerrarResponse::de).toList();
    }

    @GetMapping("/eventos/{eventoId}/cierre")
    @Operation(summary = "Conciliación del evento", description = """
            Por bebida, en todas las barras del evento: entregado, devuelto, remanente y consumo, y el consumo por
            asistente (por la cantidad de invitados). Las barras que faltan devolver van en pendientes. Si el evento ya está
            cerrado, lo congelado en consumo_evento.""")
    public CierreResponse pantalla(@PathVariable long eventoId) {
        return CierreResponse.de(cierres.pantalla(eventoId));
    }

    @PostMapping("/eventos/{eventoId}/cierre")
    @Operation(summary = "Registrar el cierre", description = """
            Solo con el evento realizado y todas sus entregas cerradas (si falta una devolución, 409 diciendo qué barra).
            En una transacción: el evento pasa a Cerrado, se congela consumo_evento y la orden de preparación queda CERRADA.
            Un evento cerrado no se vuelve a cerrar ni admite movimientos.""")
    public CierreResponse cerrar(@PathVariable long eventoId, @AuthenticationPrincipal Jwt jwt) {
        return CierreResponse.de(cierres.cerrar(eventoId, UsuarioActual.de(jwt)));
    }
}
