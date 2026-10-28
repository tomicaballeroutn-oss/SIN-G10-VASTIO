# M-07 · Comprobante interno de seña (opcional)

La decisión de incorporar M-07 sigue siendo humana. No altera las reglas de registro de señas ni sus permisos.

## Documento histórico

Se guarda el PDF original en PostgreSQL (pdf_original, bytea). Es más simple que una instantánea estructurada: conserva exactamente texto, formato, logo y metadatos incluso si cambian evento, catálogos, fuentes o generador. Las descargas posteriores devuelven los mismos bytes. Número, fecha y usuario emisor permanecen en la fila original. El costo es almacenamiento y tamaño de respaldos; no se agrega otro almacenamiento ni librería.

V12 permanece intacta. V13 agrega la columna y protege nuevas filas y la inmutabilidad con un trigger. Una instalación anterior con V12 pudo emitir sin conservar el PDF: no es posible recuperar el documento original desde los datos actuales. V13 conserva esos registros y su numeración; GET y POST devuelven 409 COMPROBANTE_SIN_ORIGINAL, sin regenerar ni renumerar. La recuperación de originales, si existieran fuera del sistema, requiere una decisión y procedimiento humanos; no se implementa una reemisión que invente historia.

## API y pantalla

- POST /api/v1/eventos/{id}/sena/comprobante: primera emisión 201 con PDF y Location; si existe, 200 con el mismo original, sin asignar otro número.
- GET en la misma dirección: solo descarga, 200; sin comprobante, 404 COMPROBANTE_NO_EMITIDO. Nunca emite ni consulta logos.
- Ambos: backend autoriza únicamente a Dirección, Coordinación, Administración o vendedora titular con acceso al importe. Los datos actuales solo se usan para autorizar una descarga existente. Cancelar no borra el documento.
- La ficha presenta acciones explícitas «Emitir comprobante» (POST) y «Descargar comprobante» (GET). Un 404 al descargar no dispara una emisión automática.
- Error de generación: 500 COMPROBANTE_GENERACION_FALLIDA; el documento y la asignación anual se revierten juntos.

## Numeración y transacción

CS-AAAA-NNNNN, del 00001 al 99999 por año, usando la zona horaria configurada. La emisión bloquea y refresca el evento antes de buscar el comprobante. El numerador usa un UPSERT con bloqueo de fila dentro de la misma transacción obligatoria; no utiliza REQUIRES_NEW. El PDF se genera y persiste antes del commit. Una restricción única por evento y otra por número complementan el bloqueo.

El 99999 se admite; el siguiente devuelve 422 NUMERADOR_COMPROBANTE_AGOTADO sin alterar la última fila. Años fuera de cuatro cifras se rechazan. Cambiar de año empieza un numerador independiente. No se extiende automáticamente el formato: si se agota, la decisión comercial es humana.

Las pruebas ejercitan emisiones simultáneas del mismo evento y de eventos distintos, rollback tras guardar, fallo del generador y respuesta inválida. Eso prueba reutilización del número revertido en esos escenarios; no se promete ausencia universal de huecos frente a intervenciones administrativas, restauraciones o cambios externos. La secuencia técnica de la clave primaria no es el numerador mostrado. Una respuesta de red perdida después del commit no borra el comprobante: el reintento devuelve el original.

## Migraciones

V10, V11 y V12 no se reescriben. V13 no agrega tablas: el total esperado es 42 y las categorías de servicio son 11. Se prueba instalación desde cero y actualización desde V11 y V12, conservando señas, moneda, datos del emisor, fecha y número de comprobantes anteriores.
