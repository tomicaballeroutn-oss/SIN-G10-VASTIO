package ar.edu.utn.vastio.configuracion.infraestructura;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.utn.vastio.configuracion.dominio.Turno;

public interface TurnoRepository extends JpaRepository<Turno, Short> {

    Optional<Turno> findByCodigo(String codigo);
}
