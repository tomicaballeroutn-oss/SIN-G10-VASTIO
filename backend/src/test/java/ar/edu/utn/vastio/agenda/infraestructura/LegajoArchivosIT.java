package ar.edu.utn.vastio.agenda.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.support.TransactionTemplate;

import ar.edu.utn.vastio.PruebaDeIntegracion;

/**
 * Archivos del legajo: si la transacción se deshace, el archivo no queda huérfano en disco.
 */
@PruebaDeIntegracion
class LegajoArchivosIT {

    @Autowired
    LegajoArchivos legajo;

    @Autowired
    TransactionTemplate transaccion;

    @Value("${vastio.legajo.directorio}")
    String directorio;

    @Test
    void siLaTransaccionSeConfirmaElArchivoQueda() throws Exception {
        String ruta = transaccion.execute(estado -> legajo.guardar(990001, new byte[] {1, 2, 3}, "pdf"));

        assertThat(legajo.leer(ruta)).containsExactly(1, 2, 3);
        Files.delete(Path.of(directorio, ruta));
    }

    @Test
    void siLaTransaccionSeDeshaceElArchivoSeBorra() {
        String ruta = transaccion.execute(estado -> {
            String guardado = legajo.guardar(990002, new byte[] {1, 2, 3}, "pdf");
            assertThat(Path.of(directorio, guardado)).exists();
            estado.setRollbackOnly();
            return guardado;
        });

        assertThat(Path.of(directorio, ruta)).doesNotExist();
    }

    @Test
    void noLeeFueraDelDirectorioDelLegajo() {
        assertThatThrownBy(() -> legajo.leer("../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
    }
}
