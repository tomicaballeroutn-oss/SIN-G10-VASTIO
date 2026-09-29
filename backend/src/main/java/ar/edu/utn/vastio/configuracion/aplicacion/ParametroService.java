package ar.edu.utn.vastio.configuracion.aplicacion;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.configuracion.dominio.Parametro;
import ar.edu.utn.vastio.configuracion.dominio.ParametroClave;
import ar.edu.utn.vastio.configuracion.infraestructura.ParametroRepository;

/**
 * Parámetros escalares: lectura para los otros módulos y edición desde UI-06.
 */
@Service
@Transactional(readOnly = true)
public class ParametroService {

    private final ParametroRepository parametros;

    public ParametroService(ParametroRepository parametros) {
        this.parametros = parametros;
    }

    public int entero(ParametroClave clave) {
        return Integer.parseInt(buscar(clave).getValor().trim());
    }

    public List<Parametro> todos() {
        return parametros.findAllByOrderByClaveAsc();
    }

    /**
     * Valida el rango de la clave y que el aviso de sesión llegue antes de que la sesión venza.
     */
    @Transactional
    public Parametro actualizar(String clave, String valor) {
        ParametroClave conocida = conocida(clave);
        int numero = numeroEnRango(conocida, valor);
        if (conocida == ParametroClave.MINUTOS_AVISO_EXPIRACION
                && numero >= entero(ParametroClave.MINUTOS_EXPIRACION_SESION)) {
            throw fueraDeRango("El aviso tiene que llegar antes de que venza la sesión: usá menos minutos que la duración de la sesión.");
        }
        if (conocida == ParametroClave.MINUTOS_EXPIRACION_SESION
                && numero <= entero(ParametroClave.MINUTOS_AVISO_EXPIRACION)) {
            throw fueraDeRango("La sesión tiene que durar más que la anticipación del aviso. Bajá primero el aviso.");
        }
        Parametro parametro = buscar(conocida);
        parametro.setValor(Integer.toString(numero));
        return parametro;
    }

    private Parametro buscar(ParametroClave clave) {
        return parametros.findById(clave.name())
                .orElseThrow(() -> new IllegalStateException("Falta el parámetro " + clave + " (se carga en V2)"));
    }

    private static ParametroClave conocida(String clave) {
        try {
            return ParametroClave.valueOf(clave);
        } catch (IllegalArgumentException e) {
            throw ProblemaException.noEncontrado("PARAMETRO_INEXISTENTE", "No encontramos ese parámetro.");
        }
    }

    private static int numeroEnRango(ParametroClave clave, String valor) {
        int numero;
        try {
            numero = Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            throw fueraDeRango("Escribí un número entero.");
        }
        if (numero < clave.getMinimo() || numero > clave.getMaximo()) {
            throw fueraDeRango("Usá un valor entre %d y %d.".formatted(clave.getMinimo(), clave.getMaximo()));
        }
        return numero;
    }

    private static ProblemaException fueraDeRango(String mensaje) {
        return ProblemaException.reglaDeNegocio("PARAMETRO_FUERA_DE_RANGO", mensaje);
    }
}
