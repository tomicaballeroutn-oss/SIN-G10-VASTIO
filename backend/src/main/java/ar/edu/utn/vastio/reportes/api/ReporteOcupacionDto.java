package ar.edu.utn.vastio.reportes.api;

import java.util.List;

/** Contrato del reporte de ocupación (A7-01). */
public record ReporteOcupacionDto(
        String desde,
        String hasta,
        String regimen,
        Resumen resumen,
        List<OcupacionSalon> porSalon,
        List<ResultadoComercial> porVendedoraYTipo,
        Filtros filtros) {

    public record Resumen(long unidadesOcupadas, double ocupacionPromedio, long eventosRealizados) { }
    public record OcupacionSalon(short salonId, String codigo, String salon, long unidadesOcupadas, long unidadesDisponibles,
            double ocupacion) { }
    public record ResultadoComercial(long vendedoraId, String vendedora, short tipoEventoId, String tipoEvento,
            long eventosIncluidos) { }
    public record Opcion(long id, String nombre) { }
    public record Filtros(List<Opcion> salones, List<Opcion> tiposEvento, List<Opcion> vendedoras) { }
}
