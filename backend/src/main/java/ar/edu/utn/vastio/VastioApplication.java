package ar.edu.utn.vastio;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class VastioApplication {

    public static final String ZONA_HORARIA = "America/Argentina/Cordoba";

    public static void main(String[] args) {
        // El driver JDBC envía la zona de la JVM a PostgreSQL. En Windows suele ser el alias
        // "America/Buenos_Aires", que la imagen postgres:17 no reconoce y rechaza la conexión.
        TimeZone.setDefault(TimeZone.getTimeZone(ZONA_HORARIA));
        SpringApplication.run(VastioApplication.class, args);
    }
}
