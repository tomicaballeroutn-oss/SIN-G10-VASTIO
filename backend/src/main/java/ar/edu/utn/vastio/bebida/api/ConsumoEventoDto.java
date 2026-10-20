package ar.edu.utn.vastio.bebida.api;

import java.math.BigDecimal;
import java.util.List;

import ar.edu.utn.vastio.bebida.aplicacion.ConsultaConsumoService.Consulta;
import ar.edu.utn.vastio.bebida.aplicacion.ConsultaConsumoService.Renglon;

/** Cantidades en botellas. El denominador es invitados, nunca asistencia real. */
public record ConsumoEventoDto(Integer invitados, String denominador, boolean usaSegmentos,
        List<Detalle> renglones, BigDecimal consumoTotal, BigDecimal porInvitadoTotal) {

    public record Detalle(long bebidaId, String nombre, String presentacion, String unidad, short unidadesPorBulto,
            BigDecimal entregado, BigDecimal devuelto, BigDecimal remanente, BigDecimal consumo, BigDecimal porInvitado) {
        static Detalle de(Renglon r) {
            var c = r.consumo();
            var b = c.getBebida();
            return new Detalle(b.getId(), b.getNombre(), b.getPresentacion(), b.getUnidad().getNombre(),
                    b.getUnidadesPorBulto(), c.getCantidadEntregada(), c.getCantidadDevuelta(), c.getCantidadRemanente(),
                    c.getCantidadConsumida(), r.porInvitado());
        }
    }

    static ConsumoEventoDto de(Consulta consulta) {
        return new ConsumoEventoDto(consulta.invitados(), "INVITADOS", consulta.usaSegmentos(),
                consulta.renglones().stream().map(Detalle::de).toList(), consulta.consumoTotal(), consulta.porInvitadoTotal());
    }
}
