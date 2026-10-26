package ar.edu.utn.vastio.reportes.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.Escenario;
import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

@PruebaDeIntegracion
@Transactional
class ReporteOcupacionIT {

    private static final LocalDate DESDE = LocalDate.of(2042, 2, 1);

    @Autowired
    MockMvc mvc;

    @Autowired
    Personas personas;

    @Autowired
    Escenario escenario;

    @Autowired
    JdbcTemplate jdbc;

    Usuario lucia;
    Usuario sofia;
    Usuario ana;
    Usuario direccion;

    @BeforeEach
    void usuarios() {
        lucia = personas.usuario("reporte.lucia", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        sofia = personas.usuario("reporte.sofia", "Sofía Méndez", RolCodigo.VENDEDORA);
        ana = personas.usuario("reporte.ana", "Ana Sosa", RolCodigo.PLANNER);
        direccion = personas.de(RolCodigo.DIRECCION);
    }

    @Test
    void incluyeEstadosQueOcupanLaUnidadYExcluyePrereservaLiberadoYCancelado() throws Exception {
        List<String> incluidos = List.of("SENADO", "CONTRATADO", "CONFIRMADO", "EN_CURSO", "REALIZADO", "CERRADO");
        int indice = 0;
        for (String estado : incluidos) {
            Usuario planner = List.of("CONFIRMADO", "EN_CURSO", "REALIZADO", "CERRADO").contains(estado) ? ana : null;
            escenario.evento("club", DESDE.plusDays(indice), "mediodia", estado, lucia, planner,
                    "Incluido " + estado);
            indice++;
        }
        for (String estado : List.of("PRE_RESERVA", "LIBERADA", "CANCELADO")) {
            escenario.evento("club", DESDE.plusDays(indice), "mediodia", estado, lucia, "Excluido " + estado);
            indice++;
        }

        consultar(RolCodigo.DIRECCION, DESDE, DESDE.plusDays(8), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.unidadesOcupadas").value(6))
                .andExpect(jsonPath("$.resumen.eventosRealizados").value(2))
                .andExpect(jsonPath("$.porVendedoraYTipo.length()").value(1))
                .andExpect(jsonPath("$.porVendedoraYTipo[0].eventosIncluidos").value(6));
    }

    @Test
    void aplicaFiltrosDeSalonTipoVendedoraYFechasInclusivas() throws Exception {
        LocalDate fecha = DESDE.plusDays(10);
        escenario.evento("club", fecha, "mediodia", "SENADO", lucia, "Evento Club");
        escenario.evento("avril", fecha, "mediodia", "SENADO", sofia, "Evento Avril");
        escenario.evento("club", fecha.plusDays(1), "mediodia", "SENADO", lucia, "Evento Fuera");
        short clubId = salonId("club");

        consultar(RolCodigo.COORDINACION, fecha, fecha, "&salonId=" + clubId + "&tipoEventoId=2&vendedoraId=" + lucia.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.unidadesOcupadas").value(1))
                .andExpect(jsonPath("$.porSalon.length()").value(1))
                .andExpect(jsonPath("$.porSalon[0].salon").value("Club de Campo"))
                .andExpect(jsonPath("$.porVendedoraYTipo[0].vendedora").value("Lucía Ferreyra"))
                .andExpect(jsonPath("$.porVendedoraYTipo[0].tipoEvento").value("Quince"));
    }

    @Test
    void regimenFiltraLosEventosDeCadaTemporada() throws Exception {
        LocalDate septiembre = LocalDate.of(2042, 9, 30);
        LocalDate octubre = LocalDate.of(2042, 10, 1);
        escenario.evento("club", septiembre, "mediodia", "SENADO", lucia, "Evento temporada baja");
        escenario.evento("club", octubre, "mediodia", "REALIZADO", lucia, ana, "Evento temporada alta");
        short clubId = salonId("club");
        consultar(RolCodigo.DIRECCION, septiembre, octubre, "&salonId=" + clubId + "&regimen=ALTA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.unidadesOcupadas").value(1))
                .andExpect(jsonPath("$.porSalon[0].unidadesDisponibles").value(2))
                .andExpect(jsonPath("$.porSalon[0].ocupacion").value(50.0));

        consultar(RolCodigo.DIRECCION, septiembre, octubre, "&salonId=" + clubId + "&regimen=BAJA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.unidadesOcupadas").value(1))
                .andExpect(jsonPath("$.porSalon[0].unidadesDisponibles").value(2))
                .andExpect(jsonPath("$.porSalon[0].ocupacion").value(50.0));
    }

    @Test
    void endpointSoloPermiteDireccionYCoordinacion() throws Exception {
        for (RolCodigo rol : List.of(RolCodigo.DIRECCION, RolCodigo.COORDINACION)) {
            mvc.perform(get(url(DESDE, DESDE)).header(HttpHeaders.AUTHORIZATION, personas.bearer(rol)))
                    .andExpect(status().isOk());
        }
        for (RolCodigo rol : List.of(RolCodigo.ADMINISTRACION, RolCodigo.COMPRAS, RolCodigo.VENDEDORA,
                RolCodigo.PLANNER, RolCodigo.BARRA, RolCodigo.COCINA)) {
            mvc.perform(get(url(DESDE, DESDE)).header(HttpHeaders.AUTHORIZATION, personas.bearer(rol)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(url(DESDE, DESDE))).andExpect(status().isUnauthorized());
    }

    @Test
    void validaRangoYRegimenAntesDeConsultar() throws Exception {
        consultar(RolCodigo.DIRECCION, DESDE.plusDays(1), DESDE, "")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PERIODO_INVALIDO"));
        consultar(RolCodigo.DIRECCION, DESDE, DESDE.plusDays(3661), "")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PERIODO_EXTENSO"));
        consultar(RolCodigo.DIRECCION, DESDE, DESDE, "&regimen=MEDIA")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("REGIMEN_INVALIDO"));
    }

    @Test
    void bloqueoHistoricoNoRestaUnaUnidadQueLuegoFueOcupadaInclusoAlFiltrarOtraVendedora() throws Exception {
        LocalDate fecha = DESDE.plusDays(20);
        long bloqueo = escenario.bloqueo("club", fecha, "mediodia", direccion, "Levantado antes del evento");
        jdbc.update("UPDATE bloqueo SET activo=false, fecha_registro=?, fecha_desbloqueo=?, usuario_desbloqueo_id=? WHERE bloqueo_id=?",
                fecha.atTime(8, 0).atOffset(java.time.ZoneOffset.ofHours(-3)),
                fecha.atTime(9, 0).atOffset(java.time.ZoneOffset.ofHours(-3)), direccion.getId(), bloqueo);
        escenario.evento("club", fecha, "mediodia", "SENADO", lucia, "Después del desbloqueo");
        escenario.evento("club", fecha, "noche", "SENADO", sofia, "Otra vendedora");
        String salon = "&salonId=" + salonId("club");
        consultar(RolCodigo.DIRECCION, fecha, fecha, salon)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porSalon[0].unidadesDisponibles").value(2))
                .andExpect(jsonPath("$.porSalon[0].ocupacion").value(100.0));
        consultar(RolCodigo.DIRECCION, fecha, fecha, salon + "&vendedoraId=" + sofia.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porSalon[0].unidadesDisponibles").value(2))
                .andExpect(jsonPath("$.porSalon[0].ocupacion").value(50.0));
        consultar(RolCodigo.DIRECCION, fecha, fecha, salon + "&tipoEventoId=1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porSalon[0].unidadesDisponibles").value(2))
                .andExpect(jsonPath("$.porSalon[0].ocupacion").value(0.0));
    }

    @Test
    void bloqueosRepetidosSeCuentanUnaVezYDisponibilidadCeroNoDividePorCero() throws Exception {
        LocalDate fecha = DESDE.plusDays(21);
        escenario.bloqueo("club", fecha, "mediodia", direccion, "Primero");
        escenario.bloqueo("club", fecha, "mediodia", direccion, "Segundo histórico");
        escenario.bloqueo("club", fecha, "noche", direccion, "Tercero");
        consultar(RolCodigo.DIRECCION, fecha, fecha, "&salonId=" + salonId("club"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porSalon[0].unidadesOcupadas").value(0))
                .andExpect(jsonPath("$.porSalon[0].unidadesDisponibles").value(0))
                .andExpect(jsonPath("$.porSalon[0].ocupacion").value(0.0))
                .andExpect(jsonPath("$.resumen.ocupacionPromedio").value(0.0));
    }

    @Test
    void bloqueosFueraDeVigenciaNoRestanYLaTemporadaUsaLaFechaDeLaUnidad() throws Exception {
        LocalDate septiembre = LocalDate.of(2042, 9, 30);
        LocalDate octubre = septiembre.plusDays(1);
        long pasado = escenario.bloqueo("club", septiembre, "mediodia", direccion, "Terminado el día anterior");
        jdbc.update("UPDATE bloqueo SET activo=false, fecha_desbloqueo=?, usuario_desbloqueo_id=? WHERE bloqueo_id=?",
                septiembre.minusDays(1).atStartOfDay().atOffset(java.time.ZoneOffset.ofHours(-3)), direccion.getId(), pasado);
        long posterior = escenario.bloqueo("club", septiembre, "noche", direccion, "Registrado después");
        jdbc.update("UPDATE bloqueo SET fecha_registro=? WHERE bloqueo_id=?",
                octubre.atStartOfDay().atOffset(java.time.ZoneOffset.ofHours(-3)), posterior);
        escenario.bloqueo("club", octubre, "noche", direccion, "Vigente en alta");
        String salon = "&salonId=" + salonId("club");
        consultar(RolCodigo.DIRECCION, septiembre, octubre, salon + "&regimen=BAJA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porSalon[0].unidadesDisponibles").value(2));
        consultar(RolCodigo.DIRECCION, septiembre, octubre, salon + "&regimen=ALTA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porSalon[0].unidadesDisponibles").value(1));
    }

    @Test
    void reprogramadoSeCuentaSoloEnSuUnidadActualYCanceladoNoImpideReutilizarla() throws Exception {
        LocalDate origen = DESDE.plusDays(23);
        LocalDate destino = origen.plusDays(1);
        long evento = escenario.evento("club", origen, "mediodia", "SENADO", lucia, "Reprogramado");
        long nuevaUnidad = escenario.unidad("avril", destino, "noche");
        jdbc.update("UPDATE evento SET unidad_id=? WHERE evento_id=?", nuevaUnidad, evento);
        escenario.evento("club", origen, "mediodia", "CANCELADO", sofia, "Cancelado");
        escenario.evento("club", origen, "mediodia", "SENADO", lucia, "Reemplazo");
        consultar(RolCodigo.DIRECCION, origen, origen, "&salonId=" + salonId("club"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.unidadesOcupadas").value(1))
                .andExpect(jsonPath("$.porSalon[0].ocupacion").value(50.0));
        consultar(RolCodigo.DIRECCION, destino, destino, "&salonId=" + salonId("avril"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.unidadesOcupadas").value(1));
    }

    @Test
    void catalogoFijoDosTurnosYRespuestaSinDatosEconomicosNiDelCliente() throws Exception {
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForList("SELECT codigo FROM turno ORDER BY codigo", String.class))
                .containsExactly("mediodia", "noche");
        escenario.evento("club", DESDE, "noche", "SENADO", lucia, "Nombre privado");
        var contenido = consultar(RolCodigo.DIRECCION, DESDE, DESDE, "")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(contenido)
                .doesNotContain("importe", "moneda", "cliente", "30111222", "Nombre privado", "contrasena");
    }

    private ResultActions consultar(RolCodigo rol, LocalDate desde, LocalDate hasta, String filtros)
            throws Exception {
        return mvc.perform(get(url(desde, hasta) + filtros)
                .header(HttpHeaders.AUTHORIZATION, personas.bearer(rol)));
    }

    private static String url(LocalDate desde, LocalDate hasta) {
        return "/api/v1/reportes/ocupacion?desde=" + desde + "&hasta=" + hasta;
    }

    private short salonId(String codigo) {
        return jdbc.queryForObject("SELECT salon_id FROM salon WHERE codigo = ?", Short.class, codigo);
    }
}
