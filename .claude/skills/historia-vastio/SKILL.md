---
name: historia-vastio
description: Flujo para construir una historia de usuario o una mejora de Vastio de punta a punta (plan, rama, migración, backend, frontend, tests, OpenAPI y PR). Usala cada vez que alguien pida implementar una historia (A7-02, B2-02…) o una mejora del sprint (M-02…).
---

# Construir una historia de Vastio

Este flujo complementa el `CLAUDE.md` del repo; si algo choca, manda el `CLAUDE.md`.

## 1. Antes de tocar código

1. `git checkout main && git pull`. Nunca trabajes sobre una rama vieja.
2. Leé lo que corresponde a la historia:
   - Historia y criterios: `docs/sprint-N.md` (y el Seguimiento del Proyecto si te lo pasan).
   - Prototipo: `design/pantallas/UIxx-*.dc.html`.
   - Modelo: `docs/der/diccionario-de-datos.md`.
   - Permisos: `docs/perfiles-y-permisos.md`.
   - Estados: `docs/maquina-de-estados.md`.
3. Buscá si ya hay código relacionado (`grep` por el nombre de la entidad, la pantalla o el parámetro). Mucho ya está preparado en la base (por ejemplo, `visible_en_cocina`, `HORIZONTE_COCINA_DIAS`).
4. Listá las dudas de negocio. **No inventes reglas**: si falta un dato o dos documentos se contradicen, preguntá antes de seguir.

## 2. Plan (esperá aprobación)

Presentá, en este orden:

- Qué criterios de aceptación cubre y cuáles no.
- Migración Flyway nueva (`V<n+1>__descripcion.sql`), si hace falta. Nunca modifiques una migración fusionada.
- Backend: entidades, servicios, endpoints (`/api/v1/...` en español y en plural), DTO, reglas.
- Autorización: qué perfiles pueden y cuáles no, según la matriz. Recordá el filtrado del importe de la seña.
- Frontend: pantallas y componentes de `src/ds` que vas a usar. Si falta uno, se agrega al sistema de diseño.
- Tests que vas a escribir.
- Documentos que hay que actualizar (diccionario de datos, permisos, `CLAUDE.md`, `docs/sprint-N.md`).

## 3. Rama y commits

- Rama: `<nombre>/<épica>-<historia>` (ej.: `berni/A7-consultar-vista-cocina`). Una historia por rama.
- Commits chicos: `tipo(módulo): descripción` en español (`feat(agenda): ...`, `fix(bebida): ...`, `docs(der): ...`, `test(agenda): ...`).

## 4. Construcción

- Backend: `api` → `aplicacion` → `dominio` → `infraestructura`. Un módulo no usa los repositorios de otro, usa su servicio.
- Toda transición de estado pasa por el servicio de la máquina de estados.
- Autorización con `@PreAuthorize`, y "solo sus eventos" en el servicio.
- Frontend: solo `src/ds`, voseo, sentence case, botones con verbo + objeto, mobile first.

## 5. Terminado

Antes de abrir el PR, todo en verde:

```powershell
cd backend; .\mvnw verify
cd frontend; npm test; npm run lint
```

- Tests de autorización para cada endpoint nuevo (quién puede y quién no).
- OpenAPI actualizado (revisá `/api/docs`).
- Documentos actualizados en el mismo PR.

## 6. Pull request

Abrí el PR a `main` con este formato y **no lo fusiones**: lo revisa otra persona del equipo.

```markdown
## Historia
<ID> · <nombre> (Sprint N)

## Qué hace
- ...

## Criterios de aceptación
- [x] ...
- [ ] ... (si alguno queda afuera, explicá por qué)

## Decisiones tomadas
- ...

## Cómo probarlo
1. Usuario/perfil: ...
2. Pasos: ...
3. Resultado esperado: ...

## Migraciones
V<n>__... (o "ninguna")

## Documentos actualizados
- ...
```

Avisale a quien te lo pidió el número de PR y qué quedó pendiente, para que lo cargue en el tablero (SPRINT ACTUAL).
