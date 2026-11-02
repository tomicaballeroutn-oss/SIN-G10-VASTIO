package ar.edu.utn.vastio.configuracion.dominio;

/**
 * Claves de {@code parametro} que usa el backend, cargadas en V2, con el rango que acepta cada una.
 */
public enum ParametroClave {
    HORIZONTE_COCINA_DIAS(1, 90),
    MINUTOS_EXPIRACION_SESION(5, 480),
    MINUTOS_AVISO_EXPIRACION(1, 60),
    /** Margen que se suma a la orden de preparación sugerida (docs/sprint-4.md, decisión 15). */
    MARGEN_ORDEN_PORCENTAJE(0, 100);

    private final int minimo;
    private final int maximo;

    ParametroClave(int minimo, int maximo) {
        this.minimo = minimo;
        this.maximo = maximo;
    }

    public int getMinimo() {
        return minimo;
    }

    public int getMaximo() {
        return maximo;
    }
}
