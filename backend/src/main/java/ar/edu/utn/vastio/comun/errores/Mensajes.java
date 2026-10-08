package ar.edu.utn.vastio.comun.errores;

/**
 * Mensajes de error compartidos. Voseo, sentence case, qué pasó y qué hacer; nunca códigos ni términos técnicos.
 */
public final class Mensajes {

    /** Código de la violación de exclusividad, venga del chequeo del servicio o del índice ux_evento_unidad_activa. */
    public static final String CODIGO_FECHA_TOMADA = "FECHA_TOMADA";
    public static final String FECHA_TOMADA = "Esa fecha ya está tomada. Elegí otro salón, otra fecha u otro turno.";
    public static final String BEBIDA_REPETIDA = "Ya hay una bebida activa con ese nombre y presentación. Modificá esa en lugar de cargar otra.";
    public static final String CODIGO_DE_OTRA_BEBIDA = "Uno de los códigos ya es de otra bebida. Quitáselo a esa bebida antes de usarlo acá.";
    public static final String DEPOSITO_MADRE_EXISTENTE = "Ya hay un depósito madre activo. Solo puede haber uno.";
    public static final String UBICACION_REPETIDA = "Ya hay una ubicación con ese nombre.";
    public static final String DATO_DUPLICADO = "Ya hay un registro con esos datos. Revisalos y volvé a intentarlo.";
    public static final String EDICION_SIMULTANEA = "Otra persona guardó cambios al mismo tiempo. Volvé a abrir la pantalla y repetí lo que hiciste.";
    public static final String DATOS_INVALIDOS = "Revisá los datos marcados.";
    public static final String DATOS_ILEGIBLES = "No pudimos leer los datos enviados. Revisalos y volvé a intentarlo.";
    public static final String ARCHIVO_GRANDE = "Cada archivo puede pesar hasta 10 MB. Sacá la foto con menos resolución o comprimí el PDF.";
    public static final String NO_ENCONTRADO = "No encontramos lo que buscás.";
    public static final String OPERACION_NO_PERMITIDA = "Esa operación no está disponible acá.";
    public static final String CREDENCIALES_INVALIDAS = "Usuario o contraseña incorrectos. Volvé a intentarlo.";
    public static final String SESION_VENCIDA = "Tu sesión expiró. Volvé a ingresar para seguir donde estabas.";
    public static final String SIN_SESION = "Ingresá con tu usuario para continuar.";
    public static final String SIN_PERMISO = "Tu perfil no tiene permiso para hacer esto.";
    public static final String ERROR_INTERNO = "Algo salió mal de nuestro lado. Volvé a intentarlo en unos minutos.";

    private Mensajes() {
    }
}
