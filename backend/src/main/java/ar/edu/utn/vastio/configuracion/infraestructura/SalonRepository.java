package ar.edu.utn.vastio.configuracion.infraestructura;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.utn.vastio.configuracion.dominio.Salon;

public interface SalonRepository extends JpaRepository<Salon, Short> {

    Optional<Salon> findByCodigo(String codigo);

    /** Orden fijo de la agenda: Avril, Club de Campo, Santa Bárbara (el de los ids de V2). */
    List<Salon> findByActivoTrueOrderByIdAsc();
}
