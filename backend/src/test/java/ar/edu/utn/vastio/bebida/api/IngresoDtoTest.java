package ar.edu.utn.vastio.bebida.api;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class IngresoDtoTest {
    @Test
    void conservaRemitoYRenglones() {
        var pedido = JsonMapper.builder().build().readValue("""
                {"numeroRemito":" R-001 ","renglones":[{"bebidaId":7,"cantidad":12}]}""",
                IngresoDto.IngresoRequest.class);
        assertThat(pedido.datos().numeroRemito()).isEqualTo("R-001");
        assertThat(pedido.datos().renglones().getFirst().bebidaId()).isEqualTo(7);
        assertThat(pedido.datos().renglones().getFirst().cantidad()).isEqualByComparingTo("12");
    }
}