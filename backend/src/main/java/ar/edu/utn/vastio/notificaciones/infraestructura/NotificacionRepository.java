package ar.edu.utn.vastio.notificaciones.infraestructura;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.utn.vastio.notificaciones.dominio.Notificacion;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update Notificacion n set n.resuelta = true where n.eventoId = :eventoId and n.tipo = ar.edu.utn.vastio.notificaciones.dominio.TipoNotificacion.CALENDAR_PENDIENTE and n.resuelta = false")
    int resolverCalendar(long eventoId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update Notificacion n set n.resuelta=true where n.tipo=ar.edu.utn.vastio.notificaciones.dominio.TipoNotificacion.CALENDAR_PENDIENTE and n.resuelta=false")
    int silenciarCalendar();

    List<Notificacion> findByEventoIdOrderByIdAsc(Long eventoId);
}
