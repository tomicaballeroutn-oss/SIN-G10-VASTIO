---
name: lluvia-de-ideas
description: Convierte una idea todavía difusa (una mejora, una pantalla nueva, un cambio de flujo) en un diseño claro antes de programar, con preguntas y alternativas. Usala cuando el equipo diga "estamos replanteando", "se nos ocurrió", "cómo lo harías" o cuando una historia no tenga criterios de aceptación cerrados. No la uses si la historia ya está definida y solo hay que construirla.
---

# De idea a diseño

Adaptada de la skill "brainstorming" de claude-code-templates (MIT, © Daniel Ávila), para Vastio.

## Proceso

**1. Entender**
- Mirá primero el estado del proyecto: `CLAUDE.md`, `docs/`, el prototipo en `design/pantallas/` y los últimos commits.
- Hacé **una pregunta por vez**, preferentemente con opciones ("¿A o B?").
- Enfocate en: para quién es, qué problema del cliente resuelve, qué restricciones hay (permisos, datos que ya existen, plazo del sprint).
- No inventes reglas de negocio: si la respuesta es del cliente, anotala como pregunta para Juaneda.

**2. Explorar**
- Proponé 2 o 3 enfoques con sus ventajas y desventajas.
- Empezá por el que recomendás y explicá por qué.
- Señalá qué se reutiliza de lo que ya está construido y qué choca con decisiones ya tomadas.

**3. Presentar el diseño**
- En secciones cortas (200–300 palabras): pantalla y flujo, datos y base, permisos, casos borde, pruebas.
- Después de cada sección, preguntá si va bien antes de seguir.
- Recortá sin culpa lo que no hace falta ahora (YAGNI): el sprint es de dos semanas.

## Después del diseño

- Escribí el diseño validado en `docs/plans/AAAA-MM-DD-<tema>.md`.
- Incluí: decisión tomada, alternativas descartadas y por qué, criterios de aceptación propuestos y preguntas abiertas.
- Las decisiones van además a la pestaña DECISIONES del tablero (avisale a quien te lo pidió para que las cargue).
- Para construirlo, seguí la skill `historia-vastio`.
