-- =====================================================================
-- Vastio · V11 · Moneda de la seña (Sprint 4, mejora M-02).
-- La seña se registra en pesos (ARS) o en dólares (USD), sin conversión. Es el único dato económico.
-- =====================================================================

ALTER TABLE evento ADD COLUMN moneda_sena varchar(3);

-- Las señas registradas hasta ahora fueron en pesos.
UPDATE evento SET moneda_sena = 'ARS' WHERE importe_sena IS NOT NULL;

ALTER TABLE evento ADD CONSTRAINT ck_evento_moneda_sena CHECK (moneda_sena IN ('ARS', 'USD'));
-- Importe y moneda van juntos: no hay importe sin moneda ni moneda sin importe.
ALTER TABLE evento ADD CONSTRAINT ck_evento_sena_con_moneda CHECK ((importe_sena IS NULL) = (moneda_sena IS NULL));
