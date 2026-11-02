package ar.edu.utn.vastio.bebida.api;

import java.util.List;

import ar.edu.utn.vastio.bebida.aplicacion.EstadisticaConsumoService.Detalle;
import ar.edu.utn.vastio.bebida.aplicacion.EstadisticaConsumoService.Filtros;
import ar.edu.utn.vastio.bebida.aplicacion.EstadisticaConsumoService.Medidas;
import ar.edu.utn.vastio.bebida.aplicacion.EstadisticaConsumoService.PorBebida;
import ar.edu.utn.vastio.bebida.aplicacion.EstadisticaConsumoService.PorTipo;
import ar.edu.utn.vastio.bebida.aplicacion.EstadisticaConsumoService.Resultado;

/**
 * Valores de lectura, sin entidades JPA. Los cocientes sin denominador se representan como null. {@code denominador}
 * ASISTENTES: por evento, la asistencia real por segmento si está completa y, si no, los invitados registrados.
 */
public record EstadisticaConsumoDto(String unidad, String denominador, Filtros filtros, Medidas resumen,
        List<PorBebida> porBebida, List<PorTipo> porTipoEvento, List<Detalle> detalle) {
    static EstadisticaConsumoDto de(Resultado r) {
        return new EstadisticaConsumoDto("BOTELLAS", "ASISTENTES", r.filtros(), r.resumen(),
                r.porBebida(), r.porTipoEvento(), r.detalle());
    }
}
