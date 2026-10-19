package ar.edu.utn.vastio.bebida.aplicacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.agenda.aplicacion.CierreEventoService;
import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService;
import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.bebida.dominio.ConsumoEvento;
import ar.edu.utn.vastio.bebida.infraestructura.ConsumoEventoRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;

/** B4-05. Lee exclusivamente el consolidado congelado; no concilia movimientos ni consulta stock. */
@Service
@Transactional(readOnly = true)
public class ConsultaConsumoService {
    private final ConsultaEventoService eventos;
    private final CierreEventoService datosEvento;
    private final ConsumoEventoRepository consumos;

    public ConsultaConsumoService(ConsultaEventoService eventos, CierreEventoService datosEvento,
            ConsumoEventoRepository consumos) {
        this.eventos = eventos;
        this.datosEvento = datosEvento;
        this.consumos = consumos;
    }

    public record Renglon(ConsumoEvento consumo, BigDecimal porInvitado) {
    }

    public record Consulta(Integer invitados, boolean usaSegmentos, List<Renglon> renglones,
            BigDecimal consumoTotal, BigDecimal porInvitadoTotal) {
    }

    public Consulta consultar(long eventoId) {
        if (eventos.operativo(eventoId).estado() != EstadoEvento.CERRADO) {
            throw ProblemaException.conflicto("EVENTO_NO_CERRADO",
                    "El consumo consolidado estará disponible cuando se cierre el evento.");
        }
        var datos = datosEvento.datos(eventoId);
        // Una fila por evento y bebida: el cierre ya consolidó todas las barras.
        List<Renglon> renglones = consumos.findByEventoId(eventoId).stream()
                .sorted(Comparator.comparing((ConsumoEvento c) -> c.getBebida().getNombre(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(c -> c.getBebida().getPresentacion(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(c -> c.getBebida().getId()))
                .map(c -> new Renglon(c, porInvitado(c.getCantidadConsumida(), datos.cantidadInvitados())))
                .toList();
        BigDecimal total = renglones.stream().map(r -> r.consumo().getCantidadConsumida())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Consulta(datos.cantidadInvitados(), datos.usaSegmentos(), renglones, total,
                porInvitado(total, datos.cantidadInvitados()));
    }

    private static BigDecimal porInvitado(BigDecimal cantidad, Integer invitados) {
        return invitados == null || invitados <= 0 ? null
                : cantidad.divide(BigDecimal.valueOf(invitados), 4, RoundingMode.HALF_UP);
    }
}
