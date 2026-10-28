-- PDF original: evita reconstruir comprobantes con datos actuales del evento o nuevas plantillas.
-- Los comprobantes de V12 conservan su identidad. Sin original no se inventa su contenido histórico.
ALTER TABLE comprobante_sena ADD COLUMN pdf_original bytea;
ALTER TABLE comprobante_sena ADD CONSTRAINT ck_comprobante_sena_pdf
    CHECK (pdf_original IS NULL OR (octet_length(pdf_original) >= 5 AND substring(pdf_original FROM 1 FOR 5) = decode('255044462d', 'hex')));

CREATE FUNCTION proteger_comprobante_sena() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP <> 'INSERT' THEN
        RAISE EXCEPTION 'comprobante_sena es inmutable' USING ERRCODE = '23514';
    END IF;
    IF NEW.pdf_original IS NULL THEN
        RAISE EXCEPTION 'Un comprobante nuevo requiere su PDF original' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_comprobante_sena_inmutable BEFORE INSERT OR UPDATE OR DELETE ON comprobante_sena
    FOR EACH ROW EXECUTE FUNCTION proteger_comprobante_sena();
