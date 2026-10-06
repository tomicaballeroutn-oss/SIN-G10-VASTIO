package ar.edu.utn.vastio.agenda.aplicacion;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.agenda.aplicacion.RequisitosConfirmacion.Requisito;
import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;

/**
 * Confirmar evento (UI-15): el evento contratado pasa a Confirmado cuando cumple los requisitos de
 * {@link RequisitosConfirmacion}. Lo hacen la planner asignada, Coordinación y Dirección. Avisa a Compras, Cocina,
 * Administración y la vendedora titular, y habilita la orden de preparación (Sprint 3).
 */
@Service
public class ConfirmacionService {

    private final ConsultaEventoService consultas;
    private final RequisitosConfirmacion requisitos;
    private final MaquinaDeEstados maquina;
    private final EntityManager entityManager;

    public ConfirmacionService(ConsultaEventoService consultas, RequisitosConfirmacion requisitos, MaquinaDeEstados maquina,
            EntityManager entityManager) {
        this.consultas = consultas;
        this.requisitos = requisitos;
        this.maquina = maquina;
        this.entityManager = entityManager;
    }

    @Transactional
    public Evento confirmar(long id, UsuarioActual quien) {
        Evento evento = consultas.visible(id, quien);
        if (!AccesoEvento.puedeConfirmar(quien, evento)) {
            throw ConsultaEventoService.sinPermiso();
        }
        if (evento.getEstado() != EstadoEvento.CONTRATADO) {
            throw ProblemaException.reglaDeNegocio("ESTADO_NO_PERMITE",
                    "El evento está en %s: se confirman eventos contratados.".formatted(MaquinaDeEstados.nombre(evento.getEstado())));
        }
        // Bloquea antes de verificar: si en el medio otra persona quita la planner o cambia los invitados, espera.
        entityManager.lock(evento, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        List<Requisito> pendientes = requisitos.de(evento).stream().filter(r -> !r.cumplido()).toList();
        if (!pendientes.isEmpty()) {
            throw ProblemaException.reglaDeNegocio("REQUISITOS_PENDIENTES", "Todavía no se puede confirmar. "
                    + pendientes.stream().map(Requisito::detalle).collect(Collectors.joining(" ")));
        }
        maquina.transicionar(evento, EstadoEvento.CONFIRMADO, quien.id(), null);
        return evento;
    }
}
