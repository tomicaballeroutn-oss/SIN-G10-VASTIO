package ar.edu.utn.vastio.agenda.infraestructura;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;

public interface EventoRepository extends JpaRepository<Evento, Long> {

    /** Eventos que ocupan una unidad entre esas fechas, con su unidad y cliente. */
    @Query("""
            select e from Evento e join fetch e.unidad u join fetch e.cliente
            where u.fecha between :desde and :hasta and e.estado not in :inactivos
            """)
    List<Evento> activosEntre(LocalDate desde, LocalDate hasta, Collection<EstadoEvento> inactivos);

    @Query("select count(e) > 0 from Evento e where e.unidad.id = :unidadId and e.estado not in :inactivos")
    boolean hayActivoEn(long unidadId, Collection<EstadoEvento> inactivos);

    /** Con unidad y cliente: la ficha se arma fuera de la transacción. */
    @Query("select e from Evento e join fetch e.unidad join fetch e.cliente where e.id = :id")
    Optional<Evento> conDetalle(long id);

    /** Eventos activos desde esa fecha, por fecha, turno y salón. */
    @Query("""
            select e from Evento e join fetch e.unidad u join fetch e.cliente
            where u.fecha >= :desde and e.estado not in :inactivos
            order by u.fecha, u.turnoId, u.salonId
            """)
    List<Evento> activosDesde(LocalDate desde, Collection<EstadoEvento> inactivos);
}
