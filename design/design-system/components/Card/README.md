Contenedor base de superficie con filo, título, acciones y pie opcionales.

**Vos pasás:** `children` y, opcionalmente, `eyebrow` (sobre el título, en `overline`), `title`, `subtitle`, `actions` (botones de ícono), `footer`, `interactive` + `onClick` (toda la tarjeta abre algo), `flush` (sin padding, para tablas).

- Una tarjeta agrupa un tema; no anides tarjetas dentro de tarjetas.
- Separá tarjetas con `space-4` en teléfono y `space-6` desde tableta.
- Una tarjeta `interactive` tiene foco, sombra al pasar el mouse y responde a Enter; su contenido no debe tener otros botones que compitan.
