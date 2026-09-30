package ar.edu.utn.vastio.agenda.infraestructura;

import java.time.LocalDate;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import ar.edu.utn.vastio.agenda.dominio.UnidadComercializable;

public interface UnidadComercializableRepository extends JpaRepository<UnidadComercializable, Long> {

    /**
     * Crea la unidad si no existe. ON CONFLICT evita que dos pedidos simultáneos choquen con la UNIQUE
     * (salon_id, fecha, turno_id) antes de llegar al bloqueo.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO unidad_comercializable (salon_id, fecha, turno_id) VALUES (:salonId, :fecha, :turnoId)
            ON CONFLICT (salon_id, fecha, turno_id) DO NOTHING""")
    void asegurar(short salonId, LocalDate fecha, short turnoId);

    /** SELECT … FOR UPDATE: quien llega segundo espera a que el primero termine su transacción. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UnidadComercializable u where u.salonId = :salonId and u.fecha = :fecha and u.turnoId = :turnoId")
    Optional<UnidadComercializable> bloquear(short salonId, LocalDate fecha, short turnoId);
}
