package ar.edu.utn.vastio.agenda.aplicacion;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.agenda.dominio.Cliente;
import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.dominio.UnidadComercializable;
import ar.edu.utn.vastio.agenda.infraestructura.BloqueoRepository;
import ar.edu.utn.vastio.agenda.infraestructura.EventoRepository;
import ar.edu.utn.vastio.agenda.infraestructura.NumeradorEvento;
import ar.edu.utn.vastio.agenda.infraestructura.UnidadComercializableRepository;
import ar.edu.utn.vastio.comun.errores.Mensajes;
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
 * <p>Exclusividad: se toma {@code SELECT … FOR UPDATE} sobre la unidad y, con el bloqueo tomado, se verifica
 * que no haya evento activo ni bloqueo. Si aun así dos transacciones llegaran a insertar, el índice
 * {@code ux_evento_unidad_activa} rechaza la segunda. En ambos casos: 409 «Esa fecha ya está tomada…».
 */
@Service
public class PreReservaService {

    private static final ZoneId ZONA = ZoneId.of(VastioApplication.ZONA_HORARIA);
    private static final int LARGO_NOMBRE = 120;

    private final UnidadComercializableRepository unidades;
    private final EventoRepository eventos;
    private final BloqueoRepository bloqueos;
    private final NumeradorEvento numerador;
    private final ClienteService clientes;
    private final CatalogoService catalogos;
    private final UsuarioService usuarios;
    private final MaquinaDeEstados maquina;

    public PreReservaService(UnidadComercializableRepository unidades, EventoRepository eventos,
            BloqueoRepository bloqueos, NumeradorEvento numerador, ClienteService clientes, CatalogoService catalogos,
            UsuarioService usuarios, MaquinaDeEstados maquina) {
        this.unidades = unidades;
        this.eventos = eventos;
        this.bloqueos = bloqueos;
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

        UnidadComercializable unidad = tomarUnidad(pedido.salonId(), pedido.fecha(), pedido.turnoId());
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

    /** Crea la unidad si hace falta, la bloquea y verifica que esté libre. */
    private UnidadComercializable tomarUnidad(short salonId, LocalDate fecha, short turnoId) {
        unidades.asegurar(salonId, fecha, turnoId);
        UnidadComercializable unidad = unidades.bloquear(salonId, fecha, turnoId).orElseThrow();
        if (eventos.hayActivoEn(unidad.getId(), EstadoEvento.INACTIVOS)) {
            throw ProblemaException.conflicto(Mensajes.CODIGO_FECHA_TOMADA, Mensajes.FECHA_TOMADA);
        }
        var bloqueo = bloqueos.activosEn(unidad.getId()).stream().findFirst();
        if (bloqueo.isPresent()) {
            String motivo = catalogos.motivo(bloqueo.get().getMotivoId()).getNombre().toLowerCase();
            throw ProblemaException.conflicto("UNIDAD_BLOQUEADA",
                    "Esa fecha está bloqueada (" + motivo + "). Elegí otro salón, otra fecha u otro turno.");
        }
        return unidad;
    }

    private static String recortar(String texto) {
        return texto.length() <= LARGO_NOMBRE ? texto : texto.substring(0, LARGO_NOMBRE);
    }
}
