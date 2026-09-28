Navegación principal: barra lateral en escritorio (`layout="sidebar"`) o barra inferior en teléfono y tableta (`layout="bottom"`).

**Vos pasás:** `items` (`[{id,label,icon,group?,badge?}]`), `value` (el ítem activo), `onSelect`, y en la lateral `logo` (usá `vastio-mark` o `vastio-mark-reverse` según el tema) y `footer` (un `Actor` del usuario).

- Pasale solo los ítems que el perfil puede usar (mínimo privilegio, RNF-SEG-02).
- Escritorio (≥ 960 px): lateral con los grupos Agenda, Bebidas y Gestión. Menor: inferior con cinco ítems como máximo; el resto va bajo "Más".
- El ítem activo lleva `aria-current="page"`, fondo `brand-tint` y peso mayor; no dependas solo del color.
- El contador `badge` usa `accent`: reservalo para novedades reales.
