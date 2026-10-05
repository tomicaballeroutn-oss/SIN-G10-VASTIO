package ar.edu.utn.vastio.agenda.aplicacion;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.agenda.dominio.Cliente;
import ar.edu.utn.vastio.agenda.dominio.ContactoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.dominio.ModificacionEvento;
import ar.edu.utn.vastio.agenda.infraestructura.ModificacionEventoRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.TipoEvento;

/**
 * Registrar evento: completa y modifica cliente, contactos, tipo, nombre, invitados y observaciones internas.
 * Cada dato que cambia deja una fila en {@code modificacion_evento} con el valor anterior y el nuevo.
 * Salón, fecha y turno no se cambian acá: eso es reprogramar.
 */
@Service
public class DatosEventoService {

    private final ConsultaEventoService consultas;
    private final ModificacionEventoRepository modificaciones;
    private final CatalogoService catalogos;
    private final EntityManager entityManager;

    public DatosEventoService(ConsultaEventoService consultas, ModificacionEventoRepository modificaciones,
            CatalogoService catalogos, EntityManager entityManager) {
        this.entityManager = entityManager;
        this.consultas = consultas;
        this.modificaciones = modificaciones;
        this.catalogos = catalogos;
    }

    public record DatosCliente(String nombre, String documento, String telefono, String email) {
    }

    /** Sin {@code id}: contacto nuevo. Los contactos que no vienen se quitan. */
    public record DatosContacto(Long id, String nombre, String vinculo, String telefono, String email) {
    }

    /**
     * @param version la que tenía la ficha al abrir el formulario: si otra persona guardó en el medio, 409.
     */
    public record DatosEvento(int version, String nombre, short tipoEventoId, Integer cantidadInvitados,
            String observacionesInternas, DatosCliente cliente, List<DatosContacto> contactos) {
    }

    @Transactional
    public Evento registrar(long id, DatosEvento datos, UsuarioActual quien) {
        Evento evento = consultas.visible(id, quien);
        if (!AccesoEvento.puedeModificar(quien, evento)) {
            throw ConsultaEventoService.sinPermiso();
        }
        if (!ConsultaEventoService.EDITABLES.contains(evento.getEstado())) {
            throw ConsultaEventoService.noPermiteEnEsteEstado(evento, "modificar sus datos");
        }
        if (evento.getVersion() != datos.version()) {
            throw ProblemaException.conflicto("EVENTO_MODIFICADO",
                    "Otra persona modificó el evento mientras lo editabas. Volvé a abrir la ficha para ver los cambios.");
        }

        // Contactos y cliente no cambian columnas de evento: sin esto, dos ediciones de contactos no se detectarían.
        // Toda ficha guardada es una versión nueva, y la fila queda bloqueada hasta terminar.
        entityManager.lock(evento, LockModeType.PESSIMISTIC_FORCE_INCREMENT);

        Registro registro = new Registro(evento.getId(), quien.id());
        if (evento.getTipoEventoId() != datos.tipoEventoId()) {
            TipoEvento nuevo = catalogos.tipoEvento(datos.tipoEventoId());
            if (!nuevo.isActivo()) {
                throw ProblemaException.reglaDeNegocio("TIPO_DADO_DE_BAJA",
                        "El tipo de evento " + nuevo.getNombre() + " está dado de baja. Elegí otro.");
            }
            registro.cambio("tipo_evento", catalogos.tipoEvento(evento.getTipoEventoId()).getNombre(), nuevo.getNombre());
        }
        registro.cambio("nombre", evento.getNombre(), datos.nombre());
        registro.cambio("cantidad_invitados", texto(evento.getCantidadInvitados()), texto(datos.cantidadInvitados()));
        registro.cambio("observaciones_internas", evento.getObservacionesInternas(), datos.observacionesInternas());
        evento.actualizarDatos(datos.nombre(), datos.tipoEventoId(), datos.cantidadInvitados(), datos.observacionesInternas());

        Cliente cliente = evento.getCliente();
        DatosCliente c = datos.cliente();
        registro.cambio("cliente.nombre", cliente.getNombre(), c.nombre());
        registro.cambio("cliente.documento", cliente.getDocumento(), c.documento());
        registro.cambio("cliente.telefono", cliente.getTelefono(), c.telefono());
        registro.cambio("cliente.email", cliente.getEmail(), c.email());
        cliente.actualizar(c.nombre(), c.documento(), c.telefono(), c.email());

        actualizarContactos(evento, datos.contactos(), registro);
        modificaciones.saveAll(registro.filas);
        return evento;
    }

    private static void actualizarContactos(Evento evento, List<DatosContacto> pedidos, Registro registro) {
        Map<Long, ContactoEvento> actuales = new HashMap<>();
        evento.getContactos().forEach(ct -> actuales.put(ct.getId(), ct));
        Set<Long> quedan = new HashSet<>();
        for (DatosContacto p : pedidos) {
            if (p.id() == null) {
                ContactoEvento nuevo = evento.agregarContacto(p.nombre(), p.vinculo(), p.telefono(), p.email());
                registro.cambio("contacto", null, nuevo.descripcion());
                continue;
            }
            ContactoEvento contacto = actuales.get(p.id());
            if (contacto == null) {
                throw ProblemaException.reglaDeNegocio("CONTACTO_INEXISTENTE",
                        "Uno de los contactos ya no está en el evento. Volvé a abrir la ficha.");
            }
            quedan.add(p.id());
            String antes = contacto.descripcion();
            evento.actualizarContacto(contacto, p.nombre(), p.vinculo(), p.telefono(), p.email());
            registro.cambio("contacto", antes, contacto.descripcion());
        }
        for (ContactoEvento contacto : actuales.values()) {
            if (!quedan.contains(contacto.getId())) {
                registro.cambio("contacto", contacto.descripcion(), null);
                evento.quitarContacto(contacto);
            }
        }
    }

    private static String texto(Integer numero) {
        return numero == null ? null : numero.toString();
    }

    /** Junta los cambios de un guardado, todos con el mismo momento. Si el valor no cambió, no deja fila. */
    private static final class Registro {
        private final long eventoId;
        private final long usuarioId;
        private final OffsetDateTime momento = OffsetDateTime.now();
        private final List<ModificacionEvento> filas = new ArrayList<>();

        Registro(long eventoId, long usuarioId) {
            this.eventoId = eventoId;
            this.usuarioId = usuarioId;
        }

        void cambio(String campo, String antes, String despues) {
            if (!Objects.equals(antes, despues)) {
                filas.add(new ModificacionEvento(eventoId, campo, antes, despues, usuarioId, momento));
            }
        }
    }
}
