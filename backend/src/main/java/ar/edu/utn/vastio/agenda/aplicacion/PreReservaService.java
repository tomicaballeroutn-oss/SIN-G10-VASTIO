package ar.edu.utn.vastio.agenda.aplicacion;

import java.time.LocalDate;
import java.time.ZoneId;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.agenda.dominio.Cliente;
import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.dominio.UnidadComercializable;
import ar.edu.utn.vastio.agenda.infraestructura.NumeradorEvento;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.Salon;
import ar.edu.utn.vastio.configuracion.dominio.TipoEvento;
import ar.edu.utn.vastio.usuarios.aplicacion.UsuarioService;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;

/**
 * Registrar pre-reserva (UI-08): aparta una unidad para un cliente mientras se negocia. No vence sola.
 *
 * <p>Exclusividad: {@link TomaDeUnidad}.
 */
@Service
public class PreReservaService {

    private static final ZoneId ZONA = ZoneId.of(VastioApplication.ZONA_HORARIA);
    private static final int LARGO_NOMBRE = 120;

    private final TomaDeUnidad tomaDeUnidad;
    private final NumeradorEvento numerador;
    private final ClienteService clientes;
    private final CatalogoService catalogos;
    private final UsuarioService usuarios;
    private final MaquinaDeEstados maquina;
    private final ConsultaEventoService consultas;
    private final EntityManager entityManager;

    public PreReservaService(TomaDeUnidad tomaDeUnidad, NumeradorEvento numerador, ClienteService clientes,
            CatalogoService catalogos, UsuarioService usuarios, MaquinaDeEstados maquina, ConsultaEventoService consultas,
            EntityManager entityManager) {
        this.consultas = consultas;
        this.entityManager = entityManager;
        this.tomaDeUnidad = tomaDeUnidad;
        this.numerador = numerador;
        this.clientes = clientes;
        this.catalogos = catalogos;
        this.usuarios = usuarios;
        this.maquina = maquina;
    }

    public record PreReserva(short salonId, LocalDate fecha, short turnoId, short tipoEventoId, String nombre,
            Long vendedoraId, ClienteService.DatosCliente cliente) {
    }

    @Transactional
    public Evento registrar(PreReserva pedido, UsuarioActual quien) {
        long vendedora = titular(pedido.vendedoraId(), quien);
        if (pedido.fecha().isBefore(LocalDate.now(ZONA))) {
            throw ProblemaException.reglaDeNegocio("FECHA_PASADA", "No se puede pre-reservar una fecha pasada. Elegí otra fecha.");
        }
        Salon salon = catalogos.salon(pedido.salonId());
        if (!salon.isActivo()) {
            throw ProblemaException.reglaDeNegocio("SALON_DADO_DE_BAJA",
                    "El salón " + salon.getNombre() + " está dado de baja: no acepta pre-reservas.");
        }
        catalogos.turno(pedido.turnoId());
        TipoEvento tipo = catalogos.tipoEvento(pedido.tipoEventoId());
        if (!tipo.isActivo()) {
            throw ProblemaException.reglaDeNegocio("TIPO_DADO_DE_BAJA",
                    "El tipo de evento " + tipo.getNombre() + " está dado de baja. Elegí otro.");
        }

        UnidadComercializable unidad = tomaDeUnidad.tomar(pedido.salonId(), pedido.fecha(), pedido.turnoId());
        Cliente cliente = clientes.obtenerOCrear(pedido.cliente());
        String nombre = pedido.nombre() == null || pedido.nombre().isBlank()
                ? tipo.getNombre() + " de " + cliente.getNombre()
                : pedido.nombre().trim();
        int anio = LocalDate.now(ZONA).getYear();
        String codigo = NumeradorEvento.codigo(anio, numerador.siguiente(anio));
        Evento evento = new Evento(codigo, unidad, cliente, tipo.getId(), vendedora, recortar(nombre));
        return maquina.registrarCreacion(evento, quien.id());
    }

    /**
     * Liberar pre-reserva (UI-10): la pre-reserva que no prosperó pasa a Liberada y la unidad vuelve a estar
     * disponible. No es una cancelación. La hacen la vendedora titular, Coordinación y Dirección.
     */
    @Transactional
    public Evento liberar(long id, String observacion, UsuarioActual quien) {
        Evento evento = consultas.visible(id, quien);
        if (!AccesoEvento.puedeOperarComoTitular(quien, evento)) {
            throw ConsultaEventoService.sinPermiso();
        }
        if (evento.getEstado() != EstadoEvento.PRE_RESERVA) {
            throw ProblemaException.reglaDeNegocio("ESTADO_NO_PERMITE", "El evento está en %s: solo se liberan pre-reservas.".formatted(
                    MaquinaDeEstados.nombre(evento.getEstado())));
        }
        // Una versión nueva: si justo otra persona registraba la seña, una de las dos recibe 409.
        entityManager.lock(evento, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        maquina.transicionar(evento, EstadoEvento.LIBERADA, quien.id(), observacion);
        return evento;
    }

    /**
     * La vendedora siempre pre-reserva a su nombre. Coordinación y Dirección eligen la vendedora interviniente
     * (si además son vendedoras y no eligen, queda a su nombre).
     */
    private long titular(Long pedida, UsuarioActual quien) {
        if (!quien.accesoTotal()) {
            if (pedida != null && pedida != quien.id()) {
                throw new ProblemaException(HttpStatus.FORBIDDEN, "SOLO_A_TU_NOMBRE", "Sin permiso",
                        "Solo podés registrar pre-reservas a tu nombre.");
            }
            return quien.id();
        }
        if (pedida == null) {
            if (quien.tiene("VENDEDORA")) {
                return quien.id();
            }
            throw ProblemaException.reglaDeNegocio("FALTA_VENDEDORA", "Elegí la vendedora interviniente.");
        }
        boolean esVendedoraActiva = usuarios.activosConPerfil(RolCodigo.VENDEDORA).stream()
                .anyMatch(u -> u.getId() == pedida.longValue());
        if (!esVendedoraActiva) {
            throw ProblemaException.reglaDeNegocio("VENDEDORA_INVALIDA", "Elegí una vendedora activa.");
        }
        return pedida;
    }

    private static String recortar(String texto) {
        return texto.length() <= LARGO_NOMBRE ? texto : texto.substring(0, LARGO_NOMBRE);
    }
}
