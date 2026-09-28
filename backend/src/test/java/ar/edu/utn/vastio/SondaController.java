package ar.edu.utn.vastio;

import java.sql.SQLException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.comun.errores.ProblemaException;

/**
 * Endpoints que existen solo en los tests, para probar seguridad y errores antes de tener historias de negocio.
 */
@RestController
@RequestMapping("/api/v1/_sonda")
class SondaController {

    @GetMapping("/autenticado")
    String autenticado() {
        return "ok";
    }

    @GetMapping("/direccion")
    @PreAuthorize("hasAnyRole('DIRECCION', 'COORDINACION')")
    String soloDireccion() {
        return "ok";
    }

    @GetMapping("/fecha-tomada")
    String fechaTomada() {
        throw new DataIntegrityViolationException("could not execute statement",
                new SQLException("ERROR: duplicate key value violates unique constraint \"ux_evento_unidad_activa\"", "23505"));
    }

    @GetMapping("/regla")
    String regla() {
        throw ProblemaException.reglaDeNegocio("SIN_PLANNER", "Asigná una planner antes de confirmar el evento.");
    }

    @GetMapping("/inesperado")
    String inesperado() {
        throw new IllegalStateException("NullPointer en algún lado");
    }
}
