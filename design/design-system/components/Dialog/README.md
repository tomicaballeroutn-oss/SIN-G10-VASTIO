Ventana modal para confirmar una acción o completar un paso corto sin salir de la pantalla; en teléfono se muestra como hoja inferior.

**Vos pasás:** `open`, `title`, `onClose`, `children` (qué va a pasar y qué se conserva) y `actions` (dos botones: uno `outline` para volver y uno `solid` con el verbo de la acción).

- Cerrá con Esc, con la X y tocando el fondo. Al abrir, el foco va al diálogo; devolvé el foco al disparador al cerrar.
- El botón principal nombra la acción ("Cancelar evento"), no "Aceptar". Si es destructiva, `tone="danger"`.
- Con `inline` se dibuja en su lugar sin cubrir la pantalla (solo para vistas previas y documentación).
- No apiles diálogos ni lo uses para mensajes informativos: para eso está `Alert`.
