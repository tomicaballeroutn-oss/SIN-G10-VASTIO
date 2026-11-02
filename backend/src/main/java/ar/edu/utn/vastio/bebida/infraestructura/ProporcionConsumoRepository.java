package ar.edu.utn.vastio.bebida.infraestructura;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.utn.vastio.bebida.dominio.ProporcionConsumo;

public interface ProporcionConsumoRepository extends JpaRepository<ProporcionConsumo, Long> {

    List<ProporcionConsumo> findByTipoEventoId(short tipoEventoId);

    Optional<ProporcionConsumo> findByTipoEventoIdAndBebidaId(short tipoEventoId, long bebidaId);
}
