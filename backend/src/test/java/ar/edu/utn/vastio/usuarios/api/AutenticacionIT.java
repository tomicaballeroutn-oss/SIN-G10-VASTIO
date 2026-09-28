package ar.edu.utn.vastio.usuarios.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.comun.errores.Mensajes;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;
import ar.edu.utn.vastio.usuarios.infraestructura.RolRepository;
import ar.edu.utn.vastio.usuarios.infraestructura.UsuarioRepository;

@PruebaDeIntegracion
class AutenticacionIT {

    private static final String CONTRASENA = "una-clave-larga";

    @Autowired
    MockMvc mvc;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    RolRepository roles;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void usuarios() {
        crear("lucia.ferreyra", "Lucía Ferreyra", true, RolCodigo.VENDEDORA, RolCodigo.PLANNER);
        crear("meli.coord", "Melina Sifón", true, RolCodigo.COORDINACION);
        crear("ex.empleada", "Ex Empleada", false, RolCodigo.BARRA);
    }

    // ---------- login ----------

    @Test
    void loginCorrectoDevuelveTokenYCookieDeRefresco() throws Exception {
        mvc.perform(login("lucia.ferreyra", CONTRASENA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenAcceso").value(notNullValue()))
                .andExpect(jsonPath("$.tokenAccesoVence").value(notNullValue()))
                .andExpect(jsonPath("$.minutosExpiracionSesion").value(60))
                .andExpect(jsonPath("$.minutosAvisoExpiracion").value(5))
                .andExpect(jsonPath("$.usuario.nombreCompleto").value("Lucía Ferreyra"))
                .andExpect(jsonPath("$.usuario.roles").value(hasItem("VENDEDORA")))
                .andExpect(jsonPath("$.usuario.roles").value(hasItem("PLANNER")))
                .andExpect(jsonPath("$.usuario.hashContrasena").doesNotExist())
                .andExpect(cookie().exists(AutenticacionController.COOKIE_REFRESCO))
                .andExpect(cookie().httpOnly(AutenticacionController.COOKIE_REFRESCO, true))
                .andExpect(cookie().path(AutenticacionController.COOKIE_REFRESCO, "/api/v1/auth"))
                .andExpect(cookie().sameSite(AutenticacionController.COOKIE_REFRESCO, "Strict"))
                .andExpect(cookie().maxAge(AutenticacionController.COOKIE_REFRESCO, 60 * 60));
    }

    @Test
    void contrasenaIncorrectaDevuelve401LegibleSinCookie() throws Exception {
        mvc.perform(login("lucia.ferreyra", "otra-cosa"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/problem+json")))
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                .andExpect(jsonPath("$.detail").value(Mensajes.CREDENCIALES_INVALIDAS))
                .andExpect(cookie().doesNotExist(AutenticacionController.COOKIE_REFRESCO));
    }

    @Test
    void usuarioInexistenteRespondeIgualQueContrasenaIncorrecta() throws Exception {
        mvc.perform(login("nadie", CONTRASENA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    void usuarioDadoDeBajaNoPuedeIniciarSesion() throws Exception {
        mvc.perform(login("ex.empleada", CONTRASENA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                .andExpect(cookie().doesNotExist(AutenticacionController.COOKIE_REFRESCO));
    }

    @Test
    void camposVaciosDevuelven400ConLosCamposMarcados() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"nombreUsuario\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.errores[?(@.campo == 'nombreUsuario')].mensaje").value(hasItem("Escribí tu usuario.")))
                .andExpect(jsonPath("$.errores[?(@.campo == 'contrasena')].mensaje").value(hasItem("Escribí tu contraseña.")));
    }

    // ---------- refresh y logout ----------

    @Test
    void refreshConCookieValidaRenuevaLaSesion() throws Exception {
        Cookie refresco = loguear("lucia.ferreyra").getResponse().getCookie(AutenticacionController.COOKIE_REFRESCO);

        mvc.perform(post("/api/v1/auth/refresh").cookie(refresco))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenAcceso").value(notNullValue()))
                .andExpect(jsonPath("$.usuario.nombreUsuario").value("lucia.ferreyra"))
                .andExpect(cookie().value(AutenticacionController.COOKIE_REFRESCO, not("")));
    }

    @Test
    void refreshSinCookieDevuelveSesionVencidaYBorraLaCookie() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_VENCIDA"))
                .andExpect(jsonPath("$.detail").value(Mensajes.SESION_VENCIDA))
                .andExpect(cookie().maxAge(AutenticacionController.COOKIE_REFRESCO, 0));
    }

    @Test
    void refreshConTokenAdulteradoDevuelveSesionVencida() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(AutenticacionController.COOKIE_REFRESCO, "no.es.un-jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_VENCIDA"));
    }

    @Test
    void elTokenDeAccesoNoSirveComoRefresco() throws Exception {
        String acceso = tokenAcceso(loguear("lucia.ferreyra"));

        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(AutenticacionController.COOKIE_REFRESCO, acceso)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_VENCIDA"));
    }

    @Test
    void siDanDeBajaAlUsuarioElRefreshFalla() throws Exception {
        crear("temporal", "Temporal", true, RolCodigo.COMPRAS);
        Cookie refresco = loguear("temporal").getResponse().getCookie(AutenticacionController.COOKIE_REFRESCO);
        Usuario temporal = usuarios.findByNombreUsuario("temporal").orElseThrow();
        temporal.darDeBaja();
        usuarios.save(temporal);

        mvc.perform(post("/api/v1/auth/refresh").cookie(refresco))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_VENCIDA"));
    }

    @Test
    void logoutBorraLaCookie() throws Exception {
        mvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(AutenticacionController.COOKIE_REFRESCO, 0));
    }

    // ---------- acceso a endpoints protegidos ----------

    @Test
    void sinTokenUnEndpointProtegidoDevuelve401() throws Exception {
        mvc.perform(get("/api/v1/_sonda/autenticado"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SIN_SESION"));
    }

    @Test
    void conTokenDeAccesoEntra() throws Exception {
        String acceso = tokenAcceso(loguear("lucia.ferreyra"));

        mvc.perform(get("/api/v1/_sonda/autenticado").header(HttpHeaders.AUTHORIZATION, "Bearer " + acceso))
                .andExpect(status().isOk());
    }

    @Test
    void elTokenDeRefrescoNoSirveComoAcceso() throws Exception {
        String refresco = loguear("lucia.ferreyra").getResponse().getCookie(AutenticacionController.COOKIE_REFRESCO).getValue();

        mvc.perform(get("/api/v1/_sonda/autenticado").header(HttpHeaders.AUTHORIZATION, "Bearer " + refresco))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_VENCIDA"));
    }

    @Test
    void elPerfilHabilitadoPuede() throws Exception {
        String acceso = tokenAcceso(loguear("meli.coord"));

        mvc.perform(get("/api/v1/_sonda/direccion").header(HttpHeaders.AUTHORIZATION, "Bearer " + acceso))
                .andExpect(status().isOk());
    }

    @Test
    void otroPerfilRecibe403Legible() throws Exception {
        String acceso = tokenAcceso(loguear("lucia.ferreyra"));

        mvc.perform(get("/api/v1/_sonda/direccion").header(HttpHeaders.AUTHORIZATION, "Bearer " + acceso))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"))
                .andExpect(jsonPath("$.detail").value(Mensajes.SIN_PERMISO));
    }

    // ---------- helpers ----------

    private void crear(String nombreUsuario, String nombre, boolean activo, RolCodigo... codigos) {
        if (usuarios.existsByNombreUsuario(nombreUsuario)) {
            return;
        }
        Usuario usuario = new Usuario(nombre, nombreUsuario, passwordEncoder.encode(CONTRASENA));
        for (RolCodigo codigo : codigos) {
            usuario.asignarRol(roles.findByCodigo(codigo).orElseThrow());
        }
        if (!activo) {
            usuario.darDeBaja();
        }
        usuarios.save(usuario);
    }

    private static org.springframework.test.web.servlet.RequestBuilder login(String usuario, String contrasena) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreUsuario\":\"%s\",\"contrasena\":\"%s\"}".formatted(usuario, contrasena));
    }

    private MvcResult loguear(String usuario) throws Exception {
        return mvc.perform(login(usuario, CONTRASENA)).andExpect(status().isOk()).andReturn();
    }

    private static String tokenAcceso(MvcResult resultado) throws Exception {
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.tokenAcceso");
    }
}
