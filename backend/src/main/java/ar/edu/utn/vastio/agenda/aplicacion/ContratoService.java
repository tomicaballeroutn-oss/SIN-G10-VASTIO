package ar.edu.utn.vastio.agenda.aplicacion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.agenda.dominio.DocumentoEvento;
import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.infraestructura.DocumentoEventoRepository;
import ar.edu.utn.vastio.agenda.infraestructura.LegajoArchivos;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;

/**
 * Registrar firma de contrato (UI-12): el evento señado pasa a Contratado con la fecha de firma y el contrato
 * digitalizado (uno o más archivos: un contrato puede ser varias fotos). La registran Coordinación y Dirección.
 * Los archivos se descargan solo con los perfiles que ven el importe de la seña: el contrato tiene importes.
 */
@Service
public class ContratoService {

    private static final ZoneId ZONA = ZoneId.of(VastioApplication.ZONA_HORARIA);
    public static final int MAXIMO_ARCHIVOS = 10;
    public static final int MAXIMO_BYTES = 10 * 1024 * 1024;
    private static final int LARGO_NOMBRE = 150;

    private final ConsultaEventoService consultas;
    private final MaquinaDeEstados maquina;
    private final DocumentoEventoRepository documentos;
    private final LegajoArchivos legajo;
    private final EntityManager entityManager;

    public ContratoService(ConsultaEventoService consultas, MaquinaDeEstados maquina, DocumentoEventoRepository documentos,
            LegajoArchivos legajo, EntityManager entityManager) {
        this.consultas = consultas;
        this.maquina = maquina;
        this.documentos = documentos;
        this.legajo = legajo;
        this.entityManager = entityManager;
    }

    /** Un archivo tal como llegó: nombre original y contenido. */
    public record Archivo(String nombre, byte[] contenido) {
    }

    /** Tipos aceptados, reconocidos por sus primeros bytes y no por la extensión. */
    enum Formato {
        PDF("application/pdf", "pdf", new byte[] {'%', 'P', 'D', 'F', '-'}),
        JPEG("image/jpeg", "jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
        PNG("image/png", "png", new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});

        final String mime;
        final String extension;
        private final byte[] firma;

        Formato(String mime, String extension, byte[] firma) {
            this.mime = mime;
            this.extension = extension;
            this.firma = firma;
        }

        static Formato de(byte[] contenido) {
            for (Formato f : values()) {
                if (contenido.length >= f.firma.length && Arrays.equals(contenido, 0, f.firma.length, f.firma, 0, f.firma.length)) {
                    return f;
                }
            }
            return null;
        }
    }

    @Transactional
    public Evento registrar(long id, LocalDate fechaFirma, List<Archivo> archivos, UsuarioActual quien) {
        Evento evento = consultas.visible(id, quien);
        if (!quien.accesoTotal()) {
            throw ConsultaEventoService.sinPermiso();
        }
        if (evento.getEstado() != EstadoEvento.SENADO) {
            throw ProblemaException.reglaDeNegocio("ESTADO_NO_PERMITE",
                    "El evento está en %s: la firma del contrato se registra sobre un evento señado.".formatted(
                            MaquinaDeEstados.nombre(evento.getEstado())));
        }
        if (fechaFirma.isAfter(LocalDate.now(ZONA))) {
            throw ProblemaException.reglaDeNegocio("FECHA_FIRMA_FUTURA",
                    "La fecha de firma no puede ser posterior a hoy. Registrá la firma cuando el contrato esté firmado.");
        }
        if (fechaFirma.isBefore(evento.getFechaSena())) {
            throw ProblemaException.reglaDeNegocio("FECHA_FIRMA_ANTERIOR_A_SENA",
                    "La fecha de firma no puede ser anterior a la seña (%s).".formatted(evento.getFechaSena()));
        }
        List<Formato> formatos = validar(archivos);

        // Una versión nueva: si justo otra persona cancelaba el evento, una de las dos recibe 409.
        entityManager.lock(evento, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        OffsetDateTime ahora = OffsetDateTime.now();
        for (int i = 0; i < archivos.size(); i++) {
            Archivo archivo = archivos.get(i);
            Formato formato = formatos.get(i);
            String ruta = legajo.guardar(evento.getId(), archivo.contenido(), formato.extension);
            documentos.save(new DocumentoEvento(evento.getId(), DocumentoEvento.Tipo.CONTRATO,
                    nombre(archivo.nombre(), formato), ruta, formato.mime, archivo.contenido().length, quien.id(), ahora));
        }
        evento.registrarFirmaContrato(fechaFirma);
        maquina.transicionar(evento, EstadoEvento.CONTRATADO, quien.id(), null);
        return evento;
    }

    public record Descarga(DocumentoEvento documento, byte[] contenido) {
    }

    @Transactional(readOnly = true)
    public Descarga descargar(long eventoId, long documentoId, UsuarioActual quien) {
        Evento evento = consultas.visible(eventoId, quien);
        if (!AccesoEvento.veImportes(quien, evento)) {
            throw ConsultaEventoService.sinPermiso();
        }
        DocumentoEvento documento = documentos.findById(documentoId)
                .filter(d -> d.getEventoId() == eventoId)
                .orElseThrow(() -> ProblemaException.noEncontrado("DOCUMENTO_INEXISTENTE", "No encontramos ese archivo."));
        return new Descarga(documento, legajo.leer(documento.getRuta()));
    }

    private static List<Formato> validar(List<Archivo> archivos) {
        if (archivos.isEmpty()) {
            throw ProblemaException.reglaDeNegocio("FALTA_CONTRATO", "Adjuntá el contrato digitalizado.");
        }
        if (archivos.size() > MAXIMO_ARCHIVOS) {
            throw ProblemaException.reglaDeNegocio("DEMASIADOS_ARCHIVOS",
                    "Adjuntá hasta %d archivos. Si el contrato tiene más hojas, juntalas en un PDF.".formatted(MAXIMO_ARCHIVOS));
        }
        return archivos.stream().map(a -> {
            if (a.contenido().length == 0) {
                throw ProblemaException.reglaDeNegocio("ARCHIVO_VACIO", "El archivo %s está vacío.".formatted(a.nombre()));
            }
            if (a.contenido().length > MAXIMO_BYTES) {
                throw ProblemaException.reglaDeNegocio("ARCHIVO_GRANDE",
                        "El archivo %s pesa más de 10 MB. Sacá la foto con menos resolución o comprimí el PDF.".formatted(a.nombre()));
            }
            Formato formato = Formato.de(a.contenido());
            if (formato == null) {
                throw ProblemaException.reglaDeNegocio("FORMATO_NO_ADMITIDO",
                        "El archivo %s no es PDF, JPG ni PNG. Adjuntá el contrato en alguno de esos formatos.".formatted(a.nombre()));
            }
            return formato;
        }).toList();
    }

    /** Nombre original sin carpetas, recortado; si no vino, uno genérico con la extensión real. */
    private static String nombre(String original, Formato formato) {
        String nombre = original == null ? "" : original.replace('\\', '/');
        nombre = nombre.substring(nombre.lastIndexOf('/') + 1).trim();
        if (nombre.isEmpty()) {
            nombre = "contrato." + formato.extension;
        }
        return nombre.length() <= LARGO_NOMBRE ? nombre : nombre.substring(nombre.length() - LARGO_NOMBRE);
    }
}
