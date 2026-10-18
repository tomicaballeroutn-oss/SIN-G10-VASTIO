package ar.edu.utn.vastio.bebida.infraestructura;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.utn.vastio.bebida.dominio.Proveedor;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

    boolean existsByActivoTrueAndRazonSocialIgnoreCaseAndIdNot(String razonSocial, Long id);

    boolean existsByCuitAndIdNot(String cuit, Long id);
}
