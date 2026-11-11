package ar.edu.utn.vastio.demo;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import jakarta.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.agenda.aplicacion.ClienteService;
import ar.edu.utn.vastio.agenda.aplicacion.ClienteService.DatosCliente;
import ar.edu.utn.vastio.agenda.aplicacion.ConfirmacionService;
import ar.edu.utn.vastio.agenda.aplicacion.ContratoService;
import ar.edu.utn.vastio.agenda.aplicacion.InvitadosService;
import ar.edu.utn.vastio.agenda.aplicacion.PlannerService;
import ar.edu.utn.vastio.agenda.aplicacion.PreReservaService;
import ar.edu.utn.vastio.agenda.aplicacion.PreReservaService.PreReserva;
import ar.edu.utn.vastio.agenda.aplicacion.SenaService;
import ar.edu.utn.vastio.agenda.aplicacion.SenaService.Sena;
import ar.edu.utn.vastio.agenda.aplicacion.ServiciosService;
import ar.edu.utn.vastio.agenda.aplicacion.ServiciosService.Servicio;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.dominio.Moneda;
import ar.edu.utn.vastio.bebida.aplicacion.BebidaService;
import ar.edu.utn.vastio.bebida.aplicacion.BebidaService.Codigo;
import ar.edu.utn.vastio.bebida.aplicacion.BebidaService.DatosBebida;
import ar.edu.utn.vastio.bebida.aplicacion.InventarioInicialService;
import ar.edu.utn.vastio.bebida.aplicacion.InventarioInicialService.Renglon;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.usuarios.aplicacion.UsuarioService;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Solo en dev: un usuario de cada perfil, eventos (pre-reservas, señados, uno contratado y dos confirmados, uno de hoy) en las próximas
 * semanas, un catálogo de bebidas y la carga inicial del depósito y de la Barra Avril, para que cualquiera del equipo
 * pruebe sin cargar nada. Se crea una vez (si no existe lucia.ferreyra) y solo con VASTIO_DEMO_CONTRASENA.
 * Usa los casos de uso reales, así los eventos tienen código, historial y avisos como cualquier otro.
 * Nunca llega al ambiente de prueba: no está en las migraciones.
 */
@Component
@Profile("dev")
@Order(2) // después del usuario inicial
public class DatosDeDemostracion implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosDeDemostracion.class);
    private static final ZoneId ZONA = ZoneId.of(VastioApplication.ZONA_HORARIA);

    private record Persona(String usuario, String nombre, RolCodigo perfil) {
    }

    private static final List<Persona> PERSONAS = List.of(
            new Persona("melina.sifon", "Melina Sifón", RolCodigo.COORDINACION),
            new Persona("gabriela.paz", "Gabriela Paz", RolCodigo.ADMINISTRACION),
            new Persona("lucia.ferreyra", "Lucía Ferreyra", RolCodigo.VENDEDORA),
            new Persona("sofia.mendez", "Sofía Méndez", RolCodigo.VENDEDORA),
            new Persona("ana.sosa", "Ana Sosa", RolCodigo.PLANNER),
            new Persona("carla.nunez", "Carla Núñez", RolCodigo.PLANNER),
            new Persona("nicolas.herrera", "Nicolás Herrera", RolCodigo.COMPRAS),
            new Persona("rocio.blanco", "Rocío Blanco", RolCodigo.BARRA),
            new Persona("julian.torres", "Julián Torres", RolCodigo.COCINA));

    /** Hasta dónde llega cada evento de ejemplo en el circuito comercial. */
    private enum Etapa { PRE_RESERVA, SENADO, CONTRATADO, CONFIRMADO }

    /**
     * Días desde hoy, salón (1 Avril, 2 Club, 3 Santa Bárbara), turno (1 mediodía, 2 noche), tipo, cliente, vendedora,
     * importe de la seña (null en pre-reserva) y etapa.
     */
    private record Reserva(int dias, int salon, int turno, int tipo, String cliente, String vendedora, Integer sena, Etapa etapa) {
    }

    private static final List<Reserva> RESERVAS = List.of(
            // Hoy al mediodía en Avril (12 a 18): pasa solo a En curso a las 12, el horario de la presentación.
            new Reserva(0, 1, 1, 2, "Martina Ortiz", "lucia.ferreyra", 500000, Etapa.CONFIRMADO),
            new Reserva(3, 1, 2, 2, "Delfina Ríos", "lucia.ferreyra", null, Etapa.PRE_RESERVA),
            new Reserva(3, 2, 1, 3, "Grupo Arcor", "sofia.mendez", 400000, Etapa.SENADO),
            new Reserva(10, 3, 2, 1, "Paula Gómez y Martín Paz", "lucia.ferreyra", 650000, Etapa.CONTRATADO),
            new Reserva(17, 1, 1, 5, "Roberto Díaz", "sofia.mendez", null, Etapa.PRE_RESERVA),
            new Reserva(24, 2, 2, 4, "Colegio Nacional de Monserrat", "lucia.ferreyra", null, Etapa.PRE_RESERVA),
            new Reserva(31, 1, 2, 1, "Camila Ruiz y Joaquín Vera", "sofia.mendez", 800000, Etapa.CONFIRMADO),
            new Reserva(38, 3, 1, 2, "Valentina Paz", "lucia.ferreyra", null, Etapa.PRE_RESERVA),
            new Reserva(1, 2, 2, 5, "Florencia Acosta", "sofia.mendez", 300000, Etapa.SENADO),
            new Reserva(2, 3, 2, 1, "Lucía Bustos y Tomás Ibarra", "lucia.ferreyra", 700000, Etapa.CONFIRMADO),
            new Reserva(4, 1, 1, 3, "Banco de Córdoba", "sofia.mendez", null, Etapa.PRE_RESERVA),
            new Reserva(5, 2, 2, 2, "Abril Funes", "lucia.ferreyra", 450000, Etapa.CONTRATADO),
            new Reserva(6, 3, 1, 5, "Hugo Bertone", "sofia.mendez", null, Etapa.PRE_RESERVA),
            new Reserva(7, 1, 2, 1, "Agustina Molina y Facundo Rey", "lucia.ferreyra", 900000, Etapa.CONFIRMADO),
            new Reserva(7, 2, 1, 3, "Laboratorios Hoya", "sofia.mendez", 350000, Etapa.SENADO),
            new Reserva(8, 3, 2, 4, "Instituto Domingo Zípoli", "lucia.ferreyra", null, Etapa.PRE_RESERVA),
            new Reserva(9, 1, 2, 2, "Renata Villalba", "sofia.mendez", 500000, Etapa.SENADO),
            new Reserva(11, 2, 2, 1, "Milagros Córdoba y Iván Luna", "lucia.ferreyra", 750000, Etapa.CONFIRMADO),
            new Reserva(12, 1, 1, 5, "Elena Quiroga", "sofia.mendez", null, Etapa.PRE_RESERVA),
            new Reserva(13, 3, 2, 2, "Josefina Aguirre", "lucia.ferreyra", 480000, Etapa.CONTRATADO),
            new Reserva(14, 1, 2, 1, "Sol Herrera y Bruno Cáceres", "sofia.mendez", 850000, Etapa.CONFIRMADO),
            new Reserva(14, 2, 2, 4, "Colegio Alemán", "lucia.ferreyra", null, Etapa.PRE_RESERVA),
            new Reserva(15, 3, 1, 3, "Tarjeta Naranja", "sofia.mendez", 420000, Etapa.SENADO),
            new Reserva(16, 2, 1, 5, "Marcelo Ledesma", "lucia.ferreyra", null, Etapa.PRE_RESERVA),
            new Reserva(18, 2, 2, 1, "Carolina Peralta y Diego Sosa", "sofia.mendez", 800000, Etapa.CONTRATADO),
            new Reserva(19, 1, 2, 2, "Catalina Moyano", "lucia.ferreyra", 520000, Etapa.SENADO),
            new Reserva(21, 2, 2, 1, "Antonella Ríos y Lautaro Gil", "sofia.mendez", 900000, Etapa.CONFIRMADO),
            new Reserva(21, 1, 1, 3, "Volkswagen Argentina", "lucia.ferreyra", null, Etapa.PRE_RESERVA),
            new Reserva(22, 3, 2, 2, "Emilia Zárate", "sofia.mendez", null, Etapa.PRE_RESERVA),
            new Reserva(25, 1, 2, 1, "Micaela Toledo y Gonzalo Paz", "lucia.ferreyra", 950000, Etapa.CONTRATADO),
            new Reserva(26, 3, 1, 5, "Norma Benítez", "sofia.mendez", 200000, Etapa.SENADO),
            new Reserva(28, 2, 2, 2, "Guadalupe Arias", "lucia.ferreyra", 550000, Etapa.CONFIRMADO),
            new Reserva(29, 2, 1, 4, "Colegio La Salle", "sofia.mendez", null, Etapa.PRE_RESERVA),
            new Reserva(33, 2, 1, 3, "Grupo Dinosaurio", "lucia.ferreyra", 380000, Etapa.SENADO),
            new Reserva(35, 1, 2, 2, "Bianca Ferrero", "sofia.mendez", null, Etapa.PRE_RESERVA),
            new Reserva(42, 3, 2, 1, "Julieta Godoy y Ramiro Vega", "lucia.ferreyra", 1000000, Etapa.CONTRATADO),
            new Reserva(45, 2, 2, 1, "Pilar Navarro y Mateo Rossi", "sofia.mendez", null, Etapa.PRE_RESERVA),
            new Reserva(52, 1, 2, 2, "Lola Medina", "lucia.ferreyra", 600000, Etapa.SENADO),
            new Reserva(60, 3, 2, 1, "Victoria Ortiz y Santiago Luque", "sofia.mendez", null, Etapa.PRE_RESERVA));

    /**
     * Tipo (1 Vino, 2 Espumante, 3 Destilado, 4 Aperitivo, 5 Cerveza), unidad (1 Caja, 2 Pack, 3 Botella), botellas por
     * bulto, stock mínimo e inventario inicial del depósito y de la
     * Barra Avril, en botellas.
     */
    private record Articulo(String nombre, String presentacion, int tipo, int unidad, int porBulto, int stockMinimo,
            String codigoCaja, String codigoBotella, int deposito, int barraAvril) {
    }

    /** Catálogo de ejemplo, solo bebida con alcohol. Los códigos son inventados. */
    private static final List<Articulo> ARTICULOS = List.of(
            new Articulo("Fernet Branca", "750 ml", 3, 1, 6, 24, "17790000000016", "7790000000019", 87, 9),
            new Articulo("Vino tinto Malbec Luigi Bosca", "750 ml", 1, 1, 6, 36, "17790000000023", "7790000000026", 120, 0),
            new Articulo("Vino blanco Chardonnay Alamos", "750 ml", 1, 1, 6, 24, "17790000000030", "7790000000033", 60, 0),
            new Articulo("Espumante Chandon Extra Brut", "750 ml", 2, 1, 6, 24, "17790000000047", "7790000000040", 72, 0),
            new Articulo("Cerveza Quilmes Clásica", "1 l", 5, 1, 12, 48, "17790000000054", "7790000000057", 240, 30),
            new Articulo("Gin Bombay Sapphire", "750 ml", 3, 1, 6, 6, "17790000000061", "7790000000064", 18, 0),
            new Articulo("Aperol", "750 ml", 4, 1, 6, 12, "17790000000078", "7790000000071", 30, 0),
            new Articulo("Vodka Smirnoff", "700 ml", 3, 3, 1, 6, null, "7790000000088", 4, 0),
            new Articulo("Vino tinto Cabernet Trapiche", "750 ml", 1, 1, 6, 24, "17790000000092", "7790000000095", 96, 0),
            new Articulo("Vino rosado Zuccardi", "750 ml", 1, 1, 6, 12, "17790000000108", "7790000000101", 36, 0),
            new Articulo("Vino blanco Torrontés Colomé", "750 ml", 1, 1, 6, 12, "17790000000115", "7790000000118", 42, 0),
            new Articulo("Espumante Baron B Brut Nature", "750 ml", 2, 1, 6, 12, "17790000000122", "7790000000125", 30, 0),
            new Articulo("Sidra Real", "720 ml", 2, 1, 6, 12, "17790000000139", "7790000000132", 48, 6),
            new Articulo("Cerveza Stella Artois", "975 ml", 5, 1, 12, 36, "17790000000146", "7790000000149", 144, 24),
            new Articulo("Cerveza Patagonia Amber", "730 ml", 5, 1, 6, 12, "17790000000153", "7790000000156", 5, 0),
            new Articulo("Whisky Johnnie Walker Red", "1 l", 3, 3, 1, 4, null, "7790000000163", 10, 2),
            new Articulo("Ron Havana Club 3 años", "750 ml", 3, 1, 6, 6, "17790000000177", "7790000000170", 12, 0),
            new Articulo("Campari", "750 ml", 4, 1, 6, 12, "17790000000184", "7790000000187", 24, 3),
            new Articulo("Gancia", "950 ml", 4, 1, 6, 12, "17790000000191", "7790000000194", 6, 0),
            new Articulo("Tequila José Cuervo", "750 ml", 3, 3, 1, 3, null, "7790000000200", 2, 0));

    /**
     * Días-salón-turno que usa {@link EscenarioCompleto} (eventos, reprogramación y bloqueos de las próximas semanas):
     * el generador no los toma, para que el escenario se cargue sin chocar con la exclusividad.
     */
    private static final Set<String> UNIDADES_DEL_ESCENARIO = Set.of("6-2-2", "9-3-2", "13-1-2", "18-3-1", "18-3-2", "20-3-2",
            "22-1-1", "25-2-2", "27-2-2", "29-3-2", "33-1-2");

    /** Opciones de servicios de ejemplo: las mismas que sugiere la pestaña Servicios (frontend, opcionesServicios.ts). */
    private static final String[] MENUS = { "Menú Clásico", "Menú Tradición", "Menú Estación", "Menú del Valle", "Menú de Autor" };
    private static final String[] BODEGAS = { "Selección", "Reserva", "Gran Reserva", "Bodega de Autor" };
    private static final String[] BARRAS = { "Barra clásica", "Barra libre", "Coctelería de autor" };

    /** Ubicaciones de V2. */
    private static final short DEPOSITO_MADRE = 1;
    private static final short BARRA_AVRIL = 2;

    /** Un PDF mínimo como contrato de ejemplo. */
    private static final byte[] CONTRATO_DEMO = "%PDF-1.4\n% Contrato de ejemplo (datos de demostración)\n%%EOF\n"
            .getBytes(StandardCharsets.US_ASCII);

    private final UsuarioService usuarios;
    private final ClienteService clientes;
    private final PreReservaService preReservas;
    private final SenaService senas;
    private final ContratoService contratos;
    private final PlannerService planners;
    private final InvitadosService invitados;
    private final ServiciosService servicios;
    private final ConfirmacionService confirmaciones;
    private final BebidaService bebidas;
    private final InventarioInicialService inventario;
    private final EntityManager entityManager;
    private final String contrasena;
    /** Con el escenario completo, el stock lo arma {@link EscenarioCompleto} con su historia. */
    private final boolean completo;

    public DatosDeDemostracion(UsuarioService usuarios, ClienteService clientes, PreReservaService preReservas, SenaService senas,
            ContratoService contratos, PlannerService planners, InvitadosService invitados, ServiciosService servicios,
            ConfirmacionService confirmaciones, BebidaService bebidas,
            InventarioInicialService inventario, EntityManager entityManager,
            @Value("${vastio.demo.contrasena:}") String contrasena, @Value("${vastio.demo.completo:false}") boolean completo) {
        this.completo = completo;
        this.entityManager = entityManager;
        this.senas = senas;
        this.contratos = contratos;
        this.planners = planners;
        this.invitados = invitados;
        this.servicios = servicios;
        this.confirmaciones = confirmaciones;
        this.bebidas = bebidas;
        this.inventario = inventario;
        this.usuarios = usuarios;
        this.clientes = clientes;
        this.preReservas = preReservas;
        this.contrasena = contrasena;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (contrasena.isBlank()) {
            log.info("Sin datos de demostración: definí VASTIO_DEMO_CONTRASENA para cargarlos.");
            return;
        }
        if (usuarios.todos().stream().anyMatch(u -> u.getNombreUsuario().equals("lucia.ferreyra"))) {
            return;
        }
        Map<String, Usuario> creados = new HashMap<>();
        for (Persona p : PERSONAS) {
            Usuario u = usuarios.crear(p.nombre(), p.usuario(), Set.of(p.perfil()), null, null, contrasena);
            u.setDebeCambiarContrasena(false);
            creados.put(p.usuario(), u);
        }
        LocalDate hoy = LocalDate.now(ZONA);
        UsuarioActual coordinacion = new UsuarioActual(creados.get("melina.sifon").getId(), Set.of(RolCodigo.COORDINACION.name()));
        Usuario ana = creados.get("ana.sosa");
        // Clientes con documento, teléfono y correo; uno que vuelve a reservar es el mismo cliente.
        Map<String, Long> clientePorNombre = new HashMap<>();
        for (String nombre : CLIENTES_SIN_EVENTOS) {
            clientePorNombre.put(nombre, clientes.obtenerOCrear(datosDeCliente(nombre)).getId());
        }
        List<Reserva> todas = new ArrayList<>(RESERVAS);
        todas.addAll(generarReservas());
        for (Reserva r : todas) {
            long clienteId = clientePorNombre.computeIfAbsent(r.cliente(), n -> clientes.obtenerOCrear(datosDeCliente(n)).getId());
            Usuario vendedora = creados.get(r.vendedora());
            UsuarioActual quien = new UsuarioActual(vendedora.getId(), Set.of(RolCodigo.VENDEDORA.name()));
            Evento evento = preReservas.registrar(
                    new PreReserva((short) r.salon(), hoy.plusDays(r.dias()), (short) r.turno(), (short) r.tipo(), null, null,
                            new DatosCliente(clienteId, null, null, null, null)),
                    quien);
            if (r.sena() != null) {
                senas.registrar(evento.getId(), new Sena(BigDecimal.valueOf(r.sena()), Moneda.ARS, hoy, null, "30111222", null), quien);
            }
            if (r.etapa() == Etapa.CONTRATADO || r.etapa() == Etapa.CONFIRMADO) {
                evento = contratos.registrar(evento.getId(), hoy,
                        List.of(new ContratoService.Archivo("contrato-de-ejemplo.pdf", CONTRATO_DEMO)), coordinacion);
            }
            if (r.etapa() == Etapa.CONFIRMADO) {
                // Todo va en una transacción: se baja a la base antes de leer la versión, como si fueran pedidos separados.
                planners.asignar(evento.getId(), ana.getId(), coordinacion);
                entityManager.flush();
                int variante = Math.floorMod(r.cliente().hashCode(), 1000);
                // Invitados entre 80 y 280; los menús (comunes + infantiles + especiales) suman justo eso.
                int cantidadInvitados = 80 + (variante % 21) * 10;
                int infantiles = cantidadInvitados / 15;
                evento = invitados.registrar(evento.getId(), evento.getVersion(), cantidadInvitados, true, coordinacion);
                entityManager.flush();
                evento = servicios.registrar(evento.getId(), evento.getVersion(), List.of(
                        // Las mismas opciones que sugiere la pestaña Servicios (opcionesServicios.ts), variando por evento.
                        new Servicio((short) 2, null, List.of(new ServiciosService.Renglon(MENUS[variante % MENUS.length], cantidadInvitados - infantiles - 8),
                                new ServiciosService.Renglon("Menú Infantil", infantiles))),
                        new Servicio((short) 5, null, List.of(new ServiciosService.Renglon("Vegetariano", 6),
                                new ServiciosService.Renglon("Sin TACC", 2))),
                        new Servicio((short) 6, null, List.of(new ServiciosService.Renglon(BODEGAS[variante % BODEGAS.length], null))),
                        new Servicio((short) 7, "Hasta las 5", List.of(new ServiciosService.Renglon(BARRAS[variante % BARRAS.length], null)))),
                        coordinacion);
                confirmaciones.confirmar(evento.getId(), new UsuarioActual(ana.getId(), Set.of(RolCodigo.PLANNER.name())));
            }
        }
        List<Renglon> deposito = new ArrayList<>();
        List<Renglon> barraAvril = new ArrayList<>();
        for (Articulo a : ARTICULOS) {
            List<Codigo> codigos = a.codigoCaja() == null ? List.of(new Codigo(a.codigoBotella(), (short) 1))
                    : List.of(new Codigo(a.codigoCaja(), (short) a.porBulto()), new Codigo(a.codigoBotella(), (short) 1));
            long id = bebidas.crear(new DatosBebida(a.nombre(), a.presentacion(), (short) a.tipo(), (short) a.unidad(),
                    (short) a.porBulto(), BigDecimal.valueOf(a.stockMinimo()), codigos)).getId();
            deposito.add(new Renglon(id, BigDecimal.valueOf(a.deposito())));
            if (a.barraAvril() > 0) {
                barraAvril.add(new Renglon(id, BigDecimal.valueOf(a.barraAvril())));
            }
        }
        long administracion = creados.get("gabriela.paz").getId();
        if (!completo) {
            inventario.guardar(DEPOSITO_MADRE, deposito, administracion);
            inventario.guardar(BARRA_AVRIL, barraAvril, administracion);
        }
        // Los avisos que generó la carga (cientos) ya se dan por leídos: la campana arranca limpia y muestra los que
        // aparecen durante la demostración.
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE notificacion_destinatario SET leida = true, fecha_lectura = now() WHERE NOT leida").executeUpdate();
        log.info("Datos de demostración cargados: {} usuarios, {} clientes, {} eventos y {} bebidas.", PERSONAS.size(), clientePorNombre.size(), todas.size(),
                ARTICULOS.size());
    }

    private static final String[] NOMBRES = { "Martina", "Sofía", "Valentina", "Camila", "Isabella", "Emma", "Mía", "Lucía",
            "Julieta", "Agustina", "Florencia", "Victoria", "Renata", "Olivia", "Malena", "Paula", "Jazmín", "Bianca", "Zoe",
            "Clara", "Abril", "Pilar", "Morena", "Candela" };
    private static final String[] NOVIOS = { "Tomás", "Juan", "Mateo", "Benjamín", "Lautaro", "Joaquín", "Facundo", "Nicolás",
            "Santiago", "Franco", "Ignacio", "Bruno", "Gonzalo", "Ramiro", "Federico", "Matías", "Lucas", "Agustín" };
    private static final String[] APELLIDOS = { "González", "Rodríguez", "Fernández", "López", "Martínez", "Pérez", "Romero",
            "Sánchez", "Álvarez", "Torres", "Ruiz", "Ramírez", "Flores", "Acosta", "Benítez", "Medina", "Herrera", "Aguirre",
            "Giménez", "Molina", "Castro", "Ortiz", "Silva", "Luna", "Juárez", "Cabrera", "Ríos", "Morales", "Vega", "Ferreyra" };
    private static final String[] EMPRESAS = { "Grupo Arcor", "Banco de Córdoba", "Tarjeta Naranja", "Volkswagen Argentina",
            "Renault Argentina", "Laboratorios Hoya", "Grido Helados", "Grupo Dinosaurio", "Mercado Libre", "Globant",
            "Universidad Siglo 21", "Cooperativa La Para", "Fiat Córdoba", "EPEC", "Aguas Cordobesas", "Colegio de Escribanos" };
    private static final String[] COLEGIOS = { "Colegio Nacional de Monserrat", "Colegio Alemán", "Colegio La Salle",
            "Instituto Domingo Zípoli", "Colegio Manuel Belgrano", "Instituto Santa Ana", "Colegio Taborin", "Escuela Garzón Agulla" };

    /**
     * Muchos eventos más, de dos meses a un año y medio, para probar la agenda a largo plazo. Semilla fija: todos ven los
     * mismos. Cuanto más lejos, menos avanzan en el circuito. Ninguno pisa una unidad de RESERVAS.
     */
    private static List<Reserva> generarReservas() {
        Random azar = new Random(20261109L);
        Set<String> tomadas = new HashSet<>();
        for (Reserva r : RESERVAS) {
            tomadas.add(r.dias() + "-" + r.salon() + "-" + r.turno());
        }
        tomadas.addAll(UNIDADES_DEL_ESCENARIO);
        List<Reserva> generadas = new ArrayList<>();
        LocalDate hoy = LocalDate.now(ZONA);
        String[] vendedoras = { "lucia.ferreyra", "sofia.mendez" };
        for (int dias = 4; dias <= 548; dias++) {
            int diaSemana = hoy.plusDays(dias).getDayOfWeek().getValue(); // 1 lunes … 7 domingo
            double probabilidad = diaSemana == 6 ? 0.75 : diaSemana == 5 ? 0.45 : diaSemana == 7 ? 0.2 : 0.06;
            // Cuanto más lejos, menos vendido.
            probabilidad *= dias < 120 ? 1.0 : dias < 300 ? 0.7 : 0.45;
            for (int salon = 1; salon <= 3; salon++) {
                for (int turno = 1; turno <= 2; turno++) {
                    double p = turno == 2 ? probabilidad : probabilidad * 0.35;
                    if (azar.nextDouble() >= p || !tomadas.add(dias + "-" + salon + "-" + turno)) {
                        continue;
                    }
                    int tipo = turno == 1 ? (azar.nextBoolean() ? 3 : 5) : new int[] { 1, 1, 1, 2, 2, 3, 4, 5 }[azar.nextInt(8)];
                    String cliente = switch (tipo) {
                        case 1 -> NOMBRES[azar.nextInt(NOMBRES.length)] + " " + APELLIDOS[azar.nextInt(APELLIDOS.length)] + " y "
                                + NOVIOS[azar.nextInt(NOVIOS.length)] + " " + APELLIDOS[azar.nextInt(APELLIDOS.length)];
                        case 3 -> EMPRESAS[azar.nextInt(EMPRESAS.length)];
                        case 4 -> COLEGIOS[azar.nextInt(COLEGIOS.length)];
                        case 5 -> (azar.nextBoolean() ? NOMBRES[azar.nextInt(NOMBRES.length)] : NOVIOS[azar.nextInt(NOVIOS.length)])
                                + " " + APELLIDOS[azar.nextInt(APELLIDOS.length)];
                        default -> NOMBRES[azar.nextInt(NOMBRES.length)] + " " + APELLIDOS[azar.nextInt(APELLIDOS.length)];
                    };
                    double avance = azar.nextDouble();
                    Etapa etapa;
                    if (dias < 60) {
                        etapa = avance < 0.15 ? Etapa.PRE_RESERVA : avance < 0.35 ? Etapa.SENADO : avance < 0.6 ? Etapa.CONTRATADO : Etapa.CONFIRMADO;
                    } else if (dias < 180) {
                        etapa = avance < 0.3 ? Etapa.PRE_RESERVA : avance < 0.65 ? Etapa.SENADO : Etapa.CONTRATADO;
                    } else {
                        etapa = avance < 0.55 ? Etapa.PRE_RESERVA : avance < 0.85 ? Etapa.SENADO : Etapa.CONTRATADO;
                    }
                    if (tipo == 4 && etapa == Etapa.CONFIRMADO) {
                        etapa = Etapa.CONTRATADO; // Egresados usa segmentos: la demo no carga invitados por segmento.
                    }
                    Integer sena = etapa == Etapa.PRE_RESERVA ? null : (200 + azar.nextInt(17) * 50) * 1000;
                    generadas.add(new Reserva(dias, salon, turno, tipo, cliente, vendedoras[azar.nextInt(2)], sena, etapa));
                }
            }
        }
        return generadas;
    }

    /** Clientes que consultaron o reservaron antes y hoy no tienen evento: para que el buscador de clientes tenga dónde elegir. */
    private static final List<String> CLIENTES_SIN_EVENTOS = List.of(
            "Lourdes Bazán", "Ezequiel Mansilla", "Carolina Bustamante", "Hernán Quinteros", "Mariana Oviedo", "Gustavo Bringas",
            "Silvina Ceballos", "Pablo Altamirano", "Daniela Tissera", "Marcos Olmedo", "Verónica Paredes", "Federico Arguello",
            "Natalia Brizuela", "Sergio Maldonado", "Cecilia Ludueña", "Diego Barrionuevo", "Laura Vaca Narvaja", "Andrés Peralta Ramos",
            "Rosario Funes", "Tomás Allende", "Fundación Hogar de Día", "Rotary Club Córdoba", "Colegio de Abogados de Córdoba",
            "Club Atlético Belgrano", "Club Atlético Talleres", "Instituto Santo Tomás", "Colegio Monserrat Ex Alumnos",
            "Cervecería Córdoba", "Grupo Werthein", "Agencia Córdoba Turismo");

    private static final java.util.regex.Pattern INSTITUCION = java.util.regex.Pattern.compile(
            "^(Grupo|Banco|Colegio|Instituto|Escuela|Laboratorios|Universidad|Cooperativa|Tarjeta|Fundación|Rotary|Club|Cervecería|Agencia|EPEC|Aguas|Fiat|Renault|Volkswagen|Mercado|Globant|Grido)\\b.*");

    /**
     * Documento, teléfono y correo inventados pero con forma real, derivados del nombre: siempre los mismos. Personas con
     * DNI y correos ficticios; empresas, colegios y clubes con CUIT y el correo de eventos. Usar example.com evita destinatarios reales.
     */
    static DatosCliente datosDeCliente(String nombre) {
        int base = Math.floorMod(nombre.hashCode(), Integer.MAX_VALUE);
        String slug = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        String telefono = String.format("351 %03d-%04d", 400 + base % 600, (base / 600) % 10000);
        if (INSTITUCION.matcher(nombre).matches()) {
            String cuit = String.format("30-%08d-%d", 50_000_000 + base % 49_999_999, base % 10);
            String dominio = slug.replaceAll("[^a-z0-9]+", "").replaceFirst("^(grupo|colegio|instituto|escuela|club)", "");
            return new DatosCliente(null, nombre, cuit, telefono, "eventos+" + dominio + "@example.com");
        }
        String[] palabras = slug.replaceAll("[^a-z ]", "").trim().split("\\s+");
        String correo = palabras[0] + (palabras.length > 1 ? "." + palabras[1] : "") + (base % 90 + 10) + "@example.com";
        String dni = String.valueOf(20_000_000 + base % 26_000_000);
        return new DatosCliente(null, nombre, dni, telefono, correo);
    }
}
