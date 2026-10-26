# A7-01 · Generar reporte de ocupación por salón y temporada

Implementación local en la rama `feature/A7-01-reporte-ocupacion`. Sin commits ni publicación.

## Alcance

Dirección y Coordinación pueden consultar un reporte de eventos señados, contratados, confirmados, en curso, realizados y cerrados, con filtros por período, salón, tipo de evento, vendedora y régimen de temporada. Presenta cifras resumen, un gráfico horizontal de ocupación por salón, su tabla equivalente y un desglose de eventos por vendedora y tipo. Se puede imprimir desde el navegador.

## Criterios de cálculo

- **Unidad comercializable:** un salón en una fecha y un turno. El denominador parte de dos turnos por día y cada salón configurado, también si hoy fue dado de baja, porque el catálogo no conserva el historial de altas y bajas. Luego descuenta las unidades con un bloqueo cuyo período de vigencia se cruza con la fecha de la unidad.
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
