package ar.edu.utn.vastio.configuracion.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.utn.vastio.configuracion.dominio.Parametro;

public interface ParametroRepository extends JpaRepository<Parametro, String> {

    List<Parametro> findAllByOrderByClaveAsc();
}
