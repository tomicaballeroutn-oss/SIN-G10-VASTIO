package ar.edu.utn.vastio.agenda.aplicacion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Solo vacía la cola LOCAL. Nunca publica, actualiza ni retira eventos en Google. */
@Configuration(proxyBeanMethods=false)
@EnableScheduling
@ConditionalOnProperty(name="vastio.calendar.seguimiento-programado",havingValue="true",matchIfMissing=true)
public class SeguimientoCalendarProgramado {
    private final ProcesadorSeguimientoCalendar procesador;
    public SeguimientoCalendarProgramado(ProcesadorSeguimientoCalendar procesador) { this.procesador=procesador; }
    @Scheduled(fixedDelay=5000,initialDelay=5000)
    public void revisar() { procesador.procesarPendientes(); }
}
