import io
import json
import pathlib
import tarfile
import tempfile
import unittest

import native_browser as native


class NativeInstallTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temp.name)
        self.platform = "linux_amd64"

    def tearDown(self):
        self.temp.cleanup()

    def archive(self, target="linux_amd64", commit=None):
        directory = self.root / "package" / target
        directory.mkdir(parents=True)
        manifest = {"platform": target, "jcef_commit": commit or native.VERSIONS["jcef_commit"]}
        (directory / "computercraftedu-native.json").write_text(json.dumps(manifest))
        (directory / "libjcef.so").write_bytes(b"test-native-library")
        archive = self.root / "native.tar.gz"
        with tarfile.open(archive, "w:gz") as t:
            t.add(directory, arcname=target)
        return archive

    def test_verified_install_preserves_other_settings(self):
        archive = self.archive()
        game = self.root / "game"
        settings = game / "config/mcef/mcef.properties"
        settings.parent.mkdir(parents=True)
        settings.write_text("# user settings\nuse-cache=true\nskip-download=false\n")
        native.install(archive, game, native.digest(archive), self.platform)
        self.assertTrue((game / "mods/mcef-libraries/linux_amd64/libjcef.so").exists())
        evidence = json.loads((game / "mods/mcef-libraries/linux_amd64/computercraftedu-install.json").read_text())
        self.assertEqual(evidence["archive_sha256"], native.digest(archive))
        self.assertIn("use-cache=true", settings.read_text())
        self.assertIn("skip-download=true", settings.read_text())
        self.assertNotIn("skip-download=false", settings.read_text())

    def test_corrupt_archive_does_not_change_game(self):
        archive = self.archive()
        game = self.root / "game"
        with self.assertRaisesRegex(ValueError, "SHA-256"):
            native.install(archive, game, "0" * 64, self.platform)
        self.assertFalse(game.exists())

    def test_wrong_architecture_does_not_change_game(self):
        archive = self.archive("macos_arm64")
        game = self.root / "game"
        with self.assertRaisesRegex(ValueError, "does not match"):
            native.install(archive, game, native.digest(archive), self.platform)
        self.assertFalse(game.exists())

    def test_wrong_jcef_version_does_not_change_game(self):
        archive = self.archive(commit="0" * 40)
        game = self.root / "game"
        with self.assertRaisesRegex(ValueError, "pinned runtime"):
            native.install(archive, game, native.digest(archive), self.platform)
        self.assertFalse(game.exists())

    def test_existing_install_is_not_overwritten(self):
        archive = self.archive()
        game = self.root / "game"
        existing = game / "mods/mcef-libraries/linux_amd64/libjcef.so"
        existing.parent.mkdir(parents=True)
        existing.write_bytes(b"existing")
        with self.assertRaisesRegex(ValueError, "preserved"):
            native.install(archive, game, native.digest(archive), self.platform)
        self.assertEqual(existing.read_bytes(), b"existing")

    def test_tar_traversal_rejected(self):
        archive = self.root / "unsafe.tar.gz"
        with tarfile.open(archive, "w:gz") as t:
            entry = tarfile.TarInfo("../outside")
            entry.size = 4
            t.addfile(entry, io.BytesIO(b"oops"))
        with self.assertRaises(tarfile.FilterError):
            native.extract(archive, self.root / "staging")
        self.assertFalse((self.root / "outside").exists())


if __name__ == "__main__":
    unittest.main()
