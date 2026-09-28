package ar.edu.utn.vastio.usuarios.infraestructura;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.utn.vastio.usuarios.dominio.Rol;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;

public interface RolRepository extends JpaRepository<Rol, Short> {

    Optional<Rol> findByCodigo(RolCodigo codigo);
}
