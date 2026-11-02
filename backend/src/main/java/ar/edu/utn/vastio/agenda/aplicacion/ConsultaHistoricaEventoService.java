package ar.edu.utn.vastio.agenda.aplicacion;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.agenda.infraestructura.EventoRepository;
import ar.edu.utn.vastio.agenda.infraestructura.EventoRepository.Historico;

/** Contrato de lectura de Agenda para estadísticas: sin clientes, importes ni entidades JPA. */
@Service
@Transactional(readOnly = true)
public class ConsultaHistoricaEventoService {
    private final EventoRepository eventos;
    private final AsistenciaPorSegmento asistencias;

    public ConsultaHistoricaEventoService(EventoRepository eventos, AsistenciaPorSegmento asistencias) {
        this.eventos = eventos;
        this.asistencias = asistencias;
    }

    /**
     * {@code invitados}: el divisor del consumo por asistente (docs/sprint-4.md, decisión 8): la asistencia real si el
     * tipo cuenta por segmento y está completa ({@code asistenciaReal} true); si no, la cantidad de invitados.
     */
    public record EventoHistorico(long id, short tipoEventoId, Integer invitados, boolean asistenciaReal) { }

    public List<EventoHistorico> cerrados(LocalDate desde, LocalDate hasta, Short tipoEventoId, Short salonId) {
        List<Historico> lista = eventos.historicos(desde == null ? LocalDate.of(1, 1, 1) : desde,
                hasta == null ? LocalDate.of(9999, 12, 31) : hasta, tipoEventoId, salonId);
        Map<Long, Integer> reales = asistencias.realesCompletas(
                lista.stream().collect(Collectors.toMap(Historico::getId, Historico::getTipoEventoId)));
        return lista.stream().map(e -> reales.containsKey(e.getId())
                        ? new EventoHistorico(e.getId(), e.getTipoEventoId(), reales.get(e.getId()), true)
                        : new EventoHistorico(e.getId(), e.getTipoEventoId(), e.getCantidadInvitados(), false))
                .toList();
    }
}
