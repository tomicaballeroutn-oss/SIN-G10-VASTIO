package ar.edu.utn.vastio.notificaciones.aplicacion;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.notificaciones.dominio.Notificacion;
import ar.edu.utn.vastio.notificaciones.dominio.TipoNotificacion;
import ar.edu.utn.vastio.notificaciones.infraestructura.NotificacionRepository;
import ar.edu.utn.vastio.usuarios.aplicacion.UsuarioService;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Genera los avisos. El ruteo se resuelve al crear: cada usuario activo de los perfiles indicados, más las personas
 * nombradas (p. ej. la vendedora titular) si siguen activas, queda como destinatario, salvo quien originó el aviso.
 * Quién recibe qué lo decide el módulo que avisa (en la agenda, {@code AvisosEvento}).
 */
@Service
@Transactional(readOnly = true)
public class NotificacionService {

    private final NotificacionRepository notificaciones;
    private final UsuarioService usuarios;

    public NotificacionService(NotificacionRepository notificaciones, UsuarioService usuarios) {
        this.notificaciones = notificaciones;
        this.usuarios = usuarios;
    }

    @Transactional
    public Notificacion notificar(Long eventoId, TipoNotificacion tipo, String mensaje, Long origenId,
            Collection<RolCodigo> perfiles) {
        return notificar(eventoId, tipo, mensaje, origenId, perfiles, List.of());
    }

    /**
     * @param personas usuarios a los que se avisa además de los perfiles; los dados de baja se descartan.
     * @return null si no quedó ningún destinatario: no se guarda un aviso que nadie va a leer.
     */
    @Transactional
    public Notificacion notificar(Long eventoId, TipoNotificacion tipo, String mensaje, Long origenId,
            Collection<RolCodigo> perfiles, Collection<Long> personas) {
        Set<Long> destinatarios = new LinkedHashSet<>();
        for (RolCodigo perfil : perfiles) {
            usuarios.activosConPerfil(perfil).stream().map(Usuario::getId).forEach(destinatarios::add);
        }
        if (!personas.isEmpty()) {
            destinatarios.addAll(usuarios.activos(personas));
        }
        if (origenId != null) {
            destinatarios.remove(origenId);
        }
        if (destinatarios.isEmpty()) {
            return null;
        }
        Notificacion notificacion = new Notificacion(eventoId, tipo, mensaje, origenId);
        destinatarios.forEach(notificacion::agregarDestinatario);
        return notificaciones.save(notificacion);
    }

    public List<Notificacion> delEvento(long eventoId) {
        return notificaciones.findByEventoIdOrderByIdAsc(eventoId);
    }
}
