package ar.edu.utn.vastio.agenda.aplicacion;

import java.util.Set;

import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;

/**
 * Reglas «solo sus eventos» de docs/perfiles-y-permisos.md. Un usuario con varios perfiles suma permisos:
 * una vendedora que además es planner ve el detalle de todos los eventos.
 */
public final class AccesoEvento {

    /** Consultan la ficha de cualquier evento. La vendedora solo la de los suyos. */
    private static final Set<String> VEN_TODOS = Set.of("DIRECCION", "COORDINACION", "ADMINISTRACION", "PLANNER", "COMPRAS");

    /** Ven los datos económicos (importe de la seña) de todos los eventos. La vendedora, solo de los suyos. */
    private static final Set<String> VEN_IMPORTES = Set.of("DIRECCION", "COORDINACION", "ADMINISTRACION");

    private AccesoEvento() {
    }

    public static boolean esTitular(UsuarioActual usuario, Evento evento) {
        return usuario.tiene("VENDEDORA") && evento.getVendedoraId() == usuario.id();
    }

    public static boolean veDetalle(UsuarioActual usuario, Evento evento) {
        return usuario.tieneAlguno(VEN_TODOS) || esTitular(usuario, evento);
    }

    public static boolean veImportes(UsuarioActual usuario, Evento evento) {
        return usuario.tieneAlguno(VEN_IMPORTES) || esTitular(usuario, evento);
    }

    public static boolean esPlannerAsignada(UsuarioActual usuario, Evento evento) {
        return usuario.tiene("PLANNER") && evento.getPlannerId() != null && evento.getPlannerId() == usuario.id();
    }

    /** Registrar o modificar datos: vendedora titular, planner asignada, Coordinación y Dirección. */
    public static boolean puedeModificar(UsuarioActual usuario, Evento evento) {
        return usuario.accesoTotal() || esTitular(usuario, evento) || esPlannerAsignada(usuario, evento);
    }

    /** Liberar pre-reserva y registrar seña: vendedora titular, Coordinación y Dirección. */
    public static boolean puedeOperarComoTitular(UsuarioActual usuario, Evento evento) {
        return usuario.accesoTotal() || esTitular(usuario, evento);
    }
}
