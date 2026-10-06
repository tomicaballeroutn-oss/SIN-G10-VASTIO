package ar.edu.utn.vastio.agenda.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.utn.vastio.agenda.dominio.ServicioContratado;

public interface ServicioContratadoRepository extends JpaRepository<ServicioContratado, Long> {

    List<ServicioContratado> findByEventoId(long eventoId);
}
