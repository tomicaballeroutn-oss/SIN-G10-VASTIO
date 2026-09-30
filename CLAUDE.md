# Vastio — guía para Claude Code

Proyecto Integrador de Seminario Integrador (UTN FRC, Grupo 10, 3K2, 2026). Sistema web interno para un complejo de tres salones de eventos. **Un solo sistema con dos módulos** sobre una base común:

- **Módulo A — Agenda de eventos:** qué se vendió, para cuándo y con qué servicios.
- **Módulo B — Control de existencias de bebida:** qué consumió cada evento y cuánto costó.

Los une la entidad **Evento**. El objetivo del negocio es **conocer costo y margen, no vigilar al personal**.

## Antes de escribir código, leé

| Documento | Para qué |
|---|---|
| `docs/contexto-del-negocio.md` | Qué problema resolvemos y reglas del circuito |
| `docs/der/diccionario-de-datos.md` | Modelo de datos, reglas de integridad (sección 2) |
| `docs/maquina-de-estados.md` | Estados del Evento y de la orden de preparación, quién dispara cada transición |
| `docs/perfiles-y-permisos.md` | Matriz historia × perfil y reglas especiales |
| `design/design-system/README.md` | Tono, color, tipografía, accesibilidad |
| `design/pantallas/UIxx-*.dc.html` | Prototipo de cada pantalla (referencia visual y de contenido) |

## Forma de trabajo

1. **Plan primero.** Para cada historia, presentá el plan (migración si hace falta, entidades, endpoints, reglas, tests, componentes) y **esperá aprobación** antes de escribir código.
2. Una historia por rama, rama corta, PR a `main` revisado por otra persona del equipo. Nombre: `<nombre>/<épica>-<historia>` (ej.: `nehuen/A2-registrar-prereserva`). Commits: `tipo(módulo): descripción` en español (`feat(agenda): registrar pre-reserva`).
3. Si un documento contradice a otro o falta un dato, **preguntá**; no inventes reglas de negocio.
4. Nunca modifiques una migración ya fusionada en `main`: agregá una nueva (`V3__...`).

## Stack

- **Backend:** Java 25, Spring Boot (última versión estable compatible con Java 25), Maven wrapper. Monolito modular. Spring Data JPA/Hibernate, Flyway, Spring Security + JWT, springdoc-openapi.
- **Base:** PostgreSQL 17.
- **Frontend:** React 18 + TypeScript + Vite, React Router. PWA responsive. **React 18, no 19**: el sistema de diseño está hecho para 18.
- **Tests:** JUnit 5, Mockito, Spring Boot Test, Testcontainers; Vitest + React Testing Library; Playwright para punta a punta.
- **Infra:** Docker + Docker Compose; GitHub Actions.
- **No usar:** microservicios, Kubernetes, mensajería, Redis, GraphQL, Keycloak, apps nativas, Tailwind (los estilos vienen del sistema de diseño).

## Estructura

```
backend/   Spring Boot. Paquete base ar.edu.utn.vastio
  ├─ usuarios/        login, usuarios, perfiles
  ├─ configuracion/   parámetros, salones, turnos, tipos, categorías, motivos
  ├─ agenda/          evento, unidad, bloqueo, seña, contrato, reprogramación, asistencia
  ├─ bebida/          catálogo, ubicaciones, stock, movimientos, entregas, orden, cierre
  ├─ notificaciones/
  ├─ reportes/
  └─ comun/           errores, seguridad, auditoría, utilidades
     Cada módulo: api (controllers, DTO) · aplicacion (servicios, casos de uso) · dominio (entidades, reglas) · infraestructura (repositorios)
     Un módulo no accede a los repositorios de otro: usa su servicio.
frontend/  Vite. src/ds (sistema de diseño portado), src/features/<módulo>, src/api
docs/      Documentación funcional
design/    Sistema de diseño y prototipos originales (solo referencia, no se compila)
```

## Convenciones de código

- Dominio en español, técnica en inglés: `EventoService`, `EventoRepository`, `registrarSena()`, `PreReservaRequest`.
- Base en `snake_case`; tablas y columnas exactamente como en el diccionario de datos.
- API REST bajo `/api/v1`, recursos en plural y en español (`/api/v1/eventos/{id}/sena`). DTO propios; nunca exponer entidades JPA.
- Estados y tipos fijos como `enum` Java mapeados a `varchar` (`@Enumerated(EnumType.STRING)`), con los mismos valores que los `CHECK` de la base.
- Importes: `BigDecimal`, pesos argentinos. Cantidades de bebida: `BigDecimal` en **botellas**; la pantalla convierte a cajones con `unidades_por_bulto`.
- Fechas y horas: `OffsetDateTime` / `timestamptz`; zona `America/Argentina/Cordoba`.

## Reglas que no se negocian

1. **Exclusividad:** un solo evento activo por salón + fecha + turno. La garantiza el índice único parcial `ux_evento_unidad_activa`; el servicio además toma `SELECT … FOR UPDATE` sobre `unidad_comercializable` antes de crear, reprogramar o bloquear. Traducí la violación a un 409 con el mensaje «Esa fecha ya está tomada. Elegí otro salón, otra fecha u otro turno.»
2. **Fecha operativa:** `unidad_comercializable.fecha` es el día en que empieza la jornada. Los movimientos de bebida se imputan por `evento_id`, **nunca por fecha**.
3. **Solo inserción:** `cambio_estado_evento`, `modificacion_evento`, `reprogramacion` y `movimiento_stock` no se actualizan ni se borran. Las correcciones de stock son asientos `AJUSTE` con `movimiento_corregido_id`.
4. **Toda transición de estado** pasa por un único servicio de la máquina de estados, que valida la condición, escribe `cambio_estado_evento` y dispara las notificaciones.
5. **Saldo negativo permitido:** `stock_ubicacion` puede quedar negativo; se acepta el movimiento y se genera una notificación `ALERTA_STOCK` a Compras y Administración. Nunca se rechaza un retiro por falta de saldo teórico.
6. **Operación a ciegas:** a la encargada de barra el backend no le envía saldos del depósito ni cantidades esperadas al cierre.
7. **Costo congelado:** al cerrar el evento se escribe `consumo_evento` con el precio de referencia vigente; no se recalcula después.
8. **Datos económicos** filtrados en el backend según perfil (ver `docs/perfiles-y-permisos.md`). La autorización se verifica siempre en el backend, con `@PreAuthorize` y, para «solo sus eventos», en el servicio.
9. **Auditoría:** toda acción que compromete o libera una unidad y todo movimiento de mercadería guarda usuario y momento, y la pantalla lo muestra con el componente `Actor`.

## Frontend

- Usá **solo** componentes de `src/ds`. Si falta uno, se agrega al sistema de diseño, no se improvisa en la pantalla.
- Textos en voseo rioplatense, en sentence case, con palabras del negocio (ver `design/design-system/README.md`). Botones con verbo + objeto («Registrar seña»), nunca «Aceptar» ni «OK». Sin emoji.
- Mobile first. Pantallas de barra y depósito con controles `lg` (52 px) y tema oscuro disponible.
- Estado de la agenda: por relleno + ícono + palabra, nunca solo por color. Colores de salón desde los tokens según `salon.codigo`.
- El token de acceso vive en memoria (no en `localStorage`); el de refresco en cookie `HttpOnly`.
- La lectura de código de barras usa la cámara del celular y requiere HTTPS; siempre ofrecer carga manual.

## Comandos (Windows, PowerShell)

```powershell
copy .env.example .env                  # una vez: completar VASTIO_ADMIN_CONTRASENA (usuario inicial «direccion»)
                                        # opcional: VASTIO_DEMO_CONTRASENA carga un usuario por perfil y eventos de ejemplo
docker compose up -d db                 # base de desarrollo
cd backend; .\mvnw spring-boot:run      # backend en http://localhost:8080 · Swagger UI en /api/docs
cd backend; .\mvnw verify               # tests (necesita Docker por Testcontainers)
cd frontend; npm install; npm run dev   # frontend en http://localhost:5173 · sistema de diseño en /_ds
cd frontend; npm test; npm run lint     # tests (Vitest) y lint del frontend
cd frontend; npm run e2e                # punta a punta (Playwright) con todo levantado; ver playwright.config.ts
                                        # el objetivo del sprint usa los datos de demostración: E2E_DEMO_CONTRASENA
```

Ambiente de prueba (Docker Compose + Caddy con HTTPS): `docs/despliegue.md`.

## Definición de terminado

- Criterios de aceptación de la historia verificados.
- Tests unitarios y de integración en verde; los endpoints nuevos tienen test de autorización (quién puede y quién no).
- OpenAPI actualizado.
- PR revisado y aprobado por otra persona del equipo.
