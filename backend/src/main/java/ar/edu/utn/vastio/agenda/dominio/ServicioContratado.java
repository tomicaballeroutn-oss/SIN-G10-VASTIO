package ar.edu.utn.vastio.agenda.dominio;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/**
 * Lo acordado con el cliente en una categoría (Menú, Tipo de barra, …): renglones libres con cantidad opcional
 * y un detalle libre (V10, M-09). Uno por categoría y evento ({@code ux_servicio_contratado_evento_categoria}, V4);
 * existe solo si tiene algo cargado. La categoría es del módulo configuración: se guarda por id.
 */
@Entity
@Table(name = "servicio_contratado")
public class ServicioContratado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "servicio_id")
    private Long id;

    @Column(name = "evento_id", nullable = false)
    private Long eventoId;

    @Column(name = "categoria_id", nullable = false)
    private Short categoriaId;

    /** Detalle libre de la categoría. Lo cargado antes de V10 quedó acá. */
    @Column(name = "descripcion", columnDefinition = "text")
    private String descripcion;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "servicio_renglon", joinColumns = @JoinColumn(name = "servicio_id"))
    @OrderColumn(name = "orden")
    private List<RenglonServicio> renglones = new ArrayList<>();

    /** Último en modificarlo. */
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "fecha_modificacion", nullable = false)
    private OffsetDateTime fechaModificacion;

    protected ServicioContratado() {
    }

    public ServicioContratado(long eventoId, short categoriaId, String descripcion, List<RenglonServicio> renglones,
            long usuarioId, OffsetDateTime momento) {
        this.eventoId = eventoId;
        this.categoriaId = categoriaId;
        cambiar(descripcion, renglones, usuarioId, momento);
    }

    public void cambiar(String descripcion, List<RenglonServicio> renglones, long usuarioId, OffsetDateTime momento) {
        this.descripcion = descripcion;
        this.renglones.clear();
        this.renglones.addAll(renglones);
        this.usuarioId = usuarioId;
        this.fechaModificacion = momento;
    }

    /**
     * Todo lo cargado en una línea, como queda en el historial: «Celíaco (2), Sin lactosa (3) · Detalle». Null si
     * no hay nada.
     */
    public static String legible(List<RenglonServicio> renglones, String descripcion) {
        String lista = renglones.stream().map(RenglonServicio::legible).collect(Collectors.joining(", "));
        String texto = Stream.of(lista, descripcion).filter(s -> s != null && !s.isEmpty()).collect(Collectors.joining(" · "));
        return texto.isEmpty() ? null : texto;
    }

    public String legible() {
        return legible(renglones, descripcion);
    }

    public Long getId() {
        return id;
    }

    public long getEventoId() {
        return eventoId;
    }

    public short getCategoriaId() {
        return categoriaId;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public List<RenglonServicio> getRenglones() {
        return List.copyOf(renglones);
    }

    public long getUsuarioId() {
        return usuarioId;
    }

    public OffsetDateTime getFechaModificacion() {
        return fechaModificacion;
    }
}
