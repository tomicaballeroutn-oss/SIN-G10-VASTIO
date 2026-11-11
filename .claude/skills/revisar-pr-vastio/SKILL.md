---
name: revisar-pr-vastio
description: Revisión de un pull request de Vastio contra las reglas del proyecto (reglas que no se negocian, permisos, migraciones, tests, sistema de diseño). Usala cuando alguien pida revisar o aprobar un PR, o antes de abrir uno propio.
---

# Revisar un PR de Vastio

El objetivo es que quien revisa pueda **explicar y defender** lo que se integra, no solo aprobarlo.

## 1. Contexto

1. `gh pr view <n>` y `gh pr diff <n>` (o `git diff main...<rama>`).
2. Identificá la historia (ID en la rama o en el título) y leé sus criterios en `docs/sprint-N.md`.
3. Leé el `CLAUDE.md`: las "Reglas que no se negocian" son la base de esta revisión.

## 2. Checklist

**Alcance**
- [ ] Hace lo que pide la historia, y solo eso (sin cambios de otras historias mezclados).
- [ ] Ningún criterio de aceptación queda sin cubrir sin explicación.
- [ ] No inventa reglas de negocio; si tomó una decisión, está escrita en el PR.

**Base de datos**
- [ ] Las migraciones son nuevas: no modifica ninguna ya fusionada en `main`.
- [ ] Las tablas y columnas coinciden con `docs/der/diccionario-de-datos.md` (y si cambia el modelo, el diccionario se actualiza en el mismo PR).
- [ ] Los enum Java tienen los mismos valores que los `CHECK` de la base.

**Reglas del negocio**
- [ ] Exclusividad de salón + fecha + turno intacta (índice `ux_evento_unidad_activa` y `FOR UPDATE`).
- [ ] Las tablas de solo inserción no se actualizan ni se borran.
- [ ] Las transiciones de estado pasan por el servicio de la máquina de estados.
- [ ] El sistema no guarda precios ni costos; el importe de la seña se filtra por perfil **en el backend**.
- [ ] La barra opera a ciegas (no recibe saldos del depósito ni cantidades esperadas).

**Seguridad y permisos**
- [ ] Cada endpoint nuevo tiene `@PreAuthorize` coherente con `docs/perfiles-y-permisos.md`.
- [ ] "Solo sus eventos" se valida en el servicio, no solo en la pantalla.
- [ ] Hay tests de autorización: quién puede y quién no (403).
- [ ] No hay credenciales, tokens ni `.env` versionados.

**Frontend**
- [ ] Usa solo componentes de `src/ds`; no hay estilos improvisados ni Tailwind.
- [ ] Textos en voseo, sentence case, botones con verbo + objeto, sin emoji.
- [ ] Funciona en celular (mobile first); el estado nunca se comunica solo por color.

**Calidad**
- [ ] CI en verde (backend y frontend).
- [ ] Tests nuevos para la lógica nueva, no solo para el camino feliz.
- [ ] OpenAPI actualizado.
- [ ] Nombres en español para el dominio y en inglés para lo técnico (`EventoService`, `registrarSena()`).

## 3. Probarlo

Seguí la sección "Cómo probarlo" del PR con la app levantada (ver comandos en `CLAUDE.md`). Probá también con un perfil que **no** debería tener acceso.

## 4. Devolución

Escribí la revisión en el PR, en este orden:

1. **Bloqueante:** lo que hay que corregir antes de aprobar (con archivo y línea).
2. **Sugerencias:** mejoras que no bloquean.
3. **Preguntas:** lo que no entendiste o no está explicado.
4. **Veredicto:** aprobar, pedir cambios o comentar.

No apruebes un PR que no probaste ni uno que no podrías explicar en la defensa.
