-- =====================================================================
-- Vastio · V4 · Servicios contratados (Sprint 2).
-- =====================================================================

-- Categorías de bebida: un cambio en un evento confirmado le llega a Compras (docs/sprint-2.md, decisión 10).
-- Configurable como visible_en_cocina, porque el nombre de la categoría se edita en Parámetros.
ALTER TABLE categoria_servicio ADD COLUMN avisa_a_compras boolean NOT NULL DEFAULT false;
UPDATE categoria_servicio SET avisa_a_compras = true WHERE categoria_id IN (6, 7); -- Bodega, Tipo de barra

-- Un texto por categoría y evento (decisión 5): la ficha muestra una descripción libre por categoría.
CREATE UNIQUE INDEX ux_servicio_contratado_evento_categoria ON servicio_contratado (evento_id, categoria_id);
