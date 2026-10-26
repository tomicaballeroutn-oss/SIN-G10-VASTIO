package ar.edu.utn.vastio.reportes.aplicacion;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.Salon;
import ar.edu.utn.vastio.configuracion.dominio.TipoEvento;
import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.reportes.api.ReporteOcupacionDto;
import ar.edu.utn.vastio.reportes.api.ReporteOcupacionDto.Opcion;
import ar.edu.utn.vastio.reportes.infraestructura.OcupacionRepository;
import ar.edu.utn.vastio.reportes.infraestructura.OcupacionRepository.EventoOcupacion;
import ar.edu.utn.vastio.usuarios.aplicacion.UsuarioService;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/** Arma las métricas de ocupación de los estados comerciales que ya ocupan la unidad, salvo pre-reserva. */
@Service
@Transactional(readOnly = true)
public class ReporteOcupacionService {

    private final OcupacionRepository ocupacion;
    private final CatalogoService catalogos;
    private final UsuarioService usuarios;

    public ReporteOcupacionService(OcupacionRepository ocupacion, CatalogoService catalogos, UsuarioService usuarios) {
        this.ocupacion = ocupacion;
        this.catalogos = catalogos;
        this.usuarios = usuarios;
    }

    public ReporteOcupacionDto consultar(LocalDate desde, LocalDate hasta, Short salonId, Short tipoEventoId,
            Long vendedoraId, String regimen) {
        List<Salon> salones = catalogos.salones();
        List<TipoEvento> tipos = catalogos.tiposEvento();
        List<Usuario> vendedoras = usuarios.todos().stream().filter(v -> v.codigosDeRol().contains(RolCodigo.VENDEDORA)).toList();
        Map<Short, TipoEvento> tipoPorId = tipos.stream().collect(Collectors.toMap(TipoEvento::getId, Function.identity()));
        Map<Long, String> nombreVendedora = usuarios.nombres(vendedoras.stream().map(Usuario::getId).toList());
        List<EventoOcupacion> eventos = ocupacion.consultar(desde, hasta, salonId, tipoEventoId, vendedoraId, regimen);
        Map<Short, Long> unidadesBloqueadas = ocupacion.bloqueadasPorSalon(desde, hasta, salonId, regimen);
        long dias = diasIncluidos(desde, hasta, regimen);
        List<Salon> salonesIncluidos = salones.stream().filter(s -> salonId == null || salonId.equals(s.getId())).toList();
        long unidades = eventos.size();

        List<ReporteOcupacionDto.OcupacionSalon> porSalon = new ArrayList<>();
        double sumaPorcentajes = 0;
        for (Salon salon : salonesIncluidos) {
            long cuenta = eventos.stream().filter(e -> e.salonId() == salon.getId()).count();
            long capacidadTotal = dias * 2;
            long disponibles = Math.max(0, capacidadTotal - unidadesBloqueadas.getOrDefault(salon.getId(), 0L));
            double porcentaje = porcentaje(cuenta, disponibles);
            sumaPorcentajes += porcentaje;
            porSalon.add(new ReporteOcupacionDto.OcupacionSalon(salon.getId(), salon.getCodigo(), salon.getNombre(), cuenta,
                    disponibles, porcentaje));
        }

        Map<ClaveComercial, Long> conteos = eventos.stream().collect(Collectors.groupingBy(
                e -> new ClaveComercial(e.vendedoraId(), e.tipoEventoId()), LinkedHashMap::new,
                Collectors.counting()));
        List<ReporteOcupacionDto.ResultadoComercial> detalle = conteos.entrySet().stream().map(e -> {
            long vendedorId = e.getKey().vendedoraId();
            short tipoId = e.getKey().tipoEventoId();
            String vendedor = nombreVendedora.getOrDefault(vendedorId, "Vendedora " + vendedorId);
            TipoEvento tipo = tipoPorId.get(tipoId);
            return new ReporteOcupacionDto.ResultadoComercial(vendedorId, vendedor, tipoId,
                    tipo == null ? "Tipo " + tipoId : tipo.getNombre(), e.getValue());
        }).sorted(Comparator.comparing(ReporteOcupacionDto.ResultadoComercial::vendedora, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ReporteOcupacionDto.ResultadoComercial::tipoEvento, String.CASE_INSENSITIVE_ORDER)).toList();

        long realizados = eventos.stream().filter(e -> e.estado() == EstadoEvento.REALIZADO || e.estado() == EstadoEvento.CERRADO).count();

        return new ReporteOcupacionDto(desde.toString(), hasta.toString(), regimen,
                new ReporteOcupacionDto.Resumen(unidades, porSalon.isEmpty() ? 0 : sumaPorcentajes / porSalon.size(), realizados),
                porSalon, detalle,
                new ReporteOcupacionDto.Filtros(
                        salones.stream().map(s -> new Opcion(s.getId(), s.getNombre())).toList(),
                        tipos.stream().map(t -> new Opcion(t.getId(), t.getNombre())).toList(),
                        vendedoras.stream().map(v -> new Opcion(v.getId(), v.getNombreCompleto())).toList()));
    }

    private static long diasIncluidos(LocalDate desde, LocalDate hasta, String regimen) {
        if (regimen == null || regimen.isBlank()) return ChronoUnit.DAYS.between(desde, hasta) + 1;
        long dias = 0;
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            int mes = YearMonth.from(fecha).getMonthValue();
            if ("ALTA".equals(regimen) ? mes >= 10 : mes <= 9) dias++;
        }
        return dias;
    }

    private static double porcentaje(long ocupadas, long capacidad) {
        return capacidad == 0 ? 0 : Math.round(ocupadas * 10000.0 / capacidad) / 100.0;
    }

    private record ClaveComercial(long vendedoraId, short tipoEventoId) { }
}
