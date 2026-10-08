package ar.edu.utn.vastio.demo;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.VastioApplication;
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
import ar.edu.utn.vastio.bebida.aplicacion.BebidaService;
import ar.edu.utn.vastio.bebida.aplicacion.BebidaService.Codigo;
import ar.edu.utn.vastio.bebida.aplicacion.BebidaService.DatosBebida;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.usuarios.aplicacion.UsuarioService;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Solo en dev: un usuario de cada perfil, eventos (pre-reservas, señados, uno contratado y uno confirmado) en las próximas
 * semanas y un catálogo de bebidas, para que cualquiera del equipo
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
            new Reserva(3, 1, 2, 2, "Delfina Ríos", "lucia.ferreyra", null, Etapa.PRE_RESERVA),
            new Reserva(3, 2, 1, 3, "Grupo Arcor", "sofia.mendez", 400000, Etapa.SENADO),
            new Reserva(10, 3, 2, 1, "Paula Gómez y Martín Paz", "lucia.ferreyra", 650000, Etapa.CONTRATADO),
            new Reserva(17, 1, 1, 5, "Roberto Díaz", "sofia.mendez", null, Etapa.PRE_RESERVA),
            new Reserva(24, 2, 2, 4, "Colegio Nacional de Monserrat", "lucia.ferreyra", null, Etapa.PRE_RESERVA),
            new Reserva(31, 1, 2, 1, "Camila Ruiz y Joaquín Vera", "sofia.mendez", 800000, Etapa.CONFIRMADO),
            new Reserva(38, 3, 1, 2, "Valentina Paz", "lucia.ferreyra", null, Etapa.PRE_RESERVA));

    /** Tipo (1 Vino, 2 Espumante, 3 Destilado, 4 Aperitivo, 5 Cerveza), unidad (1 Caja, 2 Pack, 3 Botella), botellas por bulto y stock mínimo en botellas. */
    private record Articulo(String nombre, String presentacion, int tipo, int unidad, int porBulto, int stockMinimo,
            String codigoCaja, String codigoBotella) {
    }

    /** Catálogo de ejemplo, solo bebida con alcohol. Los códigos son inventados. */
    private static final List<Articulo> ARTICULOS = List.of(
            new Articulo("Fernet Branca", "750 ml", 3, 1, 6, 24, "17790000000016", "7790000000019"),
            new Articulo("Vino tinto Malbec Luigi Bosca", "750 ml", 1, 1, 6, 36, "17790000000023", "7790000000026"),
            new Articulo("Vino blanco Chardonnay Alamos", "750 ml", 1, 1, 6, 24, "17790000000030", "7790000000033"),
            new Articulo("Espumante Chandon Extra Brut", "750 ml", 2, 1, 6, 24, "17790000000047", "7790000000040"),
            new Articulo("Cerveza Quilmes Clásica", "1 l", 5, 1, 12, 48, "17790000000054", "7790000000057"),
            new Articulo("Gin Bombay Sapphire", "750 ml", 3, 1, 6, 6, "17790000000061", "7790000000064"),
            new Articulo("Aperol", "750 ml", 4, 1, 6, 12, "17790000000078", "7790000000071"),
            new Articulo("Vodka Smirnoff", "700 ml", 3, 3, 1, 6, null, "7790000000088"));

    /** Un PDF mínimo como contrato de ejemplo. */
    private static final byte[] CONTRATO_DEMO = "%PDF-1.4\n% Contrato de ejemplo (datos de demostración)\n%%EOF\n"
            .getBytes(StandardCharsets.US_ASCII);

    private final UsuarioService usuarios;
    private final PreReservaService preReservas;
    private final SenaService senas;
    private final ContratoService contratos;
    private final PlannerService planners;
    private final InvitadosService invitados;
    private final ServiciosService servicios;
    private final ConfirmacionService confirmaciones;
    private final BebidaService bebidas;
    private final EntityManager entityManager;
    private final String contrasena;

    public DatosDeDemostracion(UsuarioService usuarios, PreReservaService preReservas, SenaService senas,
            ContratoService contratos, PlannerService planners, InvitadosService invitados, ServiciosService servicios,
            ConfirmacionService confirmaciones, BebidaService bebidas, EntityManager entityManager,
            @Value("${vastio.demo.contrasena:}") String contrasena) {
        this.entityManager = entityManager;
        this.senas = senas;
        this.contratos = contratos;
        this.planners = planners;
        this.invitados = invitados;
        this.servicios = servicios;
        this.confirmaciones = confirmaciones;
        this.bebidas = bebidas;
        this.usuarios = usuarios;
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
        for (Reserva r : RESERVAS) {
            Usuario vendedora = creados.get(r.vendedora());
            UsuarioActual quien = new UsuarioActual(vendedora.getId(), Set.of(RolCodigo.VENDEDORA.name()));
            Evento evento = preReservas.registrar(
                    new PreReserva((short) r.salon(), hoy.plusDays(r.dias()), (short) r.turno(), (short) r.tipo(), null, null,
                            new DatosCliente(null, r.cliente(), null, null, null)),
                    quien);
            if (r.sena() != null) {
                senas.registrar(evento.getId(), new Sena(BigDecimal.valueOf(r.sena()), hoy, null, "30111222", null), quien);
            }
            if (r.etapa() == Etapa.CONTRATADO || r.etapa() == Etapa.CONFIRMADO) {
                evento = contratos.registrar(evento.getId(), hoy,
                        List.of(new ContratoService.Archivo("contrato-de-ejemplo.pdf", CONTRATO_DEMO)), coordinacion);
            }
            if (r.etapa() == Etapa.CONFIRMADO) {
                // Todo va en una transacción: se baja a la base antes de leer la versión, como si fueran pedidos separados.
                planners.asignar(evento.getId(), ana.getId(), coordinacion);
                entityManager.flush();
                evento = invitados.registrar(evento.getId(), evento.getVersion(), 180, true, coordinacion);
                entityManager.flush();
                evento = servicios.registrar(evento.getId(), evento.getVersion(), List.of(
                        new Servicio((short) 2, "Lomo braseado con papas rústicas"),
                        new Servicio((short) 7, "Barra libre premium hasta las 5")), coordinacion);
                confirmaciones.confirmar(evento.getId(), new UsuarioActual(ana.getId(), Set.of(RolCodigo.PLANNER.name())));
            }
        }
        for (Articulo a : ARTICULOS) {
            List<Codigo> codigos = a.codigoCaja() == null ? List.of(new Codigo(a.codigoBotella(), (short) 1))
                    : List.of(new Codigo(a.codigoCaja(), (short) a.porBulto()), new Codigo(a.codigoBotella(), (short) 1));
            bebidas.crear(new DatosBebida(a.nombre(), a.presentacion(), (short) a.tipo(), (short) a.unidad(),
                    (short) a.porBulto(), BigDecimal.valueOf(a.stockMinimo()), codigos));
        }
        log.info("Datos de demostración cargados: {} usuarios, {} eventos y {} bebidas.", PERSONAS.size(), RESERVAS.size(),
                ARTICULOS.size());
    }
}
