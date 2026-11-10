# Skills del proyecto

Claude Code carga solo las skills de esta carpeta para cualquiera que trabaje en el repo. Cada skill es una carpeta con un `SKILL.md`.

| Skill | Cuándo se usa |
|---|---|
| `historia-vastio` | Construir una historia o una mejora: plan, rama, migración, tests y PR |
| `revisar-pr-vastio` | Revisar un PR antes de aprobarlo (o el propio antes de abrirlo) |
| `lluvia-de-ideas` | Pasar de una idea difusa a un diseño antes de programar |
| `codigo-limpio` | Al escribir o modificar código |

Para invocar una a mano en Claude Code: `/historia-vastio`, `/revisar-pr-vastio`, etc.

## Agregar una skill

1. Crear `.claude/skills/<nombre>/SKILL.md` con el encabezado `name` y `description` (la descripción decide cuándo se usa).
2. Leerla entera antes de subirla: muchas skills de internet traen scripts que no hacen nada o un stack distinto al nuestro.
3. Subirla en un PR como cualquier otro cambio.
