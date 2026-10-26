package ar.edu.utn.vastio.bebida.aplicacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.agenda.aplicacion.ConsultaHistoricaEventoService;
import ar.edu.utn.vastio.agenda.aplicacion.ConsultaHistoricaEventoService.EventoHistorico;
import ar.edu.utn.vastio.bebida.infraestructura.BebidaRepository;
import ar.edu.utn.vastio.bebida.infraestructura.ConsumoEventoRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;

/** B5-01 A. Solo consolidado congelado; el filtro de bebida nunca altera la población de eventos. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class EstadisticaConsumoService {
    private static final int TAMANO_LOTE = 1000;
    private final ConsultaHistoricaEventoService agenda;
    private final ConsumoEventoRepository consumos;
    private final BebidaRepository bebidas;
    private final CatalogoService catalogos;

    public EstadisticaConsumoService(ConsultaHistoricaEventoService agenda, ConsumoEventoRepository consumos,
            BebidaRepository bebidas, CatalogoService catalogos) {
        this.agenda = agenda;
        this.consumos = consumos;
        this.bebidas = bebidas;
        this.catalogos = catalogos;
    }

    public record Filtros(LocalDate desde, LocalDate hasta, Short tipoEventoId, Short salonId, Long bebidaId) { }
    public record Medidas(long eventos, long eventosConConsumoRegistrado, BigDecimal consumoTotal,
            BigDecimal promedioPorEvento, long eventosConInvitadosValidos, long invitadosRegistrados,
            BigDecimal consumoConInvitadosValidos, BigDecimal porInvitadoRegistrado) { }
    public record PorBebida(long bebidaId, String nombre, String presentacion, boolean activo, Medidas medidas) { }
    public record PorTipo(short tipoEventoId, String nombre, boolean activo, Medidas medidas) { }
    public record Detalle(short tipoEventoId, long bebidaId, Medidas medidas) { }
    public record Resultado(Filtros filtros, Medidas resumen, List<PorBebida> porBebida,
            List<PorTipo> porTipoEvento, List<Detalle> detalle) { }
    private record Clave(short tipoId, long bebidaId) { }

    public Resultado consultar(Filtros filtros) {
        validar(filtros);
        var tipos = catalogos.tiposEvento();
        if (filtros.tipoEventoId() != null) catalogos.tipoEvento(filtros.tipoEventoId());
        if (filtros.salonId() != null) catalogos.salon(filtros.salonId());
        var referencias = bebidas.referenciasHistoricas();
        if (filtros.bebidaId() != null && referencias.stream().noneMatch(b -> b.getId().equals(filtros.bebidaId()))) {
            throw ProblemaException.noEncontrado("BEBIDA_INEXISTENTE", "No encontramos esa bebida.");
        }
        List<EventoHistorico> eventos = agenda.cerrados(filtros.desde(), filtros.hasta(), filtros.tipoEventoId(), filtros.salonId());
        Map<Long, EventoHistorico> porId = new HashMap<>();
        Acumulador total = new Acumulador();
        Map<Short, Acumulador> porTipo = new LinkedHashMap<>();
        Set<Short> tiposHistoricos = new HashSet<>();
        eventos.forEach(e -> tiposHistoricos.add(e.tipoEventoId()));
        tipos.stream().filter(t -> filtros.tipoEventoId() == null
                ? t.isActivo() || tiposHistoricos.contains(t.getId()) : filtros.tipoEventoId().equals(t.getId()))
                .forEach(t -> porTipo.put(t.getId(), new Acumulador()));
        for (EventoHistorico e : eventos) {
            porId.put(e.id(), e);
            total.incluir(e);
            porTipo.get(e.tipoEventoId()).incluir(e);
        }
        // Filas escalares en lotes: ninguna entidad ni consulta individual por evento/bebida.
        List<ConsumoEventoRepository.Historico> filas = new ArrayList<>();
        List<Long> ids = eventos.stream().map(EventoHistorico::id).toList();
        for (int inicio = 0; inicio < ids.size(); inicio += TAMANO_LOTE) {
            filas.addAll(consumos.historicos(ids.subList(inicio, Math.min(inicio + TAMANO_LOTE, ids.size())), filtros.bebidaId()));
        }
        Set<Long> bebidasHistoricas = new HashSet<>();
        filas.forEach(f -> bebidasHistoricas.add(f.getBebidaId()));
        var elegidas = referencias.stream().filter(b -> filtros.bebidaId() == null
                ? b.getActivo() || bebidasHistoricas.contains(b.getId()) : filtros.bebidaId().equals(b.getId())).toList();
        Map<Long, Acumulador> porBebida = new LinkedHashMap<>();
        Map<Clave, Acumulador> detalle = new LinkedHashMap<>();
        for (var b : elegidas) {
            porBebida.put(b.getId(), total.poblacion());
            porTipo.forEach((tipoId, a) -> detalle.put(new Clave(tipoId, b.getId()), a.poblacion()));
        }
        for (var fila : filas) {
            EventoHistorico e = porId.get(fila.getEventoId());
            total.consumir(e, fila.getCantidad());
            porTipo.get(e.tipoEventoId()).consumir(e, fila.getCantidad());
            porBebida.get(fila.getBebidaId()).consumir(e, fila.getCantidad());
            detalle.get(new Clave(e.tipoEventoId(), fila.getBebidaId())).consumir(e, fila.getCantidad());
        }
        return new Resultado(filtros, total.medidas(), elegidas.stream().map(b -> new PorBebida(b.getId(), b.getNombre(),
                b.getPresentacion(), b.getActivo(), porBebida.get(b.getId()).medidas())).toList(),
                tipos.stream().filter(t -> porTipo.containsKey(t.getId()))
                        .map(t -> new PorTipo(t.getId(), t.getNombre(), t.isActivo(), porTipo.get(t.getId()).medidas())).toList(),
                detalle.entrySet().stream().map(d -> new Detalle(d.getKey().tipoId(), d.getKey().bebidaId(), d.getValue().medidas())).toList());
    }

    private static void validar(Filtros f) {
        if ((f.desde() != null && (f.desde().getYear() < 1 || f.desde().getYear() > 9999))
                || (f.hasta() != null && (f.hasta().getYear() < 1 || f.hasta().getYear() > 9999))
                || (f.desde() != null && f.hasta() != null && f.desde().isAfter(f.hasta()))
                || (f.tipoEventoId() != null && f.tipoEventoId() <= 0)
                || (f.salonId() != null && f.salonId() <= 0) || (f.bebidaId() != null && f.bebidaId() <= 0)) {
            throw new ProblemaException(HttpStatus.BAD_REQUEST, "FILTROS_INVALIDOS", "Revisá los filtros",
                    "Usá fechas entre los años 1 y 9999, desde anterior o igual a hasta e identificadores positivos.");
        }
    }

    /** La población se cuenta antes de unir consumos: invitados y eventos nunca se multiplican por bebida. */
    private static final class Acumulador {
        long eventos;
        long validos;
        long invitados;
        BigDecimal consumo = BigDecimal.ZERO;
        BigDecimal consumoValido = BigDecimal.ZERO;
        final Set<Long> conRegistro = new HashSet<>();

        void incluir(EventoHistorico e) {
            eventos++;
            if (e.invitados() != null && e.invitados() > 0) {
                validos++;
                invitados += e.invitados();
            }
        }

        Acumulador poblacion() {
            Acumulador a = new Acumulador();
            a.eventos = eventos;
            a.validos = validos;
            a.invitados = invitados;
            return a;
        }

        void consumir(EventoHistorico e, BigDecimal cantidad) {
            conRegistro.add(e.id());
            consumo = consumo.add(cantidad);
            if (e.invitados() != null && e.invitados() > 0) consumoValido = consumoValido.add(cantidad);
        }

        Medidas medidas() {
            return new Medidas(eventos, conRegistro.size(), consumo, dividir(consumo, eventos), validos, invitados,
                    consumoValido, dividir(consumoValido, invitados));
        }

        private static BigDecimal dividir(BigDecimal n, long d) {
            return d == 0 ? null : n.divide(BigDecimal.valueOf(d), 4, RoundingMode.HALF_UP);
        }
    }
}
