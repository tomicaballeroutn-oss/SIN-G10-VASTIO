package ar.edu.utn.vastio.agenda.aplicacion;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.agenda.dominio.ComprobanteSena;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.infraestructura.ComprobanteSenaRepository;
import ar.edu.utn.vastio.agenda.infraestructura.GeneradorComprobanteSena;
import ar.edu.utn.vastio.agenda.infraestructura.GeneradorComprobanteSena.Datos;
import ar.edu.utn.vastio.agenda.infraestructura.NumeradorComprobante;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.aplicacion.LogoSalon;
import ar.edu.utn.vastio.usuarios.aplicacion.UsuarioService;

/** Emisión transaccional por POST y descarga del PDF histórico por GET (M-07). */
@Service
public class ComprobanteSenaService {

    private final ConsultaEventoService consultas;
    private final ComprobanteSenaRepository comprobantes;
    private final NumeradorComprobante numerador;
    private final GeneradorComprobanteSena generador;
    private final CatalogoService catalogos;
    private final UsuarioService usuarios;
    private final ObjectProvider<LogoSalon> logos;
    private final EntityManager entityManager;
    private final Clock reloj;

    public ComprobanteSenaService(ConsultaEventoService consultas, ComprobanteSenaRepository comprobantes,
            NumeradorComprobante numerador, GeneradorComprobanteSena generador, CatalogoService catalogos,
            UsuarioService usuarios, ObjectProvider<LogoSalon> logos, EntityManager entityManager, Clock reloj) {
        this.consultas = consultas;
        this.comprobantes = comprobantes;
        this.numerador = numerador;
        this.generador = generador;
        this.catalogos = catalogos;
        this.usuarios = usuarios;
        this.logos = logos;
        this.entityManager = entityManager;
        this.reloj = reloj;
    }

    public record Comprobante(String numero, byte[] pdf, boolean creado) {
    }

    @Transactional(readOnly = true)
    public Comprobante descargar(long id, UsuarioActual quien) {
        autorizar(consultas.visible(id, quien), quien);
        return original(comprobantes.findByEventoId(id).orElseThrow(() -> ProblemaException.noEncontrado(
                "COMPROBANTE_NO_EMITIDO", "Todavía no se emitió el comprobante. Usá Emitir comprobante.")), false);
    }

    @Transactional
    public Comprobante emitir(long id, UsuarioActual quien) {
        Evento evento = consultas.visible(id, quien);
        autorizar(evento, quien);
        // Refresca después de obtener el bloqueo: no se emite sobre un evento leído antes de esperar.
        entityManager.refresh(evento, LockModeType.PESSIMISTIC_WRITE);
        autorizar(evento, quien);
        var existente = comprobantes.findByEventoId(id);
        if (existente.isPresent()) return original(existente.get(), false);
        if (evento.getFechaSena() == null) {
            throw ProblemaException.reglaDeNegocio("SIN_SENA", "El evento todavía no tiene seña registrada.");
        }
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        String numero = NumeradorComprobante.numero(ahora.getYear(), numerador.siguiente(ahora.getYear()));
        byte[] pdf;
        try {
            pdf = generador.generar(datos(evento, numero, ahora));
            if (pdf == null || pdf.length < 5 || !new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")) {
                throw new IllegalStateException("El generador no produjo un PDF");
            }
        } catch (RuntimeException e) {
            throw new ProblemaException(HttpStatus.INTERNAL_SERVER_ERROR, "COMPROBANTE_GENERACION_FALLIDA",
                    "No pudimos emitir el comprobante", "No se guardó ningún comprobante. Volvé a intentar la emisión.");
        }
        return original(comprobantes.saveAndFlush(new ComprobanteSena(id, numero, ahora, quien.id(), pdf)), true);
    }

    private void autorizar(Evento evento, UsuarioActual quien) {
        if (!AccesoEvento.veImportes(quien, evento)) throw ConsultaEventoService.sinPermiso();
    }

    private Comprobante original(ComprobanteSena c, boolean creado) {
        byte[] pdf = c.getPdfOriginal();
        if (pdf == null) throw ProblemaException.conflicto("COMPROBANTE_SIN_ORIGINAL",
                "Este comprobante anterior no tiene un PDF original conservado. Requiere revisión; no se reconstruye con datos actuales.");
        return new Comprobante(c.getNumero(), pdf, creado);
    }

    private Datos datos(Evento e, String numero, OffsetDateTime emision) {
        short salonId = e.getUnidad().getSalonId();
        LogoSalon logo = logos.getIfAvailable();
        Map<Long, String> nombres = usuarios.nombres(List.of(e.getVendedoraId()));
        return new Datos(numero, emision, catalogos.salon(salonId).getNombre(),
                logo == null ? null : logo.de(salonId).orElse(null), e.getCliente().getNombre(), e.getCliente().getDocumento(),
                e.getFirmanteNombre(), e.getFirmanteDni(), catalogos.tipoEvento(e.getTipoEventoId()).getNombre(), e.getNombre(),
                e.getUnidad().getFecha(), catalogos.turno(e.getUnidad().getTurnoId()).getNombre(), e.getImporteSena(),
                e.getMonedaSena(), e.getFechaSena(), nombres.get(e.getVendedoraId()));
    }
}
