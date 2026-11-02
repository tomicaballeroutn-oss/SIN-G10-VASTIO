package ar.edu.utn.vastio.bebida.aplicacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.bebida.aplicacion.EstadisticaConsumoService.Filtros;
import ar.edu.utn.vastio.bebida.aplicacion.EstadisticaConsumoService.Resultado;
import ar.edu.utn.vastio.bebida.dominio.Bebida;
import ar.edu.utn.vastio.bebida.dominio.FuenteSugerencia;
import ar.edu.utn.vastio.bebida.dominio.OrdenPreparacion.Sugerencia;
import ar.edu.utn.vastio.bebida.dominio.ProporcionConsumo;
import ar.edu.utn.vastio.bebida.infraestructura.BebidaRepository;
import ar.edu.utn.vastio.bebida.infraestructura.ProporcionConsumoRepository;
import ar.edu.utn.vastio.configuracion.aplicacion.ParametroService;
import ar.edu.utn.vastio.configuracion.dominio.ParametroClave;

/**
 * Cálculo de la orden sugerida (docs/sprint-4.md, decisiones 12 a 15). Por bebida: botellas por asistente × asistentes
 * del evento, más el margen, redondeado hacia arriba a cajas completas. Las botellas por asistente salen, en este
 * orden, de los eventos cerrados del tipo (si son al menos {@link #MINIMO_EVENTOS_DEL_TIPO}), de las proporciones
 * cargadas para el tipo, o de los eventos cerrados de todos los tipos; si no hay nada, la orden sale vacía. Usa la misma
 * cuenta que la estadística de consumo (B5-01): consumo total sobre asistentes totales.
 */
@Component
@Transactional(readOnly = true)
public class SugerenciaOrden {

    /** Con menos eventos cerrados del tipo, el promedio es poco confiable: se prueban las proporciones. */
    public static final int MINIMO_EVENTOS_DEL_TIPO = 3;

    private static final Comparator<Bebida> POR_NOMBRE = Comparator.comparing(Bebida::getNombre, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Bebida::getPresentacion, String.CASE_INSENSITIVE_ORDER);

    private final EstadisticaConsumoService estadisticas;
    private final ProporcionConsumoRepository proporciones;
    private final BebidaRepository bebidas;
    private final ParametroService parametros;

    public SugerenciaOrden(EstadisticaConsumoService estadisticas, ProporcionConsumoRepository proporciones,
            BebidaRepository bebidas, ParametroService parametros) {
        this.estadisticas = estadisticas;
        this.proporciones = proporciones;
        this.bebidas = bebidas;
        this.parametros = parametros;
    }

    /** Botellas por asistente de cada bebida y de dónde salieron. */
    record Tasas(FuenteSugerencia fuente, int eventosBase, Map<Bebida, BigDecimal> porBebida) {
    }

    /**
     * @param asistentes los previstos del evento; sin ellos no se puede calcular y las bebidas van en 0.
     */
    public Sugerencia calcular(short tipoEventoId, Integer asistentes) {
        Tasas tasas = tasas(tipoEventoId);
        short margen = (short) parametros.entero(ParametroClave.MARGEN_ORDEN_PORCENTAJE);
        Map<Bebida, BigDecimal> cantidades = new LinkedHashMap<>();
        tasas.porBebida().forEach((bebida, tasa) -> cantidades.put(bebida, cajasCompletas(bebida, tasa, asistentes, margen)));
        return new Sugerencia(tasas.fuente(), tasas.eventosBase(), asistentes, margen, cantidades);
    }

    Tasas tasas(short tipoEventoId) {
        Resultado delTipo = estadisticas.consultar(new Filtros(null, null, tipoEventoId, null, null));
        if (delTipo.resumen().eventosConInvitadosValidos() >= MINIMO_EVENTOS_DEL_TIPO) {
            return historico(FuenteSugerencia.HISTORICO_TIPO, delTipo);
        }
        List<ProporcionConsumo> cargadas = proporciones.findByTipoEventoId(tipoEventoId).stream()
                .filter(p -> p.getBebida().isActivo() && p.getBotellasPorAsistente().signum() > 0)
                .toList();
        if (!cargadas.isEmpty()) {
            return new Tasas(FuenteSugerencia.PROPORCIONES, 0, ordenadas(cargadas.stream()
                    .collect(Collectors.toMap(ProporcionConsumo::getBebida, ProporcionConsumo::getBotellasPorAsistente))));
        }
        Resultado general = estadisticas.consultar(new Filtros(null, null, null, null, null));
        if (general.resumen().eventosConInvitadosValidos() > 0) {
            return historico(FuenteSugerencia.HISTORICO_GENERAL, general);
        }
        return new Tasas(FuenteSugerencia.SIN_DATOS, 0, Map.of());
    }

    private Tasas historico(FuenteSugerencia fuente, Resultado resultado) {
        Map<Long, BigDecimal> porId = resultado.porBebida().stream()
                .filter(b -> b.activo() && b.medidas().porInvitadoRegistrado() != null
                        && b.medidas().porInvitadoRegistrado().signum() > 0)
                .collect(Collectors.toMap(EstadisticaConsumoService.PorBebida::bebidaId, b -> b.medidas().porInvitadoRegistrado()));
        Map<Long, Bebida> entidades = bebidas.findAllById(porId.keySet()).stream()
                .collect(Collectors.toMap(Bebida::getId, Function.identity()));
        return new Tasas(fuente, (int) resultado.resumen().eventosConInvitadosValidos(), ordenadas(porId.entrySet().stream()
                .collect(Collectors.toMap(e -> entidades.get(e.getKey()), Map.Entry::getValue))));
    }

    private static Map<Bebida, BigDecimal> ordenadas(Map<Bebida, BigDecimal> tasas) {
        Map<Bebida, BigDecimal> ordenadas = new LinkedHashMap<>();
        tasas.entrySet().stream().sorted(Map.Entry.comparingByKey(POR_NOMBRE))
                .forEach(e -> ordenadas.put(e.getKey(), e.getValue()));
        return ordenadas;
    }

    /** Botellas por asistente × asistentes × (1 + margen), hacia arriba a cajas completas, en botellas. */
    static BigDecimal cajasCompletas(Bebida bebida, BigDecimal tasa, Integer asistentes, short margen) {
        if (asistentes == null || asistentes <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal botellas = tasa.multiply(BigDecimal.valueOf(asistentes))
                .multiply(BigDecimal.valueOf(100L + margen)).divide(BigDecimal.valueOf(100));
        BigDecimal porBulto = BigDecimal.valueOf(Math.max(1, bebida.getUnidadesPorBulto()));
        return botellas.divide(porBulto, 0, RoundingMode.CEILING).multiply(porBulto);
    }
}
