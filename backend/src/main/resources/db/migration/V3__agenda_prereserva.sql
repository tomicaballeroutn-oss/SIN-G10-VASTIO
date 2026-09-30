-- =====================================================================
-- Vastio · V3 · Pre-reserva: código de evento, tablas de solo inserción
-- y búsqueda de clientes.
-- =====================================================================

-- Último número de evento de cada año, para el código EV-AAAA-NNNNN.
-- Se incrementa con INSERT … ON CONFLICT … RETURNING: la fila del año queda bloqueada hasta el
-- final de la transacción, así dos pre-reservas simultáneas nunca reciben el mismo número.
-- Si la transacción se deshace, el número no se consume (a diferencia de una secuencia).
CREATE TABLE numerador_evento (
    anio smallint NOT NULL,
    ultimo integer NOT NULL,
    CONSTRAINT pk_numerador_evento PRIMARY KEY (anio),
    CONSTRAINT ck_numerador_evento_1 CHECK (ultimo BETWEEN 1 AND 99999)
);

-- Solo inserción (RNF-SEG-03). La aplicación es dueña de las tablas, así que revocarle permisos
-- no alcanza: un trigger rechaza UPDATE y DELETE sin importar quién los intente.
CREATE FUNCTION fn_solo_insercion() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'La tabla % es de solo inserción: no admite %', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER tg_cambio_estado_evento_solo_insercion
    BEFORE UPDATE OR DELETE ON cambio_estado_evento
    FOR EACH ROW EXECUTE FUNCTION fn_solo_insercion();

CREATE TRIGGER tg_modificacion_evento_solo_insercion
    BEFORE UPDATE OR DELETE ON modificacion_evento
    FOR EACH ROW EXECUTE FUNCTION fn_solo_insercion();

CREATE TRIGGER tg_reprogramacion_solo_insercion
    BEFORE UPDATE OR DELETE ON reprogramacion
    FOR EACH ROW EXECUTE FUNCTION fn_solo_insercion();

CREATE TRIGGER tg_movimiento_stock_solo_insercion
    BEFORE UPDATE OR DELETE ON movimiento_stock
    FOR EACH ROW EXECUTE FUNCTION fn_solo_insercion();

-- Búsqueda de clientes al pre-reservar: por documento exacto y por comienzo de nombre.
CREATE INDEX ix_cliente_documento ON cliente (documento);
CREATE INDEX ix_cliente_nombre ON cliente (lower(nombre) text_pattern_ops);
