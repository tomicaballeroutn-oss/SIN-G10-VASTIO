package ar.edu.utn.vastio.bebida.infraestructura;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.utn.vastio.bebida.dominio.CodigoBarra;

public interface CodigoBarraRepository extends JpaRepository<CodigoBarra, String> {

    List<CodigoBarra> findByCodigoIn(Collection<String> codigos);
}
