# A7-01 · Generar reporte de ocupación por salón y temporada

PR #46, rama `meli/A7-01-reporte-ocupacion`, integrado con `main` mediante merge normal.

## Alcance

Dirección y Coordinación pueden consultar un reporte de eventos señados, contratados, confirmados, en curso, realizados y cerrados, con filtros por período, salón, tipo de evento, vendedora y régimen de temporada. Presenta cifras resumen, un gráfico horizontal de ocupación por salón, su tabla equivalente y un desglose de eventos por vendedora y tipo. Se puede imprimir desde el navegador.

## Criterios de cálculo

- **Unidad comercializable:** un salón en una fecha y un turno. El denominador parte de dos turnos por día y cada salón configurado, también si hoy fue dado de baja, porque el catálogo no conserva el historial de altas y bajas. Luego descuenta una sola vez cada unidad con un bloqueo cuyo período de vigencia se cruza con la fecha de la unidad (fecha local de Córdoba, límites inclusivos), salvo que la unidad tenga un evento en alguno de los seis estados incluidos. El evento tiene prioridad, como en `AgendaService`: un bloqueo histórico levantado el mismo día no puede restar nuevamente una unidad ocupada. Esta excepción se consulta sin filtros comerciales para que tipo y vendedora no cambien el denominador.
- **Ocupación del salón:** eventos en estados `SENADO`, `CONTRATADO`, `CONFIRMADO`, `EN_CURSO`, `REALIZADO` y `CERRADO` del salón en el período, divididos por las unidades disponibles una vez descontados los bloqueos. Se excluye `PRE_RESERVA`; `LIBERADA` y `CANCELADO` tampoco ocupan la unidad. Al elegir un régimen se cuentan solo los días de ese régimen dentro del rango.
- **Ocupación promedio:** promedio aritmético del porcentaje de cada salón mostrado (un salón filtrado equivale a ese salón).
- **Eventos del reporte:** incluye desde `SENADO` hasta `CERRADO`, excepto `PRE_RESERVA`; los eventos liberados o cancelados no cuentan. El resumen los llama eventos incluidos y no eventos realizados, porque puede incluir compromisos futuros o en curso.
- **Temporadas:** alta de octubre a diciembre; baja de enero a septiembre. Se usa la fecha de inicio de la jornada guardada en la unidad comercializable. Esta división toma como base la temporada alta indicada en `contexto-del-negocio.md`.
- Los filtros de tipo y vendedora reducen eventos y ocupación del gráfico, mientras que la disponibilidad sigue representando todas las unidades no bloqueadas en el rango elegido.
- El tercer indicador cuenta únicamente eventos `REALIZADO` y `CERRADO`; el resto de estados comerciales siguen participando en las unidades ocupadas y en el desglose por vendedora y tipo.

## API y permisos

`GET /api/v1/reportes/ocupacion?desde=AAAA-MM-DD&hasta=AAAA-MM-DD` admite además `salonId`, `tipoEventoId`, `vendedoraId` y `regimen=ALTA|BAJA`. Devuelve resumen, ocupación por salón, agrupación por vendedora y tipo, y las opciones de filtro.

El endpoint permite solo perfiles Dirección y Coordinación. No serializa entidades ni datos económicos. Rechaza un rango invertido, rangos mayores a diez años y regímenes desconocidos.

## Interfaz

Ruta `/reportes`, disponible solo para Dirección y Coordinación. El rango inicial abarca el año calendario actual para mostrar tanto eventos pasados como compromisos futuros. Al seleccionar temporada alta o baja, el rango Desde/Hasta se ajusta a los meses correspondientes que intersectan el período actual; si no hay intersección, se propone la temporada seleccionada del año inicial. La opción Ambas temporadas restaura el rango previo a seleccionar un régimen. Los filtros actualizan el reporte; la tabla permanece como alternativa accesible al gráfico. La hoja de impresión omite navegación y filtros y conserva tarjetas y tablas.

## Archivos principales

- Backend: `reportes/api`, `reportes/aplicacion`, `reportes/infraestructura`.
- Frontend: `frontend/src/api/reportes.ts`, `frontend/src/features/reportes/`.
- Seguridad: `Permisos.CONSULTAR_REPORTE_OCUPACION`.

## Validación técnica y decisiones conservadas

- Dos turnos por día está respaldado por V2 (mediodía/noche) y CatalogoService: no se agregan salones ni turnos, agenda fija de 3 × 2. No se usan las filas de unidad_comercializable como capacidad total porque se crean a demanda. No hay vigencia histórica del catálogo: no es posible reconstruir con precisión cuándo un salón comenzó o dejó de comercializarse.
- V1 garantiza unicidad salón/fecha/turno y un único evento no cancelado ni liberado por unidad. Al descontar solo bloqueos de unidades sin eventos incluidos, ocupadas y bloqueadas son conjuntos disjuntos; la disponibilidad no puede ser inferior al numerador. No se oculta un error limitando artificialmente el porcentaje a 100.
- Se conserva el promedio simple de porcentajes por salón (incluidos los salones con disponibilidad cero, que aportan 0). Alternativa pendiente de ratificación comercial: promedio ponderado por unidades disponibles.
- Se conserva la exclusión de PRE_RESERVA aunque reserve agenda: el reporte mide compromisos desde SENADO. Su inclusión requeriría una decisión de producto; no se cambió por inferencia.
- Alta octubre–diciembre tiene respaldo en contexto-del-negocio.md; baja se conserva como complemento enero–septiembre. Se usa la jornada de la unidad, incluso si el turno termina al día siguiente.
- Reprogramaciones se leen desde la unidad actual del evento; no se reconstruye una foto histórica de reservas. Cancelados y liberados se excluyen, y las tablas de historial no multiplican filas. Si se desea un reporte «a una fecha de corte» sería otro requisito.
- La vigencia de bloqueo se conserva a nivel de día; no se prorratea por horas. Se respetan bloqueos históricos ya levantados cuando alcanzaban la fecha de la unidad, sin duplicar bloqueos superpuestos. Un bloqueo dado de alta después de esa jornada o levantado antes no la descuenta.
- DTO compuesto por métricas y opciones ID/nombre de salón, tipo y vendedora. Sin importes, datos de clientes ni entidades completas. Nombres de vendedoras necesarios para el desglose autorizado.

## Cobertura

Pruebas unitarias: estados, promedio simple con diferentes denominadores, filtros, alta/baja, año bisiesto y varios años, período sin días de temporada y catálogo vacío.

Integración PostgreSQL real: seis estados incluidos y tres excluidos; fechas inclusivas; filtros; temporadas; bloqueo histórico seguido de ocupación (100 %, sin filtros; 50 % con otra vendedora); tipo sin eventos conserva capacidad; bloqueos duplicados; disponibilidad cero; límites de vigencia; unidad actual tras reprogramación y reutilización tras cancelación; catálogo de turnos; ausencia de datos sensibles; permisos D/C, otros perfiles y anónimo; validación de rango/régimen. Las fixtures SQL de reprogramación verifican la lectura del reporte; los casos de uso completos se cubren en la suite de agenda existente.

Frontend: métricas, tablas y gráfico, filtros, temporadas y restauración del rango, vacío, error/reintento, impresión, fecha vacía y bloqueo de seis perfiles. Menú conserva Estadísticas de consumo para Administración y Compras.

E2E de interfaz con API simulada: acceso por perfil, filtro, invocación de impresión y hoja de estilos de impresión en escritorio y teléfono. No sustituye las integraciones SQL ni un recorrido con el backend desplegado.

La impresión conserva los filtros de la consulta que produjo los resultados; mientras se espera una consulta nueva el botón queda deshabilitado, para no rotular datos anteriores con filtros nuevos. Vaciar una fecha no desmonta la pantalla.
