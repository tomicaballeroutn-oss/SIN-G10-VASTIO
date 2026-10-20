package ar.edu.utn.vastio.bebida.infraestructura;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ar.edu.utn.vastio.bebida.dominio.PreparacionEvento;

public interface PreparacionRepository extends JpaRepository<PreparacionEvento, Long> {
    Optional<PreparacionEvento> findByEventoIdAndClave(long eventoId, String clave);
    List<PreparacionEvento> findByEventoIdOrderByIdDesc(long eventoId);
}
