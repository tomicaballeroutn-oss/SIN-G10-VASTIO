# Tipografía: Poppins y Jost

Vastio usa dos familias con papeles distintos: Poppins es la voz de la interfaz y Jost es la voz de la marca y de los números.

## Por qué no alcanza con una sola

La tipografía del wordmark del logo es una geométrica ancha en versales con mucho espaciado, del estilo de Futura. La familia libre más cercana es Jost, que se incluye con todos sus pesos (variable, 100–900). Una tipografía así funciona muy bien como firma, pero sola no alcanza para una interfaz: el wordmark usa un único peso en versales y una interfaz necesita texto corrido, pesos de énfasis y cifras que alineen en columnas. Poppins ya es la tipografía de los documentos del proyecto, es geométrica como el logo, tiene letras amplias y abiertas que se leen bien en una tableta a las tres de la mañana y trae los pesos 400, 500, 600 y 700.

## Qué va en cada una

| Familia | Se usa para | Estilos |
| --- | --- | --- |
| Poppins | Todo lo funcional: títulos, texto, etiquetas, botones, tablas y formularios | `h1`–`h4`, `body-lg`, `body`, `body-sm`, `label`, `caption`, `button` |
| Jost | Momentos de marca y eco del wordmark | `display`, `display-sm`, `overline` |
| Jost con cifras tabulares | Cantidades, fechas y horas | `stat`, `numeral`, `numeral-sm` |

Poppins tiene cifras proporcionales (el "1" ocupa casi la mitad que el "0") y no ofrece cifras tabulares, así que una columna de cajones no se alinea. Jost sí: por eso toda cantidad, fecha u hora que se compara con otras va en un estilo de numerales. Como ambas son geométricas, conviven sin ruido dentro de una tabla o de un párrafo corto.

## Reglas

- Máximo dos familias, con estos papeles; no agregues una tercera.
- El texto de un campo nunca baja de `body` (16 px). Nada baja de `caption` (12 px).
- `overline` va siempre en versales (la clase `.overline` lo aplica) y sobre un título, nunca como título.
- `display` y `display-sm` se reservan para login, portadas y estados vacíos de escritorio; no se usan en pantallas de trabajo.
- El wordmark del logo no se compone con estas tipografías: se usa el archivo del logo.
- Las cifras dentro de una oración corrida ("36 cajones en el depósito") quedan en Poppins; solo se pasan a numerales cuando forman una columna, un valor destacado o un dato de encabezado.
