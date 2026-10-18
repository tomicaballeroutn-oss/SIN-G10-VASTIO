package ar.edu.utn.vastio.agenda.aplicacion;

import java.util.EnumSet;
import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.AmbitoMotivo;
import ar.edu.utn.vastio.configuracion.dominio.Motivo;

/**
 * Cancelar evento (UI-17): Coordinación y Dirección, desde Señado, Contratado o Confirmado (la pre-reserva se libera,
 * no se cancela). Motivo obligatorio del catálogo; el detalle es obligatorio si el motivo es «Otro». La unidad queda
 * libre (el índice de exclusividad no cuenta los cancelados) y se avisa a todas las áreas.
 */
@Service
public class CancelacionService {

    /** Desde dónde se cancela (docs/maquina-de-estados.md). */
    public static final Set<EstadoEvento> ESTADOS = EnumSet.of(EstadoEvento.SENADO, EstadoEvento.CONTRATADO,
            EstadoEvento.CONFIRMADO);

    private static final int LARGO_OBSERVACION = 255;

    private final ConsultaEventoService consultas;
    private final CatalogoService catalogos;
    private final MaquinaDeEstados maquina;
    private final EntityManager entityManager;

    public CancelacionService(ConsultaEventoService consultas, CatalogoService catalogos, MaquinaDeEstados maquina,
            EntityManager entityManager) {
        this.consultas = consultas;
        this.catalogos = catalogos;
        this.maquina = maquina;
        this.entityManager = entityManager;
    }

    /** «Otro» exige contar qué pasó. */
    public static boolean pideDetalle(Motivo motivo) {
        return motivo.pideDetalle();
    }

    /**
     * @param detalle opcional salvo con el motivo «Otro»; vacío es null.
     */
    @Transactional
    public Evento cancelar(long id, short motivoId, String detalle, UsuarioActual quien) {
        Evento evento = consultas.visible(id, quien);
        if (!quien.accesoTotal()) {
            throw ConsultaEventoService.sinPermiso();
        }
        if (evento.getEstado() == EstadoEvento.PRE_RESERVA) {
            throw ProblemaException.reglaDeNegocio("ESTADO_NO_PERMITE",
                    "Una pre-reserva no se cancela: se libera con «Liberar pre-reserva».");
        }
        if (!ESTADOS.contains(evento.getEstado())) {
            throw ConsultaEventoService.noPermiteEnEsteEstado(evento, "cancelar");
        }
        Motivo motivo = catalogos.motivo(motivoId);
        if (motivo.getAmbito() != AmbitoMotivo.CANCELACION || !motivo.isActivo()) {
            throw ProblemaException.reglaDeNegocio("MOTIVO_INVALIDO", "Elegí un motivo de cancelación de la lista.");
        }
        if (detalle == null && pideDetalle(motivo)) {
            throw ProblemaException.reglaDeNegocio("FALTA_DETALLE", "Contanos qué pasó: el motivo es «" + motivo.getNombre() + "».");
        }
        // Una versión nueva: si justo otra persona confirmaba o reprogramaba el evento, una de las dos recibe 409.
        entityManager.lock(evento, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        evento.cancelar(motivo.getId(), detalle);
        maquina.transicionar(evento, EstadoEvento.CANCELADO, quien.id(), observacion(motivo, detalle));
        return evento;
    }

    /** Lo que queda en el historial: «Desistimiento del cliente: se mudan a Mendoza». */
    private static String observacion(Motivo motivo, String detalle) {
        String texto = detalle == null ? motivo.getNombre() : motivo.getNombre() + ": " + detalle;
        return texto.length() <= LARGO_OBSERVACION ? texto : texto.substring(0, LARGO_OBSERVACION - 1) + "…";
    }
}
