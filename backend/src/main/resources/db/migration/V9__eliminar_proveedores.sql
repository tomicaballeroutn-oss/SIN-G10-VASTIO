-- Decisión 19/10/2026. V1–V8 se conservan intactas.
-- El bloqueo y la comprobación ocurren en la misma transacción que el DDL.
-- Operación manual previa: scripts/resguardo_proveedores.py y docs/eliminacion-proveedores-despliegue.md.
LOCK TABLE public.proveedor, public.bebida, public.ingreso IN ACCESS EXCLUSIVE MODE;

DO $$
DECLARE
    huella_actual text;
    respaldo_valido boolean := false;
BEGIN
    IF EXISTS (SELECT 1 FROM public.proveedor)
       OR EXISTS (SELECT 1 FROM public.bebida WHERE proveedor_habitual_id IS NOT NULL)
       OR EXISTS (SELECT 1 FROM public.ingreso WHERE proveedor_id IS NOT NULL) THEN
        SELECT encode(sha256(convert_to(jsonb_build_object(
            'proveedores', (SELECT coalesce(jsonb_agg(to_jsonb(p) ORDER BY proveedor_id), '[]'::jsonb) FROM public.proveedor p),
            'bebidas', (SELECT coalesce(jsonb_agg(jsonb_build_array(bebida_id, proveedor_habitual_id) ORDER BY bebida_id), '[]'::jsonb) FROM public.bebida),
            'ingresos', (SELECT coalesce(jsonb_agg(jsonb_build_array(ingreso_id, proveedor_id) ORDER BY ingreso_id), '[]'::jsonb) FROM public.ingreso)
        )::text, 'UTF8')), 'hex') INTO huella_actual;

        IF to_regclass('public.resguardo_proveedores_v9') IS NOT NULL THEN
            EXECUTE 'SELECT EXISTS (SELECT 1 FROM public.resguardo_proveedores_v9
                WHERE id AND base_oid = (SELECT oid FROM pg_database WHERE datname = current_database())
                AND base_nombre = current_database() AND huella = $1
                AND archivo_sha256 ~ ''^[0-9a-f]{64}$'' AND manifiesto_sha256 ~ ''^[0-9a-f]{64}$''
                AND verificado_en IS NOT NULL)'
                INTO respaldo_valido USING huella_actual;
        END IF;
        IF NOT respaldo_valido THEN
            RAISE EXCEPTION 'V9 bloqueada: datos históricos sin respaldo validado para el estado actual. Ejecutar el procedimiento de resguardo y restauración verificada antes de migrar.';
        END IF;
    END IF;
END $$;

ALTER TABLE public.bebida DROP CONSTRAINT fk_bebida_proveedor_habitual_id;
ALTER TABLE public.ingreso DROP CONSTRAINT fk_ingreso_proveedor_id;
ALTER TABLE public.bebida DROP COLUMN proveedor_habitual_id;
ALTER TABLE public.ingreso DROP COLUMN proveedor_id;
DROP TABLE public.proveedor;
-- Recibo operativo transitorio, no un archivo histórico dentro de la aplicación.
DROP TABLE IF EXISTS public.resguardo_proveedores_v9;
