"""Pruebas de control de flujo: herramientas PostgreSQL simuladas, nunca una BD.

Linux (o WSL), Python 3.11+, sh POSIX y flock real. Sin Docker ni credenciales.
"""
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import unittest


SCRIPT = Path(__file__).with_name("respaldo_local.sh").resolve()
SHELL = os.environ.get("VASTIO_TEST_SH") or shutil.which("sh")


def posix(path):
    return str(path).replace("\\", "/")


class RespaldoLocalTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="vastio-m10-sintetico-")
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.backups = self.root / "respaldos"
        self.legajo = self.root / "origen-legajo"
        self.legajo.mkdir()
        (self.legajo / "contrato.txt").write_text("contrato sintetico", encoding="utf-8")
        # No se hereda ninguna variable PG* de la maquina del operador.
        self.env = {k: v for k, v in os.environ.items() if not k.startswith("PG")}
        self.env.update(
            PGDATABASE="base_sintetica", PGHOST="destino-inexistente.invalid",
            PGUSER="sintetico", RESPALDO_ENTORNO="test_m10",
            RESPALDO_TRANSFERIR="0",
            RESPALDO_DIRECTORIO=posix(self.backups),
            RESPALDO_LEGAJO=posix(self.legajo),
            TEST_BIN=posix(self.bin), TEST_ROOT=posix(self.root), TEST_MODE="ok",
            TEST_PYTHON=sys.executable,
        )
        self.wrapper = self.root / "ejecutar.sh"
        self.wrapper.write_text(
            'export PATH="/usr/bin:/bin:$PATH"\n'
            'if command -v cygpath > /dev/null; then TEST_BIN=$(cygpath -u "$TEST_BIN"); fi\n'
            'export PATH="$TEST_BIN:$PATH"\nexec /bin/sh "$@"\n', encoding="utf-8")
        self.tool("pg_dump", '''
printf 'parcial sintetico' > "$3"
case "$TEST_MODE" in
  dump-fail) exit 7 ;;
  empty) : > "$3" ;;
  hold) exec "$TEST_PYTHON" "$TEST_ROOT/pausar.py" dump "$3" ;;
  *) printf 'archivo sintetico completo' > "$3" ;;
esac
''')
        self.tool("pg_restore", '''
[ "$1" = --list ] || exit 90
[ "$TEST_MODE" != invalid ] || exit 8
''')
        self.tool("sleep", '''
printf '%s\n' "$1" >> "$TEST_ROOT/esperas"
if [ "$TEST_MODE" = hold-wait ]; then exec "$TEST_PYTHON" "$TEST_ROOT/pausar.py" espera; fi
if [ -f "$TEST_ROOT/ya-espero" ]; then exit 23; fi
touch "$TEST_ROOT/ya-espero"
''')
        # Proceso sin descendientes: permite observar FD heredado y senales reales.
        (self.root / "pausar.py").write_text('''
import os, pathlib, sys, time
root = pathlib.Path(os.environ["TEST_ROOT"])
(root / "hijo.pid").write_text(str(os.getpid()))
(root / "listo").touch()
while not (root / "continuar").exists():
    time.sleep(0.02)
if sys.argv[1] == "dump":
    pathlib.Path(sys.argv[2]).write_text("archivo sintetico completo")
''', encoding="utf-8")

    def tool(self, name, content):
        path = self.bin / name
        path.write_text("#!/bin/sh\nset -eu\n" + content, encoding="utf-8", newline="\n")
        path.chmod(0o700)

    @property
    def scope(self):
        return self.backups / "test_m10" / "base_sintetica"

    def run_backup(self, mode="ok", args=("--once",)):
        self.env["TEST_MODE"] = mode
        return subprocess.run(
            [SHELL, posix(self.wrapper), posix(SCRIPT), *args],
            env=self.env, capture_output=True, text=True, timeout=15,
        )

    def old_file(self, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("respaldo sintetico previo", encoding="utf-8")
        os.utime(path, (1_000_000_000, 1_000_000_000))
        return path

    def test_publica_al_final_y_no_sobrescribe(self):
        for _ in range(2):
            result = self.run_backup()
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("ciclo_completo", result.stdout)
        self.assertEqual(len(list(self.scope.glob("*.dump"))), 2)
        self.assertFalse(list(self.scope.glob("*.partial")))
        self.assertEqual((self.scope / "legajo/contrato.txt").read_text(), "contrato sintetico")
        self.assertFalse((self.scope / ".en-curso").exists())

    def test_fallos_no_publican_ni_aplican_retencion(self):
        old = self.old_file(self.scope / "vastio-anterior.dump")
        for mode, stage, code in [("dump-fail", "pg_dump", 7),
                                  ("empty", "validar_archivo", 1),
                                  ("invalid", "validar_archivo", 8)]:
            with self.subTest(mode=mode):
                result = self.run_backup(mode)
                self.assertEqual(result.returncode, code, result.stderr)
                self.assertIn("etapa=" + stage, result.stderr)
                self.assertNotIn("OK", result.stdout)
                self.assertEqual(list(self.scope.glob("*.dump")), [old])
                self.assertFalse((self.scope / ".en-curso").exists())
        self.assertEqual(len(list(self.scope.glob("*.partial"))), 3)
        retry = self.run_backup()
        self.assertEqual(retry.returncode, 0, retry.stderr)
        self.assert_dumps_completos(1)

    def test_fallo_legajo_no_publica(self):
        self.env["RESPALDO_LEGAJO"] = posix(self.root / "no-existe")
        result = self.run_backup()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("etapa=legajo", result.stderr)
        self.assertFalse(list(self.scope.glob("*.dump")))
        self.assertTrue(list(self.scope.glob("*.partial")))

    def test_retencion_no_toca_otros_entornos_bases_o_subcarpetas(self):
        own = self.old_file(self.scope / "vastio-antiguo.dump")
        preserved = [
            self.backups / "otro_entorno/base_sintetica/vastio-antiguo.dump",
            self.backups / "test_m10/otra_base/vastio-antiguo.dump",
            self.backups / "vastio-historico.dump",
            self.scope / "subcarpeta/vastio-antiguo.dump",
            self.scope / "vastio-incompleto.partial",
            self.scope / "ajeno.dump",
        ]
        for path in preserved:
            self.old_file(path)
        result = self.run_backup()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(own.exists())
        self.assertTrue(all(path.exists() for path in preserved))

    def test_error_retencion_es_visible_sin_invalidar_dump_terminado(self):
        self.tool("find", "exit 11\n")
        result = self.run_backup()
        self.assertEqual(result.returncode, 11)
        self.assertIn("etapa=retencion", result.stderr)
        self.assertEqual(len(list(self.scope.glob("*.dump"))), 1)
        self.assertNotIn("ciclo_completo", result.stdout)

    def test_entorno_y_base_obligatorios_sin_escape_de_ruta(self):
        for key in ("RESPALDO_ENTORNO", "PGDATABASE"):
            original = self.env[key]
            for value in ("", "../otra", "a/b", "a\\b", "base con espacios"):
                with self.subTest(key=key, value=value):
                    self.env[key] = value
                    self.assertEqual(self.run_backup().returncode, 2)
            self.env[key] = original
        self.assertFalse(self.backups.exists())

    def test_marcador_antiguo_y_archivo_lock_no_bloquean(self):
        # La version anterior pudo dejar este directorio; no se elimina ni adopta.
        old = self.scope / ".en-curso"
        old.mkdir(parents=True)
        lock = self.scope / ".respaldo.lock"
        lock.write_text("marcador sin proceso")
        inode = lock.stat().st_ino
        result = self.run_backup()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(old.exists())
        self.assertEqual(lock.stat().st_ino, inode)
        self.assertEqual(lock.read_text(), "marcador sin proceso")

    def test_conserva_periodicidad_y_propaga_error_de_espera(self):
        result = self.run_backup(args=())
        self.assertEqual(result.returncode, 23, result.stderr)
        self.assertEqual((self.root / "esperas").read_text().splitlines(), ["86400", "86400"])
        self.assertEqual(len(list(self.scope.glob("*.dump"))), 2)
        self.assertIn("etapa=espera", result.stderr)

    def esperar(self, condition):
        limit = time.monotonic() + 5
        while time.monotonic() < limit:
            if condition():
                return
            time.sleep(0.02)
        self.fail("Timeout esperando proceso sintetico")

    def iniciar_pausado(self, phase):
        self.env["TEST_MODE"] = "hold" if phase == "dump" else "hold-wait"
        process = subprocess.Popen(
            [SHELL, posix(self.wrapper), posix(SCRIPT), *(["--once"] if phase == "dump" else [])],
            env=self.env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
            start_new_session=True,
        )
        # Grupo exclusivo creado por esta prueba; incluye solo sus procesos sinteticos.
        def limpiar():
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            process.communicate(timeout=5)
        self.addCleanup(limpiar)
        self.esperar(lambda: (self.root / "listo").exists())
        return process

    def assert_dumps_completos(self, expected):
        dumps = list(self.scope.glob("*.dump"))
        self.assertEqual(len(dumps), expected)
        for path in dumps:
            self.assertEqual(path.read_text(), "archivo sintetico completo")

    def test_term_e_int_durante_dump_y_espera(self):
        # Cada combinacion usa su propio directorio/proceso y limpieza.
        for phase in ("dump", "espera"):
            for sig, code in ((signal.SIGTERM, 143), (signal.SIGINT, 130)):
                with self.subTest(phase=phase, signal=sig):
                    case = RespaldoLocalTest()
                    case.setUp()
                    try:
                        p = case.iniciar_pausado(phase)
                        p.send_signal(sig)
                        out, err = p.communicate(timeout=3)
                        self.assertEqual(p.returncode, code, err)
                        case.assert_dumps_completos(0 if phase == "dump" else 1)
                        if phase == "dump":
                            self.assertNotIn("publicado=", out)
                            self.assertEqual(len(list(case.scope.glob("*.partial"))), 1)
                        self.assertIn("codigo=" + str(code), err)
                        retry = case.run_backup()
                        self.assertEqual(retry.returncode, 0, retry.stderr)
                    finally:
                        case.doCleanups()

    def test_kill_dump_respeta_hijo_superviviente_y_luego_recupera(self):
        p = self.iniciar_pausado("dump")
        lock = self.scope / ".respaldo.lock"
        inode = lock.stat().st_ino
        p.kill()
        p.wait(timeout=3)
        # El hijo aun escribe: hereda FD 9 y mantiene flock aunque murio el padre.
        retry = self.run_backup()
        self.assertEqual(retry.returncode, 1, retry.stderr)
        self.assertIn("Otro respaldo activo", retry.stderr)
        self.assertEqual(lock.stat().st_ino, inode)
        self.assert_dumps_completos(0)
        (self.root / "continuar").touch()
        p.communicate(timeout=3)
        self.esperar(lambda: subprocess.run(
            ["flock", "-n", str(lock), "true"], capture_output=True).returncode == 0)
        self.assert_dumps_completos(0)  # Hijo terminado no publica el .partial.
        self.assertEqual(len(list(self.scope.glob("*.partial"))), 1)
        retry = self.run_backup()
        self.assertEqual(retry.returncode, 0, retry.stderr)
        self.assert_dumps_completos(1)

    def test_kill_espera_no_bloquea_siguiente(self):
        p = self.iniciar_pausado("espera")
        p.kill()
        p.wait(timeout=3)
        # Incluso con sleep superviviente NO debe haber descriptor de bloqueo.
        retry = self.run_backup()
        self.assertEqual(retry.returncode, 0, retry.stderr)
        self.assert_dumps_completos(2)
        (self.root / "continuar").touch()
        p.communicate(timeout=3)

    def test_dos_respaldos_concurrentes_no_borran_lock_ajeno(self):
        p = self.iniciar_pausado("dump")
        lock = self.scope / ".respaldo.lock"
        inode = lock.stat().st_ino
        for _ in range(2):
            other = self.run_backup()
            self.assertEqual(other.returncode, 1, other.stderr)
            self.assertEqual(lock.stat().st_ino, inode)
            self.assert_dumps_completos(0)
        (self.root / "continuar").touch()
        _, err = p.communicate(timeout=3)
        self.assertEqual(p.returncode, 0, err)
        self.assert_dumps_completos(1)
        self.assertEqual(self.run_backup().returncode, 0)

    def test_no_hay_lock_durante_espera(self):
        p = self.iniciar_pausado("espera")
        retry = self.run_backup()
        self.assertEqual(retry.returncode, 0, retry.stderr)
        self.assert_dumps_completos(2)
        p.terminate()
        p.communicate(timeout=3)


if __name__ == "__main__":
    if not sys.platform.startswith("linux") or not SHELL or not shutil.which("flock"):
        raise SystemExit("Se requiere Linux/WSL con sh POSIX y flock real; no se simula el bloqueo.")
    unittest.main(verbosity=2)
