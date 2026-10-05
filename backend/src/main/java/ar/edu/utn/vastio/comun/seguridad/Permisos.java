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

    private Permisos() {
    }
}
