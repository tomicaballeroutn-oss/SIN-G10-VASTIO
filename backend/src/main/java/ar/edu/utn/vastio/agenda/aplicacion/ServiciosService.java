package ar.edu.utn.vastio.agenda.aplicacion;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.dominio.ModificacionEvento;
import ar.edu.utn.vastio.agenda.dominio.ServicioContratado;
import ar.edu.utn.vastio.agenda.infraestructura.ModificacionEventoRepository;
import ar.edu.utn.vastio.agenda.infraestructura.ServicioContratadoRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.CategoriaServicio;

/**
 * Registrar servicios contratados (UI-13): un texto libre por categoría configurable. Lo hacen la vendedora titular,
 * la planner asignada, Coordinación y Dirección, entre Pre-reserva y Confirmado. Cada categoría que cambia queda en
 * {@code modificacion_evento} como {@code servicio.<categoría>} y el guardado genera un aviso de modificación.
 */
@Service
public class ServiciosService {

    private final ConsultaEventoService consultas;
    private final ServicioContratadoRepository servicios;
    private final ModificacionEventoRepository modificaciones;
    private final CatalogoService catalogos;
    private final AvisosEvento avisos;
    private final EntityManager entityManager;

    public ServiciosService(ConsultaEventoService consultas, ServicioContratadoRepository servicios,
            ModificacionEventoRepository modificaciones, CatalogoService catalogos, AvisosEvento avisos,
            EntityManager entityManager) {
        this.consultas = consultas;
        this.servicios = servicios;
        this.modificaciones = modificaciones;
        this.catalogos = catalogos;
        this.avisos = avisos;
        this.entityManager = entityManager;
    }

    /** Texto de una categoría; vacío o null la deja sin servicio. Las categorías que no vienen no cambian. */
    public record Servicio(short categoriaId, String descripcion) {
    }

    /**
     * @param version la que tenía la ficha al abrir la pestaña: si otra persona guardó en el medio, 409.
     */
    @Transactional
    public Evento registrar(long id, int version, List<Servicio> pedidos, UsuarioActual quien) {
        Evento evento = consultas.visible(id, quien);
        if (!AccesoEvento.puedeModificar(quien, evento)) {
            throw ConsultaEventoService.sinPermiso();
        }
        if (!ConsultaEventoService.EDITABLES.contains(evento.getEstado())) {
            throw ConsultaEventoService.noPermiteEnEsteEstado(evento, "modificar sus servicios");
        }
        if (evento.getVersion() != version) {
            throw ProblemaException.conflicto("EVENTO_MODIFICADO",
                    "Otra persona modificó el evento mientras lo editabas. Volvé a abrir la ficha para ver los cambios.");
        }
        if (pedidos.stream().map(Servicio::categoriaId).distinct().count() < pedidos.size()) {
            throw ProblemaException.reglaDeNegocio("CATEGORIA_REPETIDA", "Cada categoría va una sola vez.");
        }
        entityManager.lock(evento, LockModeType.PESSIMISTIC_FORCE_INCREMENT);

        Map<Short, CategoriaServicio> categorias = catalogos.categorias().stream()
                .collect(Collectors.toMap(CategoriaServicio::getId, Function.identity()));
        Map<Short, ServicioContratado> actuales = new HashMap<>();
        servicios.findByEventoId(id).forEach(s -> actuales.put(s.getCategoriaId(), s));

        OffsetDateTime momento = OffsetDateTime.now();
        List<ModificacionEvento> filas = new ArrayList<>();
        Set<CategoriaServicio> cambiadas = new LinkedHashSet<>();
        for (Servicio pedido : pedidos) {
            CategoriaServicio categoria = categorias.get(pedido.categoriaId());
            if (categoria == null) {
                throw ProblemaException.reglaDeNegocio("CATEGORIA_INEXISTENTE", "Una de las categorías ya no existe. Volvé a abrir la ficha.");
            }
            ServicioContratado actual = actuales.get(categoria.getId());
            String antes = actual == null ? null : actual.getDescripcion();
            String despues = pedido.descripcion() == null || pedido.descripcion().isBlank() ? null : pedido.descripcion().strip();
            if (Objects.equals(antes, despues)) {
                continue;
            }
            if (!categoria.isActivo()) {
                throw ProblemaException.reglaDeNegocio("CATEGORIA_DADA_DE_BAJA",
                        "La categoría " + categoria.getNombre() + " está dada de baja: lo cargado se conserva pero no se modifica.");
            }
            if (despues == null) {
                servicios.delete(actual);
            } else if (actual == null) {
                servicios.save(new ServicioContratado(id, categoria.getId(), despues, quien.id(), momento));
            } else {
                actual.cambiar(despues, quien.id(), momento);
            }
            filas.add(new ModificacionEvento(id, "servicio." + categoria.getNombre(), antes, despues, quien.id(), momento));
            cambiadas.add(categoria);
        }

        if (evento.getEstado() == EstadoEvento.CONFIRMADO) {
            Set<Short> conServicio = new LinkedHashSet<>(actuales.keySet());
            for (Servicio pedido : pedidos) {
                if (pedido.descripcion() == null || pedido.descripcion().isBlank()) {
                    conServicio.remove(pedido.categoriaId());
                } else {
                    conServicio.add(pedido.categoriaId());
                }
            }
            categorias.values().stream()
                    .filter(c -> c.isActivo() && c.isRequeridaParaConfirmar() && !conServicio.contains(c.getId()))
                    .findFirst()
                    .ifPresent(c -> {
                        throw ProblemaException.reglaDeNegocio("SERVICIO_REQUERIDO",
                                "El evento está confirmado: %s no puede quedar vacío.".formatted(c.getNombre()));
                    });
        }

        modificaciones.saveAll(filas);
        avisos.modificacion(evento, filas.stream().map(ModificacionEvento::getCampo).toList(),
                cambiadas.stream().anyMatch(CategoriaServicio::isVisibleEnCocina),
                cambiadas.stream().anyMatch(CategoriaServicio::isAvisaACompras), quien.id());
        return evento;
    }
}
