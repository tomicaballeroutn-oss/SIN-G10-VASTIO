package ar.edu.utn.vastio.bebida.infraestructura;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ar.edu.utn.vastio.bebida.dominio.Bebida;

public interface BebidaRepository extends JpaRepository<Bebida, Long> {

    @Query("SELECT DISTINCT b FROM Bebida b LEFT JOIN FETCH b.codigos")
    List<Bebida> findAllConCodigos();

    /** Otra bebida activa con el mismo nombre y presentación, sin distinguir mayúsculas (ux_bebida_nombre_presentacion_activa). */
    @Query("""
            SELECT b FROM Bebida b
            WHERE b.activo AND lower(b.nombre) = lower(:nombre) AND lower(b.presentacion) = lower(:presentacion)
              AND b.id <> :id""")
    List<Bebida> activasConMismoNombre(String nombre, String presentacion, long id);

    /** Saldo de una ubicación con la bebida. */
    interface Saldo {
        Short getUbicacionId();

        String getUbicacion();

        BigDecimal getCantidad();
    }

    /** Ubicaciones donde la bebida tiene saldo distinto de cero, por nombre. */
    @Query(nativeQuery = true, value = """
            SELECT s.ubicacion_id AS ubicacionId, u.nombre AS ubicacion, s.cantidad AS cantidad
            FROM stock_ubicacion s JOIN ubicacion u USING (ubicacion_id)
            WHERE s.bebida_id = :bebidaId AND s.cantidad <> 0
            ORDER BY u.nombre""")
    List<Saldo> saldos(long bebidaId);
}
