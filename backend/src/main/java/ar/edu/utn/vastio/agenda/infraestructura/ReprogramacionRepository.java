package ar.edu.utn.vastio.agenda.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ar.edu.utn.vastio.agenda.dominio.Reprogramacion;

public interface ReprogramacionRepository extends JpaRepository<Reprogramacion, Long> {

    /** Con las dos unidades: la ficha se arma fuera de la transacción. */
    @Query("""
            select r from Reprogramacion r join fetch r.unidadAnterior join fetch r.unidadNueva
            where r.eventoId = :eventoId order by r.fechaHora, r.id
            """)
    List<Reprogramacion> delEvento(long eventoId);
}
