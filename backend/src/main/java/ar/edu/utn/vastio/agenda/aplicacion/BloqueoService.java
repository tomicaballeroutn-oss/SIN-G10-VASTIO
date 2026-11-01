package ar.edu.utn.vastio.agenda.aplicacion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.agenda.dominio.Bloqueo;
import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.dominio.UnidadComercializable;
import ar.edu.utn.vastio.agenda.infraestructura.BloqueoRepository;
import ar.edu.utn.vastio.agenda.infraestructura.EventoRepository;
import ar.edu.utn.vastio.agenda.infraestructura.UnidadComercializableRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.AmbitoMotivo;
import ar.edu.utn.vastio.configuracion.dominio.Motivo;
import ar.edu.utn.vastio.configuracion.dominio.Salon;
import ar.edu.utn.vastio.configuracion.dominio.Turno;
import ar.edu.utn.vastio.usuarios.aplicacion.UsuarioService;

/**
 * Bloqueo de unidades (UI-21, A1-02): saca de la venta salones × fechas × turnos por mantenimiento, feriado o evento
 * propio. No es un estado del evento: un bloqueo no se aplica sobre una unidad con evento activo ni sobre una ya bloqueada.
 *
 * <p>Exclusividad: igual que {@link TomaDeUnidad}, crea las unidades que falten y toma {@code SELECT … FOR UPDATE} sobre
 * cada una antes de mirar eventos y bloqueos. Las toma siempre en el mismo orden (salón, fecha, turno), así que dos
 * bloqueos simultáneos que se pisan no se traban entre sí: el segundo espera y después ve al primero (409).
 * Una pre-reserva que llega en el medio espera el mismo {@code FOR UPDATE} y, cuando entra, ve el bloqueo.
 */
@Service
public class BloqueoService {

    /** Tope de un bloqueo: medio año. Más que eso casi seguro es un error de tipeo en el año. */
    public static final int MAXIMO_DIAS = 180;

    private static final ZoneId ZONA = ZoneId.of(VastioApplication.ZONA_HORARIA);
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final UnidadComercializableRepository unidades;
    private final BloqueoRepository bloqueos;
    private final EventoRepository eventos;
    private final CatalogoService catalogos;
    private final UsuarioService usuarios;

    public BloqueoService(UnidadComercializableRepository unidades, BloqueoRepository bloqueos, EventoRepository eventos,
            CatalogoService catalogos, UsuarioService usuarios) {
        this.unidades = unidades;
        this.bloqueos = bloqueos;
        this.eventos = eventos;
        this.catalogos = catalogos;
        this.usuarios = usuarios;
    }

    /** {@code hasta} vacío: un solo día. */
    public record Pedido(Set<Short> salonIds, LocalDate desde, LocalDate hasta, Set<Short> turnoIds, short motivoId,
            String detalle) {
    }

    public record Item(short id, String nombre) {
    }

    /** Un bloqueo tal como lo muestra la lista: el rectángulo salones × fechas × turnos que cubre. */
    public record BloqueoVista(long id, Item motivo, String detalle, List<Item> salones, LocalDate desde, LocalDate hasta,
            List<Item> turnos, int cantidadUnidades, long registradoPorId, String registradoPor,
            OffsetDateTime fechaRegistro) {
    }

    @Transactional
    public BloqueoVista registrar(Pedido pedido, UsuarioActual quien) {
        LocalDate desde = pedido.desde();
        LocalDate hasta = pedido.hasta() == null ? desde : pedido.hasta();
        if (hasta.isBefore(desde)) {
            throw ProblemaException.reglaDeNegocio("RANGO_INVALIDO",
                    "La fecha final no puede ser anterior a la inicial. Revisá el rango.");
        }
        if (desde.isBefore(LocalDate.now(ZONA))) {
            throw ProblemaException.reglaDeNegocio("FECHA_PASADA", "No se puede bloquear una fecha pasada. Elegí otra fecha.");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAXIMO_DIAS) {
            throw ProblemaException.reglaDeNegocio("RANGO_MUY_LARGO",
                    "Podés bloquear hasta " + MAXIMO_DIAS + " días por vez. Dividí el bloqueo en dos.");
        }
        Motivo motivo = motivo(pedido.motivoId());
        Map<Short, Salon> salones = new TreeMap<>();
        for (short id : new TreeSet<>(pedido.salonIds())) {
            Salon salon = catalogos.salon(id);
            if (!salon.isActivo()) {
                throw ProblemaException.reglaDeNegocio("SALON_DADO_DE_BAJA",
                        "El salón " + salon.getNombre() + " está dado de baja: no hace falta bloquearlo.");
            }
            salones.put(id, salon);
        }
        Map<Short, Turno> turnos = new TreeMap<>();
        for (short id : new TreeSet<>(pedido.turnoIds())) {
            turnos.put(id, catalogos.turno(id));
        }
        String detalle = pedido.detalle() == null || pedido.detalle().isBlank() ? null : pedido.detalle().trim();

        // Siempre en el mismo orden: salón, fecha, turno. Es lo que evita el deadlock entre dos bloqueos.
        List<UnidadComercializable> tomadas = new ArrayList<>();
        for (short salonId : salones.keySet()) {
            for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
                for (short turnoId : turnos.keySet()) {
                    unidades.asegurar(salonId, fecha, turnoId);
                    tomadas.add(unidades.bloquear(salonId, fecha, turnoId).orElseThrow());
                }
            }
        }
        verificarLibres(tomadas, desde, hasta, salones, turnos);

        Bloqueo bloqueo = bloqueos.save(new Bloqueo(motivo.getId(), detalle, quien.id(), OffsetDateTime.now(), tomadas));
        return vista(bloqueo, nombresDeLaLista(List.of(bloqueo)));
    }

    /** Los bloqueos activos que todavía tienen alguna fecha por delante, el más próximo primero. */
    @Transactional(readOnly = true)
    public List<BloqueoVista> activos() {
        LocalDate hoy = LocalDate.now(ZONA);
        List<Bloqueo> vigentes = bloqueos.activosConUnidades(hoy);
        Nombres nombres = nombresDeLaLista(vigentes);
        return vigentes.stream().map(b -> vista(b, nombres))
                .sorted(Comparator.comparing(BloqueoVista::desde).thenComparing(BloqueoVista::id))
                .toList();
    }

    /**
     * Desbloquear: las unidades vuelven a la venta. El bloqueo no se borra; queda inactivo con quién y cuándo.
     *
     * @throws ProblemaException 404 si no existe, 409 si ya estaba desbloqueado (por ejemplo, otra persona lo hizo antes).
     */
    @Transactional
    public void desbloquear(long id, UsuarioActual quien) {
        Bloqueo bloqueo = bloqueos.paraDesbloquear(id).orElseThrow(
                () -> ProblemaException.noEncontrado("BLOQUEO_INEXISTENTE", "No encontramos ese bloqueo."));
        if (!bloqueo.isActivo()) {
            throw ProblemaException.conflicto("BLOQUEO_YA_DESBLOQUEADO",
                    "Ese bloqueo ya estaba desbloqueado. Actualizá la lista para ver cómo está ahora.");
        }
        bloqueo.desbloquear(quien.id(), OffsetDateTime.now());
    }

    // ---------- reglas ----------

    private Motivo motivo(short motivoId) {
        Motivo motivo = catalogos.motivo(motivoId);
        if (motivo.getAmbito() != AmbitoMotivo.BLOQUEO || !motivo.isActivo()) {
            throw ProblemaException.reglaDeNegocio("MOTIVO_INVALIDO", "Elegí un motivo de bloqueo de la lista.");
        }
        return motivo;
    }

    /**
     * Con las unidades ya tomadas con {@code FOR UPDATE}: ninguna puede tener evento activo ni otro bloqueo.
     * El conflicto con un evento nombra el evento, para que la persona sepa a quién llamar o qué liberar.
     */
    private void verificarLibres(List<UnidadComercializable> tomadas, LocalDate desde, LocalDate hasta,
            Map<Short, Salon> salones, Map<Short, Turno> turnos) {
        Set<Long> ids = tomadas.stream().map(UnidadComercializable::getId).collect(Collectors.toSet());
        List<Evento> conEvento = eventos.activosEntre(desde, hasta, EstadoEvento.INACTIVOS).stream()
                .filter(e -> ids.contains(e.getUnidad().getId()))
                .sorted(Comparator.comparing((Evento e) -> e.getUnidad().getFecha())
                        .thenComparing(e -> e.getUnidad().getTurnoId()).thenComparing(e -> e.getUnidad().getSalonId()))
                .toList();
        if (!conEvento.isEmpty()) {
            Evento primero = conEvento.getFirst();
            throw ProblemaException.conflicto("UNIDAD_OCUPADA", donde(primero.getUnidad(), salones, turnos) + " ya tiene el evento "
                    + primero.getCodigo() + " (" + primero.getNombre() + ")." + otras(conEvento.size())
                    + " Liberalo, cancelalo o reprogramalo antes de bloquear esa fecha, o excluila del rango.");
        }
        Set<Long> yaVistas = new HashSet<>();
        List<BloqueoRepository.UnidadBloqueada> yaBloqueadas = bloqueos.activosEntre(desde, hasta).stream()
                .filter(b -> ids.contains(b.unidad().getId()) && yaVistas.add(b.unidad().getId()))
                .sorted(Comparator.comparing((BloqueoRepository.UnidadBloqueada b) -> b.unidad().getFecha())
                        .thenComparing(b -> b.unidad().getTurnoId()).thenComparing(b -> b.unidad().getSalonId()))
                .toList();
        if (!yaBloqueadas.isEmpty()) {
            var primera = yaBloqueadas.getFirst();
            String motivo = catalogos.motivo(primera.bloqueo().getMotivoId()).getNombre().toLowerCase();
            throw ProblemaException.conflicto("YA_BLOQUEADA", donde(primera.unidad(), salones, turnos) + " ya está bloqueado ("
                    + motivo + ")." + otras(yaBloqueadas.size()) + " Desbloquealo primero o elegí otras fechas.");
        }
    }

    private static String donde(UnidadComercializable unidad, Map<Short, Salon> salones, Map<Short, Turno> turnos) {
        return salones.get(unidad.getSalonId()).getNombre() + " el " + unidad.getFecha().format(DIA) + " (turno "
                + turnos.get(unidad.getTurnoId()).getNombre().toLowerCase() + ")";
    }

    private static String otras(int cantidad) {
        return cantidad > 1 ? " Hay otras " + (cantidad - 1) + " en la misma situación." : "";
    }

    // ---------- vista ----------

    /** Nombres que la lista necesita, cargados una sola vez. */
    private record Nombres(Map<Short, String> salones, Map<Short, String> turnos, Map<Short, String> motivos,
            Map<Long, String> personas) {
    }

    private Nombres nombresDeLaLista(List<Bloqueo> lista) {
        Set<Long> personas = lista.stream().map(Bloqueo::getUsuarioId).collect(Collectors.toSet());
        return new Nombres(
                catalogos.salones().stream().collect(Collectors.toMap(Salon::getId, Salon::getNombre)),
                catalogos.turnos().stream().collect(Collectors.toMap(Turno::getId, Turno::getNombre)),
                catalogos.motivos().stream().collect(Collectors.toMap(Motivo::getId, Motivo::getNombre)),
                usuarios.nombres(personas));
    }

    private BloqueoVista vista(Bloqueo b, Nombres nombres) {
        Set<UnidadComercializable> unidadesDelBloqueo = b.getUnidades();
        List<Item> salones = unidadesDelBloqueo.stream().map(UnidadComercializable::getSalonId).distinct().sorted()
                .map(id -> new Item(id, nombres.salones().get(id))).toList();
        List<Item> turnos = unidadesDelBloqueo.stream().map(UnidadComercializable::getTurnoId).distinct().sorted()
                .map(id -> new Item(id, nombres.turnos().get(id))).toList();
        LocalDate desde = unidadesDelBloqueo.stream().map(UnidadComercializable::getFecha).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate hasta = unidadesDelBloqueo.stream().map(UnidadComercializable::getFecha).max(Comparator.naturalOrder()).orElseThrow();
        return new BloqueoVista(b.getId(), new Item(b.getMotivoId(), nombres.motivos().get(b.getMotivoId())), b.getDetalle(),
                salones, desde, hasta, turnos, unidadesDelBloqueo.size(), b.getUsuarioId(),
                nombres.personas().get(b.getUsuarioId()), b.getFechaRegistro());
    }
}
