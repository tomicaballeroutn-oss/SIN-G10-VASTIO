package ar.edu.utn.vastio.comun.errores;

import org.springframework.http.HttpStatus;

/**
 * Error esperado que se muestra a la persona tal cual. El mensaje va en voseo y con palabras del negocio:
 * dice qué pasó y qué hacer («Esa fecha ya está tomada. Elegí otro salón, otra fecha u otro turno.»).
 * El {@link ManejadorDeErrores} la traduce a un {@code ProblemDetail}.
 */
public class ProblemaException extends RuntimeException {

    private final HttpStatus estado;
    private final String codigo;
    private final String titulo;

    public ProblemaException(HttpStatus estado, String codigo, String titulo, String mensaje) {
        super(mensaje);
        this.estado = estado;
        this.codigo = codigo;
        this.titulo = titulo;
    }

    public static ProblemaException noEncontrado(String codigo, String mensaje) {
        return new ProblemaException(HttpStatus.NOT_FOUND, codigo, "No encontrado", mensaje);
    }

    public static ProblemaException conflicto(String codigo, String mensaje) {
        return new ProblemaException(HttpStatus.CONFLICT, codigo, "Conflicto", mensaje);
    }

    public static ProblemaException reglaDeNegocio(String codigo, String mensaje) {
        return new ProblemaException(HttpStatus.UNPROCESSABLE_CONTENT, codigo, "No se puede hacer", mensaje);
    }

    public HttpStatus getEstado() {
        return estado;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getTitulo() {
        return titulo;
    }
}
