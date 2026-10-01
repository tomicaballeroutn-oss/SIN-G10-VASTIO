package ar.edu.utn.vastio.configuracion.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;

/**
 * Configurar parámetros del sistema (UI-06). Cada test deshace sus cambios.
 */
@PruebaDeIntegracion
@Transactional
class CatalogoIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    Personas personas;

    // ---------- lectura ----------

    @Test
    void cualquierPerfilConSesionLeeLosCatalogos() throws Exception {
        String vendedora = personas.bearer(RolCodigo.VENDEDORA);

        leer("/salones", vendedora)
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].codigo").value("avril"))
                .andExpect(jsonPath("$[2].nombre").value("Santa Bárbara"));
        leer("/turnos", vendedora)
                .andExpect(jsonPath("$[0].codigo").value("mediodia"))
                .andExpect(jsonPath("$[1].horaInicio").value("20:00:00"))
                .andExpect(jsonPath("$[1].cruzaMedianoche").value(true));
        leer("/tipos-evento", vendedora).andExpect(jsonPath("$[*].nombre", hasItem("Egresados")));
        leer("/segmentos-asistencia", vendedora).andExpect(jsonPath("$", hasSize(3)));
        leer("/categorias-servicio", vendedora).andExpect(jsonPath("$[0].nombre").value("Recepción"));
        leer("/motivos", vendedora).andExpect(jsonPath("$[*].ambito", hasItem("BLOQUEO")));
        leer("/parametros", vendedora)
                .andExpect(jsonPath("$[?(@.clave == 'HORIZONTE_COCINA_DIAS')].maximo").value(hasItem(90)));
    }

    @Test
    void sinSesionNoSeLee() throws Exception {
        mvc.perform(get("/api/v1/salones"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SIN_SESION"));
    }

    // ---------- salones y turnos ----------

    @Test
    void administracionEditaUnSalonSinTocarSuCodigo() throws Exception {
        escribir(put("/api/v1/salones/1"), RolCodigo.ADMINISTRACION, """
                {"nombre":"Avril Eventos","capacidad":350,"activo":true}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Avril Eventos"))
                .andExpect(jsonPath("$.capacidad").value(350))
                .andExpect(jsonPath("$.codigo").value("avril"));
    }

    @Test
    void unSalonNoPuedeLlamarseIgualQueOtro() throws Exception {
        escribir(put("/api/v1/salones/2"), RolCodigo.ADMINISTRACION, """
                {"nombre":"avril","activo":true}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NOMBRE_REPETIDO"))
                .andExpect(jsonPath("$.detail").value("Ya hay un salón con ese nombre."));
    }

    @Test
    void laCapacidadTieneQueSerPositiva() throws Exception {
        escribir(put("/api/v1/salones/1"), RolCodigo.ADMINISTRACION, """
                {"nombre":"Avril","capacidad":0,"activo":true}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("capacidad"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("La capacidad tiene que ser mayor a 0."));
    }

    @Test
    void elHorarioDeUnTurnoSeCambiaYSeCalculaSiCruzaLaMedianoche() throws Exception {
        escribir(put("/api/v1/turnos/1"), RolCodigo.COORDINACION, """
                {"nombre":"Mediodía","horaInicio":"11:30","horaFin":"17:00"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horaInicio").value("11:30:00"))
                .andExpect(jsonPath("$.cruzaMedianoche").value(false));
        escribir(put("/api/v1/turnos/1"), RolCodigo.COORDINACION, """
                {"nombre":"Mediodía","horaInicio":"13:00","horaFin":"01:00"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cruzaMedianoche").value(true));
    }

    @Test
    void unTurnoNoPuedeEmpezarYTerminarALaMismaHora() throws Exception {
        escribir(put("/api/v1/turnos/2"), RolCodigo.ADMINISTRACION, """
                {"nombre":"Noche","horaInicio":"20:00","horaFin":"20:00"}""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("TURNO_SIN_DURACION"));
    }

    // ---------- catálogos con alta ----------

    @Test
    void seAgregaUnTipoDeEventoYNoSeRepiteElNombre() throws Exception {
        escribir(post("/api/v1/tipos-evento"), RolCodigo.ADMINISTRACION, """
                {"nombre":"Bautismo","usaSegmentos":false}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Bautismo"))
                .andExpect(jsonPath("$.activo").value(true));
        escribir(post("/api/v1/tipos-evento"), RolCodigo.ADMINISTRACION, """
                {"nombre":"  quince ","usaSegmentos":false}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya hay un tipo de evento con ese nombre."));
    }

    @Test
    void unTipoDadoDeBajaSigueEnLaListaComoInactivo() throws Exception {
        escribir(put("/api/v1/tipos-evento/5"), RolCodigo.DIRECCION, """
                {"nombre":"Cumpleaños","usaSegmentos":false,"activo":false}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        leer("/tipos-evento", personas.bearer(RolCodigo.DIRECCION))
                .andExpect(jsonPath("$[?(@.nombre == 'Cumpleaños')].activo").value(hasItem(false)));
    }

    @Test
    void seAgregaYSeEditaUnSegmento() throws Exception {
        escribir(post("/api/v1/segmentos-asistencia"), RolCodigo.ADMINISTRACION, """
                {"nombre":"Invitados de la promo"}""")
                .andExpect(status().isCreated());
        escribir(put("/api/v1/segmentos-asistencia/3"), RolCodigo.ADMINISTRACION, """
                {"nombre":"Venta en puerta","activo":false}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void seAgregaUnaCategoriaYSeCambiaSiEsRequeridaParaConfirmar() throws Exception {
        escribir(post("/api/v1/categorias-servicio"), RolCodigo.ADMINISTRACION, """
                {"nombre":"Ambientación","orden":11,"visibleEnCocina":false,"requeridaParaConfirmar":false}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orden").value(11));
        escribir(put("/api/v1/categorias-servicio/1"), RolCodigo.ADMINISTRACION, """
                {"nombre":"Recepción","orden":1,"visibleEnCocina":true,"requeridaParaConfirmar":true,"activo":true}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requeridaParaConfirmar").value(true));
        escribir(post("/api/v1/categorias-servicio"), RolCodigo.ADMINISTRACION, """
                {"nombre":"Sin orden"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("orden"));
    }

    @Test
    void elNombreDeUnMotivoEsUnicoDentroDeSuAmbito() throws Exception {
        // «Otro» existe para cancelación, reprogramación y ajuste, pero no para bloqueo.
        escribir(post("/api/v1/motivos"), RolCodigo.ADMINISTRACION, """
                {"ambito":"BLOQUEO","nombre":"Otro"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ambito").value("BLOQUEO"));
        escribir(post("/api/v1/motivos"), RolCodigo.ADMINISTRACION, """
                {"ambito":"CANCELACION","nombre":"otro"}""")
                .andExpect(status().isConflict());
        escribir(post("/api/v1/motivos"), RolCodigo.ADMINISTRACION, """
                {"nombre":"Sin ámbito"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("ambito"));
    }

    // ---------- parámetros generales ----------

    @Test
    void seCambiaElHorizonteDeCocinaDentroDelRango() throws Exception {
        escribir(put("/api/v1/parametros/HORIZONTE_COCINA_DIAS"), RolCodigo.ADMINISTRACION, """
                {"valor":" 30 "}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value("30"));
        escribir(put("/api/v1/parametros/HORIZONTE_COCINA_DIAS"), RolCodigo.ADMINISTRACION, """
                {"valor":"0"}""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Usá un valor entre 1 y 90."));
        escribir(put("/api/v1/parametros/HORIZONTE_COCINA_DIAS"), RolCodigo.ADMINISTRACION, """
                {"valor":"quince"}""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Escribí un número entero."));
    }

    @Test
    void elAvisoDeSesionTieneQueLlegarAntesDeQueVenza() throws Exception {
        escribir(put("/api/v1/parametros/MINUTOS_AVISO_EXPIRACION"), RolCodigo.ADMINISTRACION, """
                {"valor":"60"}""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_FUERA_DE_RANGO"));
        escribir(put("/api/v1/parametros/MINUTOS_EXPIRACION_SESION"), RolCodigo.ADMINISTRACION, """
                {"valor":"5"}""")
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void unParametroDesconocidoDevuelve404() throws Exception {
        escribir(put("/api/v1/parametros/NO_EXISTE"), RolCodigo.ADMINISTRACION, """
                {"valor":"1"}""")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INEXISTENTE"));
    }

    // ---------- autorización ----------

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"DIRECCION", "COORDINACION", "ADMINISTRACION"})
    void configuranDireccionCoordinacionYAdministracion(RolCodigo rol) throws Exception {
        escribir(put("/api/v1/salones/3"), rol, """
                {"nombre":"Santa Bárbara","capacidad":200,"activo":true}""")
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"VENDEDORA", "PLANNER", "COMPRAS", "BARRA", "COCINA"})
    void elRestoDeLosPerfilesNoConfigura(RolCodigo rol) throws Exception {
        escribir(put("/api/v1/salones/3"), rol, """
                {"nombre":"Santa Bárbara","activo":true}""")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        escribir(post("/api/v1/tipos-evento"), rol, """
                {"nombre":"Bautismo","usaSegmentos":false}""")
                .andExpect(status().isForbidden());
        escribir(put("/api/v1/parametros/HORIZONTE_COCINA_DIAS"), rol, """
                {"valor":"20"}""")
                .andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private ResultActions leer(String ruta, String bearer) throws Exception {
        return mvc.perform(get("/api/v1" + ruta).header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk());
    }

    private ResultActions escribir(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pedido,
            RolCodigo rol, String json) throws Exception {
        return mvc.perform(pedido.header(HttpHeaders.AUTHORIZATION, personas.bearer(rol))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
