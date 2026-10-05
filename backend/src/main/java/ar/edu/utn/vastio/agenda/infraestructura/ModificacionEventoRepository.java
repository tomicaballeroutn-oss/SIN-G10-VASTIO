package ar.edu.utn.vastio.agenda.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.utn.vastio.agenda.dominio.ModificacionEvento;

public interface ModificacionEventoRepository extends JpaRepository<ModificacionEvento, Long> {

    List<ModificacionEvento> findByEventoIdOrderByFechaHoraAscIdAsc(long eventoId);
}
