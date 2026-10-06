package ar.edu.utn.vastio.notificaciones.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.notificaciones.aplicacion.NotificacionService;
import ar.edu.utn.vastio.notificaciones.dominio.TipoNotificacion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Consultar notificaciones (UI-22): cada persona ve y marca solo las suyas.
 */
@PruebaDeIntegracion
@Transactional
class NotificacionIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    Personas personas;

    @Autowired
    NotificacionService notificaciones;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    Usuario gabriela;
    Usuario nicolas;

    @BeforeEach
    void personas() {
        gabriela = personas.usuario("gabriela.avisos", "Gabriela Paz", RolCodigo.ADMINISTRACION);
        nicolas = personas.usuario("nicolas.avisos", "Nicolás Herrera", RolCodigo.COMPRAS);
    }

    @Test
    void listaLasPropiasMasRecientesPrimeroConElContador() throws Exception {
        long primera = aviso(TipoNotificacion.EVENTO_NUEVO, "Nueva pre-reserva: A", gabriela);
        long segunda = aviso(TipoNotificacion.SENA, "Seña registrada: B", gabriela, nicolas);
        aviso(TipoNotificacion.CONFIRMACION, "Evento confirmado: C", nicolas);
        jdbc.update("UPDATE notificacion SET fecha_hora = fecha_hora - interval '1 hour' WHERE notificacion_id = ?", primera);

        listar(gabriela, false, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificaciones[*].id", contains((int) segunda, (int) primera)))
                .andExpect(jsonPath("$.notificaciones[0].tipo").value("SENA"))
                .andExpect(jsonPath("$.notificaciones[0].mensaje").value("Seña registrada: B"))
                .andExpect(jsonPath("$.notificaciones[0].leida").value(false))
                .andExpect(jsonPath("$.hayMas").value(false))
                .andExpect(jsonPath("$.sinLeer").value(2));
        mvc.perform(get("/api/v1/notificaciones/sin-leer").header(HttpHeaders.AUTHORIZATION, personas.bearer(gabriela)))
                .andExpect(jsonPath("$.cantidad").value(2));
    }

    @Test
    void marcarUnaLaSacaDeSinLeerSoloParaQuienLaLee() throws Exception {
        long aviso = aviso(TipoNotificacion.SENA, "Seña registrada: B", gabriela, nicolas);

        mvc.perform(post("/api/v1/notificaciones/{id}/lectura", aviso).header(HttpHeaders.AUTHORIZATION, personas.bearer(gabriela)))
                .andExpect(status().isNoContent());

        listar(gabriela, true, 0).andExpect(jsonPath("$.notificaciones", hasSize(0))).andExpect(jsonPath("$.sinLeer").value(0));
        listar(gabriela, false, 0).andExpect(jsonPath("$.notificaciones[0].leida").value(true));
        listar(nicolas, true, 0).andExpect(jsonPath("$.notificaciones", hasSize(1)));
        assertThat(jdbc.queryForObject("SELECT fecha_lectura IS NOT NULL FROM notificacion_destinatario WHERE notificacion_id = ? AND usuario_id = ?",
                Boolean.class, aviso, gabriela.getId())).isTrue();
    }

    @Test
    void nadieMarcaUnAvisoQueNoEsSuyo() throws Exception {
        long ajeno = aviso(TipoNotificacion.CONFIRMACION, "Evento confirmado: C", nicolas);

        mvc.perform(post("/api/v1/notificaciones/{id}/lectura", ajeno).header(HttpHeaders.AUTHORIZATION, personas.bearer(gabriela)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NOTIFICACION_INEXISTENTE"));
        listar(nicolas, true, 0).andExpect(jsonPath("$.notificaciones", hasSize(1)));
    }

    @Test
    void marcarTodasDejaElContadorEnCero() throws Exception {
        aviso(TipoNotificacion.EVENTO_NUEVO, "A", gabriela);
        aviso(TipoNotificacion.SENA, "B", gabriela, nicolas);

        mvc.perform(post("/api/v1/notificaciones/lectura").header(HttpHeaders.AUTHORIZATION, personas.bearer(gabriela)))
                .andExpect(status().isNoContent());
        entityManager.clear();

        listar(gabriela, false, 0).andExpect(jsonPath("$.sinLeer").value(0));
        listar(nicolas, false, 0).andExpect(jsonPath("$.sinLeer").value(1));
    }

    @Test
    void vienenDeA20() throws Exception {
        for (int i = 0; i < 21; i++) {
            aviso(TipoNotificacion.MODIFICACION, "Evento modificado " + i, gabriela);
        }

        listar(gabriela, false, 0).andExpect(jsonPath("$.notificaciones", hasSize(20))).andExpect(jsonPath("$.hayMas").value(true));
        listar(gabriela, false, 1).andExpect(jsonPath("$.notificaciones", hasSize(1))).andExpect(jsonPath("$.hayMas").value(false))
                .andExpect(jsonPath("$.pagina").value(1));
    }

    @ParameterizedTest
    @EnumSource(RolCodigo.class)
    void todosLosPerfilesConsultanSusNotificaciones(RolCodigo rol) throws Exception {
        listar(personas.de(rol), false, 0).andExpect(status().isOk());
    }

    @Test
    void sinSesionNoHayNotificaciones() throws Exception {
        mvc.perform(get("/api/v1/notificaciones")).andExpect(status().isUnauthorized());
    }

    private long aviso(TipoNotificacion tipo, String mensaje, Usuario... destinatarios) {
        var guardada = notificaciones.notificar(null, tipo, mensaje, null, List.of(),
                Arrays.stream(destinatarios).map(Usuario::getId).toList());
        entityManager.flush();
        return guardada.getId();
    }

    private ResultActions listar(Usuario quien, boolean soloSinLeer, int pagina) throws Exception {
        return mvc.perform(get("/api/v1/notificaciones").param("soloSinLeer", String.valueOf(soloSinLeer))
                .param("pagina", String.valueOf(pagina)).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)));
    }
}
