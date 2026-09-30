package ar.edu.utn.vastio.agenda.aplicacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;

/**
 * Registrar seña (UI-11): la pre-reserva pasa a Señado y se vuelve una reserva firme.
 * La registran la vendedora titular, Coordinación y Dirección. Avisa a Administración y Coordinación.
 */
@Service
public class SenaService {

    private static final ZoneId ZONA = ZoneId.of(VastioApplication.ZONA_HORARIA);

    private final ConsultaEventoService consultas;
    private final MaquinaDeEstados maquina;
    private final EntityManager entityManager;

    public SenaService(ConsultaEventoService consultas, MaquinaDeEstados maquina, EntityManager entityManager) {
        this.consultas = consultas;
        this.maquina = maquina;
        this.entityManager = entityManager;
    }

    public record Sena(BigDecimal importe, LocalDate fechaPago, String firmanteNombre, String firmanteDni,
            String firmanteContacto) {
    }

    @Transactional
    public Evento registrar(long id, Sena sena, UsuarioActual quien) {
        Evento evento = consultas.visible(id, quien);
        if (!AccesoEvento.puedeOperarComoTitular(quien, evento)) {
            throw ConsultaEventoService.sinPermiso();
        }
        if (evento.getEstado() != EstadoEvento.PRE_RESERVA) {
            throw ProblemaException.reglaDeNegocio("ESTADO_NO_PERMITE",
                    "El evento está en %s: la seña se registra sobre una pre-reserva.".formatted(
                            MaquinaDeEstados.nombre(evento.getEstado())));
        }
        if (sena.fechaPago().isAfter(LocalDate.now(ZONA))) {
            throw ProblemaException.reglaDeNegocio("FECHA_PAGO_FUTURA",
                    "La fecha del pago no puede ser posterior a hoy. Registrá la seña cuando esté paga.");
        }
        // Una versión nueva: si justo otra persona liberaba la pre-reserva, una de las dos recibe 409.
        entityManager.lock(evento, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        evento.registrarSena(sena.importe(), sena.fechaPago(), sena.firmanteNombre(), sena.firmanteDni(),
                sena.firmanteContacto());
        maquina.transicionar(evento, EstadoEvento.SENADO, quien.id(), null);
        return evento;
    }
}
