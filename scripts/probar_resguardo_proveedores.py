"""Prueba el mecanismo real contra la base descartable creada por MigracionProveedoresIT."""
import argparse
import json
from pathlib import Path
import tempfile

import resguardo_proveedores as backup


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--container", required=True)
    parser.add_argument("--database", required=True)
    parser.add_argument("--user", required=True)
    parser.add_argument("--output", required=True, type=Path)
    opts = parser.parse_args()
    opts.output.mkdir(parents=True, exist_ok=True)
    folder = Path(tempfile.mkdtemp(prefix="resguardo-v9-", dir=opts.output)) / "archivo"
    args = argparse.Namespace(directory=folder, database=opts.database, user=opts.user,
                              source_container=opts.container, pg_bin="", writes_stopped=True)
    backup.create(args)
    backup.verify(args)
    args.writes_stopped = False
    try:
        backup.authorize(args)
        raise AssertionError("Se autorizó sin declarar la pausa de escrituras")
    except RuntimeError as error:
        assert "writes-stopped" in str(error)
    args.writes_stopped = True
    # An altered extract must never authorize the destructive migration.
    extract = folder / "datos-afectados.json"
    original = extract.read_bytes()
    extract.write_bytes(original + b" ")
    try:
        backup.authorize(args)
        raise AssertionError("Se aceptó un archivo alterado")
    except RuntimeError as error:
        assert "SHA-256" in str(error)
    finally:
        extract.write_bytes(original)
    backup.authorize(args)
    cmd = backup.source(args, "psql")
    assert backup.query(cmd, "SELECT count(*) FROM resguardo_proveedores_v9") == "1"
    manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
    assert manifest["counts"] == {"proveedores": 3, "bebidas": 2, "ingresos": 2}
    # Drift invalidates authorization. An existing receipt cannot match the new state.
    backup.query(cmd, "UPDATE proveedor SET telefono = 'posterior al respaldo' WHERE proveedor_id = 1")
    try:
        backup.authorize(args)
        raise AssertionError("Se autorizó una fuente distinta del respaldo")
    except RuntimeError as error:
        assert "obsoleto" in str(error)
    assert backup.query(cmd, "SELECT huella FROM resguardo_proveedores_v9") != backup.query(cmd, backup.FINGERPRINT)
    # Restore the original value and keep the real receipt for the Java Flyway migration.
    backup.query(cmd, "UPDATE proveedor SET telefono = '123' WHERE proveedor_id = 1")
    assert backup.query(cmd, "SELECT huella FROM resguardo_proveedores_v9") == backup.query(cmd, backup.FINGERPRINT)
    print("Resguardo real: create, verify, authorize, archivo alterado y fuente obsoleta: OK")


if __name__ == "__main__":
    main()
