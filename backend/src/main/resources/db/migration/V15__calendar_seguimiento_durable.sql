-- Captura mínima en la transacción de origen. La proyección Calendar se ejecuta DESPUÉS del commit.
CREATE TABLE calendar_seguimiento (
    evento_id bigint PRIMARY KEY REFERENCES evento(evento_id) ON DELETE CASCADE,
    revision bigint NOT NULL DEFAULT 1,
    procesada bigint NOT NULL DEFAULT 0,
    intentos integer NOT NULL DEFAULT 0,
    ultimo_error varchar(60),
    proximo_intento timestamptz NOT NULL DEFAULT now(),
    actualizado timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_calendar_seguimiento_revision CHECK (revision >= procesada AND procesada >= 0)
);

CREATE FUNCTION fn_calendar_encolar_evento() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' OR (NEW.estado, NEW.unidad_id, NEW.tipo_evento_id, NEW.hora_inicio)
            IS DISTINCT FROM (OLD.estado, OLD.unidad_id, OLD.tipo_evento_id, OLD.hora_inicio) THEN
        INSERT INTO calendar_seguimiento(evento_id) VALUES(NEW.evento_id)
        ON CONFLICT(evento_id) DO UPDATE SET revision=calendar_seguimiento.revision+1,
            proximo_intento=now(), actualizado=now();
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_calendar_evento AFTER INSERT OR UPDATE OF estado, unidad_id, tipo_evento_id, hora_inicio ON evento
    FOR EACH ROW EXECUTE FUNCTION fn_calendar_encolar_evento();

CREATE FUNCTION fn_calendar_encolar_catalogo() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_TABLE_NAME = 'turno' THEN
        INSERT INTO calendar_seguimiento(evento_id)
        SELECT e.evento_id FROM evento e JOIN unidad_comercializable u USING(unidad_id) WHERE u.turno_id=NEW.turno_id
        ON CONFLICT(evento_id) DO UPDATE SET revision=calendar_seguimiento.revision+1, proximo_intento=now(), actualizado=now();
    ELSIF TG_TABLE_NAME = 'salon' THEN
        INSERT INTO calendar_seguimiento(evento_id)
        SELECT e.evento_id FROM evento e JOIN unidad_comercializable u USING(unidad_id) WHERE u.salon_id=NEW.salon_id
        ON CONFLICT(evento_id) DO UPDATE SET revision=calendar_seguimiento.revision+1, proximo_intento=now(), actualizado=now();
    ELSE
        INSERT INTO calendar_seguimiento(evento_id)
        SELECT e.evento_id FROM evento e WHERE e.tipo_evento_id=NEW.tipo_evento_id
        ON CONFLICT(evento_id) DO UPDATE SET revision=calendar_seguimiento.revision+1, proximo_intento=now(), actualizado=now();
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_calendar_turno AFTER UPDATE OF nombre, hora_inicio, hora_fin, cruza_medianoche ON turno
    FOR EACH ROW EXECUTE FUNCTION fn_calendar_encolar_catalogo();
CREATE TRIGGER tg_calendar_salon AFTER UPDATE OF nombre ON salon
    FOR EACH ROW EXECUTE FUNCTION fn_calendar_encolar_catalogo();
CREATE TRIGGER tg_calendar_tipo AFTER UPDATE OF nombre ON tipo_evento
    FOR EACH ROW EXECUTE FUNCTION fn_calendar_encolar_catalogo();

INSERT INTO calendar_seguimiento(evento_id)
    SELECT e.evento_id FROM evento e LEFT JOIN publicacion_calendar p USING(evento_id)
    WHERE e.estado IN ('SENADO','CONTRATADO','CONFIRMADO') OR p.evento_id IS NOT NULL;

-- Corregir el indicador técnico; no cambiar identidad, historial ni estado comercial.
UPDATE publicacion_calendar SET retiro_solicitado=false WHERE estado='RETIRADO';
