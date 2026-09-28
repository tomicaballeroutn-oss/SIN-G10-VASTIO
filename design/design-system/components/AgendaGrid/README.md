Calendario mensual que muestra, en cada día, las seis unidades comercializables (3 salones × 2 turnos) de un vistazo.

**Vos pasás:** `year`, `month` (0–11), `events` (`[{date:'AAAA-MM-DD', salon, turno:'mediodia'|'noche', status}]`), `today`, `selected`, `onSelectDay`, `onPrev` y `onNext`. Un evento cancelado no debe enviarse: la unidad vuelve a verse disponible.

- Columna = salón (Avril, Club de Campo, Santa Bárbara), fila superior = mediodía, fila inferior = noche. Estado por relleno: punteado pre-reserva, claro señado, sólido confirmado, sólido con anillo realizado, rayado bloqueado.
- Cada día se lee para lectores de pantalla ("sábado 26 de septiembre. Avril noche: Confirmado; …").
- Mantené la orden de salones y el color de cada uno (RNF-DIS-01). Debajo, mostrá el detalle del día seleccionado con `EventCard`.
- En teléfono las marcas de cada día se vuelven muy chicas: el detalle se lee en la lista del día seleccionado, debajo de la grilla, y cada celda ya se anuncia completa a lectores de pantalla.
- Para la vista semanal y diaria, no estires esta grilla: usá `EventCard` por turno agrupadas por salón.
