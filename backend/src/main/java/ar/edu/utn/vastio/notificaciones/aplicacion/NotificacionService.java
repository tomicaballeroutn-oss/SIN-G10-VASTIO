package ar.edu.utn.vastio.notificaciones.aplicacion;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.notificaciones.dominio.Notificacion;
import ar.edu.utn.vastio.notificaciones.dominio.NotificacionDestinatario;
import ar.edu.utn.vastio.notificaciones.dominio.TipoNotificacion;
import ar.edu.utn.vastio.notificaciones.infraestructura.NotificacionDestinatarioRepository;
import ar.edu.utn.vastio.notificaciones.infraestructura.NotificacionRepository;
import ar.edu.utn.vastio.usuarios.aplicacion.UsuarioService;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Genera los avisos. El ruteo se resuelve al crear: cada usuario activo de los perfiles indicados, más las personas
 * nombradas (p. ej. la vendedora titular) si siguen activas, queda como destinatario, salvo quien originó el aviso.
 * Quién recibe qué lo decide el módulo que avisa (en la agenda, {@code AvisosEvento}). Cada persona consulta solo
 * los suyos y los marca como leídos (UI-22).
 */
@Service
@Transactional(readOnly = true)
public class NotificacionService {

    /** Avisos por página en la lista. */
    public static final int POR_PAGINA = 20;

    private final NotificacionRepository notificaciones;
    private final NotificacionDestinatarioRepository recibidas;
    private final UsuarioService usuarios;

    public NotificacionService(NotificacionRepository notificaciones, NotificacionDestinatarioRepository recibidas,
            UsuarioService usuarios) {
        this.notificaciones = notificaciones;
        this.recibidas = recibidas;
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

    /** Los avisos de la persona, más recientes primero, de a {@link #POR_PAGINA}. */
    public Slice<NotificacionDestinatario> delUsuario(long usuarioId, boolean soloSinLeer, int pagina) {
        return recibidas.delUsuario(usuarioId, soloSinLeer, PageRequest.of(Math.max(0, pagina), POR_PAGINA));
    }

    public long sinLeer(long usuarioId) {
        return recibidas.sinLeer(usuarioId);
    }

    /**
     * @throws ProblemaException 404 si el aviso no existe o es de otra persona (no se revela cuál de las dos).
     */
    @Transactional
    public void marcarLeida(long notificacionId, long usuarioId) {
        recibidas.de(notificacionId, usuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("NOTIFICACION_INEXISTENTE", "No encontramos ese aviso."))
                .marcarLeida(OffsetDateTime.now());
    }

    @Transactional
    public int marcarTodasLeidas(long usuarioId) {
        return recibidas.marcarTodasLeidas(usuarioId, OffsetDateTime.now());
    }

    public List<Notificacion> delEvento(long eventoId) {
        return notificaciones.findByEventoIdOrderByIdAsc(eventoId);
    }
}
