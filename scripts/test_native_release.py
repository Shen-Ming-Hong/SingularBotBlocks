import hashlib
import json
import pathlib
import shutil
import tempfile
import unittest

import native_browser as native
import native_release as release


class NativeReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temp.name)
        self.dist = self.root / "dist"
        for target in native.VERSIONS["platforms"]:
            package = self.dist / ("native-" + target)
            package.mkdir(parents=True)
            archive = package / (target + ".tar.gz")
            archive.write_bytes(target.encode())
            (package / (target + ".tar.gz.sha256")).write_text(native.digest(archive) + "  " + archive.name + "\n")
            evidence = self.dist / ("native-evidence-" + target)
            evidence.mkdir()
            self.write_source(target)

    def tearDown(self):
        self.temp.cleanup()

    def write_source(self, target, **changes):
        manifest = {
            "jcef_commit": native.VERSIONS["jcef_commit"], "cef_version": native.VERSIONS["cef_version"],
            "platform": target, "native_patches": release.expected_patches(),
        }
        manifest.update(changes)
        (self.dist / ("native-evidence-" + target) / "build-source.json").write_text(json.dumps(manifest))

    def test_stages_all_platforms_with_checksums_and_notes(self):
        output = self.root / "assets"
        release.stage(self.dist, output, "123", "abc")
        sums = (output / "SHA256SUMS").read_text()
        for target in native.VERSIONS["platforms"]:
            self.assertTrue((output / (target + ".tar.gz")).is_file())
            self.assertTrue((output / (target + "-build-source.json")).is_file())
            self.assertIn(native.digest(output / (target + ".tar.gz")), sums)
        notes = (output / "release-notes.md").read_text()
        self.assertIn("run 123", notes)
        self.assertIn("marked macOS Intel unsupported", notes)

    def test_stages_run_without_unsupported_platform(self):
        shutil.rmtree(self.dist / "native-macos_amd64")
        shutil.rmtree(self.dist / "native-evidence-macos_amd64")
        output = self.root / "assets"
        results = release.stage(self.dist, output, "123", "abc")
        self.assertNotIn("macos_amd64", [r["platform"] for r in results])
        self.assertFalse((output / "macos_amd64.tar.gz").exists())

    def test_rejects_missing_supported_platform(self):
        shutil.rmtree(self.dist / "native-macos_arm64")
        with self.assertRaises(ValueError):
            release.stage(self.dist, self.root / "assets", "123", "abc")

    def test_rejects_changed_archive(self):
        (self.dist / "native-linux_amd64/linux_amd64.tar.gz").write_bytes(b"tampered")
        with self.assertRaisesRegex(ValueError, "SHA-256"):
            release.stage(self.dist, self.root / "assets", "1", "a")
        self.assertFalse((self.root / "assets").exists())

    def test_accepts_windows_crlf_patch_digests(self):
        # Windows checkouts record the digest of the CRLF-converted patch files.
        crlf = [
            {"file": p.name, "sha256": hashlib.sha256(p.read_bytes().replace(b"\n", b"\r\n")).hexdigest()}
            for p in sorted((native.ROOT / "native/patches").glob("*.patch"))
        ]
        self.write_source("windows_amd64", native_patches=crlf)
        release.stage(self.dist, self.root / "assets", "1", "a")

    def test_rejects_different_patch_set(self):
        self.write_source("windows_amd64", native_patches=release.expected_patches()[:1])
        with self.assertRaisesRegex(ValueError, "patch set"):
            release.stage(self.dist, self.root / "assets", "1", "a")

    def test_rejects_missing_evidence(self):
        (self.dist / "native-evidence-macos_arm64/build-source.json").unlink()
        with self.assertRaisesRegex(ValueError, "Missing"):
            release.stage(self.dist, self.root / "assets", "1", "a")


if __name__ == "__main__":
    unittest.main()
