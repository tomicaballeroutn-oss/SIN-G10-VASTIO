package ar.edu.utn.vastio.demo;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import ar.edu.utn.vastio.agenda.aplicacion.PreReservaService;
import ar.edu.utn.vastio.agenda.aplicacion.PreReservaService.PreReserva;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.usuarios.aplicacion.UsuarioService;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Solo en dev: un usuario de cada perfil y pre-reservas en las próximas semanas, para que cualquiera del equipo
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

    /** Días desde hoy, salón (1 Avril, 2 Club, 3 Santa Bárbara), turno (1 mediodía, 2 noche), tipo, cliente, vendedora. */
    private record Reserva(int dias, int salon, int turno, int tipo, String cliente, String vendedora) {
    }

    private static final List<Reserva> RESERVAS = List.of(
            new Reserva(3, 1, 2, 2, "Delfina Ríos", "lucia.ferreyra"),
            new Reserva(3, 2, 1, 3, "Grupo Arcor", "sofia.mendez"),
            new Reserva(10, 3, 2, 1, "Paula Gómez y Martín Paz", "lucia.ferreyra"),
            new Reserva(17, 1, 1, 5, "Roberto Díaz", "sofia.mendez"),
            new Reserva(24, 2, 2, 4, "Colegio Nacional de Monserrat", "lucia.ferreyra"),
            new Reserva(31, 1, 2, 1, "Camila Ruiz y Joaquín Vera", "sofia.mendez"),
            new Reserva(38, 3, 1, 2, "Valentina Paz", "lucia.ferreyra"));

    private final UsuarioService usuarios;
    private final PreReservaService preReservas;
    private final String contrasena;

    public DatosDeDemostracion(UsuarioService usuarios, PreReservaService preReservas,
            @Value("${vastio.demo.contrasena:}") String contrasena) {
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
        for (Reserva r : RESERVAS) {
            Usuario vendedora = creados.get(r.vendedora());
            preReservas.registrar(
                    new PreReserva((short) r.salon(), hoy.plusDays(r.dias()), (short) r.turno(), (short) r.tipo(), null, null,
                            new DatosCliente(null, r.cliente(), null, null, null)),
                    new UsuarioActual(vendedora.getId(), Set.of(RolCodigo.VENDEDORA.name())));
        }
        log.info("Datos de demostración cargados: {} usuarios y {} pre-reservas.", PERSONAS.size(), RESERVAS.size());
    }
}
