package ar.edu.utn.vastio.bebida.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.utn.vastio.bebida.dominio.TipoBebida;

public interface TipoBebidaRepository extends JpaRepository<TipoBebida, Short> {

    List<TipoBebida> findAllByOrderByIdAsc();
}
