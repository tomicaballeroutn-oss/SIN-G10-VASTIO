package ar.edu.utn.vastio.agenda.infraestructura;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.utn.vastio.agenda.dominio.AsistenciaSegmento;

public interface AsistenciaSegmentoRepository extends JpaRepository<AsistenciaSegmento, Long> {

    List<AsistenciaSegmento> findByEventoId(long eventoId);

    List<AsistenciaSegmento> findByEventoIdIn(Collection<Long> eventoIds);
}
