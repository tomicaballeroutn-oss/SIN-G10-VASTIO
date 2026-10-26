package ar.edu.utn.vastio.agenda.aplicacion;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.agenda.infraestructura.EventoRepository;

/** Contrato de lectura de Agenda para estadísticas: sin clientes, importes ni entidades JPA. */
@Service
@Transactional(readOnly = true)
public class ConsultaHistoricaEventoService {
    private final EventoRepository eventos;

    public ConsultaHistoricaEventoService(EventoRepository eventos) {
        this.eventos = eventos;
    }

    public record EventoHistorico(long id, short tipoEventoId, Integer invitados) { }

    public List<EventoHistorico> cerrados(LocalDate desde, LocalDate hasta, Short tipoEventoId, Short salonId) {
        return eventos.historicos(desde == null ? LocalDate.of(1, 1, 1) : desde,
                hasta == null ? LocalDate.of(9999, 12, 31) : hasta, tipoEventoId, salonId).stream()
                .map(e -> new EventoHistorico(e.getId(), e.getTipoEventoId(), e.getCantidadInvitados())).toList();
    }
}
