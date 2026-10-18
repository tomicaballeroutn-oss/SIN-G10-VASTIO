package ar.edu.utn.vastio.bebida.api;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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

import ar.edu.utn.vastio.bebida.api.UbicacionDto.UbicacionRequest;
import ar.edu.utn.vastio.bebida.api.UbicacionDto.UbicacionResponse;
import ar.edu.utn.vastio.bebida.aplicacion.UbicacionService;
import ar.edu.utn.vastio.bebida.dominio.Ubicacion;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.Permisos;
import ar.edu.utn.vastio.configuracion.dominio.Salon;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Configurar ubicaciones de stock (UI-26). Configuran Dirección, Coordinación y Administración; Compras las consulta
 * (las usa para el ingreso y el stock).
 */
@RestController
@RequestMapping("/api/v1/ubicaciones")
@Tag(name = "Ubicaciones de stock", description = "Depósito madre, depósitos de transición y barras de cada salón")
public class UbicacionController {

    private final UbicacionService ubicaciones;

    public UbicacionController(UbicacionService ubicaciones) {
        this.ubicaciones = ubicaciones;
    }

    @GetMapping
    @PreAuthorize(Permisos.CONSULTAR_UBICACIONES)
    @Operation(summary = "Listar ubicaciones", description = "Depósito, transiciones y barras, en ese orden; las dadas de baja con activo = false.")
    public List<UbicacionResponse> listar() {
        Map<Short, Salon> salones = salones();
        return ubicaciones.todas().stream().map(u -> UbicacionResponse.de(u, salones)).toList();
    }

    @PostMapping
    @PreAuthorize(Permisos.CONFIGURAR)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dar de alta una ubicación", description = """
            Un solo depósito madre activo. Una barra exige salón activo y respeta su máximo de barras (una; Avril, dos);
            se abastece del depósito madre o de una transición activa.""")
    public UbicacionResponse crear(@Valid @RequestBody UbicacionRequest pedido) {
        if (pedido.tipo() == null) {
            throw ProblemaException.reglaDeNegocio("SIN_TIPO", "Elegí el tipo de ubicación.");
        }
        return respuesta(ubicaciones.crear(pedido.tipo(), pedido.datos()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permisos.CONFIGURAR)
    @Operation(summary = "Modificar una ubicación", description = "El tipo no cambia.")
    public UbicacionResponse modificar(@PathVariable short id, @Valid @RequestBody UbicacionRequest pedido) {
        return respuesta(ubicaciones.modificar(id, pedido.tipo(), pedido.datos()));
    }

    @PostMapping("/{id}/baja")
    @PreAuthorize(Permisos.CONFIGURAR)
    @Operation(summary = "Dar de baja una ubicación", description = """
            Se rechaza si es el depósito madre, si tiene saldo distinto de cero o si alguna barra activa se abastece de
            ella.""")
    public UbicacionResponse darDeBaja(@PathVariable short id) {
        return respuesta(ubicaciones.darDeBaja(id));
    }

    @PostMapping("/{id}/reactivacion")
    @PreAuthorize(Permisos.CONFIGURAR)
    @Operation(summary = "Reactivar una ubicación", description = "Vuelve a chequear el depósito madre único, el máximo de barras y el origen de la barra.")
    public UbicacionResponse reactivar(@PathVariable short id) {
        return respuesta(ubicaciones.reactivar(id));
    }

    private UbicacionResponse respuesta(Ubicacion u) {
        return UbicacionResponse.de(u, salones());
    }

    private Map<Short, Salon> salones() {
        return ubicaciones.salones().stream().collect(Collectors.toMap(Salon::getId, Function.identity()));
    }
}
