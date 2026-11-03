-- =====================================================================
-- Vastio · V5 · Catálogo de bebidas (Sprint 3).
-- =====================================================================

-- Sin precios ni dinero (docs/sprint-3.md, decisión 1): el sistema maneja cantidades. La única excepción es la seña.
ALTER TABLE bebida DROP COLUMN precio_referencia;
ALTER TABLE consumo_evento DROP COLUMN precio_referencia_aplicado;
ALTER TABLE consumo_evento DROP COLUMN costo_total;

-- Solo bebida con alcohol en esta etapa (decisión 2). Ninguna bebida usa estos tipos todavía.
DELETE FROM tipo_bebida WHERE nombre IN ('Gaseosa', 'Agua');

-- La caja es la forma de mostrar y cargar; todo se guarda en botellas (decisión 3).
UPDATE unidad_manipulacion SET nombre = 'Caja' WHERE nombre = 'Cajón';

ALTER TABLE bebida ALTER COLUMN activo SET DEFAULT true;

-- No hay dos bebidas activas con el mismo nombre y presentación, sin distinguir mayúsculas (decisión 5).
CREATE UNIQUE INDEX ux_bebida_nombre_presentacion_activa ON bebida (lower(nombre), lower(presentacion)) WHERE activo;

-- Solo dígitos, de 8 a 14 (EAN-8, EAN-13, DUN-14) (decisión 6).
ALTER TABLE codigo_barra ADD CONSTRAINT ck_codigo_barra_formato CHECK (codigo ~ '^[0-9]{8,14}$');
CREATE INDEX ix_codigo_barra_bebida ON codigo_barra (bebida_id);
