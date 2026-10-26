package ar.edu.utn.vastio.comun.seguridad;

/**
 * Expresiones de {@code @PreAuthorize} por grupo de perfiles, según la matriz de docs/perfiles-y-permisos.md.
 * Dirección y Coordinación tienen acceso total. Las reglas «solo sus eventos» se verifican en el servicio.
 */
public final class Permisos {

    /** B5-01 A: estadísticas históricas de solo lectura. */
    public static final String CONSULTAR_ESTADISTICAS_CONSUMO = "hasAnyRole('DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'COMPRAS')";

    /** B4-05: consulta histórica, independiente de la operación de barra y del cierre. */
    public static final String CONSULTAR_CONSUMO = "hasAnyRole('DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'COMPRAS')";

    /** B5-04: preparación manual; no otorga operación de barra. */
    public static final String REGISTRAR_PREPARACION = "hasAnyRole('DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'COMPRAS')";

    /** A7-02: vista de cocina, como en la matriz (Planner, Compras y Cocina consultan; Administración y Vendedora no). */
    public static final String VISTA_COCINA = "hasAnyRole('DIRECCION', 'COORDINACION', 'PLANNER', 'COMPRAS', 'COCINA')";

    /** Administrar usuarios. */
    public static final String ACCESO_TOTAL = "hasAnyRole('DIRECCION', 'COORDINACION')";

    /** Configurar parámetros. */
    public static final String CONFIGURAR = "hasAnyRole('DIRECCION', 'COORDINACION', 'ADMINISTRACION')";

    /** A1-02: registrar, consultar y desbloquear bloqueos de unidades. Administración, además del acceso total. */
    public static final String BLOQUEAR_UNIDADES = "hasAnyRole('DIRECCION', 'COORDINACION', 'ADMINISTRACION')";

    /** Consultar agenda: todos menos Barra y Cocina. */
    public static final String AGENDA = "hasAnyRole('DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'VENDEDORA', 'PLANNER', 'COMPRAS')";

    /** Registrar pre-reserva: la vendedora (a su nombre), Coordinación y Dirección. */
    public static final String PRERESERVA = "hasAnyRole('DIRECCION', 'COORDINACION', 'VENDEDORA')";

    /** Consultar evento (ficha y lista): todos menos Barra y Cocina. La vendedora, solo los suyos (en el servicio). */
    public static final String CONSULTAR_EVENTOS = AGENDA;

    /** Registrar o modificar datos de un evento; la regla «solo los suyos» se verifica en el servicio. */
    public static final String EDITAR_EVENTOS = "hasAnyRole('DIRECCION', 'COORDINACION', 'VENDEDORA', 'PLANNER')";

    /** Catálogo de bebidas: Administración y Compras, además del acceso total. */
    public static final String CATALOGO_BEBIDA = "hasAnyRole('DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'COMPRAS')";

    /** Consultar ubicaciones de stock: quienes las configuran y Compras (ingreso y stock). */
    public static final String CONSULTAR_UBICACIONES = CATALOGO_BEBIDA;

    /** Carga inicial e ingreso de bebida: Administración y Compras, además del acceso total. */
    public static final String INGRESO_BEBIDA = CATALOGO_BEBIDA;

    /** Consultar stock: Administración y Compras ven todo; Barra, solo las barras de la jornada (en el servicio). */
    public static final String CONSULTAR_STOCK = "hasAnyRole('DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'COMPRAS', 'BARRA')";

    /** Ajuste de stock (recuento y rotura): Administración y Compras, además del acceso total. */
    public static final String AJUSTE_STOCK = CATALOGO_BEBIDA;

    /**
     * Salida a barra, retiro adicional, devolución y cierre: la encargada de barra y Compras, además del acceso total
     * (docs/sprint-3-operacion.md, decisiones 3, 9 y 12). La barra, solo los eventos de la jornada (en el servicio).
     */
    public static final String OPERAR_BARRA = "hasAnyRole('DIRECCION', 'COORDINACION', 'COMPRAS', 'BARRA')";

    private Permisos() {
    }
}
