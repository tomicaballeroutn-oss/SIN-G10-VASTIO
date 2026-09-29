package ar.edu.utn.vastio.configuracion.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.utn.vastio.configuracion.dominio.TipoEvento;

public interface TipoEventoRepository extends JpaRepository<TipoEvento, Short> {

    List<TipoEvento> findAllByOrderByNombreAsc();

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Short id);
}
