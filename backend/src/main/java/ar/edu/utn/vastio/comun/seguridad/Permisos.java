package ar.edu.utn.vastio.comun.seguridad;

/**
 * Expresiones de {@code @PreAuthorize} por grupo de perfiles, según la matriz de docs/perfiles-y-permisos.md.
 * Dirección y Coordinación tienen acceso total. Las reglas «solo sus eventos» se verifican en el servicio.
 */
public final class Permisos {

    /** Administrar usuarios. */
    public static final String ACCESO_TOTAL = "hasAnyRole('DIRECCION', 'COORDINACION')";

    /** Configurar parámetros. */
    public static final String CONFIGURAR = "hasAnyRole('DIRECCION', 'COORDINACION', 'ADMINISTRACION')";

    /** Consultar agenda: todos menos Barra y Cocina. */
    public static final String AGENDA = "hasAnyRole('DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'VENDEDORA', 'PLANNER', 'COMPRAS')";

    /** Registrar pre-reserva: la vendedora (a su nombre), Coordinación y Dirección. */
    public static final String PRERESERVA = "hasAnyRole('DIRECCION', 'COORDINACION', 'VENDEDORA')";

    /** Registrar o modificar datos de un evento; la regla «solo los suyos» se verifica en el servicio. */
    public static final String EDITAR_EVENTOS = "hasAnyRole('DIRECCION', 'COORDINACION', 'VENDEDORA', 'PLANNER')";

    private Permisos() {
    }
}
