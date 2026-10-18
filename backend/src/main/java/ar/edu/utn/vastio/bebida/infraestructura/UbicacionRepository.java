package ar.edu.utn.vastio.bebida.infraestructura;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import ar.edu.utn.vastio.bebida.dominio.TipoUbicacion;
import ar.edu.utn.vastio.bebida.dominio.Ubicacion;

public interface UbicacionRepository extends JpaRepository<Ubicacion, Short> {

    /** Bloquea la ubicación hasta el final de la transacción: dos guardados de su carga inicial no se cruzan. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Ubicacion u WHERE u.id = :id")
    Optional<Ubicacion> bloquear(short id);

    @Query("SELECT u FROM Ubicacion u LEFT JOIN FETCH u.abastecimiento")
    List<Ubicacion> findAllConAbastecimiento();

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Short id);

    Optional<Ubicacion> findFirstByTipoAndActivoTrue(TipoUbicacion tipo);

    List<Ubicacion> findByTipoAndSalonIdAndActivoTrue(TipoUbicacion tipo, Short salonId);

    /** Barras activas que se abastecen de esa ubicación (una transición). */
    List<Ubicacion> findByAbastecimientoIdAndActivoTrue(Short abastecimientoId);

    /** Bebidas con saldo distinto de cero en la ubicación. */
    @Query(nativeQuery = true, value = "SELECT count(*) FROM stock_ubicacion WHERE ubicacion_id = :ubicacionId AND cantidad <> 0")
    long bebidasConSaldo(short ubicacionId);
}
