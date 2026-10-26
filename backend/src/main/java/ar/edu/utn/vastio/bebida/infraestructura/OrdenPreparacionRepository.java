package ar.edu.utn.vastio.bebida.infraestructura;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ar.edu.utn.vastio.bebida.dominio.EstadoOrden;
import ar.edu.utn.vastio.bebida.dominio.OrdenPreparacion;

public interface OrdenPreparacionRepository extends JpaRepository<OrdenPreparacion, Long> {

    /** La orden vigente del evento (a lo sumo una, por {@code ux_orden_evento_vigente}). */
    @Query("select o from OrdenPreparacion o where o.eventoId = :eventoId and o.estado in :vigentes")
    Optional<OrdenPreparacion> vigente(long eventoId, Collection<EstadoOrden> vigentes);

    default Optional<OrdenPreparacion> vigente(long eventoId) {
        return vigente(eventoId, EstadoOrden.VIGENTES);
    }

    /** Las vigentes de esos eventos, con sus renglones. */
    @Query("select distinct o from OrdenPreparacion o left join fetch o.renglones where o.eventoId in :eventoIds and o.estado in :vigentes")
    List<OrdenPreparacion> vigentes(Collection<Long> eventoIds, Collection<EstadoOrden> vigentes);

    default List<OrdenPreparacion> vigentes(Collection<Long> eventoIds) {
        return eventoIds.isEmpty() ? List.of() : vigentes(eventoIds, EstadoOrden.VIGENTES);
    }

    /** La última del evento, vigente o no (una cerrada se sigue consultando). */
    Optional<OrdenPreparacion> findFirstByEventoIdOrderByIdDesc(long eventoId);
}
