package ar.edu.utn.vastio.agenda.infraestructura;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.utn.vastio.agenda.dominio.ComprobanteSena;

public interface ComprobanteSenaRepository extends JpaRepository<ComprobanteSena, Long> {

    Optional<ComprobanteSena> findByEventoId(long eventoId);
}
