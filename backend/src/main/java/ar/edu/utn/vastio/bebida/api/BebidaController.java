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

import ar.edu.utn.vastio.bebida.api.BebidaDto.BebidaRequest;
import ar.edu.utn.vastio.bebida.api.BebidaDto.BebidaResponse;
import ar.edu.utn.vastio.bebida.api.BebidaDto.SaldoResponse;
import ar.edu.utn.vastio.bebida.api.BebidaDto.TipoResponse;
import ar.edu.utn.vastio.bebida.api.BebidaDto.UnidadResponse;
import ar.edu.utn.vastio.bebida.aplicacion.BebidaService;
import ar.edu.utn.vastio.comun.seguridad.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Catálogo de bebidas (UI-25) y búsqueda por código de barras (UI-30). Dirección, Coordinación, Administración y
 * Compras. Las cantidades van en botellas; no hay precios.
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize(Permisos.CATALOGO_BEBIDA)
@Tag(name = "Bebidas", description = "Catálogo de bebidas con sus códigos de barras, tipos y unidades de manipulación")
public class BebidaController {

    private final BebidaService bebidas;

    public BebidaController(BebidaService bebidas) {
        this.bebidas = bebidas;
    }

    @GetMapping("/bebidas")
    @Operation(summary = "Listar bebidas", description = "Activas primero, por nombre y presentación; las dadas de baja con activo = false.")
    public List<BebidaResponse> listar() {
        return bebidas.todas().stream().map(BebidaResponse::de).toList();
    }

    @GetMapping("/bebidas/{id}")
    @Operation(summary = "Consultar una bebida")
    public BebidaResponse consultar(@PathVariable long id) {
        return BebidaResponse.de(bebidas.bebida(id));
    }

    @GetMapping("/bebidas/codigos/{codigo}")
    @Operation(summary = "Buscar por código de barras", description = """
            La bebida de un código leído con la cámara o tipeado; puede estar dada de baja. 404 si el código no está en el
            catálogo.""")
    public BebidaResponse porCodigo(@PathVariable String codigo) {
        return BebidaResponse.de(bebidas.porCodigo(codigo));
    }

    @GetMapping("/bebidas/{id}/saldos")
    @Operation(summary = "Saldos de una bebida", description = "Ubicaciones donde tiene saldo distinto de cero, en botellas. Se muestran antes de darla de baja.")
    public List<SaldoResponse> saldos(@PathVariable long id) {
        return bebidas.saldos(id).stream().map(SaldoResponse::de).toList();
    }

    @PostMapping("/bebidas")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dar de alta una bebida", description = """
            Nombre y presentación no se repiten entre las activas; un código de barras pertenece a una sola bebida. Con
            unidad Botella, las unidades por bulto quedan en 1.""")
    public BebidaResponse crear(@Valid @RequestBody BebidaRequest pedido) {
        return BebidaResponse.de(bebidas.crear(pedido.datos()));
    }

    @PutMapping("/bebidas/{id}")
    @Operation(summary = "Modificar una bebida", description = "Reemplaza los datos y la lista de códigos de barras.")
    public BebidaResponse modificar(@PathVariable long id, @Valid @RequestBody BebidaRequest pedido) {
        return BebidaResponse.de(bebidas.modificar(id, pedido.datos()));
    }

    @PostMapping("/bebidas/{id}/baja")
    @Operation(summary = "Dar de baja una bebida", description = """
            Baja lógica, aunque tenga saldo: deja de ofrecerse para cargar y se sigue viendo en la consulta de stock.""")
    public BebidaResponse darDeBaja(@PathVariable long id) {
        return BebidaResponse.de(bebidas.darDeBaja(id));
    }

    @PostMapping("/bebidas/{id}/reactivacion")
    @Operation(summary = "Reactivar una bebida", description = "Se rechaza si ya hay otra activa con el mismo nombre y presentación.")
    public BebidaResponse reactivar(@PathVariable long id) {
        return BebidaResponse.de(bebidas.reactivar(id));
    }

    @GetMapping("/tipos-bebida")
    @Operation(summary = "Listar tipos de bebida", description = "Lista fija: Vino, Espumante, Destilado, Aperitivo y Cerveza.")
    public List<TipoResponse> tipos() {
        return bebidas.tipos().stream().map(TipoResponse::de).toList();
    }

    @GetMapping("/unidades-manipulacion")
    @Operation(summary = "Listar unidades de manipulación", description = "Lista fija: Caja, Pack y Botella.")
    public List<UnidadResponse> unidades() {
        return bebidas.unidades().stream().map(UnidadResponse::de).toList();
    }
}
