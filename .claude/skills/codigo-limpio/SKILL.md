---
name: codigo-limpio
description: Estándares prácticos de código limpio para Vastio (nombres, funciones, estructura, qué revisar antes de editar). Usala al escribir o modificar código del backend o del frontend.
---

# Código limpio en Vastio

Adaptada de la skill "clean-code" de claude-code-templates (MIT, © Daniel Ávila). Las convenciones del `CLAUDE.md` tienen prioridad.

## Principios

| Principio | Regla |
|---|---|
| Responsabilidad única | Cada clase y cada función hace una sola cosa |
| No repetir | Si algo se repite tres veces, se extrae |
| Simple | La solución más simple que cumple los criterios |
| YAGNI | No construyas lo que la historia no pide |
| Boy Scout | Dejá el archivo un poco mejor de como lo encontraste |

## Nombres

- Dominio en español, técnica en inglés: `EventoService`, `registrarSena()`, `PreReservaRequest`.
- Que revelen la intención: `invitadosDefinitivos`, no `n`.
- Booleanos como pregunta: `esVisibleEnCocina`, `tieneSena`.
- Constantes en mayúsculas: `HORIZONTE_COCINA_DIAS`.
- Si un nombre necesita un comentario para entenderse, cambiá el nombre.

## Funciones y estructura

- Cortas y con un solo nivel de abstracción.
- Pocos parámetros (hasta 3); si son más, un objeto o un DTO.
- Cláusulas de guarda en lugar de `if` anidados (máximo 2 niveles).
- Sin números mágicos: constantes con nombre o parámetros del sistema.
- El código relacionado vive junto (por módulo, según la estructura del `CLAUDE.md`).

## Qué no hacer

| Evitar | En su lugar |
|---|---|
| Comentar lo obvio | Código que se explique solo; comentá el porqué, no el qué |
| Un helper para una línea | Escribirlo donde se usa |
| `utils` con una sola función | Ponerla donde se usa |
| Clases "Dios" o servicios de 500 líneas | Partir por responsabilidad |
| Exponer entidades JPA en la API | DTO propios |
| Estilos sueltos en el frontend | Componentes de `src/ds` |

## Antes de editar un archivo

- ¿Quién lo importa o lo usa? Puede romperse.
- ¿Qué tests lo cubren? Pueden fallar.
- ¿Es compartido (por ejemplo, un componente de `src/ds`)? El cambio afecta a otras pantallas.
- Editá el archivo y todos los que dependen de él en la misma tarea. Nunca dejes imports rotos.

## Antes de decir "listo"

- ¿Hice exactamente lo que se pidió?
- ¿Corrí `mvnw verify`, `npm test` y `npm run lint` y están en verde?
- ¿Quedó algún caso borde sin cubrir? Si sí, decilo.
