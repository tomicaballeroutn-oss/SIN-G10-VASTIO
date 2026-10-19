package ar.edu.utn.vastio.bebida.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import ar.edu.utn.vastio.bebida.dominio.ConsumoEvento;

public interface ConsumoEventoRepository extends JpaRepository<ConsumoEvento, ConsumoEvento.Clave> {

    List<ConsumoEvento> findByEventoId(long eventoId);

    /**
     * Al cerrar el evento, su orden de preparación vigente (si existía) queda CERRADA. La orden todavía no tiene entidad
     * («Registrar la preparación del evento» la construyen en paralelo): se actualiza la tabla directamente.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE orden_preparacion SET estado = 'CERRADA' WHERE evento_id = :eventoId AND estado NOT IN ('DESCARTADA', 'CERRADA')""")
    int cerrarOrdenDePreparacion(long eventoId);
}
