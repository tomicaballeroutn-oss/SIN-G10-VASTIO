package ar.edu.utn.vastio.configuracion.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.utn.vastio.configuracion.dominio.CategoriaServicio;

public interface CategoriaServicioRepository extends JpaRepository<CategoriaServicio, Short> {

    List<CategoriaServicio> findAllByOrderByOrdenAscNombreAsc();

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Short id);
}
