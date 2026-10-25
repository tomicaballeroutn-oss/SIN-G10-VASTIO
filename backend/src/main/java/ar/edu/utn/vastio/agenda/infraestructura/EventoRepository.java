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

    interface Historico {
        Long getId();
        Short getTipoEventoId();
        Integer getCantidadInvitados();
    }

    /** La fecha operativa pertenece a la unidad, nunca a la fecha de cierre. */
    @Query("""
            select e.id as id, e.tipoEventoId as tipoEventoId, e.cantidadInvitados as cantidadInvitados
            from Evento e join e.unidad u
            where e.estado = ar.edu.utn.vastio.agenda.dominio.EstadoEvento.CERRADO
              and u.fecha between :desde and :hasta
              and (:tipoEventoId is null or e.tipoEventoId = :tipoEventoId)
              and (:salonId is null or u.salonId = :salonId)
            order by e.id
            """)
    List<Historico> historicos(LocalDate desde, LocalDate hasta, Short tipoEventoId, Short salonId);

    /** Eventos que ocupan una unidad entre esas fechas, con su unidad y cliente. */
    @Query("""
            select e from Evento e join fetch e.unidad u join fetch e.cliente
            where u.fecha between :desde and :hasta and e.estado not in :inactivos
            """)
    List<Evento> activosEntre(LocalDate desde, LocalDate hasta, Collection<EstadoEvento> inactivos);

    /** Vista de cocina: los eventos en esos estados entre dos fechas, por fecha, turno y salón. */
    @Query("""
            select e from Evento e join fetch e.unidad u
            where u.fecha between :desde and :hasta and e.estado in :estados
            order by u.fecha, u.turnoId, u.salonId
            """)
    List<Evento> enEstadosEntre(LocalDate desde, LocalDate hasta, Collection<EstadoEvento> estados);

    @Query("select count(e) > 0 from Evento e where e.unidad.id = :unidadId and e.estado not in :inactivos")
    boolean hayActivoEn(long unidadId, Collection<EstadoEvento> inactivos);

    /** Con unidad y cliente: la ficha se arma fuera de la transacción. */
    @Query("select e from Evento e join fetch e.unidad join fetch e.cliente where e.id = :id")
    Optional<Evento> conDetalle(long id);

    /** Eventos de esa fecha en esos estados, con su unidad. */
    @Query("select e from Evento e join fetch e.unidad u where u.fecha = :fecha and e.estado in :estados order by u.salonId, u.turnoId")
    List<Evento> deLaFecha(LocalDate fecha, Collection<EstadoEvento> estados);

    /** Ids de los eventos en esos estados con fecha de jornada hasta esa, del más viejo al más nuevo. */
    @Query("""
            select e.id from Evento e join e.unidad u
            where u.fecha <= :hasta and e.estado in :estados
            order by u.fecha, u.turnoId, e.id
            """)
    List<Long> idsHasta(LocalDate hasta, Collection<EstadoEvento> estados);

    /** Eventos en ese estado, del más viejo al más nuevo. */
    @Query("select e from Evento e join fetch e.unidad u where e.estado = :estado order by u.fecha, u.turnoId, u.salonId")
    List<Evento> enEstado(EstadoEvento estado);

    /** Eventos activos desde esa fecha, por fecha, turno y salón. */
    @Query("""
            select e from Evento e join fetch e.unidad u join fetch e.cliente
            where u.fecha >= :desde and e.estado not in :inactivos
            order by u.fecha, u.turnoId, u.salonId
            """)
    List<Evento> activosDesde(LocalDate desde, Collection<EstadoEvento> inactivos);
}
