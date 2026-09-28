package ar.edu.utn.vastio.configuracion.aplicacion;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.configuracion.dominio.Parametro;
import ar.edu.utn.vastio.configuracion.dominio.ParametroClave;
import ar.edu.utn.vastio.configuracion.infraestructura.ParametroRepository;

/**
 * Lectura de parámetros para los otros módulos.
 */
@Service
@Transactional(readOnly = true)
public class ParametroService {

    private final ParametroRepository parametros;

    public ParametroService(ParametroRepository parametros) {
        this.parametros = parametros;
    }

    public int entero(ParametroClave clave) {
        Parametro parametro = parametros.findById(clave.name())
                .orElseThrow(() -> new IllegalStateException("Falta el parámetro " + clave + " (se carga en V2)"));
        return Integer.parseInt(parametro.getValor().trim());
    }
}
