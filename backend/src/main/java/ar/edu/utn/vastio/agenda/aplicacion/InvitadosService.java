package ar.edu.utn.vastio.agenda.aplicacion;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.dominio.ModificacionEvento;
import ar.edu.utn.vastio.agenda.infraestructura.ModificacionEventoRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;

/**
 * Registrar cantidad de invitados (UI-14): la cantidad y si ya es definitiva, condición de «Confirmar evento».
 * Lo hacen la vendedora titular, la planner asignada, Coordinación y Dirección, entre Pre-reserva y Confirmado.
 * En un evento confirmado la cantidad puede cambiar pero sigue siendo definitiva. Superar la capacidad del salón no
 * se rechaza: hay eventos que la superan y la pantalla lo advierte.
 */
@Service
public class InvitadosService {

    private final ConsultaEventoService consultas;
    private final ModificacionEventoRepository modificaciones;
    private final AvisosEvento avisos;
    private final EntityManager entityManager;

    public InvitadosService(ConsultaEventoService consultas, ModificacionEventoRepository modificaciones,
            AvisosEvento avisos, EntityManager entityManager) {
        this.consultas = consultas;
        this.modificaciones = modificaciones;
        this.avisos = avisos;
        this.entityManager = entityManager;
    }

    /**
     * @param version la que tenía la ficha al abrir el diálogo: si otra persona guardó en el medio, 409.
     */
    @Transactional
    public Evento registrar(long id, int version, int cantidad, boolean definitivos, UsuarioActual quien) {
        Evento evento = consultas.visible(id, quien);
        if (!AccesoEvento.puedeModificar(quien, evento)) {
            throw ConsultaEventoService.sinPermiso();
        }
        if (!ConsultaEventoService.EDITABLES.contains(evento.getEstado())) {
            throw ConsultaEventoService.noPermiteEnEsteEstado(evento, "cambiar la cantidad de invitados");
        }
        if (evento.getVersion() != version) {
            throw ProblemaException.conflicto("EVENTO_MODIFICADO",
                    "Otra persona modificó el evento mientras lo editabas. Volvé a abrir la ficha para ver los cambios.");
        }
        if (evento.getEstado() == EstadoEvento.CONFIRMADO && !definitivos) {
            throw ProblemaException.reglaDeNegocio("INVITADOS_DEFINITIVOS",
                    "El evento está confirmado: la cantidad de invitados sigue siendo definitiva.");
        }
        entityManager.lock(evento, LockModeType.PESSIMISTIC_FORCE_INCREMENT);

        OffsetDateTime momento = OffsetDateTime.now();
        List<ModificacionEvento> filas = new ArrayList<>();
        String antes = evento.getCantidadInvitados() == null ? null : evento.getCantidadInvitados().toString();
        if (!Objects.equals(antes, String.valueOf(cantidad))) {
            filas.add(new ModificacionEvento(id, "cantidad_invitados", antes, String.valueOf(cantidad), quien.id(), momento));
        }
        if (evento.isInvitadosDefinitivos() != definitivos) {
            filas.add(new ModificacionEvento(id, "invitados_definitivos", sino(evento.isInvitadosDefinitivos()), sino(definitivos),
                    quien.id(), momento));
        }
        evento.registrarInvitados(cantidad, definitivos);
        modificaciones.saveAll(filas);
        avisos.modificacion(evento, filas.stream().map(ModificacionEvento::getCampo).toList(), quien.id());
        return evento;
    }

    private static String sino(boolean valor) {
        return valor ? "sí" : "no";
    }
}
