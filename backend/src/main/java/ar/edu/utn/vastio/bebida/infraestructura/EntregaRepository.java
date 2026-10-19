package ar.edu.utn.vastio.bebida.infraestructura;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ar.edu.utn.vastio.bebida.dominio.Entrega;

public interface EntregaRepository extends JpaRepository<Entrega, Long> {

    /** Una por evento y barra (uq_entrega_1). */
    Optional<Entrega> findByEventoIdAndBarraId(long eventoId, short barraId);

    /** La entrega abierta de otro evento en esa barra: hasta su devolución, la barra no recibe a otro evento. */
    @Query("""
            SELECT e FROM Entrega e
            WHERE e.barra.id = :barraId AND e.eventoId <> :eventoId
              AND e.estado = ar.edu.utn.vastio.bebida.dominio.EstadoEntrega.ABIERTA""")
    List<Entrega> abiertasDeOtroEvento(short barraId, long eventoId);

    List<Entrega> findByEventoIdOrderByIdAsc(long eventoId);
}
