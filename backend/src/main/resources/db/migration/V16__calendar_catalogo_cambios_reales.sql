-- No reescribir V14/V15 ya aplicadas. Solo encolar cambios efectivos de la proyección pública.
DROP TRIGGER tg_calendar_turno ON turno;
CREATE TRIGGER tg_calendar_turno AFTER UPDATE OF nombre, hora_inicio, hora_fin, cruza_medianoche ON turno
    FOR EACH ROW WHEN ((OLD.nombre, OLD.hora_inicio, OLD.hora_fin, OLD.cruza_medianoche)
        IS DISTINCT FROM (NEW.nombre, NEW.hora_inicio, NEW.hora_fin, NEW.cruza_medianoche))
    EXECUTE FUNCTION fn_calendar_encolar_catalogo();

DROP TRIGGER tg_calendar_salon ON salon;
CREATE TRIGGER tg_calendar_salon AFTER UPDATE OF nombre ON salon
    FOR EACH ROW WHEN (OLD.nombre IS DISTINCT FROM NEW.nombre)
    EXECUTE FUNCTION fn_calendar_encolar_catalogo();

DROP TRIGGER tg_calendar_tipo ON tipo_evento;
CREATE TRIGGER tg_calendar_tipo AFTER UPDATE OF nombre ON tipo_evento
    FOR EACH ROW WHEN (OLD.nombre IS DISTINCT FROM NEW.nombre)
    EXECUTE FUNCTION fn_calendar_encolar_catalogo();
