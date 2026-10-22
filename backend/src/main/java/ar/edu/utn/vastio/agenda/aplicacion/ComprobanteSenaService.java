package ar.edu.utn.vastio.agenda.aplicacion;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
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

/**
 * Comprobante interno de la seña en PDF (M-07). Lo descargan solo quienes ven el importe (Dirección, Coordinación,
 * Administración y la vendedora titular), una vez señado el evento. La primera descarga le asigna un número correlativo
 * (CS-AAAA-NNNNN) y guarda quién y cuándo; las siguientes repiten número y fecha de emisión. Si el salón tiene logo
 * ({@link LogoSalon}, M-04), va en el encabezado; si no, su nombre.
 */
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

    public record Comprobante(String numero, byte[] pdf) {
    }

    @Transactional
    public Comprobante emitir(long id, UsuarioActual quien) {
        Evento evento = consultas.visible(id, quien);
        if (!AccesoEvento.veImportes(quien, evento)) {
            throw ConsultaEventoService.sinPermiso();
        }
        if (evento.getFechaSena() == null) {
            throw ProblemaException.reglaDeNegocio("SIN_SENA",
                    "El evento todavía no tiene seña registrada: el comprobante se emite una vez señado.");
        }
        // Dos primeras descargas a la vez: la segunda espera y encuentra el comprobante de la primera.
        entityManager.lock(evento, LockModeType.PESSIMISTIC_WRITE);
        ComprobanteSena comprobante = comprobantes.findByEventoId(id).orElseGet(() -> nuevo(id, quien));
        return new Comprobante(comprobante.getNumero(), generador.generar(datos(evento, comprobante)));
    }

    private ComprobanteSena nuevo(long eventoId, UsuarioActual quien) {
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        String numero = NumeradorComprobante.numero(ahora.getYear(), numerador.siguiente(ahora.getYear()));
        return comprobantes.save(new ComprobanteSena(eventoId, numero, ahora, quien.id()));
    }

    private Datos datos(Evento e, ComprobanteSena c) {
        short salonId = e.getUnidad().getSalonId();
        LogoSalon logo = logos.getIfAvailable();
        Map<Long, String> nombres = usuarios.nombres(List.of(e.getVendedoraId()));
        return new Datos(c.getNumero(), c.getFechaEmision(), catalogos.salon(salonId).getNombre(),
                logo == null ? null : logo.de(salonId).orElse(null), e.getCliente().getNombre(), e.getCliente().getDocumento(),
                e.getFirmanteNombre(), e.getFirmanteDni(), catalogos.tipoEvento(e.getTipoEventoId()).getNombre(), e.getNombre(),
                e.getUnidad().getFecha(), catalogos.turno(e.getUnidad().getTurnoId()).getNombre(), e.getImporteSena(),
                e.getMonedaSena(), e.getFechaSena(), nombres.get(e.getVendedoraId()));
    }
}
