#!/usr/bin/env python3
"""Resguardo V8 y comprobación mediante restauración en PostgreSQL 17 descartable.

No ejecuta V9. create/verify no escriben en la base fuente. authorize solo instala
un recibo transitorio después de restaurar y comparar el respaldo nuevamente.
Python 3, Docker y clientes PostgreSQL 17 (o --source-container) son requisitos.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time
import uuid


SNAPSHOT = """
SELECT jsonb_build_object(
 'proveedores', (SELECT coalesce(jsonb_agg(to_jsonb(p) ORDER BY proveedor_id), '[]'::jsonb) FROM public.proveedor p),
 'bebidas', (SELECT coalesce(jsonb_agg(jsonb_build_array(bebida_id, proveedor_habitual_id) ORDER BY bebida_id), '[]'::jsonb) FROM public.bebida),
 'ingresos', (SELECT coalesce(jsonb_agg(jsonb_build_array(ingreso_id, proveedor_id) ORDER BY ingreso_id), '[]'::jsonb) FROM public.ingreso)
)::text
""".strip()
FINGERPRINT = "SELECT encode(sha256(convert_to((" + SNAPSHOT + "), 'UTF8')), 'hex')"
RECEIPT = """
CREATE TABLE IF NOT EXISTS public.resguardo_proveedores_v9 (
 id boolean PRIMARY KEY CHECK (id), base_oid oid NOT NULL, base_nombre text NOT NULL,
 huella text NOT NULL, archivo_sha256 text NOT NULL, manifiesto_sha256 text NOT NULL,
 verificado_en timestamptz NOT NULL
);
REVOKE ALL ON public.resguardo_proveedores_v9 FROM PUBLIC;
"""


def run(args, *, data=None, output=None):
    result = subprocess.run(args, input=data, stdout=output or subprocess.PIPE,
                            stderr=subprocess.PIPE, timeout=600,
                            env={**os.environ, "PGCLIENTENCODING": "UTF8"})
    if result.returncode:
        # Do not echo connection strings or environment secrets.
        raise RuntimeError(f"Falló {Path(args[0]).name}: " + result.stderr.decode("utf-8", errors="replace"))
    return result.stdout


def sha(path):
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def save_json(path, value):
    with path.open("x", encoding="utf-8", newline="\n") as handle:
        json.dump(value, handle, ensure_ascii=False, indent=2)
        handle.write("\n")


def source(args, executable):
    if args.source_container:
        return ["docker", "exec", "-i", args.source_container, executable,
                "-U", args.user, "-d", args.database]
    return [str(Path(args.pg_bin) / executable) if args.pg_bin else executable,
            "-U", args.user, "-d", args.database]


def query(command, sql):
    return run(command + ["-X", "-A", "-t", "--set=ON_ERROR_STOP=1"],
               data=(sql + ";\n").encode()).decode("utf-8").strip()


def identity(args):
    return json.loads(query(source(args, "psql"), """
        SELECT jsonb_build_object('database', current_database(), 'oid', oid)
        FROM pg_database WHERE datname = current_database()"""))


def require_v8(command):
    version = query(command, "SELECT version FROM public.flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1")
    if version != "8":
        raise RuntimeError("Se requiere una base Flyway V8 validada, sin V9 aplicada.")


def restore_and_snapshot(folder):
    """Only restores to a fresh container created here; never accepts a restore URL."""
    name = "vastio-v9-restore-" + uuid.uuid4().hex
    started = False
    try:
        run(["docker", "run", "--rm", "-d", "--name", name, "--network", "none",
             "-e", "POSTGRES_HOST_AUTH_METHOD=trust", "postgres:17"])
        started = True
        cmd = ["docker", "exec", "-i", name, "psql", "-U", "postgres", "-d", "postgres"]
        for attempt in range(60):
            try:
                query(cmd, "SELECT 1")
                break
            except RuntimeError:
                if attempt == 59:
                    raise
                time.sleep(0.5)
        with (folder / "v8.dump").open("rb") as dump:
            restored = subprocess.run(["docker", "exec", "-i", name, "pg_restore", "-U", "postgres",
                                       "-d", "postgres", "--exit-on-error", "--no-owner", "--no-privileges"],
                                      stdin=dump, capture_output=True, timeout=600)
        if restored.returncode:
            raise RuntimeError("Falló restauración descartable: " + restored.stderr.decode(errors="replace"))
        require_v8(cmd)
        snapshot = query(cmd, SNAPSHOT)
        fingerprint = query(cmd, FINGERPRINT)
        return snapshot, fingerprint
    finally:
        if started:
            run(["docker", "rm", "-f", name])


def create(args):
    folder = args.directory.resolve()
    folder.mkdir(parents=True, exist_ok=False)
    os.chmod(folder, 0o700)  # Windows: operator must also apply the documented ACL.
    require_v8(source(args, "psql"))
    with (folder / "v8.dump").open("xb") as dump:
        run(source(args, "pg_dump") + ["--format=custom"], output=dump)
    snapshot, fingerprint = restore_and_snapshot(folder)
    # Source drift while dumping/restoring invalidates the preparation.
    if query(source(args, "psql"), FINGERPRINT) != fingerprint:
        raise RuntimeError("La fuente cambió durante el respaldo: detener escrituras y repetir en un directorio nuevo.")
    with (folder / "datos-afectados.json").open("x", encoding="utf-8", newline="\n") as handle:
        handle.write(snapshot + "\n")
    data = json.loads(snapshot)
    save_json(folder / "manifest.json", {
        "format": 1, "source": identity(args), "flyway": "8", "fingerprint": fingerprint,
        "created_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "counts": {key: len(value) for key, value in data.items()},
        "files": {file: sha(folder / file) for file in ("v8.dump", "datos-afectados.json")},
        "restore_verified": True,
    })
    print("Respaldo creado y restaurado en contenedor descartable. Sin autorización de V9 en la fuente.")


def verify(args):
    folder = args.directory.resolve()
    manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
    if manifest.get("format") != 1 or manifest.get("flyway") != "8":
        raise RuntimeError("Formato de manifiesto inesperado.")
    for file in ("v8.dump", "datos-afectados.json"):
        if sha(folder / file) != manifest["files"][file]:
            raise RuntimeError("SHA-256 incorrecto: " + file)
    snapshot, fingerprint = restore_and_snapshot(folder)
    if snapshot + "\n" != (folder / "datos-afectados.json").read_text(encoding="utf-8"):
        raise RuntimeError("El extracto no coincide con la restauración completa.")
    if fingerprint != manifest["fingerprint"]:
        raise RuntimeError("La huella no coincide con la restauración.")
    return manifest


def authorize(args):
    if not args.writes_stopped:
        raise RuntimeError("Detener escrituras y confirmar --writes-stopped antes de autorizar.")
    manifest = verify(args)  # Always restore again; a boolean in a file is not proof.
    command = source(args, "psql")
    require_v8(command)
    if identity(args) != manifest["source"]:
        raise RuntimeError("El manifiesto pertenece a otra base.")
    digest = sha(args.directory / "manifest.json")
    # All interpolated hashes have been recalculated and checked, never use arbitrary SQL from the manifest.
    fingerprint = query(command, FINGERPRINT)
    if fingerprint != manifest["fingerprint"]:
        raise RuntimeError("La fuente cambió: respaldo obsoleto. Repetir create.")
    dump_sha = sha(args.directory / "v8.dump")
    sql = f"""
BEGIN;
SET LOCAL lock_timeout = '10s';
LOCK TABLE public.proveedor, public.bebida, public.ingreso IN SHARE MODE;
{RECEIPT}
DO $guard$ BEGIN
 IF ({FINGERPRINT}) <> '{fingerprint}' THEN
  RAISE EXCEPTION 'La fuente cambió mientras se autorizaba: repetir respaldo';
 END IF;
END $guard$;
DELETE FROM public.resguardo_proveedores_v9;
INSERT INTO public.resguardo_proveedores_v9
 SELECT true, oid, current_database(), '{fingerprint}', '{dump_sha}', '{digest}', now()
 FROM pg_database WHERE datname = current_database();
COMMIT;
"""
    query(command, sql)
    print("Recibo instalado para esta base y huella. V9 NO ejecutada. Mantener escrituras detenidas.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["create", "verify", "authorize"])
    parser.add_argument("directory", type=Path)
    parser.add_argument("--database", required=True, help="Nombre de base, nunca URI/conninfo con secretos")
    parser.add_argument("--user", required=True)
    parser.add_argument("--pg-bin", default="", help="Directorio de clientes PostgreSQL 17")
    parser.add_argument("--source-container", help="Contenedor fuente; omitir para usar PGHOST/PGPORT/PGPASSFILE")
    parser.add_argument("--writes-stopped", action="store_true")
    args = parser.parse_args()
    if any(part in args.database for part in ("=", ":", "/", "\\")):
        parser.error("Usar nombre de base simple; configurar conexión mediante variables libpq.")
    try:
        if args.mode == "create":
            create(args)
        elif args.mode == "verify":
            verify(args)
            print("Hashes, restauración completa y extracto verificados. Fuente sin cambios.")
        else:
            authorize(args)
    except (RuntimeError, OSError, ValueError, KeyError, subprocess.TimeoutExpired) as error:
        parser.exit(1, str(error) + "\n")


if __name__ == "__main__":
    main()
