package ar.edu.utn.vastio.bebida.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.utn.vastio.bebida.dominio.UnidadManipulacion;

public interface UnidadManipulacionRepository extends JpaRepository<UnidadManipulacion, Short> {

    List<UnidadManipulacion> findAllByOrderByIdAsc();
}
