package ar.edu.utn.vastio.configuracion.aplicacion;

import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.configuracion.dominio.AmbitoMotivo;
import ar.edu.utn.vastio.configuracion.dominio.CategoriaServicio;
import ar.edu.utn.vastio.configuracion.dominio.Motivo;
import ar.edu.utn.vastio.configuracion.dominio.Salon;
import ar.edu.utn.vastio.configuracion.dominio.TipoEvento;
import ar.edu.utn.vastio.configuracion.dominio.TipoSegmentoAsistencia;
import ar.edu.utn.vastio.configuracion.dominio.Turno;
import ar.edu.utn.vastio.configuracion.infraestructura.CategoriaServicioRepository;
import ar.edu.utn.vastio.configuracion.infraestructura.MotivoRepository;
import ar.edu.utn.vastio.configuracion.infraestructura.SalonRepository;
import ar.edu.utn.vastio.configuracion.infraestructura.TipoEventoRepository;
import ar.edu.utn.vastio.configuracion.infraestructura.TipoSegmentoAsistenciaRepository;
import ar.edu.utn.vastio.configuracion.infraestructura.TurnoRepository;

/**
 * Catálogos configurables (UI-06) y su lectura para los otros módulos.
 * Nada se borra: la baja es lógica y lo ya registrado conserva el valor.
 * Salones y turnos no se agregan: cada salón tiene su color en el sistema de diseño y la agenda es de 3 × 2.
 */
@Service
@Transactional(readOnly = true)
public class CatalogoService {

    /** Para los chequeos de nombre repetido al crear: ningún id vale 0. */
    private static final short SIN_ID = 0;

    private final SalonRepository salones;
    private final TurnoRepository turnos;
    private final TipoEventoRepository tiposEvento;
    private final TipoSegmentoAsistenciaRepository segmentos;
    private final CategoriaServicioRepository categorias;
    private final MotivoRepository motivos;

    public CatalogoService(SalonRepository salones, TurnoRepository turnos, TipoEventoRepository tiposEvento,
            TipoSegmentoAsistenciaRepository segmentos, CategoriaServicioRepository categorias, MotivoRepository motivos) {
        this.salones = salones;
        this.turnos = turnos;
        this.tiposEvento = tiposEvento;
        this.segmentos = segmentos;
        this.categorias = categorias;
        this.motivos = motivos;
    }

    // ---------- salones ----------

    public List<Salon> salones() {
        return salones.findAllByOrderByIdAsc();
    }

    public Salon salon(short id) {
        return salones.findById(id).orElseThrow(() -> ProblemaException.noEncontrado("SALON_INEXISTENTE",
                "No encontramos ese salón."));
    }

    @Transactional
    public Salon actualizarSalon(short id, String nombre, Integer capacidad, boolean activo) {
        Salon salon = salon(id);
        if (salones.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw repetido("Ya hay un salón con ese nombre.");
        }
        salon.actualizar(nombre, capacidad, activo);
        return salon;
    }

    // ---------- turnos ----------

    public List<Turno> turnos() {
        return turnos.findAllByOrderByHoraInicioAsc();
    }

    public Turno turno(short id) {
        return turnos.findById(id).orElseThrow(() -> ProblemaException.noEncontrado("TURNO_INEXISTENTE",
                "No encontramos ese turno."));
    }

    @Transactional
    public Turno actualizarTurno(short id, String nombre, LocalTime horaInicio, LocalTime horaFin) {
        Turno turno = turno(id);
        if (turnos.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw repetido("Ya hay un turno con ese nombre.");
        }
        if (horaInicio.equals(horaFin)) {
            throw ProblemaException.reglaDeNegocio("TURNO_SIN_DURACION",
                    "El turno no puede empezar y terminar a la misma hora. Revisá el horario.");
        }
        turno.actualizar(nombre, horaInicio, horaFin);
        return turno;
    }

    // ---------- tipos de evento ----------

    public List<TipoEvento> tiposEvento() {
        return tiposEvento.findAllByOrderByNombreAsc();
    }

    public TipoEvento tipoEvento(short id) {
        return tiposEvento.findById(id).orElseThrow(() -> ProblemaException.noEncontrado("TIPO_EVENTO_INEXISTENTE",
                "No encontramos ese tipo de evento."));
    }

    @Transactional
    public TipoEvento crearTipoEvento(String nombre, boolean usaSegmentos) {
        if (tiposEvento.existsByNombreIgnoreCaseAndIdNot(nombre, SIN_ID)) {
            throw repetido("Ya hay un tipo de evento con ese nombre.");
        }
        return tiposEvento.save(new TipoEvento(nombre, usaSegmentos));
    }

    @Transactional
    public TipoEvento actualizarTipoEvento(short id, String nombre, boolean usaSegmentos, boolean activo) {
        TipoEvento tipo = tipoEvento(id);
        if (tiposEvento.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw repetido("Ya hay un tipo de evento con ese nombre.");
        }
        tipo.actualizar(nombre, usaSegmentos, activo);
        return tipo;
    }

    // ---------- segmentos de asistencia ----------

    public List<TipoSegmentoAsistencia> segmentos() {
        return segmentos.findAllByOrderByIdAsc();
    }

    @Transactional
    public TipoSegmentoAsistencia crearSegmento(String nombre) {
        if (segmentos.existsByNombreIgnoreCaseAndIdNot(nombre, SIN_ID)) {
            throw repetido("Ya hay un segmento con ese nombre.");
        }
        return segmentos.save(new TipoSegmentoAsistencia(nombre));
    }

    @Transactional
    public TipoSegmentoAsistencia actualizarSegmento(short id, String nombre, boolean activo) {
        TipoSegmentoAsistencia segmento = segmentos.findById(id).orElseThrow(() -> ProblemaException.noEncontrado(
                "SEGMENTO_INEXISTENTE", "No encontramos ese segmento."));
        if (segmentos.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw repetido("Ya hay un segmento con ese nombre.");
        }
        segmento.actualizar(nombre, activo);
        return segmento;
    }

    // ---------- categorías de servicio ----------

    public List<CategoriaServicio> categorias() {
        return categorias.findAllByOrderByOrdenAscNombreAsc();
    }

    @Transactional
    public CategoriaServicio crearCategoria(String nombre, short orden, boolean visibleEnCocina,
            boolean requeridaParaConfirmar, boolean avisaACompras) {
        if (categorias.existsByNombreIgnoreCaseAndIdNot(nombre, SIN_ID)) {
            throw repetido("Ya hay una categoría con ese nombre.");
        }
        return categorias.save(new CategoriaServicio(nombre, orden, visibleEnCocina, requeridaParaConfirmar, avisaACompras));
    }

    @Transactional
    public CategoriaServicio actualizarCategoria(short id, String nombre, short orden, boolean visibleEnCocina,
            boolean requeridaParaConfirmar, boolean avisaACompras, boolean activo) {
        CategoriaServicio categoria = categorias.findById(id).orElseThrow(() -> ProblemaException.noEncontrado(
                "CATEGORIA_INEXISTENTE", "No encontramos esa categoría."));
        if (categorias.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw repetido("Ya hay una categoría con ese nombre.");
        }
        categoria.actualizar(nombre, orden, visibleEnCocina, requeridaParaConfirmar, avisaACompras, activo);
        return categoria;
    }

    // ---------- motivos ----------

    public List<Motivo> motivos() {
        return motivos.findAllByOrderByAmbitoAscIdAsc();
    }

    public Motivo motivo(short id) {
        return motivos.findById(id).orElseThrow(() -> ProblemaException.noEncontrado("MOTIVO_INEXISTENTE",
                "No encontramos ese motivo."));
    }

    @Transactional
    public Motivo crearMotivo(AmbitoMotivo ambito, String nombre) {
        if (motivos.existsByAmbitoAndNombreIgnoreCaseAndIdNot(ambito, nombre, SIN_ID)) {
            throw repetido("Ya hay un motivo con ese nombre para el mismo uso.");
        }
        return motivos.save(new Motivo(ambito, nombre));
    }

    @Transactional
    public Motivo actualizarMotivo(short id, String nombre, boolean activo) {
        Motivo motivo = motivos.findById(id).orElseThrow(() -> ProblemaException.noEncontrado(
                "MOTIVO_INEXISTENTE", "No encontramos ese motivo."));
        if (motivos.existsByAmbitoAndNombreIgnoreCaseAndIdNot(motivo.getAmbito(), nombre, id)) {
            throw repetido("Ya hay un motivo con ese nombre para el mismo uso.");
        }
        motivo.actualizar(nombre, activo);
        return motivo;
    }

    private static ProblemaException repetido(String mensaje) {
        return ProblemaException.conflicto("NOMBRE_REPETIDO", mensaje);
    }
}
