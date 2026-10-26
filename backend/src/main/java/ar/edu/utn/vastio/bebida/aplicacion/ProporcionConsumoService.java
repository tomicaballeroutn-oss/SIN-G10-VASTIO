package ar.edu.utn.vastio.bebida.aplicacion;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.bebida.dominio.Bebida;
import ar.edu.utn.vastio.bebida.dominio.ProporcionConsumo;
import ar.edu.utn.vastio.bebida.infraestructura.BebidaRepository;
import ar.edu.utn.vastio.bebida.infraestructura.ProporcionConsumoRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;

/**
 * Proporciones de consumo (docs/sprint-4.md, decisión 14): por tipo de evento y bebida, las botellas por asistente con
 * que arranca la orden sugerida mientras no hay suficientes eventos cerrados del tipo. Una por tipo y bebida.
 */
@Service
@Transactional(readOnly = true)
public class ProporcionConsumoService {

    private final ProporcionConsumoRepository proporciones;
    private final BebidaRepository bebidas;
    private final CatalogoService catalogos;

    public ProporcionConsumoService(ProporcionConsumoRepository proporciones, BebidaRepository bebidas,
            CatalogoService catalogos) {
        this.proporciones = proporciones;
        this.bebidas = bebidas;
        this.catalogos = catalogos;
    }

    /** Las del tipo de evento, por nombre de bebida. */
    public List<ProporcionConsumo> delTipo(short tipoEventoId) {
        catalogos.tipoEvento(tipoEventoId);
        return proporciones.findByTipoEventoId(tipoEventoId).stream()
                .sorted(Comparator.comparing((ProporcionConsumo p) -> p.getBebida().getNombre(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(p -> p.getBebida().getPresentacion(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Agrega la de la bebida o cambia la que había. Solo bebidas activas. */
    @Transactional
    public ProporcionConsumo guardar(short tipoEventoId, long bebidaId, BigDecimal botellasPorAsistente) {
        catalogos.tipoEvento(tipoEventoId);
        Bebida bebida = bebidas.findById(bebidaId).filter(Bebida::isActivo).orElseThrow(() -> ProblemaException.reglaDeNegocio(
                "BEBIDA_INVALIDA", "Esa bebida no existe o está dada de baja."));
        ProporcionConsumo proporcion = proporciones.findByTipoEventoIdAndBebidaId(tipoEventoId, bebidaId)
                .orElseGet(() -> new ProporcionConsumo(tipoEventoId, bebida, botellasPorAsistente));
        proporcion.setBotellasPorAsistente(botellasPorAsistente);
        return proporciones.save(proporcion);
    }

    @Transactional
    public void quitar(long id) {
        ProporcionConsumo proporcion = proporciones.findById(id).orElseThrow(() -> ProblemaException.noEncontrado(
                "PROPORCION_INEXISTENTE", "No encontramos esa proporción."));
        proporciones.delete(proporcion);
    }
}
