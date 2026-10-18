package ar.edu.utn.vastio.agenda.aplicacion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import ar.edu.utn.vastio.VastioApplication;

/**
 * Corre {@link RealizacionService} una vez al arrancar (después de los datos de demostración) y después cada minuto.
 * En los tests se apaga ({@code vastio.realizacion.automatica: false}) y se llama al servicio con un reloj fijo.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "vastio.realizacion.automatica", havingValue = "true", matchIfMissing = true)
public class RealizacionProgramada {

    private final RealizacionService realizacion;

    public RealizacionProgramada(RealizacionService realizacion) {
        this.realizacion = realizacion;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        realizacion.ponerAlDia();
    }

    @Scheduled(cron = "0 * * * * *", zone = VastioApplication.ZONA_HORARIA)
    public void cadaMinuto() {
        realizacion.ponerAlDia();
    }
}
