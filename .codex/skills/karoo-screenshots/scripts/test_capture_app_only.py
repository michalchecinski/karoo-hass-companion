#!/usr/bin/env python3
"""Exercise capture failure isolation with fake ADB and real macOS image tools."""

import os
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import unittest
import zlib


SCRIPT = Path(__file__).with_name("capture-app-only.sh")
AVD = "karoo-hass-capture-regression"


def png(width, height):
    def chunk(kind, data):
        return (struct.pack(">I", len(data)) + kind + data
                + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))

    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress((b"\0" + b"\x22\x55\x88" * width) * height))
            + chunk(b"IEND", b""))


FAKE_ADB = r'''#!/usr/bin/env python3
import os
from pathlib import Path
import sys

root = Path(os.environ["CAPTURE_TEST_ROOT"])
case = os.environ["CAPTURE_TEST_CASE"]
args = sys.argv[1:]
assert args[:2] == ["-s", "emulator-5560"], args
command = args[2:]
captured = root / "captured"
if command == ["emu", "avd", "name"]:
    if case == "probe-failed" and captured.exists():
        sys.exit(1)
    print("OtherAvd" if case == "wrong-avd" else "karoo-hass-capture-regression")
    print("OK")
elif command == ["shell", "dumpsys", "activity", "activities"]:
    package = "com.karoohass"
    if case == "wrong-app" or (case == "app-switched" and captured.exists()):
        package = "com.example.other"
    key = case.removeprefix("key-") if case.startswith("key-") else "ResumedActivity"
    print(f"  {key}: ActivityRecord{{abc u0 {package}/.MainActivity t42}}")
elif command == ["shell", "wm", "size"]:
    print("Physical size: 480x800")
elif command == ["exec-out", "screencap", "-p"]:
    captured.touch()
    if case == "capture-failed":
        sys.stdout.buffer.write(b"partial capture")
        sys.exit(1)
    name = "wrong-size.png" if case == "wrong-png-size" else "raw.png"
    sys.stdout.buffer.write(b"not a PNG" if case == "malformed-png" else (root / name).read_bytes())
else:
    raise AssertionError(command)
'''


@unittest.skipUnless(shutil.which("sips") and shutil.which("zsh"), "Requires macOS sips and zsh")
class CaptureTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="karoo-capture-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        adb = self.root / "platform-tools" / "adb"
        adb.parent.mkdir()
        adb.write_text(FAKE_ADB)
        adb.chmod(0o755)
        (self.root / "raw.png").write_bytes(png(480, 800))
        (self.root / "wrong-size.png").write_bytes(png(320, 800))
        self.output = self.root / "existing image.png"
        self.original = b"existing tracked image must survive"
        self.output.write_bytes(self.original)

    def command(self, output=None, avd=AVD, serial="emulator-5560"):
        return ["zsh", str(SCRIPT), serial, str(output or self.output), avd]

    def environment(self, case):
        return dict(os.environ, ANDROID_SDK_ROOT=str(self.root),
                    CAPTURE_TEST_ROOT=str(self.root), CAPTURE_TEST_CASE=case)

    def capture(self, case, **kwargs):
        return subprocess.run(self.command(**kwargs), env=self.environment(case),
                              capture_output=True, text=True, timeout=30)

    def assert_preserved(self, result):
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.output.read_bytes(), self.original)
        self.assertFalse(list(self.root.glob(".karoo-capture.*")))

    def test_rejects_shared_avd_and_physical_device(self):
        self.assert_preserved(self.capture("normal", avd="Karoo"))
        self.assert_preserved(self.capture("normal", serial="physical-device"))

    def test_rejects_mismatched_avd_identity(self):
        self.assert_preserved(self.capture("wrong-avd"))

    def test_rejects_another_app_before_capture(self):
        self.assert_preserved(self.capture("wrong-app"))
        self.assertFalse((self.root / "captured").exists())

    def test_app_switch_during_capture_preserves_existing_image(self):
        self.assert_preserved(self.capture("app-switched"))
        self.assertTrue((self.root / "captured").exists())

    def test_failed_or_invalid_capture_preserves_existing_image(self):
        for case in ("capture-failed", "malformed-png", "wrong-png-size", "probe-failed"):
            with self.subTest(case=case):
                self.assert_preserved(self.capture(case))

    def test_supported_activity_formats_produce_real_cropped_png(self):
        for key in ("ResumedActivity", "mResumedActivity", "topResumedActivity"):
            with self.subTest(key=key):
                result = self.capture("key-" + key)
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                data = self.output.read_bytes()
                self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n")
                self.assertEqual(struct.unpack(">II", data[16:24]), (480, 656))
                self.assertFalse(list(self.root.glob(".karoo-capture.*")))

    def test_parallel_candidates_do_not_share_temporary_files(self):
        outputs = [self.root / "first.png", self.root / "second.png"]
        processes = [subprocess.Popen(self.command(output=p), env=self.environment("normal"),
                                     stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
                     for p in outputs]
        for process in processes:
            stdout, stderr = process.communicate(timeout=30)
            self.assertEqual(process.returncode, 0, stdout + stderr)
        self.assertTrue(all(p.read_bytes().startswith(b"\x89PNG\r\n\x1a\n") for p in outputs))
        self.assertFalse(list(self.root.glob(".karoo-capture.*")))


if __name__ == "__main__":
    unittest.main(verbosity=2)
