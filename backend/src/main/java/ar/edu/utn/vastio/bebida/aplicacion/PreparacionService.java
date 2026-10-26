package ar.edu.utn.vastio.bebida.aplicacion;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService;
import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService.EventoOperativo;
import ar.edu.utn.vastio.agenda.aplicacion.OperacionEventoService;
import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.bebida.dominio.*;
import ar.edu.utn.vastio.bebida.infraestructura.*;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.notificaciones.aplicacion.NotificacionService;
import ar.edu.utn.vastio.notificaciones.dominio.TipoNotificacion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;

/** B5-04. Un traspaso por solicitud, sin órdenes, reservas ni entregas a barra. */
@Service
@Transactional(readOnly = true)
public class PreparacionService {
    private final PreparacionRepository preparaciones;
    private final MovimientoStockRepository movimientos;
    private final MovimientoService motor;
    private final UbicacionRepository ubicaciones;
    private final BebidaRepository bebidas;
    private final ConsultaEventoService consultas;
    private final OperacionEventoService operaciones;
    private final NotificacionService avisos;
    private final OrdenPreparacionRepository ordenes;
    private final EntityManager em;
    private final Clock reloj;

    public PreparacionService(PreparacionRepository preparaciones, MovimientoStockRepository movimientos,
            MovimientoService motor, UbicacionRepository ubicaciones, BebidaRepository bebidas,
            ConsultaEventoService consultas, OperacionEventoService operaciones, NotificacionService avisos,
            OrdenPreparacionRepository ordenes, EntityManager em, Clock reloj) {
        this.ordenes = ordenes;
        this.preparaciones = preparaciones;
        this.movimientos = movimientos;
        this.motor = motor;
        this.ubicaciones = ubicaciones;
        this.bebidas = bebidas;
        this.consultas = consultas;
        this.operaciones = operaciones;
        this.avisos = avisos;
        this.em = em;
        this.reloj = reloj;
    }

    public record Renglon(long bebidaId, BigDecimal cantidad) { }
    public record Datos(short barraId, short destinoId, List<Renglon> renglones) { }
    public record Registrada(PreparacionEvento preparacion, List<MovimientoStock> renglones) { }
    public record Resultado(Registrada registrada, boolean repetida) { }
    public record Circuito(Ubicacion barra, Ubicacion abastecimiento) { }
    /** {@code orden}: la orden de preparación vigente, como referencia (docs/sprint-4.md, decisión 25); null si no hay. */
    public record Pantalla(EventoOperativo evento, Ubicacion madre, List<Circuito> circuitos, List<Registrada> historial,
            OrdenPreparacion orden) { }

    public Pantalla pantalla(long eventoId) {
        EventoOperativo evento = consultas.operativo(eventoId);
        List<Circuito> circuitos = ubicaciones.findByTipoAndSalonIdAndActivoTrue(TipoUbicacion.BARRA, evento.salonId())
                .stream().sorted(Comparator.comparing(Ubicacion::getNombre)).map(b -> new Circuito(b, b.getAbastecimiento())).toList();
        return new Pantalla(evento, ubicaciones.findFirstByTipoAndActivoTrue(TipoUbicacion.DEPOSITO).orElse(null), circuitos,
                preparaciones.findByEventoIdOrderByIdDesc(eventoId).stream().map(this::registrada).toList(),
                ordenes.vigente(eventoId).orElse(null));
    }

    @Transactional
    public Resultado registrar(long eventoId, String clave, Datos datos, long usuarioId) {
        if (clave == null || !clave.matches("[A-Za-z0-9_-]{1,100}")) {
            throw ProblemaException.reglaDeNegocio("CLAVE_INVALIDA", "Volvé a abrir la preparación antes de registrar.");
        }
        validarCantidades(datos);
        String huella = huella(datos);
        // Serializa también solicitudes con una clave nueva para este evento. Se refresca después de esperar.
        EventoOperativo evento = operaciones.bloquear(eventoId);
        Optional<PreparacionEvento> anterior = preparaciones.findByEventoIdAndClave(eventoId, clave);
        if (anterior.isPresent()) {
            if (!anterior.get().getHuella().equals(huella)) {
                throw ProblemaException.conflicto("CLAVE_REUTILIZADA", "Esta confirmación corresponde a otra carga. Recuperá la preparación anterior.");
            }
            // Antes de estado, actividad del catálogo o configuración: recuperar un commit nunca vuelve a mover stock.
            return new Resultado(registrada(anterior.get()), true);
        }
        if (evento.estado() != EstadoEvento.CONFIRMADO && evento.estado() != EstadoEvento.EN_CURSO) {
            throw ProblemaException.conflicto("ESTADO_NO_PERMITE", "El evento está en %s: prepará bebida con el evento confirmado o en curso."
                    .formatted(evento.nombreDelEstado()));
        }
        Ubicacion barra = ubicaciones.findById(datos.barraId()).orElseThrow(() -> regla("BARRA_INVALIDA", "Elegí una barra del salón del evento."));
        Ubicacion destino = ubicaciones.findById(datos.destinoId()).orElseThrow(() -> regla("DESTINO_INVALIDO", "No encontramos el depósito de transición. Volvé a cargar la pantalla."));
        Ubicacion madre = ubicaciones.findFirstByTipoAndActivoTrue(TipoUbicacion.DEPOSITO)
                .orElseThrow(() -> regla("SIN_DEPOSITO_MADRE", "No hay un depósito madre activo. Configuralo en Ubicaciones de stock."));
        // Conjunto completo antes del primer movimiento, compatible con retiros, devoluciones y recuentos.
        Stream.of(barra, madre, destino).map(Ubicacion::getId).distinct().sorted().forEach(movimientos::bloquearUbicacion);
        em.refresh(barra);
        em.refresh(madre);
        em.refresh(destino);
        validarCircuito(evento, barra, madre, destino);
        Map<Long, Bebida> porId = new LinkedHashMap<>();
        for (Renglon r : datos.renglones().stream().sorted(Comparator.comparingLong(Renglon::bebidaId)).toList()) {
            boolean activa = bebidas.activaBloqueada(r.bebidaId()).orElseThrow(() -> regla("BEBIDA_INEXISTENTE", "Una bebida ya no está en el catálogo. Revisá la preparación."));
            Bebida b = bebidas.findById(r.bebidaId()).orElseThrow(() -> regla("BEBIDA_INEXISTENTE", "Una bebida ya no está en el catálogo. Revisá la preparación."));
            if (!activa) throw regla("BEBIDA_DADA_DE_BAJA", "%s está dada de baja. Sacala de la preparación.".formatted(b.descripcion()));
            porId.put(b.getId(), b);
        }
        PreparacionEvento p = preparaciones.save(new PreparacionEvento(eventoId, barra, madre, destino, usuarioId,
                OffsetDateTime.now(reloj), clave, huella));
        List<String> negativos = new ArrayList<>();
        for (Renglon r : datos.renglones()) {
            var resultado = motor.registrar(MovimientoStock.preparacion(porId.get(r.bebidaId()), r.cantidad().setScale(2), p));
            if (resultado.saldoOrigen().signum() < 0) negativos.add(porId.get(r.bebidaId()).descripcion());
        }
        if (!negativos.isEmpty()) {
            String mensaje = "Saldo negativo en %s después de preparar %s: %s. Falta registrar un movimiento."
                    .formatted(madre.getNombre(), evento.nombre(), String.join(", ", negativos));
            avisos.notificar(null, TipoNotificacion.ALERTA_STOCK, mensaje.length() > 255 ? mensaje.substring(0, 254) + "…" : mensaje,
                    usuarioId, List.of(RolCodigo.COMPRAS, RolCodigo.ADMINISTRACION));
        }
        return new Resultado(registrada(p), false);
    }

    private Registrada registrada(PreparacionEvento p) {
        return new Registrada(p, movimientos.dePreparacion(p.getId()));
    }

    static void validarCantidades(Datos datos) {
        if (datos == null || datos.renglones() == null || datos.renglones().isEmpty() || datos.renglones().size() > 200) {
            throw regla("PREPARACION_VACIA", "Cargá entre 1 y 200 bebidas con sus cantidades.");
        }
        Set<Long> ids = new HashSet<>();
        for (Renglon r : datos.renglones()) {
            if (r == null || r.bebidaId() <= 0 || r.cantidad() == null || r.cantidad().signum() <= 0
                    || r.cantidad().stripTrailingZeros().scale() > 0 || r.cantidad().compareTo(new BigDecimal("99999999")) > 0) {
                throw regla("CANTIDAD_INVALIDA", "Cargá cantidades positivas en botellas enteras, hasta 99.999.999.");
            }
            if (!ids.add(r.bebidaId())) throw regla("BEBIDA_REPETIDA", "Una bebida aparece dos veces. Sumá las cantidades en un solo renglón.");
        }
    }

    private static void validarCircuito(EventoOperativo e, Ubicacion barra, Ubicacion madre, Ubicacion destino) {
        if (!barra.isActivo() || barra.getTipo() != TipoUbicacion.BARRA || !Objects.equals(barra.getSalonId(), e.salonId())) {
            throw regla("BARRA_INVALIDA", "Elegí una barra activa del salón del evento.");
        }
        if (barra.getAbastecimiento().getTipo() == TipoUbicacion.DEPOSITO) {
            throw regla("NO_REQUIERE_PREPARACION", "Esta barra retira directamente del depósito madre y no requiere preparación.");
        }
        if (!madre.isActivo() || !destino.isActivo() || destino.getTipo() != TipoUbicacion.TRANSICION
                || !barra.getAbastecimiento().getId().equals(destino.getId())) {
            throw ProblemaException.conflicto("CIRCUITO_CAMBIADO", "Cambió el abastecimiento de la barra o el destino está inactivo. Volvé a cargar la preparación.");
        }
    }

    /** Orden de renglones y escala decimal no cambian el contenido de una operación. */
    static String huella(Datos datos) {
        String contenido = datos.barraId() + ":" + datos.destinoId() + ":" + datos.renglones().stream()
                .sorted(Comparator.comparingLong(Renglon::bebidaId))
                .map(r -> r.bebidaId() + "=" + r.cantidad().stripTrailingZeros().toPlainString()).collect(Collectors.joining(";"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static ProblemaException regla(String codigo, String mensaje) { return ProblemaException.reglaDeNegocio(codigo, mensaje); }
}
