package ar.edu.utn.vastio.notificaciones.infraestructura;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import ar.edu.utn.vastio.notificaciones.dominio.NotificacionDestinatario;

public interface NotificacionDestinatarioRepository extends JpaRepository<NotificacionDestinatario, NotificacionDestinatario.Id> {

    /** Los avisos de una persona, más recientes primero, con la notificación. */
    @Query("""
            select d from NotificacionDestinatario d join fetch d.notificacion n
            where d.id.usuarioId = :usuarioId and (:soloSinLeer = false or d.leida = false)
            order by n.fechaHora desc, n.id desc
            """)
    Slice<NotificacionDestinatario> delUsuario(long usuarioId, boolean soloSinLeer, Pageable pagina);

    @Query("select count(d) from NotificacionDestinatario d where d.id.usuarioId = :usuarioId and d.leida = false")
    long sinLeer(long usuarioId);

    @Query("select d from NotificacionDestinatario d where d.id.notificacionId = :notificacionId and d.id.usuarioId = :usuarioId")
    Optional<NotificacionDestinatario> de(long notificacionId, long usuarioId);

    @Modifying
    @Query("""
            update NotificacionDestinatario d set d.leida = true, d.fechaLectura = :ahora
            where d.id.usuarioId = :usuarioId and d.leida = false
            """)
    int marcarTodasLeidas(long usuarioId, OffsetDateTime ahora);
}
