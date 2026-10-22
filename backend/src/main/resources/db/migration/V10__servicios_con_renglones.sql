-- =====================================================================
-- Vastio · V10 · Servicios contratados con renglones (Sprint 4, mejora M-09).
-- Cada categoría admite renglones libres con cantidad opcional («Celíaco · 2») y un detalle libre.
-- Nada predefinido: lo pidió así Berni (22/10/2026) para que la carga sea lo más flexible posible.
-- =====================================================================

-- El texto que había pasa a ser el detalle de la categoría. Ahora puede faltar si hay renglones.
ALTER TABLE servicio_contratado ALTER COLUMN descripcion DROP NOT NULL;

-- Renglones de una categoría, en el orden en que se cargaron. Se reemplazan juntos al guardar.
CREATE TABLE servicio_renglon (
    servicio_id bigint NOT NULL,
    orden integer NOT NULL,
    texto varchar(120) NOT NULL,
    cantidad integer,
    CONSTRAINT pk_servicio_renglon PRIMARY KEY (servicio_id, orden),
    CONSTRAINT fk_servicio_renglon_servicio_id FOREIGN KEY (servicio_id) REFERENCES servicio_contratado (servicio_id) ON DELETE CASCADE,
    CONSTRAINT ck_servicio_renglon_texto CHECK (btrim(texto) <> ''),
    CONSTRAINT ck_servicio_renglon_cantidad CHECK (cantidad IS NULL OR cantidad > 0)
);

-- Categorías del Sprint 4 (decisión del 22/10/2026): Menú, Menús especiales, Bodega, Tipo de barra y Otros.
-- Se dan de baja, sin borrar lo cargado, las que no siguen. Recepción, Postre y After quedan visibles en cocina:
-- un evento con algo cargado ahí no lo pierde en la vista de cocina. Extras pasa a visible en cocina (lo muestran
-- el prototipo UI-23 y la historia A7-02); como está dada de baja, solo se nota si se la vuelve a activar.
-- Se cambian por id y solo si conservan el nombre original: en una instalación donde ya se editaron en Parámetros,
-- manda lo que configuraron.
UPDATE categoria_servicio SET nombre = 'Menú' WHERE categoria_id = 2 AND nombre = 'Plato principal'
    AND NOT EXISTS (SELECT 1 FROM categoria_servicio WHERE nombre = 'Menú');
UPDATE categoria_servicio SET activo = false
    WHERE (categoria_id, nombre) IN ((1, 'Recepción'), (3, 'Postre'), (4, 'After'), (8, 'Técnica'), (9, 'Mobiliario'), (10, 'Extras'));
UPDATE categoria_servicio SET visible_en_cocina = true WHERE categoria_id = 10 AND nombre = 'Extras';

INSERT INTO categoria_servicio (nombre, orden, visible_en_cocina, requerida_para_confirmar, avisa_a_compras, activo)
VALUES ('Otros', (SELECT coalesce(max(orden), 0) + 1 FROM categoria_servicio), false, false, false, true)
ON CONFLICT (nombre) DO NOTHING;
