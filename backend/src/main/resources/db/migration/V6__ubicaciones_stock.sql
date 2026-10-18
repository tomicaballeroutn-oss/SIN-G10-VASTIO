-- =====================================================================
-- Vastio · V6 · Ubicaciones de stock (Sprint 3).
-- =====================================================================

-- Tres tipos: depósito madre, depósito de transición y barra (docs/sprint-3.md, decisión 8). Las divisiones internas
-- del depósito no se modelan: se quitan el tipo SECTOR y la ubicación padre. Ninguna ubicación los usa.
ALTER TABLE ubicacion DROP CONSTRAINT ck_ubicacion_1;
ALTER TABLE ubicacion DROP COLUMN ubicacion_padre_id;
ALTER TABLE ubicacion DROP CONSTRAINT ck_ubicacion_tipo;
ALTER TABLE ubicacion ADD CONSTRAINT ck_ubicacion_tipo CHECK (tipo IN ('DEPOSITO', 'TRANSICION', 'BARRA'));

-- Un solo depósito madre activo, y nombres únicos sin distinguir mayúsculas.
CREATE UNIQUE INDEX ux_ubicacion_deposito_madre ON ubicacion (tipo) WHERE tipo = 'DEPOSITO' AND activo;
CREATE UNIQUE INDEX ux_ubicacion_nombre ON ubicacion (lower(nombre));

-- Desde dónde se abastece cada barra: el depósito madre o un depósito de transición. Si es una transición, se puede
-- habilitar el retiro directo del depósito madre como contingencia (decisión 10). Solo las barras tienen origen.
ALTER TABLE ubicacion ADD COLUMN ubicacion_abastecimiento_id smallint;
ALTER TABLE ubicacion ADD COLUMN permite_retiro_directo boolean NOT NULL DEFAULT false;
ALTER TABLE ubicacion ADD CONSTRAINT fk_ubicacion_ubicacion_abastecimiento_id
    FOREIGN KEY (ubicacion_abastecimiento_id) REFERENCES ubicacion (ubicacion_id);
UPDATE ubicacion SET ubicacion_abastecimiento_id = 1 WHERE tipo = 'BARRA';
ALTER TABLE ubicacion ADD CONSTRAINT ck_ubicacion_abastecimiento CHECK (
    (tipo = 'BARRA' AND ubicacion_abastecimiento_id IS NOT NULL)
    OR (tipo <> 'BARRA' AND ubicacion_abastecimiento_id IS NULL AND NOT permite_retiro_directo));

-- Una barra por salón; Avril, hasta dos (decisión 9). Dato del salón, sin pantalla.
ALTER TABLE salon ADD COLUMN maximo_barras smallint NOT NULL DEFAULT 1;
ALTER TABLE salon ADD CONSTRAINT ck_salon_maximo_barras CHECK (maximo_barras >= 1);
UPDATE salon SET maximo_barras = 2 WHERE codigo = 'avril';
