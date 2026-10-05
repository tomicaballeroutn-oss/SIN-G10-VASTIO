package ar.edu.utn.vastio.configuracion.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.comun.seguridad.Permisos;
import ar.edu.utn.vastio.configuracion.api.CatalogoDto.CategoriaRequest;
import ar.edu.utn.vastio.configuracion.api.CatalogoDto.CategoriaResponse;
import ar.edu.utn.vastio.configuracion.api.CatalogoDto.MotivoAltaRequest;
import ar.edu.utn.vastio.configuracion.api.CatalogoDto.MotivoRequest;
import ar.edu.utn.vastio.configuracion.api.CatalogoDto.MotivoResponse;
import ar.edu.utn.vastio.configuracion.api.CatalogoDto.ParametroRequest;
import ar.edu.utn.vastio.configuracion.api.CatalogoDto.ParametroResponse;
import ar.edu.utn.vastio.configuracion.api.CatalogoDto.SalonRequest;
import ar.edu.utn.vastio.configuracion.api.CatalogoDto.SalonResponse;
import ar.edu.utn.vastio.configuracion.api.CatalogoDto.SegmentoRequest;
import ar.edu.utn.vastio.configuracion.api.CatalogoDto.SegmentoResponse;
import ar.edu.utn.vastio.configuracion.api.CatalogoDto.TipoEventoRequest;
import ar.edu.utn.vastio.configuracion.api.CatalogoDto.TipoEventoResponse;
import ar.edu.utn.vastio.configuracion.api.CatalogoDto.TurnoRequest;
import ar.edu.utn.vastio.configuracion.api.CatalogoDto.TurnoResponse;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.aplicacion.ParametroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Configurar parámetros del sistema (UI-06). Leer: cualquier perfil con sesión (las pantallas los usan para
 * armar formularios y la agenda). Modificar: Administración, Coordinación y Dirección.
 * Los listados incluyen lo dado de baja con {@code activo = false}; cada pantalla decide qué ofrecer.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Parámetros", description = "Salones, turnos, tipos de evento, segmentos, categorías, motivos y parámetros generales")
public class CatalogoController {

    private final CatalogoService catalogos;
    private final ParametroService parametros;

    public CatalogoController(CatalogoService catalogos, ParametroService parametros) {
        this.catalogos = catalogos;
        this.parametros = parametros;
    }

    // ---------- salones ----------

    @GetMapping("/salones")
    @Operation(summary = "Listar salones", description = "En el orden de la agenda.")
    public List<SalonResponse> salones() {
        return catalogos.salones().stream().map(SalonResponse::de).toList();
    }

    @PutMapping("/salones/{id}")
    @PreAuthorize(Permisos.CONFIGURAR)
    @Operation(summary = "Editar un salón", description = "Nombre, capacidad y baja lógica. El código (color de la agenda) no cambia.")
    public SalonResponse actualizarSalon(@PathVariable short id, @Valid @RequestBody SalonRequest pedido) {
        return SalonResponse.de(catalogos.actualizarSalon(id, pedido.nombre().trim(), pedido.capacidad(), pedido.activo()));
    }

    // ---------- turnos ----------

    @GetMapping("/turnos")
    @Operation(summary = "Listar turnos", description = "Por hora de inicio: Mediodía y Noche.")
    public List<TurnoResponse> turnos() {
        return catalogos.turnos().stream().map(TurnoResponse::de).toList();
    }

    @PutMapping("/turnos/{id}")
    @PreAuthorize(Permisos.CONFIGURAR)
    @Operation(summary = "Editar un turno", description = "Nombre y horario, aunque haya eventos futuros. Si la hora de fin es anterior a la de inicio, cruza la medianoche.")
    public TurnoResponse actualizarTurno(@PathVariable short id, @Valid @RequestBody TurnoRequest pedido) {
        return TurnoResponse.de(catalogos.actualizarTurno(id, pedido.nombre().trim(), pedido.horaInicio(), pedido.horaFin()));
    }

    // ---------- tipos de evento ----------

    @GetMapping("/tipos-evento")
    @Operation(summary = "Listar tipos de evento")
    public List<TipoEventoResponse> tiposEvento() {
        return catalogos.tiposEvento().stream().map(TipoEventoResponse::de).toList();
    }

    @PostMapping("/tipos-evento")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permisos.CONFIGURAR)
    @Operation(summary = "Agregar un tipo de evento")
    public TipoEventoResponse crearTipoEvento(@Valid @RequestBody TipoEventoRequest pedido) {
        return TipoEventoResponse.de(catalogos.crearTipoEvento(pedido.nombre().trim(), pedido.usaSegmentos()));
    }

    @PutMapping("/tipos-evento/{id}")
    @PreAuthorize(Permisos.CONFIGURAR)
    @Operation(summary = "Editar o dar de baja un tipo de evento")
    public TipoEventoResponse actualizarTipoEvento(@PathVariable short id, @Valid @RequestBody TipoEventoRequest pedido) {
        return TipoEventoResponse.de(catalogos.actualizarTipoEvento(id, pedido.nombre().trim(), pedido.usaSegmentos(),
                activo(pedido.activo())));
    }

    // ---------- segmentos de asistencia ----------

    @GetMapping("/segmentos-asistencia")
    @Operation(summary = "Listar segmentos de asistencia")
    public List<SegmentoResponse> segmentos() {
        return catalogos.segmentos().stream().map(SegmentoResponse::de).toList();
    }

    @PostMapping("/segmentos-asistencia")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permisos.CONFIGURAR)
    @Operation(summary = "Agregar un segmento de asistencia")
    public SegmentoResponse crearSegmento(@Valid @RequestBody SegmentoRequest pedido) {
        return SegmentoResponse.de(catalogos.crearSegmento(pedido.nombre().trim()));
    }

    @PutMapping("/segmentos-asistencia/{id}")
    @PreAuthorize(Permisos.CONFIGURAR)
    @Operation(summary = "Editar o dar de baja un segmento de asistencia")
    public SegmentoResponse actualizarSegmento(@PathVariable short id, @Valid @RequestBody SegmentoRequest pedido) {
        return SegmentoResponse.de(catalogos.actualizarSegmento(id, pedido.nombre().trim(), activo(pedido.activo())));
    }

    // ---------- categorías de servicio ----------

    @GetMapping("/categorias-servicio")
    @Operation(summary = "Listar categorías de servicio", description = "En el orden de la ficha.")
    public List<CategoriaResponse> categorias() {
        return catalogos.categorias().stream().map(CategoriaResponse::de).toList();
    }

    @PostMapping("/categorias-servicio")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permisos.CONFIGURAR)
    @Operation(summary = "Agregar una categoría de servicio")
    public CategoriaResponse crearCategoria(@Valid @RequestBody CategoriaRequest pedido) {
        return CategoriaResponse.de(catalogos.crearCategoria(pedido.nombre().trim(), pedido.orden(),
                pedido.visibleEnCocina(), pedido.requeridaParaConfirmar(), pedido.avisaACompras()));
    }

    @PutMapping("/categorias-servicio/{id}")
    @PreAuthorize(Permisos.CONFIGURAR)
    @Operation(summary = "Editar o dar de baja una categoría de servicio")
    public CategoriaResponse actualizarCategoria(@PathVariable short id, @Valid @RequestBody CategoriaRequest pedido) {
        return CategoriaResponse.de(catalogos.actualizarCategoria(id, pedido.nombre().trim(), pedido.orden(),
                pedido.visibleEnCocina(), pedido.requeridaParaConfirmar(), pedido.avisaACompras(), activo(pedido.activo())));
    }

    // ---------- motivos ----------

    @GetMapping("/motivos")
    @Operation(summary = "Listar motivos", description = "De cancelación, reprogramación, bloqueo y ajuste de stock.")
    public List<MotivoResponse> motivos() {
        return catalogos.motivos().stream().map(MotivoResponse::de).toList();
    }

    @PostMapping("/motivos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permisos.CONFIGURAR)
    @Operation(summary = "Agregar un motivo")
    public MotivoResponse crearMotivo(@Valid @RequestBody MotivoAltaRequest pedido) {
        return MotivoResponse.de(catalogos.crearMotivo(pedido.ambito(), pedido.nombre().trim()));
    }

    @PutMapping("/motivos/{id}")
    @PreAuthorize(Permisos.CONFIGURAR)
    @Operation(summary = "Editar o dar de baja un motivo", description = "El ámbito no cambia.")
    public MotivoResponse actualizarMotivo(@PathVariable short id, @Valid @RequestBody MotivoRequest pedido) {
        return MotivoResponse.de(catalogos.actualizarMotivo(id, pedido.nombre().trim(), activo(pedido.activo())));
    }

    // ---------- parámetros generales ----------

    @GetMapping("/parametros")
    @Operation(summary = "Listar parámetros generales", description = "Con el rango que acepta cada uno.")
    public List<ParametroResponse> parametros() {
        return parametros.todos().stream().map(ParametroResponse::de).toList();
    }

    @PutMapping("/parametros/{clave}")
    @PreAuthorize(Permisos.CONFIGURAR)
    @Operation(summary = "Cambiar un parámetro general")
    public ParametroResponse actualizarParametro(@PathVariable String clave, @Valid @RequestBody ParametroRequest pedido) {
        return ParametroResponse.de(parametros.actualizar(clave, pedido.valor()));
    }

    /** Si la edición no manda {@code activo}, queda activo. */
    private static boolean activo(Boolean activo) {
        return activo == null || activo;
    }
}
