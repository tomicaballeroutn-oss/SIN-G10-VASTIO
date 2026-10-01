package ar.edu.utn.vastio.configuracion.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.utn.vastio.configuracion.dominio.AmbitoMotivo;
import ar.edu.utn.vastio.configuracion.dominio.Motivo;

public interface MotivoRepository extends JpaRepository<Motivo, Short> {

    List<Motivo> findAllByOrderByAmbitoAscIdAsc();

    boolean existsByAmbitoAndNombreIgnoreCaseAndIdNot(AmbitoMotivo ambito, String nombre, Short id);
}
