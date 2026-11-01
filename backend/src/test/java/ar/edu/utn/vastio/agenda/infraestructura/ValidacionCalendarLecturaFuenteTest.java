package ar.edu.utn.vastio.agenda.infraestructura;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.gson.GsonFactory;

class ValidacionCalendarLecturaFuenteTest {
    @Test void distingueNullJsonDeHoraParticular() throws Exception {
        var nulo=GsonFactory.getDefaultInstance().fromString("{\"particular\":null}",GenericJson.class);
        var hora=GsonFactory.getDefaultInstance().fromString("{\"particular\":\"12:30:00\"}",GenericJson.class);
        assertThat(ValidacionCalendarLecturaTest.textoOpcional(nulo,"particular")).isNull();
        assertThat(ValidacionCalendarLecturaTest.textoOpcional(hora,"particular")).isEqualTo("12:30:00");
    }
}
