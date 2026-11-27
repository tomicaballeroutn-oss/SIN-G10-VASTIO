-- M-06: retirar las capturas antes de sus funciones y las tablas que escribían.
DROP TRIGGER tg_calendar_evento ON evento;
DROP TRIGGER tg_calendar_turno ON turno;
DROP TRIGGER tg_calendar_salon ON salon;
DROP TRIGGER tg_calendar_tipo ON tipo_evento;

DROP FUNCTION fn_calendar_encolar_evento();
DROP FUNCTION fn_calendar_encolar_catalogo();

DROP TABLE calendar_seguimiento;
DROP TABLE operacion_calendar;
DROP TABLE publicacion_calendar;

-- La FK de destinatarios no borra en cascada. Conservar todos los avisos comerciales.
DELETE FROM notificacion_destinatario d
USING notificacion n
WHERE d.notificacion_id = n.notificacion_id AND n.tipo = 'CALENDAR_PENDIENTE';

DELETE FROM notificacion WHERE tipo = 'CALENDAR_PENDIENTE';

ALTER TABLE notificacion DROP CONSTRAINT ck_notificacion_tipo;
ALTER TABLE notificacion ADD CONSTRAINT ck_notificacion_tipo CHECK (tipo IN (
    'EVENTO_NUEVO', 'SENA', 'CONTRATO', 'CONFIRMACION', 'MODIFICACION', 'REPROGRAMACION',
    'CANCELACION', 'PLANNER_ASIGNADA', 'ALERTA_STOCK', 'ASISTENCIA_REAL', 'ORDEN_PREPARACION'
));
