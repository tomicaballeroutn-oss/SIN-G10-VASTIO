-- Asistencia real de cierre (docs/sprint-4.md, decisión 9): al pasar a Realizado un evento de egresados, la planner
-- recibe el aviso para cargarla.
ALTER TABLE notificacion DROP CONSTRAINT ck_notificacion_tipo;
ALTER TABLE notificacion ADD CONSTRAINT ck_notificacion_tipo CHECK (tipo IN ('EVENTO_NUEVO', 'SENA', 'CONTRATO',
    'CONFIRMACION', 'MODIFICACION', 'REPROGRAMACION', 'CANCELACION', 'PLANNER_ASIGNADA', 'ALERTA_STOCK', 'ASISTENCIA_REAL'));
