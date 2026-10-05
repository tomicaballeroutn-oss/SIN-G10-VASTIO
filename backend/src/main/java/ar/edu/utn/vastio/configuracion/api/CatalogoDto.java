package ar.edu.utn.vastio.configuracion.api;

import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import ar.edu.utn.vastio.configuracion.dominio.AmbitoMotivo;
import ar.edu.utn.vastio.configuracion.dominio.CategoriaServicio;
import ar.edu.utn.vastio.configuracion.dominio.Motivo;
import ar.edu.utn.vastio.configuracion.dominio.Parametro;
import ar.edu.utn.vastio.configuracion.dominio.ParametroClave;
import ar.edu.utn.vastio.configuracion.dominio.Salon;
import ar.edu.utn.vastio.configuracion.dominio.TipoEvento;
import ar.edu.utn.vastio.configuracion.dominio.TipoSegmentoAsistencia;
import ar.edu.utn.vastio.configuracion.dominio.Turno;

/**
 * Pedidos y respuestas de los catálogos (UI-06). En las ediciones, {@code activo} falso es la baja lógica.
 */
public final class CatalogoDto {

    private CatalogoDto() {
    }

    // ---------- salones ----------

    public record SalonResponse(short id, String codigo, String nombre, Integer capacidad, boolean activo) {
        static SalonResponse de(Salon s) {
            return new SalonResponse(s.getId(), s.getCodigo(), s.getNombre(), s.getCapacidad(), s.isActivo());
        }
    }

    public record SalonRequest(
            @NotBlank(message = "Escribí el nombre del salón.") @Size(max = 40, message = "Usá hasta 40 caracteres.") String nombre,
            @Positive(message = "La capacidad tiene que ser mayor a 0.") Integer capacidad,
            boolean activo) {
    }

    // ---------- turnos ----------

    public record TurnoResponse(short id, String codigo, String nombre, LocalTime horaInicio, LocalTime horaFin,
            boolean cruzaMedianoche) {
        static TurnoResponse de(Turno t) {
            return new TurnoResponse(t.getId(), t.getCodigo(), t.getNombre(), t.getHoraInicio(), t.getHoraFin(),
                    t.isCruzaMedianoche());
        }
    }

    public record TurnoRequest(
            @NotBlank(message = "Escribí el nombre del turno.") @Size(max = 20, message = "Usá hasta 20 caracteres.") String nombre,
            @NotNull(message = "Elegí la hora de inicio.") LocalTime horaInicio,
            @NotNull(message = "Elegí la hora de fin.") LocalTime horaFin) {
    }

    // ---------- tipos de evento ----------

    public record TipoEventoResponse(short id, String nombre, boolean usaSegmentos, boolean activo) {
        static TipoEventoResponse de(TipoEvento t) {
            return new TipoEventoResponse(t.getId(), t.getNombre(), t.isUsaSegmentos(), t.isActivo());
        }
    }

    public record TipoEventoRequest(
            @NotBlank(message = "Escribí el nombre del tipo de evento.") @Size(max = 40, message = "Usá hasta 40 caracteres.") String nombre,
            boolean usaSegmentos,
            Boolean activo) {
    }

    // ---------- segmentos de asistencia ----------

    public record SegmentoResponse(short id, String nombre, boolean activo) {
        static SegmentoResponse de(TipoSegmentoAsistencia s) {
            return new SegmentoResponse(s.getId(), s.getNombre(), s.isActivo());
        }
    }

    public record SegmentoRequest(
            @NotBlank(message = "Escribí el nombre del segmento.") @Size(max = 40, message = "Usá hasta 40 caracteres.") String nombre,
            Boolean activo) {
    }

    // ---------- categorías de servicio ----------

    public record CategoriaResponse(short id, String nombre, short orden, boolean visibleEnCocina,
            boolean requeridaParaConfirmar, boolean avisaACompras, boolean activo) {
        static CategoriaResponse de(CategoriaServicio c) {
            return new CategoriaResponse(c.getId(), c.getNombre(), c.getOrden(), c.isVisibleEnCocina(),
                    c.isRequeridaParaConfirmar(), c.isAvisaACompras(), c.isActivo());
        }
    }

    public record CategoriaRequest(
            @NotBlank(message = "Escribí el nombre de la categoría.") @Size(max = 40, message = "Usá hasta 40 caracteres.") String nombre,
            @NotNull(message = "Indicá el orden.") @Min(value = 1, message = "El orden empieza en 1.") @Max(value = 999, message = "Usá un orden hasta 999.") Short orden,
            boolean visibleEnCocina,
            boolean requeridaParaConfirmar,
            boolean avisaACompras,
            Boolean activo) {
    }

    // ---------- motivos ----------

    public record MotivoResponse(short id, AmbitoMotivo ambito, String nombre, boolean activo) {
        static MotivoResponse de(Motivo m) {
            return new MotivoResponse(m.getId(), m.getAmbito(), m.getNombre(), m.isActivo());
        }
    }

    public record MotivoAltaRequest(
            @NotNull(message = "Elegí para qué se usa el motivo.") AmbitoMotivo ambito,
            @NotBlank(message = "Escribí el motivo.") @Size(max = 80, message = "Usá hasta 80 caracteres.") String nombre) {
    }

    /** El ámbito no se edita: un motivo ya usado en una cancelación no puede pasar a ser de bloqueo. */
    public record MotivoRequest(
            @NotBlank(message = "Escribí el motivo.") @Size(max = 80, message = "Usá hasta 80 caracteres.") String nombre,
            Boolean activo) {
    }

    // ---------- parámetros ----------

    public record ParametroResponse(String clave, String valor, String descripcion, Integer minimo, Integer maximo) {
        static ParametroResponse de(Parametro p) {
            ParametroClave clave = conocida(p.getClave());
            return new ParametroResponse(p.getClave(), p.getValor(), p.getDescripcion(),
                    clave == null ? null : clave.getMinimo(), clave == null ? null : clave.getMaximo());
        }

        private static ParametroClave conocida(String clave) {
            for (ParametroClave c : ParametroClave.values()) {
                if (c.name().equals(clave)) {
                    return c;
                }
            }
            return null;
        }
    }

    public record ParametroRequest(@NotBlank(message = "Escribí un valor.") @Size(max = 255) String valor) {
    }
}
