# Principios de diseño

Cuatro principios ordenan cada decisión de pantalla. Cuando dos componentes o dos pantallas discrepan, gana el que respete más de estos cuatro, en este orden: consistencia, simpleza, contraste, balance.

## Consistencia

El sistema no admite excepciones por pantalla. La misma cosa se ve y se comporta igual en todo Vastio, para que la persona no tenga que pensar cómo se usa.

- Hay tres variantes de botón y no más: `solid` (con fondo), `outline` (contorno, sin fondo) y `text` (solo subrayado, para acciones terciarias y de menú), cada una con su versión con ícono. Los tamaños son tres (`sm`, `md`, `lg`). No existen "botones especiales".
- Por vista hay un único botón `solid` de tono `brand`: la acción principal. Lo demás es `outline` o `text`.
- Los títulos usan siempre `h1`, `h2`, `h3` y `h4`; los botones siempre `button`; el texto siempre `body` o `body-sm`. No definas tamaños nuevos: si falta uno, se agrega al sistema, no a la pantalla.
- Un mismo dato se muestra siempre con el mismo componente: el estado de un evento es `StatusChip`, el salón es `SalonTag`, quién y cuándo es `Actor`, una cantidad de bebida es `numeral` con su unidad.
- Un componente sirve para cualquier contenido: una tarjeta con un título de una línea y otra con dos se comportan igual. Si un caso obliga a una excepción, se rediseña el componente.

## Simpleza

Un producto mejora sacando cosas, no agregando. Vastio hace dos cosas y las hace bien: la agenda y el control de bebida.

- Una pantalla, una tarea. La encargada de barra que registra una entrega no ve reportes; la vendedora que pre-reserva no ve stock.
- Cada perfil ve solo lo que su rol necesita (RNF-SEG-02): `Nav` recibe únicamente los ítems permitidos.
- Mostrá primero lo que se decide ahora y escondé el resto detrás de una acción clara: el historial de un evento vive en `Timeline`, detrás de "Ver historial".
- Preferí una palabra a un párrafo y un dato a un gráfico decorativo. Sin ilustraciones, sin métricas de relleno.
- Los estados vacíos dicen qué hacer (`EmptyState`), los errores dicen cómo resolverlos.

## Contraste

El contraste separa lo importante de lo secundario y hace legible todo, también de noche y bajo el sol del estacionamiento.

- La jerarquía se construye con tamaño, peso y color de la paleta (`ink`, `ink-muted`, `brand-ink`), no con decoración.
- La acción principal es la única con relleno de marca. El resto retrocede.
- Texto 4,5:1 y elementos con significado 3:1 en los dos temas. Los estados van con ícono y palabra, nunca solo con color.

## Balance

- Todo se alinea a la grilla de 4 px (`space-*`). Los márgenes y separaciones se repiten: `space-4` entre campos, `space-6` entre tarjetas, `space-8` entre secciones.
- Agrupá por proximidad antes que por cajas: el espacio dentro de un grupo es menor que el espacio entre grupos.
- Alineá a la izquierda el texto y a la derecha los números de una columna (`numeral-sm`), para poder compararlos de un vistazo.
- Dejá aire: el tema claro descansa sobre `canvas`; no llenes una pantalla porque hay espacio.
