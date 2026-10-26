-- Generar y ajustar la orden de preparación (docs/sprint-4.md, decisiones 11 a 25).
-- Una orden por evento con los totales de todas sus barras: el destino queda sin uso. La generada al confirmar el
-- evento no tiene usuario (responsable null = sistema).
ALTER TABLE orden_preparacion ALTER COLUMN ubicacion_destino_id DROP NOT NULL;
ALTER TABLE orden_preparacion ALTER COLUMN responsable_id DROP NOT NULL;

-- De dónde salió la sugerencia y sobre qué se calculó, para explicarla y avisar si los asistentes cambiaron.
ALTER TABLE orden_preparacion ADD COLUMN fuente varchar(20) NOT NULL DEFAULT 'SIN_DATOS';
ALTER TABLE orden_preparacion ADD CONSTRAINT ck_orden_preparacion_fuente
    CHECK (fuente IN ('HISTORICO_TIPO', 'PROPORCIONES', 'HISTORICO_GENERAL', 'SIN_DATOS'));
ALTER TABLE orden_preparacion ADD COLUMN eventos_base integer NOT NULL DEFAULT 0;
ALTER TABLE orden_preparacion ADD CONSTRAINT ck_orden_preparacion_eventos_base CHECK (eventos_base >= 0);
ALTER TABLE orden_preparacion ADD COLUMN asistentes_base integer;
ALTER TABLE orden_preparacion ADD COLUMN margen_porcentaje smallint NOT NULL DEFAULT 0;
ALTER TABLE orden_preparacion ADD COLUMN fecha_calculo timestamptz NOT NULL DEFAULT now();

-- Auditoría: quién la ajustó, la confirmó y la reabrió (regla 9). Versión para no pisar ajustes ajenos.
ALTER TABLE orden_preparacion ADD COLUMN usuario_modificacion_id bigint;
ALTER TABLE orden_preparacion ADD COLUMN fecha_modificacion timestamptz;
ALTER TABLE orden_preparacion ADD COLUMN usuario_confirmacion_id bigint;
ALTER TABLE orden_preparacion ADD COLUMN usuario_reapertura_id bigint;
ALTER TABLE orden_preparacion ADD COLUMN fecha_reapertura timestamptz;
ALTER TABLE orden_preparacion ADD COLUMN version integer NOT NULL DEFAULT 0;
ALTER TABLE orden_preparacion ADD CONSTRAINT fk_orden_preparacion_usuario_modificacion_id
    FOREIGN KEY (usuario_modificacion_id) REFERENCES usuario (usuario_id);
ALTER TABLE orden_preparacion ADD CONSTRAINT fk_orden_preparacion_usuario_confirmacion_id
    FOREIGN KEY (usuario_confirmacion_id) REFERENCES usuario (usuario_id);
ALTER TABLE orden_preparacion ADD CONSTRAINT fk_orden_preparacion_usuario_reapertura_id
    FOREIGN KEY (usuario_reapertura_id) REFERENCES usuario (usuario_id);

INSERT INTO parametro (clave, valor, descripcion) VALUES
 ('MARGEN_ORDEN_PORCENTAJE', '10', 'Margen que se suma a la orden de preparación sugerida, en porcentaje');

ALTER TABLE notificacion DROP CONSTRAINT ck_notificacion_tipo;
ALTER TABLE notificacion ADD CONSTRAINT ck_notificacion_tipo CHECK (tipo IN ('EVENTO_NUEVO', 'SENA', 'CONTRATO',
    'CONFIRMACION', 'MODIFICACION', 'REPROGRAMACION', 'CANCELACION', 'PLANNER_ASIGNADA', 'ALERTA_STOCK', 'ASISTENCIA_REAL',
    'ORDEN_PREPARACION'));
