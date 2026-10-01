package ar.edu.utn.vastio.configuracion.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.utn.vastio.configuracion.dominio.TipoSegmentoAsistencia;

public interface TipoSegmentoAsistenciaRepository extends JpaRepository<TipoSegmentoAsistencia, Short> {

    List<TipoSegmentoAsistencia> findAllByOrderByIdAsc();

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Short id);
}
