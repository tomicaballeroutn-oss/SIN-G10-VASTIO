-- Precondición de la seña (A3-01): la seña solo se registra cuando el evento tiene cargados la cantidad de invitados y los
-- servicios de las categorías marcadas como requeridas para señar. Es el dato a partir del cual la organización determina el
-- precio y, por lo tanto, la seña. El sistema no calcula el precio: solo valida que el dato esté antes de dejar señar.
-- Análogo a requerida_para_confirmar: configurable por categoría (UI-06), sin desplegables fijos.

ALTER TABLE categoria_servicio
    ADD COLUMN requerida_para_senar boolean NOT NULL DEFAULT false;

-- Las decididas con el cliente: el menú (plato principal) y la bodega fijan el precio junto con la cantidad de invitados.
UPDATE categoria_servicio SET requerida_para_senar = true WHERE categoria_id IN (2, 6);
