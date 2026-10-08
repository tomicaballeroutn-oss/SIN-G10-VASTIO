package ar.edu.utn.vastio.bebida.aplicacion;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.bebida.dominio.Bebida;
import ar.edu.utn.vastio.bebida.dominio.MovimientoStock;
import ar.edu.utn.vastio.bebida.dominio.Ubicacion;
import ar.edu.utn.vastio.bebida.infraestructura.BebidaRepository;
import ar.edu.utn.vastio.bebida.infraestructura.MovimientoStockRepository;
import ar.edu.utn.vastio.bebida.infraestructura.UbicacionRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.AmbitoMotivo;
import ar.edu.utn.vastio.configuracion.dominio.Motivo;
import ar.edu.utn.vastio.notificaciones.aplicacion.NotificacionService;
import ar.edu.utn.vastio.notificaciones.dominio.TipoNotificacion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;

/**
 * Registrar ajuste de stock (UI-38): un recuento físico registra un AJUSTE por la diferencia con el saldo teórico, y
 * una rotura declarada, una MERMA que sale de la ubicación. Ninguno edita movimientos: agregan un asiento. Declarar una
 * rotura es simple y sin castigo. Si el saldo queda negativo se acepta y se avisa a Compras y Administración
 * (docs/sprint-3.md, decisiones 17 y 18).
 */
@Service
@Transactional(readOnly = true)
public class AjusteService {

    private final UbicacionRepository ubicaciones;
    private final BebidaRepository bebidas;
    private final MovimientoStockRepository movimientos;
    private final MovimientoService movimientoService;
    private final CatalogoService catalogos;
    private final NotificacionService notificaciones;

    public AjusteService(UbicacionRepository ubicaciones, BebidaRepository bebidas, MovimientoStockRepository movimientos,
            MovimientoService movimientoService, CatalogoService catalogos, NotificacionService notificaciones) {
        this.ubicaciones = ubicaciones;
        this.bebidas = bebidas;
        this.movimientos = movimientos;
        this.movimientoService = movimientoService;
        this.catalogos = catalogos;
        this.notificaciones = notificaciones;
    }

    /** Detalle vacío es null; con el motivo «Otro» es obligatorio. */
    public record DatosAjuste(short ubicacionId, long bebidaId, BigDecimal cantidad, short motivoId, String detalle) {
    }

    /** {@code movimiento} null si el recuento coincidió con el saldo. Saldo y diferencia en botellas. */
    public record Resultado(MovimientoStock movimiento, Ubicacion ubicacion, Bebida bebida, BigDecimal saldoAnterior,
            BigDecimal saldo, boolean avisoSaldoNegativo) {
    }

    /** Lo contado reemplaza al saldo teórico: se registra la diferencia. El saldo se lee bloqueado. */
    @Transactional
    public Resultado registrarRecuento(DatosAjuste datos, long usuarioId) {
        Ubicacion ubicacion = ubicacion(datos.ubicacionId());
        Bebida bebida = bebida(datos.bebidaId());
        Motivo motivo = motivo(datos.motivoId(), datos.detalle());
        BigDecimal saldo = movimientos.saldoBloqueado(ubicacion.getId(), bebida.getId()).orElse(BigDecimal.ZERO);
        BigDecimal diferencia = datos.cantidad().subtract(saldo);
        if (diferencia.signum() == 0) {
            return new Resultado(null, ubicacion, bebida, saldo, saldo, false);
        }
        var registrado = movimientoService.registrar(MovimientoStock.ajuste(bebida, ubicacion, diferencia, motivo.getId(),
                datos.detalle(), usuarioId));
        BigDecimal nuevo = diferencia.signum() > 0 ? registrado.saldoDestino() : registrado.saldoOrigen();
        return new Resultado(registrado.movimiento(), ubicacion, bebida, saldo, nuevo, false);
    }

    /** Sale de la ubicación sin destino. Nunca se rechaza por falta de saldo (regla 5): si queda negativo, se avisa. */
    @Transactional
    public Resultado declararRotura(DatosAjuste datos, long usuarioId) {
        Ubicacion ubicacion = ubicacion(datos.ubicacionId());
        Bebida bebida = bebida(datos.bebidaId());
        Motivo motivo = motivo(datos.motivoId(), datos.detalle());
        var registrado = movimientoService.registrar(MovimientoStock.merma(bebida, ubicacion, datos.cantidad(), motivo.getId(),
                datos.detalle(), usuarioId));
        BigDecimal nuevo = registrado.saldoOrigen();
        boolean negativo = nuevo.signum() < 0;
        if (negativo) {
            notificaciones.notificar(null, TipoNotificacion.ALERTA_STOCK,
                    "Saldo negativo de %s en %s: falta registrar un movimiento.".formatted(bebida.descripcion(), ubicacion.getNombre()),
                    usuarioId, List.of(RolCodigo.COMPRAS, RolCodigo.ADMINISTRACION));
        }
        return new Resultado(registrado.movimiento(), ubicacion, bebida, nuevo.add(datos.cantidad()), nuevo, negativo);
    }

    private Motivo motivo(short id, String detalle) {
        Motivo motivo = catalogos.motivo(id);
        if (motivo.getAmbito() != AmbitoMotivo.AJUSTE || !motivo.isActivo()) {
            throw ProblemaException.reglaDeNegocio("MOTIVO_INVALIDO", "Elegí un motivo de ajuste de la lista.");
        }
        if (motivo.pideDetalle() && detalle == null) {
            throw ProblemaException.reglaDeNegocio("FALTA_DETALLE", "Con el motivo «%s», contá qué pasó en el detalle.".formatted(motivo.getNombre()));
        }
        return motivo;
    }

    /** Solo ubicaciones activas: una dada de baja no tiene saldo. */
    private Ubicacion ubicacion(short id) {
        Ubicacion ubicacion = ubicaciones.findById(id).orElseThrow(() -> ProblemaException.noEncontrado("UBICACION_INEXISTENTE",
                "No encontramos esa ubicación."));
        if (!ubicacion.isActivo()) {
            throw ProblemaException.reglaDeNegocio("UBICACION_INACTIVA", "%s está dada de baja.".formatted(ubicacion.getNombre()));
        }
        return ubicacion;
    }

    /** Puede estar dada de baja: así se deja en cero el saldo que le quedó (decisión del equipo). */
    private Bebida bebida(long id) {
        return bebidas.findById(id).orElseThrow(() -> ProblemaException.noEncontrado("BEBIDA_INEXISTENTE", "No encontramos esa bebida."));
    }
}
