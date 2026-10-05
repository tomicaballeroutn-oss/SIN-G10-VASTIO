package ar.edu.utn.vastio.agenda.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.utn.vastio.agenda.dominio.CambioEstadoEvento;

public interface CambioEstadoEventoRepository extends JpaRepository<CambioEstadoEvento, Long> {

    List<CambioEstadoEvento> findByEventoIdOrderByFechaHoraAscIdAsc(long eventoId);
}
