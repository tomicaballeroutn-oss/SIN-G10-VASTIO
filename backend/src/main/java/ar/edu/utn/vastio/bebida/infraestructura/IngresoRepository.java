package ar.edu.utn.vastio.bebida.infraestructura;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ar.edu.utn.vastio.bebida.dominio.Ingreso;

public interface IngresoRepository extends JpaRepository<Ingreso, Long> {

    /** Ingresos de mercadería (sin la carga inicial), del más reciente al más viejo. */
    @Query("SELECT i FROM Ingreso i JOIN FETCH i.destino WHERE NOT i.esInventarioInicial ORDER BY i.fechaRegistro DESC, i.id DESC")
    List<Ingreso> recientes(Pageable pagina);
}
