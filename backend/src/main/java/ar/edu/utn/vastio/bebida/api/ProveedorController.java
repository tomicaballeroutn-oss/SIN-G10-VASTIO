package ar.edu.utn.vastio.bebida.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.bebida.api.ProveedorDto.BebidasRequest;
import ar.edu.utn.vastio.bebida.api.ProveedorDto.ProveedorRequest;
import ar.edu.utn.vastio.bebida.api.ProveedorDto.ProveedorResponse;
import ar.edu.utn.vastio.bebida.aplicacion.ProveedorService;
import ar.edu.utn.vastio.comun.seguridad.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Proveedores de bebida (UI-28, paso 2). Los mismos perfiles que el catálogo: Dirección, Coordinación, Administración y
 * Compras.
 */
@RestController
@RequestMapping("/api/v1/proveedores")
@PreAuthorize(Permisos.CATALOGO_BEBIDA)
@Tag(name = "Proveedores", description = "Datos maestros de proveedores y las bebidas que proveen")
public class ProveedorController {

    private final ProveedorService proveedores;

    public ProveedorController(ProveedorService proveedores) {
        this.proveedores = proveedores;
    }

    @GetMapping
    @Operation(summary = "Listar proveedores", description = "Activos primero, con las bebidas que los tienen como proveedor habitual.")
    public List<ProveedorResponse> listar() {
        return proveedores.todos().stream().map(ProveedorResponse::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dar de alta un proveedor", description = """
            Razón social sin repetir entre los activos. CUIT opcional, con o sin guiones: 11 dígitos con dígito verificador
            válido y sin repetir.""")
    public ProveedorResponse crear(@Valid @RequestBody ProveedorRequest pedido) {
        return ProveedorResponse.de(proveedores.crear(pedido.datos()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modificar un proveedor")
    public ProveedorResponse modificar(@PathVariable long id, @Valid @RequestBody ProveedorRequest pedido) {
        return ProveedorResponse.de(proveedores.modificar(id, pedido.datos()));
    }

    @PutMapping("/{id}/bebidas")
    @Operation(summary = "Fijar las bebidas que provee", description = """
            Deja exactamente esas bebidas con este proveedor habitual: las nuevas se lo cambian aunque tuvieran otro y las que
            ya no están quedan sin proveedor habitual.""")
    public ProveedorResponse fijarBebidas(@PathVariable long id, @Valid @RequestBody BebidasRequest pedido) {
        return ProveedorResponse.de(proveedores.fijarBebidas(id, pedido.bebidaIds()));
    }

    @PostMapping("/{id}/baja")
    @Operation(summary = "Dar de baja un proveedor", description = "Baja lógica: las bebidas lo conservan como habitual hasta que se les elija otro.")
    public ProveedorResponse darDeBaja(@PathVariable long id) {
        return ProveedorResponse.de(proveedores.darDeBaja(id));
    }

    @PostMapping("/{id}/reactivacion")
    @Operation(summary = "Reactivar un proveedor")
    public ProveedorResponse reactivar(@PathVariable long id) {
        return ProveedorResponse.de(proveedores.reactivar(id));
    }
}
