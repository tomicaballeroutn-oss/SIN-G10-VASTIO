package ar.edu.utn.vastio.usuarios.aplicacion;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.TokenService;
import ar.edu.utn.vastio.configuracion.aplicacion.ParametroService;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;
import ar.edu.utn.vastio.usuarios.infraestructura.UsuarioRepository;

class ContrasenaEquivalenciaTest {
    BCryptPasswordEncoder encoder;
    UsuarioRepository usuarios;
    AutenticacionService autenticacion;

    @BeforeEach void datos() {
        encoder = spy(new BCryptPasswordEncoder());
        usuarios = mock(UsuarioRepository.class);
        autenticacion = new AutenticacionService(usuarios, encoder, mock(TokenService.class), mock(ParametroService.class));
    }

    Usuario usuario(String clave) {
        Usuario u = new Usuario("Cuenta", "cuenta", encoder.encode(clave));
        when(usuarios.buscarParaCambiarContrasena(7L)).thenReturn(Optional.of(u));
        clearInvocations(encoder);
        return u;
    }

    @Test void laRepeticionTextualUsaUnaSolaVerificacion() {
        Usuario u = usuario("clave-vigente");
        assertThatThrownBy(() -> autenticacion.cambiarContrasena(7L, "clave-vigente", "clave-vigente"))
                .isInstanceOfSatisfying(ProblemaException.class, e -> assertThat(e.getCodigo()).isEqualTo("CONTRASENA_REPETIDA"));
        verify(encoder, times(1)).matches(anyString(), eq(u.getHashContrasena()));
        verify(encoder, never()).encode(any());
    }

    @ParameterizedTest @ValueSource(strings={"x","á"})
    void unSufijoDeLaActualNoPermiteReutilizarLaCredencial(String letra) {
        String vigente = letra.repeat(letra.equals("á") ? 36 : 72);
        Usuario u = usuario(vigente);
        String hash = u.getHashContrasena();
        String actual = vigente + "sufijo";
        assertThat(actual).isNotEqualTo(vigente);
        assertThatThrownBy(() -> autenticacion.cambiarContrasena(7L, actual, vigente))
                .isInstanceOfSatisfying(ProblemaException.class, e -> assertThat(e.getCodigo()).isEqualTo("CONTRASENA_REPETIDA"));
        verify(encoder, times(2)).matches(anyString(), eq(hash));
        verify(encoder, never()).encode(any());
        assertThat(u.getHashContrasena()).isEqualTo(hash);
    }

    @Test void bcryptNoNormalizaUnicodeYCadenasDistintasPuedenCambiarLaClave() {
        String actual = "clave-café-larga";
        String nueva = "clave-cafe\u0301-larga";
        Usuario u = usuario(actual);
        String hash = u.getHashContrasena();
        autenticacion.cambiarContrasena(7L, actual, nueva);
        verify(encoder, times(2)).matches(anyString(), eq(hash));
        verify(encoder, times(1)).encode(nueva);
        assertThat(new BCryptPasswordEncoder().matches(nueva, u.getHashContrasena())).isTrue();
        assertThat(new BCryptPasswordEncoder().matches(actual, u.getHashContrasena())).isFalse();
    }

    @Test void laActualIncorrectaNoPermiteCambiarNiCodificaUnaNueva() {
        Usuario u = usuario("clave-vigente");
        String hash = u.getHashContrasena();
        assertThatThrownBy(() -> autenticacion.cambiarContrasena(7L, "equivocada", "otra-clave"))
                .isInstanceOfSatisfying(ProblemaException.class, e -> assertThat(e.getCodigo()).isEqualTo("CONTRASENA_ACTUAL_INCORRECTA"));
        verify(encoder, times(1)).matches(anyString(), eq(hash));
        verify(encoder, never()).encode(any());
        assertThat(u.getHashContrasena()).isEqualTo(hash);
    }
}
