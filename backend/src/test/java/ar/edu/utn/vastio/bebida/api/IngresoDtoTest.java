package ar.edu.utn.vastio.bebida.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class IngresoDtoTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "\"incorrecto\"", "\"1\"", "true", "[]", "{}", "9223372036854775808"})
    void rechazaIdentificadoresQueNoSonEnterosLongSinTruncarlos(String id) {
        assertThatThrownBy(() -> mapper.readValue("{\"proveedorId\":" + id + ",\"renglones\":[]}", IngresoDto.IngresoRequest.class))
                .isInstanceOf(JacksonException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\"proveedorId\":null,"})
    void admiteProveedorOmitidoONulo(String campo) {
        var pedido = mapper.readValue("{" + campo + "\"renglones\":[]}", IngresoDto.IngresoRequest.class);
        assertThat(pedido.proveedorId()).isNull();
        assertThat(pedido.datos().proveedorId()).isNull();
    }

    @Test
    void conservaElIdentificadorEnteroEnLosDatosDelServicio() {
        var pedido = mapper.readValue("{\"proveedorId\":80,\"renglones\":[]}", IngresoDto.IngresoRequest.class);
        assertThat(pedido.datos().proveedorId()).isEqualTo(80L);
    }
}
