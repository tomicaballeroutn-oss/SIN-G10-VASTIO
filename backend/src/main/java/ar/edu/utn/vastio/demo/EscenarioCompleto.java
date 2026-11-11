package ar.edu.utn.vastio.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.agenda.aplicacion.AsistenciaService;
import ar.edu.utn.vastio.agenda.aplicacion.BloqueoService;
import ar.edu.utn.vastio.agenda.aplicacion.CancelacionService;
import ar.edu.utn.vastio.agenda.aplicacion.ClienteService.DatosCliente;
import ar.edu.utn.vastio.agenda.aplicacion.ConfirmacionService;
import ar.edu.utn.vastio.agenda.aplicacion.ContratoService;
import ar.edu.utn.vastio.agenda.aplicacion.InvitadosService;
import ar.edu.utn.vastio.agenda.aplicacion.MaquinaDeEstados;
import ar.edu.utn.vastio.agenda.aplicacion.PlannerService;
import ar.edu.utn.vastio.agenda.aplicacion.PreReservaService;
import ar.edu.utn.vastio.agenda.aplicacion.PreReservaService.PreReserva;
import ar.edu.utn.vastio.agenda.aplicacion.ReprogramacionService;
import ar.edu.utn.vastio.agenda.aplicacion.SenaService;
import ar.edu.utn.vastio.agenda.aplicacion.SenaService.Sena;
import ar.edu.utn.vastio.agenda.aplicacion.ServiciosService;
import ar.edu.utn.vastio.agenda.aplicacion.ServiciosService.Servicio;
import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.dominio.Moneda;
import ar.edu.utn.vastio.agenda.infraestructura.EventoRepository;
import ar.edu.utn.vastio.agenda.infraestructura.NumeradorEvento;
import ar.edu.utn.vastio.bebida.aplicacion.OrdenPreparacionService;
import ar.edu.utn.vastio.bebida.aplicacion.PreparacionService;
import ar.edu.utn.vastio.bebida.dominio.OrdenPreparacion;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.notificaciones.aplicacion.NotificacionService;
import ar.edu.utn.vastio.notificaciones.dominio.TipoNotificacion;
import ar.edu.utn.vastio.usuarios.aplicacion.UsuarioService;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Solo en dev y con VASTIO_DEMO_COMPLETO=true: completa los datos de demostración ({@link DatosDeDemostracion}) para
 * recorrer la aplicación entera frente al cliente.
 * <ul>
 * <li>Historia: eventos cerrados de los últimos cinco meses (casamientos, quinces, egresados, corporativo y cumpleaños)
 * con su historial de estados, entregas a barra, devoluciones, consumo congelado y órdenes cerradas; ingresos al
 * depósito, un recuento y una rotura. Alimenta las estadísticas, el reporte de ocupación y la orden sugerida.</li>
 * <li>Ayer: un evento de egresados Realizado con la devolución hecha, que espera la asistencia real y el cierre.</li>
 * <li>Hoy: el evento del mediodía en Avril (12 a 18), con su orden confirmada y la bebida preparada en la transición; el sistema
 *     lo pasa a En curso a las 12.</li>
 * <li>Próximas semanas: órdenes sugerida, modificada y confirmada; egresados por confirmar; una seña en dólares; una
 * cancelación, una pre-reserva liberada, una reprogramación y dos bloqueos.</li>
 * </ul>
 * La historia va por SQL, con sus fechas reales; lo de hoy en adelante, por los casos de uso. Se carga una vez (si no
 * existe la «Transición Avril») y nunca en el ambiente de prueba.
 */
@Component
@Profile("dev")
@Order(3) // después de DatosDeDemostracion
public class EscenarioCompleto implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EscenarioCompleto.class);
    private static final ZoneId ZONA = ZoneId.of(VastioApplication.ZONA_HORARIA);

    /** Ubicaciones de V2 (y la transición que crea el escenario). */
    private static final short DEPOSITO = 1;
    private static final short[] BARRA_DEL_SALON = {0, 2, 3, 4};
    private static final short BARRA_AVRIL = 2;

    /** Tipos de evento de V2. */
    private static final short CASAMIENTO = 1, QUINCE = 2, CORPORATIVO = 3, EGRESADOS = 4, CUMPLEANIOS = 5;

    /** Segmentos de V2: 1 Comensales cena (los invitados), 2 Entradas anticipadas, 3 Venta en puerta. */
    private static final short ANTICIPADAS = 2, PUERTA = 3;

    /** Bebidas del catálogo de demostración, en este orden. */
    private static final List<String> BEBIDAS = List.of("Fernet Branca", "Vino tinto Malbec Luigi Bosca",
            "Vino blanco Chardonnay Alamos", "Espumante Chandon Extra Brut", "Cerveza Quilmes Clásica", "Gin Bombay Sapphire",
            "Aperol", "Vodka Smirnoff");

    /** Botellas por asistente por tipo de evento, en el orden de {@link #BEBIDAS}. */
    private static final Map<Short, double[]> CONSUMO = Map.of(
            CASAMIENTO, new double[] {0.05, 0.22, 0.10, 0.09, 0.30, 0.03, 0.02, 0.01},
            QUINCE, new double[] {0.07, 0.05, 0.03, 0.06, 0.28, 0.02, 0.02, 0.03},
            CORPORATIVO, new double[] {0.02, 0.18, 0.12, 0.08, 0.20, 0.02, 0.04, 0.00},
            EGRESADOS, new double[] {0.09, 0.02, 0.00, 0.03, 0.40, 0.03, 0.01, 0.05},
            CUMPLEANIOS, new double[] {0.06, 0.12, 0.06, 0.05, 0.25, 0.02, 0.03, 0.01});

    /**
     * Saldo del depósito madre al final de la historia, antes de preparar la bebida de hoy (que se lleva una parte a la
     * transición): después, Espumante, Gin y Vodka quedan bajo el mínimo, a propósito.
     */
    private static final int[] DEPOSITO_FINAL = {54, 96, 42, 48, 180, 12, 30, 12};

    /**
     * Un evento cerrado. {@code anticipadas} y {@code puerta}: previsto y real de los segmentos (solo egresados); la real
     * de los comensales es {@code comensalesReales}.
     */
    private record Historico(int diasAtras, int salon, int turno, short tipo, String nombre, String cliente, int invitados,
            String vendedora, String planner, int anticipadas, int puerta, int comensalesReales, int anticipadasReales,
            int puertaReales) {

        Historico(int diasAtras, int salon, int turno, short tipo, String nombre, String cliente, int invitados,
                String vendedora, String planner) {
            this(diasAtras, salon, turno, tipo, nombre, cliente, invitados, vendedora, planner, 0, 0, 0, 0, 0);
        }
    }

    private static final List<Historico> HISTORIA = List.of(
            new Historico(150, 1, 2, CASAMIENTO, "Sofía y Martín", "Sofía Herrera", 160, "lucia.ferreyra", "ana.sosa"),
            new Historico(136, 2, 2, QUINCE, "Quince de Abril Gómez", "Mariana Gómez", 150, "sofia.mendez", "carla.nunez"),
            new Historico(122, 3, 2, CASAMIENTO, "Lucía y Tomás", "Tomás Bianchi", 200, "lucia.ferreyra", "ana.sosa"),
            new Historico(108, 1, 2, QUINCE, "Quince de Martina Ruiz", "Laura Ruiz", 170, "sofia.mendez", "ana.sosa"),
            new Historico(95, 2, 1, CORPORATIVO, "Aniversario Grupo Arcor", "Grupo Arcor", 120, "sofia.mendez", "carla.nunez"),
            new Historico(81, 1, 2, CASAMIENTO, "Florencia y Diego", "Florencia Castro", 180, "lucia.ferreyra", "carla.nunez"),
            new Historico(67, 3, 2, EGRESADOS, "Egresados Colegio Alemán", "Colegio Alemán de Córdoba", 140, "lucia.ferreyra",
                    "ana.sosa", 80, 25, 136, 92, 31),
            new Historico(53, 2, 2, QUINCE, "Quince de Camila Paz", "Andrea Paz", 130, "sofia.mendez", "carla.nunez"),
            new Historico(40, 1, 2, CASAMIENTO, "Julieta y Ramiro", "Julieta Domínguez", 190, "lucia.ferreyra", "ana.sosa"),
            new Historico(26, 3, 1, CUMPLEANIOS, "Cumple 50 de Roberto", "Roberto Funes", 90, "sofia.mendez", "carla.nunez"),
            new Historico(12, 2, 2, EGRESADOS, "Egresados Instituto Santa Ana", "Instituto Santa Ana", 120, "lucia.ferreyra",
                    "carla.nunez", 70, 20, 118, 75, 28),
            new Historico(5, 1, 2, QUINCE, "Quince de Valentina Sosa", "Graciela Sosa", 160, "sofia.mendez", "ana.sosa"));

    /** Los últimos (desde este índice) tienen orden de preparación cerrada vinculada a la entrega. */
    private static final int PRIMERO_CON_ORDEN = 8;

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final UsuarioService usuarios;
    private final NumeradorEvento numerador;
    private final PreReservaService preReservas;
    private final SenaService senas;
    private final ContratoService contratos;
    private final PlannerService planners;
    private final InvitadosService invitados;
    private final ServiciosService servicios;
    private final ConfirmacionService confirmaciones;
    private final CancelacionService cancelaciones;
    private final ReprogramacionService reprogramaciones;
    private final BloqueoService bloqueos;
    private final AsistenciaService asistencias;
    private final EventoRepository eventos;
    private final MaquinaDeEstados maquina;
    private final OrdenPreparacionService ordenes;
    private final PreparacionService preparaciones;
    private final NotificacionService notificaciones;
    private final String contrasena;
    private final boolean completo;

    private final Map<String, Long> personas = new HashMap<>();
    private final long[] bebida = new long[BEBIDAS.size()];
    private final int[] porBulto = new int[BEBIDAS.size()];
    /** Saldo que dejan los movimientos de la historia, por ubicación y bebida (índice de {@link #BEBIDAS}). */
    private final Map<Short, long[]> saldo = new HashMap<>();

    public EscenarioCompleto(JdbcTemplate jdbc, EntityManager entityManager, UsuarioService usuarios, NumeradorEvento numerador,
            PreReservaService preReservas, SenaService senas, ContratoService contratos, PlannerService planners,
            InvitadosService invitados, ServiciosService servicios, ConfirmacionService confirmaciones,
            CancelacionService cancelaciones, ReprogramacionService reprogramaciones, BloqueoService bloqueos,
            AsistenciaService asistencias, EventoRepository eventos, MaquinaDeEstados maquina,
            OrdenPreparacionService ordenes, PreparacionService preparaciones, NotificacionService notificaciones,
            @Value("${vastio.demo.contrasena:}") String contrasena, @Value("${vastio.demo.completo:false}") boolean completo) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.usuarios = usuarios;
        this.numerador = numerador;
        this.preReservas = preReservas;
        this.senas = senas;
        this.contratos = contratos;
        this.planners = planners;
        this.invitados = invitados;
        this.servicios = servicios;
        this.confirmaciones = confirmaciones;
        this.cancelaciones = cancelaciones;
        this.reprogramaciones = reprogramaciones;
        this.bloqueos = bloqueos;
        this.asistencias = asistencias;
        this.eventos = eventos;
        this.maquina = maquina;
        this.ordenes = ordenes;
        this.preparaciones = preparaciones;
        this.notificaciones = notificaciones;
        this.contrasena = contrasena;
        this.completo = completo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!completo || contrasena.isBlank()) {
            return;
        }
        if (jdbc.queryForObject("SELECT count(*) FROM ubicacion WHERE nombre = 'Transición Avril'", Integer.class) > 0) {
            return;
        }
        usuarios.todos().forEach((Usuario u) -> personas.put(u.getNombreUsuario(), u.getId()));
        if (!personas.containsKey("lucia.ferreyra")) {
            log.warn("Sin los datos de demostración básicos: no se carga el escenario completo.");
            return;
        }
        for (int i = 0; i < BEBIDAS.size(); i++) {
            bebida[i] = jdbc.queryForObject("SELECT bebida_id FROM bebida WHERE nombre = ?", Long.class, BEBIDAS.get(i));
            porBulto[i] = jdbc.queryForObject("SELECT unidades_por_bulto FROM bebida WHERE bebida_id = ?", Integer.class, bebida[i]);
        }
        LocalDate hoy = LocalDate.now(ZONA);

        historia(hoy);
        ayer(hoy);
        stockDelDeposito(hoy);
        proporciones();
        circuitoAvril();
        entityManager.flush();
        entityManager.clear();

        proximos(hoy);
        hoy(hoy);
        // Como en los datos básicos: los avisos de la carga ya se dan por leídos.
        entityManager.flush();
        jdbc.update("UPDATE notificacion_destinatario SET leida = true, fecha_lectura = now() WHERE NOT leida");
        log.info("Escenario completo de demostración cargado: {} eventos cerrados, uno realizado, el de hoy en curso y los próximos.",
                HISTORIA.size());
    }

    // ---------- historia ----------

    private void historia(LocalDate hoy) {
        for (int n = 0; n < HISTORIA.size(); n++) {
            Historico h = HISTORIA.get(n);
            LocalDate fecha = hoy.minusDays(h.diasAtras());
            long evento = eventoCerrado(h, fecha, EstadoEvento.CERRADO);
            int asistentes = h.tipo() == EGRESADOS ? h.comensalesReales() + h.anticipadasReales() + h.puertaReales() : h.invitados();
            Long orden = n >= PRIMERO_CON_ORDEN ? ordenCerrada(evento, h, fecha, asistentes, n) : null;
            entrega(evento, h, fecha, asistentes, n, true, orden, n == HISTORIA.size() - 1);
        }
    }

    /** Un evento con todo su recorrido hasta {@code estado} (Cerrado o Realizado), con las fechas reales. */
    private long eventoCerrado(Historico h, LocalDate fecha, EstadoEvento estado) {
        long vendedora = personas.get(h.vendedora());
        long planner = personas.get(h.planner());
        long coordinacion = personas.get("melina.sifon");
        OffsetDateTime alta = momento(fecha.minusDays(120), 11, 0);
        long unidad = jdbc.queryForObject("INSERT INTO unidad_comercializable (salon_id, fecha, turno_id) VALUES (?, ?, ?) RETURNING unidad_id",
                Long.class, h.salon(), fecha, h.turno());
        long cliente = jdbc.queryForObject("INSERT INTO cliente (nombre, telefono) VALUES (?, ?) RETURNING cliente_id", Long.class,
                h.cliente(), "351 4" + String.format("%06d", Math.abs(h.cliente().hashCode()) % 1000000));
        String codigo = NumeradorEvento.codigo(alta.getYear(), numerador.siguiente(alta.getYear()));
        long id = jdbc.queryForObject("""
                INSERT INTO evento (codigo, unidad_id, cliente_id, tipo_evento_id, vendedora_id, planner_id, estado, nombre,
                    cantidad_invitados, invitados_definitivos, importe_sena, moneda_sena, fecha_sena, firmante_nombre, firmante_dni,
                    fecha_firma_contrato, fecha_creacion, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, true, ?, 'ARS', ?, ?, ?, ?, ?, 0) RETURNING evento_id""", Long.class,
                codigo, unidad, cliente, h.tipo(), vendedora, planner, estado.name(), h.nombre(), h.invitados(),
                BigDecimal.valueOf(300000 + (long) h.invitados() * 2500), fecha.minusDays(110), h.cliente(),
                String.valueOf(20000000 + Math.abs(h.nombre().hashCode()) % 20000000), fecha.minusDays(95), alta);
        cambio(id, null, "PRE_RESERVA", vendedora, alta);
        cambio(id, "PRE_RESERVA", "SENADO", vendedora, momento(fecha.minusDays(110), 16, 20));
        cambio(id, "SENADO", "CONTRATADO", coordinacion, momento(fecha.minusDays(95), 12, 5));
        cambio(id, "CONTRATADO", "CONFIRMADO", planner, momento(fecha.minusDays(20), 17, 40));
        LocalTime inicio = h.turno() == 1 ? LocalTime.of(12, 0) : LocalTime.of(20, 0);
        cambio(id, "CONFIRMADO", "EN_CURSO", null, momento(fecha, inicio.getHour(), 0));
        LocalDate fin = h.turno() == 1 ? fecha : fecha.plusDays(1);
        cambio(id, "EN_CURSO", "REALIZADO", null, momento(fin, h.turno() == 1 ? 18 : 6, 0));
        if (estado == EstadoEvento.CERRADO) {
            cambio(id, "REALIZADO", "CERRADO", personas.get("nicolas.herrera"), momento(fin, 10, 30));
        }
        servicio(id, (short) 2, menu(h.tipo()), planner, momento(fecha.minusDays(25), 10, 0));
        servicio(id, (short) 7, barra(h.tipo()),
                planner, momento(fecha.minusDays(25), 10, 0));
        if (h.tipo() == EGRESADOS) {
            jdbc.update("INSERT INTO asistencia_segmento (evento_id, tipo_segmento_id, cantidad_prevista, cantidad_real) VALUES (?, 1, NULL, ?)",
                    id, estado == EstadoEvento.CERRADO ? h.comensalesReales() : null);
            jdbc.update("INSERT INTO asistencia_segmento (evento_id, tipo_segmento_id, cantidad_prevista, cantidad_real) VALUES (?, ?, ?, ?)",
                    id, ANTICIPADAS, h.anticipadas(), estado == EstadoEvento.CERRADO ? h.anticipadasReales() : null);
            jdbc.update("INSERT INTO asistencia_segmento (evento_id, tipo_segmento_id, cantidad_prevista, cantidad_real) VALUES (?, ?, ?, ?)",
                    id, PUERTA, h.puerta(), estado == EstadoEvento.CERRADO ? h.puertaReales() : null);
        }
        return id;
    }

    /**
     * Entrega a la barra del salón: salida al empezar, a veces un retiro adicional, la devolución con el consumo al
     * terminar y, si el evento se cerró, el consumo congelado.
     *
     * @param remanente deja algo en la barra para el evento siguiente.
     */
    private void entrega(long evento, Historico h, LocalDate fecha, int asistentes, int n, boolean cerrar, Long orden, boolean remanente) {
        short barra = BARRA_DEL_SALON[h.salon()];
        long encargada = personas.get("rocio.blanco");
        int horaInicio = h.turno() == 1 ? 12 : 20;
        LocalDate fin = h.turno() == 1 ? fecha : fecha.plusDays(1);
        OffsetDateTime salida = momento(fecha, horaInicio, 15);
        OffsetDateTime retiro = momento(fecha, horaInicio + 3, 30);
        OffsetDateTime devolucion = momento(fin, h.turno() == 1 ? 17 : 4, 40);
        long entrega = jdbc.queryForObject("""
                INSERT INTO entrega (evento_id, ubicacion_destino_id, orden_id, estado, usuario_apertura_id, fecha_apertura,
                    usuario_cierre_id, fecha_cierre) VALUES (?, ?, ?, 'CERRADA', ?, ?, ?, ?) RETURNING entrega_id""", Long.class,
                evento, barra, orden, encargada, salida, encargada, devolucion);
        double[] tasas = CONSUMO.get(h.tipo());
        for (int i = 0; i < BEBIDAS.size(); i++) {
            long consumido = Math.round(tasas[i] * asistentes * ruido(n, i));
            if (consumido == 0) {
                continue;
            }
            long entregado = cajas(i, consumido) + porBulto[i];
            long adicional = (n % 3 == 0 && (i == 0 || i == 4)) ? porBulto[i] : 0;
            long quedan = remanente && (i == 0 || i == 1 || i == 4) ? Math.min(entregado + adicional - consumido, porBulto[i] / 2 + 1) : 0;
            long devuelto = entregado + adicional - consumido - quedan;
            mov("SALIDA_BARRA", i, entregado, DEPOSITO, barra, evento, entrega, null, null, encargada, salida);
            if (adicional > 0) {
                mov("RETIRO_ADICIONAL", i, adicional, DEPOSITO, barra, evento, entrega, null, null, encargada, retiro);
            }
            if (devuelto > 0) {
                mov("DEVOLUCION", i, devuelto, barra, DEPOSITO, evento, entrega, null, null, encargada, devolucion);
            }
            if (quedan > 0) {
                mov("REMANENTE", i, quedan, barra, barra, evento, entrega, null, null, encargada, devolucion);
            }
            mov("CONSUMO", i, consumido, barra, null, evento, entrega, null, null, encargada, devolucion);
            if (cerrar) {
                jdbc.update("""
                        INSERT INTO consumo_evento (evento_id, bebida_id, cantidad_entregada, cantidad_devuelta, cantidad_remanente,
                            cantidad_consumida) VALUES (?, ?, ?, ?, ?, ?)""", evento, bebida[i], entregado + adicional, devuelto, quedan,
                        consumido);
            }
        }
    }

    /** Orden de preparación cerrada de un evento pasado, con lo que salió a la barra. */
    private long ordenCerrada(long evento, Historico h, LocalDate fecha, int asistentes, int n) {
        long compras = personas.get("nicolas.herrera");
        long orden = jdbc.queryForObject("""
                INSERT INTO orden_preparacion (evento_id, estado, ubicacion_origen_id, responsable_id, fecha_creacion,
                    fecha_confirmacion, fuente, eventos_base, asistentes_base, margen_porcentaje, fecha_calculo,
                    usuario_confirmacion_id) VALUES (?, 'CERRADA', ?, NULL, ?, ?, 'HISTORICO_TIPO', ?, ?, 10, ?, ?)
                RETURNING orden_id""", Long.class, evento, DEPOSITO, momento(fecha.minusDays(20), 17, 40),
                momento(fecha.minusDays(3), 11, 15), n, h.tipo() == EGRESADOS ? h.invitados() + h.anticipadas() + h.puerta() : h.invitados(),
                momento(fecha.minusDays(20), 17, 40), compras);
        double[] tasas = CONSUMO.get(h.tipo());
        for (int i = 0; i < BEBIDAS.size(); i++) {
            long consumido = Math.round(tasas[i] * asistentes * ruido(n, i));
            if (consumido > 0) {
                long cantidad = cajas(i, consumido) + porBulto[i];
                jdbc.update("""
                        INSERT INTO detalle_orden_preparacion (orden_id, bebida_id, cantidad_sugerida, cantidad_a_retirar)
                        VALUES (?, ?, ?, ?)""", orden, bebida[i], cajas(i, Math.round(tasas[i] * asistentes * 1.1)), cantidad);
            }
        }
        return orden;
    }

    // ---------- ayer: egresados realizado, sin cierre ----------

    private void ayer(LocalDate hoy) {
        Historico h = new Historico(1, 3, 2, EGRESADOS, "Egresados Escuela Normal Superior", "Escuela Normal Superior", 130,
                "lucia.ferreyra", "carla.nunez", 85, 20, 127, 88, 26);
        long evento = eventoCerrado(h, hoy.minusDays(1), EstadoEvento.REALIZADO);
        long orden = jdbc.queryForObject("""
                INSERT INTO orden_preparacion (evento_id, estado, ubicacion_origen_id, fecha_creacion, fecha_confirmacion,
                    fuente, eventos_base, asistentes_base, margen_porcentaje, fecha_calculo, usuario_confirmacion_id)
                VALUES (?, 'CONFIRMADA', ?, ?, ?, 'PROPORCIONES', 0, 235, 10, ?, ?) RETURNING orden_id""", Long.class,
                evento, DEPOSITO, momento(hoy.minusDays(21), 17, 40), momento(hoy.minusDays(4), 11, 0), momento(hoy.minusDays(21), 17, 40),
                personas.get("nicolas.herrera"));
        double[] tasas = CONSUMO.get(EGRESADOS);
        for (int i = 0; i < BEBIDAS.size(); i++) {
            long sugerida = cajas(i, Math.round(tasas[i] * 235 * 1.1));
            if (sugerida > 0) {
                jdbc.update("""
                        INSERT INTO detalle_orden_preparacion (orden_id, bebida_id, cantidad_sugerida, cantidad_a_retirar)
                        VALUES (?, ?, ?, ?)""", orden, bebida[i], sugerida, sugerida);
            }
        }
        entrega(evento, h, hoy.minusDays(1), h.comensalesReales() + h.anticipadasReales() + h.puertaReales(), HISTORIA.size(), false,
                orden, false);
        notificaciones.notificar(evento, TipoNotificacion.ASISTENCIA_REAL,
                "Cargá la asistencia real de cierre: " + h.nombre() + " · Santa Bárbara · Noche", null, List.of(),
                List.of(personas.get("carla.nunez")));
    }

    // ---------- stock, proporciones y circuito ----------

    /**
     * Inventario inicial hace seis meses e ingresos mensuales, calculados para que el depósito termine con
     * {@link #DEPOSITO_FINAL}; un recuento con faltante y una rotura. Al final, los saldos.
     */
    private void stockDelDeposito(LocalDate hoy) {
        long administracion = personas.get("gabriela.paz");
        long compras = personas.get("nicolas.herrera");
        mov("AJUSTE", 0, 2, DEPOSITO, null, null, null, null, (short) 12, compras, momento(hoy.minusDays(60), 9, 30));
        mov("MERMA", 3, 1, DEPOSITO, null, null, null, null, (short) 11, compras, momento(hoy.minusDays(30), 15, 10));
        long[] neto = saldo.computeIfAbsent(DEPOSITO, k -> new long[BEBIDAS.size()]);
        long[] falta = new long[BEBIDAS.size()];
        for (int i = 0; i < BEBIDAS.size(); i++) {
            falta[i] = DEPOSITO_FINAL[i] - neto[i];
        }
        long inicial = ingreso(true, null, hoy.minusDays(170), administracion);
        int[] dias = {140, 110, 80, 50, 20};
        long[] ingresos = new long[dias.length];
        for (int k = 0; k < dias.length; k++) {
            ingresos[k] = ingreso(false, "R-0001-%08d".formatted(4810 + k * 37), hoy.minusDays(dias[k]), compras);
        }
        for (int i = 0; i < BEBIDAS.size(); i++) {
            long primero = cajas(i, Math.round(falta[i] * 0.4));
            mov("INVENTARIO_INICIAL", i, primero, null, DEPOSITO, null, null, inicial, null, administracion, momento(hoy.minusDays(170), 10, 0));
            long resto = falta[i] - primero;
            for (int k = 0; k < dias.length && resto > 0; k++) {
                long cantidad = k == dias.length - 1 ? resto : Math.min(resto, cajas(i, Math.round(resto / (double) (dias.length - k))));
                mov("INGRESO", i, cantidad, null, DEPOSITO, null, null, ingresos[k], null, compras, momento(hoy.minusDays(dias[k]), 10, 30));
                resto -= cantidad;
            }
        }
        saldo.forEach((ubicacion, cantidades) -> {
            for (int i = 0; i < BEBIDAS.size(); i++) {
                if (cantidades[i] != 0) {
                    jdbc.update("""
                            INSERT INTO stock_ubicacion (ubicacion_id, bebida_id, cantidad, fecha_actualizacion) VALUES (?, ?, ?, now())
                            ON CONFLICT (ubicacion_id, bebida_id) DO UPDATE SET cantidad = EXCLUDED.cantidad""",
                            ubicacion, bebida[i], cantidades[i]);
                }
            }
        });
    }

    private long ingreso(boolean inicial, String remito, LocalDate fecha, long usuario) {
        return jdbc.queryForObject("""
                INSERT INTO ingreso (ubicacion_destino_id, numero_remito, fecha_ingreso, es_inventario_inicial, usuario_id, fecha_registro)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING ingreso_id""", Long.class, DEPOSITO, remito, fecha, inicial, usuario,
                momento(fecha, 10, 0));
    }

    /** Egresados tiene pocos eventos cerrados: su orden sale de las proporciones. Cumpleaños, también. */
    private void proporciones() {
        for (short tipo : new short[] {EGRESADOS, CUMPLEANIOS}) {
            double[] tasas = CONSUMO.get(tipo);
            for (int i = 0; i < BEBIDAS.size(); i++) {
                if (tasas[i] > 0) {
                    jdbc.update("INSERT INTO proporcion_consumo (tipo_evento_id, bebida_id, botellas_por_asistente) VALUES (?, ?, ?)",
                            tipo, bebida[i], BigDecimal.valueOf(tasas[i]).setScale(4, RoundingMode.HALF_UP));
                }
            }
        }
    }

    /** Avril trabaja con un depósito de transición: Compras prepara ahí y la barra retira de ahí (B5-04). */
    private void circuitoAvril() {
        short transicion = jdbc.queryForObject("""
                INSERT INTO ubicacion (nombre, tipo, salon_id, activo, permite_retiro_directo) VALUES ('Transición Avril', 'TRANSICION', 1, true, false)
                RETURNING ubicacion_id""", Short.class);
        jdbc.update("UPDATE ubicacion SET ubicacion_abastecimiento_id = ?, permite_retiro_directo = true WHERE ubicacion_id = ?",
                transicion, BARRA_AVRIL);
    }

    // ---------- próximas semanas, por los casos de uso ----------

    private void proximos(LocalDate hoy) {
        UsuarioActual coordinacion = quien("melina.sifon", "COORDINACION");
        UsuarioActual compras = quien("nicolas.herrera", "COMPRAS");

        // Órdenes en los tres estados: sugerida (la del evento del día 31 de la demo básica), modificada y confirmada.
        long casamiento = confirmado(hoy.plusDays(6), 2, 2, CASAMIENTO, "Agustina y Lucas", "Agustina Ferrero", 220, "lucia.ferreyra",
                "carla.nunez", coordinacion);
        OrdenPreparacion modificada = ordenes.consultar(casamiento).orden();
        ordenes.ajustar(casamiento, modificada.getVersion(), Map.of(bebida[3], BigDecimal.valueOf(48), bebida[5], BigDecimal.valueOf(12)), compras);
        entityManager.flush();
        long quince = confirmado(hoy.plusDays(9), 3, 2, QUINCE, "Quince de Emma Torres", "Paula Torres", 150, "sofia.mendez",
                "ana.sosa", coordinacion);
        ordenes.confirmar(quince, ordenes.consultar(quince).orden().getVersion(), compras);
        entityManager.flush();

        // Egresados contratado: falta la asistencia prevista para confirmar.
        Evento egresados = hastaContratado(hoy.plusDays(13), 1, 2, EGRESADOS, "Egresados Colegio Nacional", "Colegio Nacional de Córdoba",
                "lucia.ferreyra", BigDecimal.valueOf(900000), Moneda.ARS, coordinacion);
        planners.asignar(egresados.getId(), personas.get("ana.sosa"), coordinacion);
        entityManager.flush();
        egresados = invitados.registrar(egresados.getId(), version(egresados.getId()), 140, true, coordinacion);
        entityManager.flush();
        servicios.registrar(egresados.getId(), version(egresados.getId()), servicios(EGRESADOS), coordinacion);
        entityManager.flush();

        // Una seña en dólares, una cancelación, una pre-reserva liberada y una reprogramación.
        hastaSenado(hoy.plusDays(22), 1, 1, CORPORATIVO, "Jornada anual Mercado Libre", "Mercado Libre", "sofia.mendez",
                BigDecimal.valueOf(3000), Moneda.USD);
        Evento cancelado = hastaSenado(hoy.plusDays(20), 3, 2, CUMPLEANIOS, "Cumple 18 de Mateo", "Silvia Mansilla", "lucia.ferreyra",
                BigDecimal.valueOf(250000), Moneda.ARS);
        cancelaciones.cancelar(cancelado.getId(), (short) 1, "La familia decidió festejar en otro lugar.", coordinacion);
        Evento liberada = preReserva(hoy.plusDays(27), 2, 2, QUINCE, "Quince de Sol Díaz", "Sol Díaz", "sofia.mendez");
        preReservas.liberar(liberada.getId(), "El cliente eligió otra fecha.", quien("sofia.mendez", "VENDEDORA"));
        Evento movido = hastaContratado(hoy.plusDays(33), 1, 2, CASAMIENTO, "Brenda y Nicolás", "Brenda Quiroga", "lucia.ferreyra",
                BigDecimal.valueOf(700000), Moneda.ARS, coordinacion);
        reprogramaciones.reprogramar(movido.getId(), new ReprogramacionService.Pedido((short) 3, hoy.plusDays(29), (short) 2, (short) 4,
                "Los novios adelantaron la fecha."), coordinacion);

        // Bloqueos: mantenimiento de Santa Bárbara y un evento propio en el Club.
        bloqueos.registrar(new BloqueoService.Pedido(Set.of((short) 3), hoy.plusDays(18), hoy.plusDays(18), Set.of((short) 1, (short) 2),
                (short) 7, "Pintura y arreglo del techo del salón."), coordinacion);
        bloqueos.registrar(new BloqueoService.Pedido(Set.of((short) 2), hoy.plusDays(25), hoy.plusDays(25), Set.of((short) 2),
                (short) 9, "Fiesta de fin de año del personal."), coordinacion);

        // La orden del evento del día 31 (demo básica) se generó sin historia: se recalcula con la de ahora.
        jdbc.queryForList("SELECT o.evento_id FROM orden_preparacion o WHERE o.estado = 'SUGERIDA'", Long.class).forEach(eventoId -> {
            ordenes.recalcular(eventoId, ordenes.consultar(eventoId).orden().getVersion(), compras);
            entityManager.flush();
        });
    }

    /** Hoy: el evento del mediodía en Avril (demo básica), con la orden confirmada y la bebida preparada; En curso lo pone el sistema. */
    private void hoy(LocalDate hoy) {
        UsuarioActual compras = quien("nicolas.herrera", "COMPRAS");
        Long eventoId = jdbc.query("""
                SELECT e.evento_id FROM evento e JOIN unidad_comercializable u USING (unidad_id)
                WHERE u.fecha = ? AND u.salon_id = 1 AND u.turno_id = 1 AND e.estado = 'CONFIRMADO'""",
                rs -> rs.next() ? rs.getLong(1) : null, hoy);
        if (eventoId == null) {
            return;
        }
        OrdenPreparacion orden = ordenes.consultar(eventoId).orden();
        orden = ordenes.confirmar(eventoId, orden.getVersion(), compras);
        entityManager.flush();
        short transicion = jdbc.queryForObject("SELECT ubicacion_id FROM ubicacion WHERE nombre = 'Transición Avril'", Short.class);
        List<PreparacionService.Renglon> renglones = orden.getRenglones().stream()
                .filter(r -> r.getCantidadARetirar().signum() > 0)
                .map(r -> new PreparacionService.Renglon(r.getBebida().getId(), r.getCantidadARetirar())).toList();
        preparaciones.registrar(eventoId, "demo-preparacion-hoy", new PreparacionService.Datos(BARRA_AVRIL, transicion, renglones),
                personas.get("nicolas.herrera"));
        entityManager.flush();
        // No se fuerza «En curso»: lo pone el sistema a las 12 (RealizacionProgramada), en vivo durante la presentación.
    }

    // ---------- ayudas para los eventos futuros ----------

    private long confirmado(LocalDate fecha, int salon, int turno, short tipo, String nombre, String cliente, int cantidad,
            String vendedora, String planner, UsuarioActual coordinacion) {
        Evento e = hastaContratado(fecha, salon, turno, tipo, nombre, cliente, vendedora,
                BigDecimal.valueOf(400000 + cantidad * 3000L), Moneda.ARS, coordinacion);
        planners.asignar(e.getId(), personas.get(planner), coordinacion);
        entityManager.flush();
        invitados.registrar(e.getId(), version(e.getId()), cantidad, true, coordinacion);
        entityManager.flush();
        servicios.registrar(e.getId(), version(e.getId()), servicios(tipo), coordinacion);
        entityManager.flush();
        confirmaciones.confirmar(e.getId(), quien(planner, "PLANNER"));
        entityManager.flush();
        return e.getId();
    }

    private Evento hastaContratado(LocalDate fecha, int salon, int turno, short tipo, String nombre, String cliente, String vendedora,
            BigDecimal sena, Moneda moneda, UsuarioActual coordinacion) {
        Evento e = hastaSenado(fecha, salon, turno, tipo, nombre, cliente, vendedora, sena, moneda);
        e = contratos.registrar(e.getId(), LocalDate.now(ZONA), List.of(new ContratoService.Archivo("contrato-de-ejemplo.pdf",
                "%PDF-1.4\n% Contrato de ejemplo (datos de demostración)\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII))),
                coordinacion);
        entityManager.flush();
        return e;
    }

    private Evento hastaSenado(LocalDate fecha, int salon, int turno, short tipo, String nombre, String cliente, String vendedora,
            BigDecimal sena, Moneda moneda) {
        Evento e = preReserva(fecha, salon, turno, tipo, nombre, cliente, vendedora);
        e = senas.registrar(e.getId(), new Sena(sena, moneda, LocalDate.now(ZONA), cliente,
                String.valueOf(25000000 + Math.abs(cliente.hashCode()) % 10000000), "351 555 0101"), quien(vendedora, "VENDEDORA"));
        entityManager.flush();
        return e;
    }

    private Evento preReserva(LocalDate fecha, int salon, int turno, short tipo, String nombre, String cliente, String vendedora) {
        Evento e = preReservas.registrar(new PreReserva((short) salon, fecha, (short) turno, tipo, nombre, null,
                new DatosCliente(null, cliente, null, "351 555 0101", null)), quien(vendedora, "VENDEDORA"));
        entityManager.flush();
        return e;
    }

    private static List<Servicio> servicios(short tipo) {
        // Las mismas opciones que sugiere la pestaña Servicios (frontend, opcionesServicios.ts).
        return List.of(new Servicio((short) 2, null, List.of(new ServiciosService.Renglon(menu(tipo), null))),
                new Servicio((short) 5, null, List.of(new ServiciosService.Renglon("Vegetariano", 8), new ServiciosService.Renglon("Sin TACC", 3))),
                new Servicio((short) 6, null, List.of(new ServiciosService.Renglon(tipo == CORPORATIVO ? "Selección" : "Reserva", null))),
                new Servicio((short) 7, null, List.of(new ServiciosService.Renglon(barra(tipo), null))));
    }

    private static String menu(short tipo) {
        return switch (tipo) {
            case CASAMIENTO -> "Menú Clásico";
            case QUINCE -> "Menú Tradición";
            case EGRESADOS -> "Menú Estación";
            case CORPORATIVO -> "Menú de Autor";
            default -> "Menú del Valle";
        };
    }

    /** Tipo de barra (todas son libres). */
    private static String barra(short tipo) {
        return switch (tipo) {
            case CORPORATIVO -> "Barra clásica";
            case EGRESADOS -> "Barra de gin";
            default -> "Coctelería de autor";
        };
    }

    private int version(long eventoId) {
        return jdbc.queryForObject("SELECT version FROM evento WHERE evento_id = ?", Integer.class, eventoId);
    }

    private UsuarioActual quien(String usuario, String perfil) {
        return new UsuarioActual(personas.get(usuario), Set.of(perfil));
    }

    // ---------- SQL de bajo nivel ----------

    private void cambio(long evento, String anterior, String nuevo, Long usuario, OffsetDateTime cuando) {
        jdbc.update("""
                INSERT INTO cambio_estado_evento (evento_id, estado_anterior, estado_nuevo, usuario_id, fecha_hora)
                VALUES (?, ?, ?, ?, ?)""", evento, anterior, nuevo, usuario, cuando);
    }

    private void servicio(long evento, short categoria, String texto, long usuario, OffsetDateTime cuando) {
        jdbc.update("""
                INSERT INTO servicio_contratado (evento_id, categoria_id, descripcion, usuario_id, fecha_modificacion)
                VALUES (?, ?, ?, ?, ?)""", evento, categoria, texto, usuario, cuando);
    }

    /** Un asiento, y su efecto en {@link #saldo}. {@code i}: índice de {@link #BEBIDAS}. */
    private void mov(String tipo, int i, long cantidad, Short origen, Short destino, Long evento, Long entrega, Long ingreso,
            Short motivo, long usuario, OffsetDateTime cuando) {
        jdbc.update("""
                INSERT INTO movimiento_stock (tipo, bebida_id, cantidad, ubicacion_origen_id, ubicacion_destino_id, evento_id,
                    entrega_id, ingreso_id, motivo_id, usuario_id, fecha_hora) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                tipo, bebida[i], cantidad, origen, destino, evento, entrega, ingreso, motivo, usuario, cuando);
        if (origen != null && !origen.equals(destino)) {
            saldo.computeIfAbsent(origen, k -> new long[BEBIDAS.size()])[i] -= cantidad;
        }
        if (destino != null && !destino.equals(origen)) {
            saldo.computeIfAbsent(destino, k -> new long[BEBIDAS.size()])[i] += cantidad;
        }
    }

    /** Hacia arriba a cajas completas. */
    private long cajas(int i, long botellas) {
        return (long) Math.ceil(botellas / (double) porBulto[i]) * porBulto[i];
    }

    /** Entre 0,85 y 1,15, fijo para cada evento y bebida: los números no salen redondos ni cambian entre cargas. */
    private static double ruido(int evento, int bebida) {
        return 0.85 + ((evento * 7 + bebida * 13) % 31) / 100.0;
    }

    private static OffsetDateTime momento(LocalDate fecha, int hora, int minuto) {
        return fecha.atTime(hora, minuto).atZone(ZONA).toOffsetDateTime();
    }
}
