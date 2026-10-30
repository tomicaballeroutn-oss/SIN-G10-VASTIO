# UI-03 — Integración de pruebas y política JWT

La funcionalidad y el contrato de cuenta están en [Mi cuenta](sprint-4-mi-cuenta.md).

## Pruebas con @WithMockUser

Crear el fixture persistido cuyo nombre coincide con el `username` de la anotación y aplicar `.with(personas.jwtDeUsuarioSimulado())` a la solicitud MockMvc:

```java
@Test
@WithMockUser(username = "prueba.direccion", roles = "DIRECCION")
void permiteLaOperacionAutorizada() throws Exception {
    personas.de(RolCodigo.DIRECCION);
    mvc.perform(get("/api/v1/_sonda/direccion").with(personas.jwtDeUsuarioSimulado()))
            .andExpect(status().isOk());
}
```

El adaptador existe solo en `src/test`: emite un JWT firmado que atraviesa el decoder y la autorización reales. Conserva los roles simulados sin ampliarlos con los del fixture y toma de la entidad la obligación de cambio. Un principal no JWT sin adaptador continúa denegado; no hay una excepción de seguridad por perfil de pruebas.

## Política de tokens

El access token exige subject numérico positivo y el claim booleano `debeCambiarContrasena`. Un access antiguo sin claim o con claims inválidos recibe 401; un refresh anterior válido consulta el estado vigente. Un token con la marca true sigue restringido después del cambio hasta renovar. El cambio propio no revoca globalmente las sesiones abiertas.

El modelo actual no ofrece un reset que cambie false→true después del primer ingreso. Antes de implementar ese flujo se necesita invalidación o revalidación de tokens: cambiar únicamente la columna no revoca los access ni los refresh ya emitidos.

Una firma inválida recibe 401 con el código genérico existente `SESION_VENCIDA`, también usado para expiración y otros JWT inválidos. Ese código indica que la sesión no es utilizable, no demuestra que la causa sea temporal. Se conserva el contrato de recuperación del cliente; no se exponen detalles criptográficos ni se admite el token rechazado.

## Interacción con Google Calendar

La restricción de primer ingreso se aplica también a las consultas y operaciones de Calendar y al contador de notificaciones. Cambiar la contraseña no habilita el token anterior: el cliente debe renovar la sesión. La renovación conserva los roles y los endpoints Calendar siguen reservados a Dirección y Coordinación, sin ampliar permisos al resto de los perfiles.

`CuentaJwtIT` verifica esta matriz con JWT reales, cambio propio y refresh; la integración Calendar se mantiene desactivada en ese contexto de prueba. Los E2E de cuenta comprueban que una visita inicial a Pendientes de Google Calendar redirige a Mi cuenta sin consultar Calendar ni notificaciones, y que después de renovar se recuperan navegación y consultas autorizadas, sin escrituras externas.

## Alcance de la medición SQL

`CuentaJwtIT` mide sentencias preparadas por Hibernate durante tres solicitudes a una sonda sin acceso a datos. Las estadísticas no cuentan `JdbcTemplate` ni JDBC directo y no prueban cero SQL global, cero consultas en endpoints de negocio ni ausencia de saturación bajo carga. La regla de autorización por marca y el decoder no tienen acceso a repositorios o JDBC; login y refresh sí leen el estado persistido.
