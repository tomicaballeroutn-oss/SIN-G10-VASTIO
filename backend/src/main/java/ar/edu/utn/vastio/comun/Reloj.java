package ar.edu.utn.vastio.comun;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ar.edu.utn.vastio.VastioApplication;

/**
 * Reloj del sistema en la zona de Córdoba. Inyectable para que los tests fijen la hora (p. ej. la jornada de la barra
 * antes de las 06:00).
 */
@Configuration
public class Reloj {

    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of(VastioApplication.ZONA_HORARIA));
    }
}
