"""rclone REAL con backend local temporal; PostgreSQL simulado. Linux/WSL.

Requiere python3, sh, flock, tar, sha256sum y rclone (o RCLONE_TEST_BIN).
No lee configuraciones reales ni conecta Drive.
"""
import hashlib
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tarfile
import time
import unittest

import test_respaldo_local as local

RCLONE = os.environ.get("RCLONE_TEST_BIN") or shutil.which("rclone")


class TransferenciaTest(unittest.TestCase):
    def setUp(self):
        self.base = local.RespaldoLocalTest()
        self.base.setUp()
        self.addCleanup(self.base.doCleanups)
        self.dest = self.base.root / "externo"
        config = self.base.root / "rclone.conf"
        config.write_text("[prueba]\ntype = local\n", encoding="utf-8")
        self.base.env.update(RESPALDO_TRANSFERIR="1", RCLONE_CONFIG=str(config),
                             RCLONE_DESTINO="prueba:" + str(self.dest), REAL_RCLONE=RCLONE)
        self.wrapper()

    def wrapper(self, slow=False):
        self.base.tool("rclone", '''
echo SENSITIVE_SENTINEL >&2
printf '%s' "$$" > "$TEST_ROOT/rclone.pid"
exec "$REAL_RCLONE" "$@"''' + (" --bwlimit 64k\n" if slow else "\n"))

    @property
    def external(self):
        return self.dest / self.base.env["RESPALDO_ENTORNO"] / self.base.env["PGDATABASE"]

    def run_backup(self, args=("--once",)):
        return self.base.run_backup(args=args)

    def assert_safe_log(self, result):
        self.assertNotIn("SENSITIVE_SENTINEL", result.stdout + result.stderr)
        self.assertNotIn(str(self.dest), result.stdout + result.stderr)

    def check_package(self):
        packages = list(self.external.glob("vastio-*"))
        self.assertEqual(len(packages), 1)
        package = packages[0]
        self.assertTrue((package / "COMPLETO").is_file())
        expected = (package / "SHA256SUMS").read_text().split()[0]
        self.assertEqual(hashlib.sha256((package / "datos.tar").read_bytes()).hexdigest(), expected)
        with tarfile.open(package / "datos.tar") as archive:
            names = archive.getnames()
            self.assertFalse(any(x.endswith((".partial", ".tmp")) or ".respaldo.lock" in x for x in names))
            dumps = [x for x in names if x.endswith(".dump")]
            self.assertEqual(len(dumps), 1)
            self.assertEqual(archive.extractfile(dumps[0]).read(), b"archivo sintetico completo")
            self.assertEqual(archive.extractfile("legajo/contrato.txt").read(), b"contrato sintetico")
        return package

    def test_exito_exclusiones_recuperacion_e_integridad(self):
        self.base.scope.mkdir(parents=True)
        (self.base.scope / "vastio-incompleto.partial").write_text("incompleto")
        (self.base.legajo / "en-carga.partial").write_text("incompleto")
        (self.base.legajo / ".respaldo.lock").write_text("no transferir")
        result = self.run_backup()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assert_safe_log(result)
        package = self.check_package()
        self.assertEqual(set(x.name for x in package.iterdir()), {"COMPLETO", "datos.tar", "SHA256SUMS"})
        self.assertTrue(list(self.base.scope.glob("*.dump")))

    def test_fallo_conserva_local_y_reintento_sin_duplicar(self):
        old = self.base.old_file(self.base.scope / "vastio-historico.dump")
        self.dest.write_text("destino bloqueado sintetico")
        result = self.run_backup()
        self.assertNotEqual(result.returncode, 0)
        self.assert_safe_log(result)
        self.assertTrue(old.exists())  # Retencion no corre tras transferencia fallida.
        self.assertEqual(len(list(self.base.scope.glob("*.dump"))), 2)
        ready = list((self.base.scope / "transferencias").glob("*.listo"))
        self.assertEqual(len(ready), 1)
        before = (ready[0] / "datos.tar").read_bytes()
        self.dest.unlink()  # Solo archivo sintetico creado por esta prueba.
        result = self.run_backup(args=("--transfer-only",))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assert_safe_log(result)
        package = self.check_package()
        self.assertEqual((package / "datos.tar").read_bytes(), before)
        mtimes = {x.name: x.stat().st_mtime_ns for x in package.iterdir()}
        # Simula corte tras publicar marcador remoto pero antes del ack local.
        (ready[0] / ".enviado").unlink()
        result = self.run_backup(args=("--transfer-only",))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual({x.name: x.stat().st_mtime_ns for x in package.iterdir()}, mtimes)
        self.assertEqual(len(list(self.base.scope.glob("*.dump"))), 2)

    def test_fallo_dump_no_transfiere(self):
        result = self.base.run_backup(mode="dump-fail")
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.dest.exists())
        self.assertFalse((self.base.root / "rclone.pid").exists())

    def test_separacion_entorno_y_base(self):
        result = self.run_backup()
        self.assertEqual(result.returncode, 0, result.stderr)
        original = self.external
        self.base.env["RESPALDO_ENTORNO"] = "otro_entorno"
        self.base.env["PGDATABASE"] = "otra_base"
        result = self.run_backup()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(len(list(original.glob("*/COMPLETO"))), 1)
        self.assertEqual(len(list(self.external.glob("*/COMPLETO"))), 1)

    def test_paquete_local_alterado_no_se_publica(self):
        self.dest.write_text("bloqueado")
        self.assertNotEqual(self.run_backup().returncode, 0)
        ready = next((self.base.scope / "transferencias").glob("*.listo"))
        with (ready / "datos.tar").open("ab") as handle:
            handle.write(b"alterado")
        self.dest.unlink()
        result = self.run_backup(args=("--transfer-only",))
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.dest.exists())

    def cola_pendiente(self):
        self.dest.write_text("destino temporalmente bloqueado")
        for _ in range(2):
            self.assertNotEqual(self.run_backup().returncode, 0)
        self.dest.unlink()
        return sorted((self.base.scope / "transferencias").glob("*.listo"))

    def verificar_enviado(self, paquete):
        remoto = self.external / paquete.name.removesuffix(".listo")
        self.assertTrue((remoto / "COMPLETO").is_file())
        self.assertTrue((paquete / ".enviado").is_file())
        self.assertEqual(hashlib.sha256((remoto / "datos.tar").read_bytes()).hexdigest(),
                         (paquete / "SHA256SUMS").read_text().split()[0])

    def verificar_pendiente(self, paquete):
        self.assertTrue(paquete.is_dir())
        self.assertTrue((paquete / "datos.tar").is_file())
        self.assertFalse((paquete / ".enviado").exists())
        self.assertFalse((self.external / paquete.name.removesuffix(".listo") / "COMPLETO").exists())

    def test_corrupto_seguido_de_valido_y_reintento(self):
        primero, segundo = self.cola_pendiente()
        original = (primero / "datos.tar").read_bytes()
        (primero / "datos.tar").write_bytes(original + b"alterado")
        result = self.run_backup(args=("--transfer-only",))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("paquete=" + primero.name.removesuffix(".listo"), result.stderr)
        self.assertIn("etapa=verificar_local", result.stderr)
        self.assert_safe_log(result)
        self.verificar_pendiente(primero)
        self.verificar_enviado(segundo)
        # Reponer SOLO los bytes sinteticos originales del fixture.
        (primero / "datos.tar").write_bytes(original)
        mtime = (self.external / segundo.name.removesuffix(".listo") / "datos.tar").stat().st_mtime_ns
        self.assertEqual(self.run_backup(args=("--transfer-only",)).returncode, 0)
        self.verificar_enviado(primero)
        self.assertEqual((self.external / segundo.name.removesuffix(".listo") / "datos.tar").stat().st_mtime_ns, mtime)
        self.assertEqual(len(list(self.base.scope.glob("*.dump"))), 2)

    def test_fallo_transferencia_seguido_de_valido(self):
        # Cubrir tambien check y marcador: un fallo no puede saltarse un paso.
        for etapa in ("copy", "check", "copyto"):
            with self.subTest(etapa=etapa):
                case = TransferenciaTest()
                case.setUp()
                try:
                    primero, segundo = case.cola_pendiente()
                    case.base.env.update(TEST_FAIL_PACKAGE=primero.name.removesuffix(".listo"), TEST_FAIL_STAGE=etapa)
                    case.base.tool("rclone", '''
echo SENSITIVE_SENTINEL >&2
if [ "$1" = "$TEST_FAIL_STAGE" ]; then
    case "$2" in */"$TEST_FAIL_PACKAGE".listo|*/"$TEST_FAIL_PACKAGE".listo/COMPLETO) exit 19 ;; esac
fi
exec "$REAL_RCLONE" "$@"
''')
                    result = case.run_backup(args=("--transfer-only",))
                    self.assertEqual(result.returncode, 19, result.stderr)
                    self.assertIn("etapa=rclone_" + ("completo" if etapa == "copyto" else etapa), result.stderr)
                    case.assert_safe_log(result)
                    case.verificar_pendiente(primero)
                    case.verificar_enviado(segundo)
                    case.wrapper()
                    self.assertEqual(case.run_backup(args=("--transfer-only",)).returncode, 0)
                    case.verificar_enviado(primero)
                finally:
                    case.doCleanups()

    def test_todos_los_paquetes_validos(self):
        paquetes = self.cola_pendiente()
        self.assertEqual(self.run_backup(args=("--transfer-only",)).returncode, 0)
        for paquete in paquetes:
            self.verificar_enviado(paquete)

    def test_todos_los_paquetes_fallidos_y_reintento(self):
        paquetes = self.cola_pendiente()
        # Destino no escribible para todos; rclone real, sin simulacion.
        self.dest.write_text("bloqueado otra vez")
        result = self.run_backup(args=("--transfer-only",))
        self.assertNotEqual(result.returncode, 0)
        for paquete in paquetes:
            self.assertIn("paquete=" + paquete.name.removesuffix(".listo"), result.stderr)
            self.verificar_pendiente(paquete)
        self.dest.unlink()
        self.assertEqual(self.run_backup(args=("--transfer-only",)).returncode, 0)
        for paquete in paquetes:
            self.verificar_enviado(paquete)

    def test_fallo_empaquetado_no_bloquea_pendientes(self):
        paquetes = self.cola_pendiente()
        self.base.tool("tar", 'exit 17\n')
        result = self.run_backup()
        self.assertEqual(result.returncode, 17, result.stderr)
        self.assertIn("etapa=preparar_transferencia", result.stderr)
        self.assertNotIn("ciclo_completo", result.stdout)
        for paquete in paquetes:
            self.verificar_enviado(paquete)
        self.assertTrue(list((self.base.scope / "transferencias").glob("*.partial")))
        self.assertEqual(len(list(self.base.scope.glob("*.dump"))), 3)

    def test_fallo_empaquetado_conserva_espera_diaria(self):
        self.base.tool("tar", 'exit 17\n')
        result = self.run_backup(args=())
        self.assertEqual(result.returncode, 23, result.stderr)
        self.assertEqual((self.base.root / "esperas").read_text().splitlines(), ["86400", "86400"])
        self.assertNotIn("ciclo_completo", result.stdout)

    def test_transferencia_fallida_no_cambia_espera_diaria(self):
        self.dest.write_text("bloqueado")
        result = self.run_backup(args=())
        self.assertEqual(result.returncode, 23, result.stderr)  # sleep simulado existente
        self.assertEqual((self.base.root / "esperas").read_text().splitlines(), ["86400", "86400"])

    def test_term_y_kill_durante_rclone_y_reintento(self):
        for sig in (signal.SIGTERM, signal.SIGKILL):
            with self.subTest(signal=sig):
                case = TransferenciaTest()
                case.setUp()
                try:
                    case.wrapper(slow=True)
                    (case.base.legajo / "grande.bin").write_bytes(os.urandom(3 * 1024 * 1024))
                    p = subprocess.Popen([local.SHELL, str(case.base.wrapper), str(local.SCRIPT), "--once"],
                                         env=case.base.env, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                         text=True, start_new_session=True)
                    def clean():
                        try:
                            os.killpg(p.pid, signal.SIGKILL)
                        except ProcessLookupError:
                            pass
                        p.communicate(timeout=5)
                    case.addCleanup(clean)
                    case.base.esperar(lambda: (case.base.root / "rclone.pid").exists())
                    # Esperar bytes en destino, no solo el arranque de rclone: una
                    # interrupcion puede dejar el nombre final con datos truncados.
                    case.base.esperar(lambda: any(x.is_file() and x.stat().st_size > 65536
                                                  for x in case.external.rglob("*")))
                    p.send_signal(sig)
                    p.wait(timeout=5)
                    self.assertEqual(p.returncode, 143 if sig == signal.SIGTERM else -signal.SIGKILL)
                    self.assertTrue(list(case.base.scope.glob("*.dump")))
                    self.assertFalse(list(case.external.glob("*/COMPLETO")))
                    if sig == signal.SIGKILL:
                        blocked = case.run_backup(args=("--transfer-only",))
                        self.assertEqual(blocked.returncode, 1)  # Hijos vivos conservan flock.
                        os.killpg(p.pid, signal.SIGKILL)  # Solo grupo exclusivo de la prueba.
                    p.communicate(timeout=5)
                    case.wrapper()
                    retry = case.run_backup(args=("--transfer-only",))
                    self.assertEqual(retry.returncode, 0, retry.stderr)
                    case.check_package()
                    self.assertEqual(len(list(case.base.scope.glob("*.dump"))), 1)
                finally:
                    case.doCleanups()


if __name__ == "__main__":
    if not sys.platform.startswith("linux") or not RCLONE or not shutil.which("flock"):
        raise SystemExit("Se requiere Linux/WSL y rclone real; no se simula la transferencia.")
    unittest.main(verbosity=2)
