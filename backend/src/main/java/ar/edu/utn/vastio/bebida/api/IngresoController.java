package ar.edu.utn.vastio.bebida.api;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.bebida.api.IngresoDto.IngresoRequest;
import ar.edu.utn.vastio.bebida.api.IngresoDto.IngresoResponse;
import ar.edu.utn.vastio.bebida.aplicacion.IngresoService;
import ar.edu.utn.vastio.bebida.aplicacion.IngresoService.IngresoRegistrado;
import ar.edu.utn.vastio.comun.seguridad.Permisos;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.usuarios.aplicacion.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Registrar ingreso de bebida (UI-29). Dirección, Coordinación, Administración y Compras. Las cantidades van en botellas.
 */
@RestController
@RequestMapping("/api/v1/ingresos")
@PreAuthorize(Permisos.INGRESO_BEBIDA)
@Tag(name = "Ingresos de bebida", description = "Mercadería comprada que entra al depósito madre")
public class IngresoController {

    private final IngresoService ingresos;
    private final UsuarioService usuarios;

    public IngresoController(IngresoService ingresos, UsuarioService usuarios) {
        this.ingresos = ingresos;
        this.usuarios = usuarios;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un ingreso", description = """
            Entra al depósito madre: un movimiento INGRESO por bebida, que suma al saldo. Fecha no futura (sin fecha, hoy);
            bebidas activas, sin repetir, en botellas enteras. No se edita ni se anula: un error se corrige con un recuento.""")
    public IngresoResponse registrar(@Valid @RequestBody IngresoRequest pedido, @AuthenticationPrincipal Jwt jwt) {
        return respuesta(List.of(ingresos.registrar(pedido.datos(), UsuarioActual.de(jwt).id()))).getFirst();
    }

    @GetMapping
    @Operation(summary = "Últimos ingresos", description = "Del más reciente al más viejo, con quién y cuándo. Sin la carga inicial. Hasta 50.")
    public List<IngresoResponse> recientes(@RequestParam(defaultValue = "10") int limite) {
        return respuesta(ingresos.recientes(Math.clamp(limite, 1, 50)));
    }

    private List<IngresoResponse> respuesta(List<IngresoRegistrado> lista) {
        Map<Long, String> nombres = usuarios.nombres(lista.stream().map(r -> r.ingreso().getUsuarioId()).toList());
        return lista.stream().map(r -> IngresoResponse.de(r, nombres)).toList();
    }
}
