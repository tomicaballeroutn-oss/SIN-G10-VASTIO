package ar.edu.utn.vastio.agenda.infraestructura;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Archivos del legajo en disco: {@code <directorio>/<evento_id>/<uuid>.<extensión>}. En el ambiente de prueba el
 * directorio es un volumen de Docker (ver docs/despliegue.md). La base guarda solo la ruta relativa.
 */
@Component
public class LegajoArchivos {

    private static final Logger log = LoggerFactory.getLogger(LegajoArchivos.class);

    private final Path directorio;

    public LegajoArchivos(@Value("${vastio.legajo.directorio:datos/legajo}") String directorio) {
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
    }

    /**
     * Guarda el archivo y devuelve su ruta relativa. Si la transacción en curso se deshace, el archivo se borra:
     * no quedan archivos sin su fila en {@code documento_evento}.
     */
    public String guardar(long eventoId, byte[] contenido, String extension) {
        String ruta = eventoId + "/" + UUID.randomUUID() + "." + extension;
        Path destino = resolver(ruta);
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, contenido);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo del legajo " + ruta, e);
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int estado) {
                    if (estado != STATUS_COMMITTED) {
                        borrar(ruta);
                    }
                }
            });
        }
        return ruta;
    }

    public byte[] leer(String ruta) {
        try {
            return Files.readAllBytes(resolver(ruta));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo del legajo " + ruta, e);
        }
    }

    void borrar(String ruta) {
        try {
            Files.deleteIfExists(resolver(ruta));
        } catch (IOException e) {
            log.warn("No se pudo borrar el archivo del legajo {}", ruta, e);
        }
    }

    /** La ruta sale de la base, pero igual se verifica que no se escape del directorio. */
    private Path resolver(String ruta) {
        Path archivo = directorio.resolve(ruta).normalize();
        if (!archivo.startsWith(directorio)) {
            throw new IllegalArgumentException("Ruta fuera del legajo: " + ruta);
        }
        return archivo;
    }
}
