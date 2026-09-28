package ar.edu.utn.vastio.comun.errores;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce toda excepción a un {@code ProblemDetail} con un mensaje legible para la persona.
 * <ul>
 * <li>{@code title}: resumen corto.</li>
 * <li>{@code detail}: qué pasó y qué hacer, listo para mostrar.</li>
 * <li>{@code codigo}: identificador estable para que el frontend reaccione (p. ej. {@code SESION_VENCIDA}).</li>
 * <li>{@code errores}: en los 400 de validación, lista de {@code {campo, mensaje}}.</li>
 * </ul>
 */
@RestControllerAdvice
public class ManejadorDeErrores extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorDeErrores.class);

    /** Restricciones de la base con un mensaje propio. El resto de las violaciones usa {@link Mensajes#DATO_DUPLICADO}. */
    private static final Map<String, String> MENSAJE_POR_RESTRICCION = Map.of(
            "ux_evento_unidad_activa", Mensajes.FECHA_TOMADA);

    @ExceptionHandler(ProblemaException.class)
    ProblemDetail problema(ProblemaException ex) {
        return problema(ex.getEstado(), ex.getCodigo(), ex.getTitulo(), ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integridad(DataIntegrityViolationException ex) {
        String causa = String.valueOf(ex.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        for (var entrada : MENSAJE_POR_RESTRICCION.entrySet()) {
            if (causa.contains(entrada.getKey())) {
                return problema(HttpStatus.CONFLICT, entrada.getKey().toUpperCase(Locale.ROOT), "Conflicto", entrada.getValue());
            }
        }
        log.warn("Violación de integridad sin mensaje propio: {}", causa);
        return problema(HttpStatus.CONFLICT, "DATO_DUPLICADO", "Conflicto", Mensajes.DATO_DUPLICADO);
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail noAutenticado(AuthenticationException ex) {
        if (ex instanceof BadCredentialsException) {
            return problema(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", "Usuario o contraseña incorrectos",
                    Mensajes.CREDENCIALES_INVALIDAS);
        }
        if (ex instanceof InvalidBearerTokenException || ex instanceof SesionVencidaException) {
            return sesionVencida();
        }
        return problema(HttpStatus.UNAUTHORIZED, "SIN_SESION", "Ingresá para continuar", Mensajes.SIN_SESION);
    }

    public static ProblemDetail sesionVencida() {
        return problema(HttpStatus.UNAUTHORIZED, "SESION_VENCIDA", "Tu sesión expiró", Mensajes.SESION_VENCIDA);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail sinPermiso(AccessDeniedException ex) {
        return problema(HttpStatus.FORBIDDEN, "SIN_PERMISO", "Sin permiso", Mensajes.SIN_PERMISO);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception ex) {
        log.error("Error no previsto", ex);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "Error inesperado", Mensajes.ERROR_INTERNO);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("campo", e.getField(), "mensaje", String.valueOf(e.getDefaultMessage())))
                .toList();
        ProblemDetail body = problema(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", "Datos incompletos", Mensajes.DATOS_INVALIDOS);
        body.setProperty("errores", errores);
        return ResponseEntity.badRequest().headers(headers).body(body);
    }

    /** Errores propios de Spring MVC (JSON mal formado, método no soportado, ruta inexistente): mensaje en castellano. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (body == null && ex instanceof ErrorResponse respuesta) {
            body = respuesta.getBody();
        }
        if (body instanceof ProblemDetail detalle
                && (detalle.getProperties() == null || !detalle.getProperties().containsKey("codigo"))) {
            String mensaje = switch (statusCode.value()) {
                case 400 -> Mensajes.DATOS_ILEGIBLES;
                case 404 -> Mensajes.NO_ENCONTRADO;
                case 405, 415 -> Mensajes.OPERACION_NO_PERMITIDA;
                default -> statusCode.is5xxServerError() ? Mensajes.ERROR_INTERNO : detalle.getDetail();
            };
            detalle.setDetail(mensaje);
            detalle.setProperty("codigo", "HTTP_" + statusCode.value());
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private static ProblemDetail problema(HttpStatusCode estado, String codigo, String titulo, String mensaje) {
        ProblemDetail detalle = ProblemDetail.forStatusAndDetail(estado, mensaje);
        detalle.setTitle(titulo);
        detalle.setProperty("codigo", codigo);
        return detalle;
    }
}
