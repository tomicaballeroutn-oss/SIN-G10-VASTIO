package ar.edu.utn.vastio.agenda.infraestructura;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import ar.edu.utn.vastio.agenda.dominio.Bloqueo;
import ar.edu.utn.vastio.agenda.dominio.UnidadComercializable;

public interface BloqueoRepository extends JpaRepository<Bloqueo, Long> {

    /** Unidad bloqueada y su bloqueo activo, entre esas fechas. */
    record UnidadBloqueada(UnidadComercializable unidad, Bloqueo bloqueo) {
    }

    @Query("""
            select new ar.edu.utn.vastio.agenda.infraestructura.BloqueoRepository$UnidadBloqueada(u, b)
            from Bloqueo b join b.unidades u
            where b.activo = true and u.fecha between :desde and :hasta
            """)
    List<UnidadBloqueada> activosEntre(LocalDate desde, LocalDate hasta);

    /** El bloqueo activo de una unidad, si hay. */
    @Query("select b from Bloqueo b join b.unidades u where b.activo = true and u.id = :unidadId")
    List<Bloqueo> activosEn(long unidadId);

    /** Los bloqueos activos con sus unidades: la lista de UI-21. */
    @Query("select distinct b from Bloqueo b join fetch b.unidades where b.activo = true")
    List<Bloqueo> activosConUnidades();

    /** SELECT … FOR UPDATE: dos desbloqueos simultáneos no se pisan; el segundo ve el bloqueo ya inactivo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Bloqueo b where b.id = :id")
    Optional<Bloqueo> paraDesbloquear(long id);
}
