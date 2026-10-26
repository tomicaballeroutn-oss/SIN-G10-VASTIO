package ar.edu.utn.vastio.bebida.api;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.bebida.aplicacion.ProporcionConsumoService;
import ar.edu.utn.vastio.bebida.dominio.Bebida;
import ar.edu.utn.vastio.bebida.dominio.ProporcionConsumo;
import ar.edu.utn.vastio.comun.seguridad.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Proporciones de consumo (Parámetros, docs/sprint-4.md, decisión 14). Las configuran Administración, Coordinación y
 * Dirección.
 */
@RestController
@RequestMapping("/api/v1/proporciones-consumo")
@PreAuthorize(Permisos.CONFIGURAR)
@Tag(name = "Proporciones de consumo", description = "Botellas por asistente con que arranca la orden sugerida")
public class ProporcionConsumoController {

    private final ProporcionConsumoService proporciones;

    public ProporcionConsumoController(ProporcionConsumoService proporciones) {
        this.proporciones = proporciones;
    }

    public record ProporcionResponse(long id, short tipoEventoId, long bebidaId, String nombre, String presentacion,
            boolean activa, BigDecimal botellasPorAsistente) {
        static ProporcionResponse de(ProporcionConsumo p) {
            Bebida b = p.getBebida();
            return new ProporcionResponse(p.getId(), p.getTipoEventoId(), b.getId(), b.getNombre(), b.getPresentacion(),
                    b.isActivo(), p.getBotellasPorAsistente());
        }
    }

    public record ProporcionRequest(
            @NotNull(message = "Elegí el tipo de evento.") Short tipoEventoId,
            @NotNull(message = "Elegí la bebida.") Long bebidaId,
            @NotNull(message = "Indicá las botellas por asistente.")
            @DecimalMin(value = "0.0001", message = "Las botellas por asistente tienen que ser mayores a 0.")
            @DecimalMax(value = "50", message = "Revisá el número: más de 50 botellas por asistente no parece real.")
            @Digits(integer = 2, fraction = 4, message = "Usá hasta cuatro decimales.") BigDecimal botellasPorAsistente) {
    }

    @GetMapping
    @Operation(summary = "Proporciones de un tipo de evento", description = "Por nombre de bebida.")
    public List<ProporcionResponse> delTipo(@RequestParam short tipoEventoId) {
        return proporciones.delTipo(tipoEventoId).stream().map(ProporcionResponse::de).toList();
    }

    @PutMapping
    @Operation(summary = "Guardar una proporción", description = """
            Agrega la proporción de la bebida en el tipo de evento o cambia la que había. Solo bebidas activas.""")
    public ProporcionResponse guardar(@Valid @RequestBody ProporcionRequest pedido) {
        return ProporcionResponse.de(proporciones.guardar(pedido.tipoEventoId(), pedido.bebidaId(), pedido.botellasPorAsistente()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Quitar una proporción")
    public void quitar(@PathVariable long id) {
        proporciones.quitar(id);
    }
}
