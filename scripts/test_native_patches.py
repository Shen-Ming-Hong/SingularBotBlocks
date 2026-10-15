"""Verify patch integrity and repeatable preparation against the pinned upstream file."""
import pathlib
import subprocess
import tempfile
import unittest

import native_browser as native


class NativePatchTests(unittest.TestCase):
    def source(self, scratch):
        source = pathlib.Path(scratch)
        (source / "native").mkdir()
        original = pathlib.Path(__file__).parent / "fixtures/jcef-a78e832-audio-handler.cpp"
        (source / "native/audio_handler.cpp").write_bytes(original.read_bytes())
        keyboard = pathlib.Path(__file__).parent / "fixtures/jcef-a78e832-browser-keyboard.cpp"
        (source / "native/CefBrowser_N.cpp").write_bytes(keyboard.read_bytes())
        subprocess.run(["git", "init", "--quiet", str(source)], check=True)
        return source

    def test_pinned_upstream_can_be_prepared_twice_without_changing_the_result(self):
        with tempfile.TemporaryDirectory() as scratch:
            source = self.source(scratch)
            patches = native.apply_native_patches(source)
            first = (source / "native/audio_handler.cpp").read_bytes()
            keyboard = (source / "native/CefBrowser_N.cpp").read_bytes()
            self.assertEqual(native.apply_native_patches(source), patches)
            self.assertEqual((source / "native/audio_handler.cpp").read_bytes(), first)
            self.assertEqual((source / "native/CefBrowser_N.cpp").read_bytes(), keyboard)
            for patch in patches:
                self.assertEqual(patch["sha256"], native.digest(native.ROOT / "native/patches" / patch["file"]))

    def test_unknown_keyboard_source_is_rejected_and_preserved(self):
        with tempfile.TemporaryDirectory() as scratch:
            source = self.source(scratch)
            file = source / "native/CefBrowser_N.cpp"
            file.write_text("Unrecognized keyboard implementation\n")
            before = file.read_bytes()
            with self.assertRaisesRegex(ValueError, "does not match the pinned source"):
                native.apply_native_patches(source)
            self.assertEqual(file.read_bytes(), before)

    def test_unknown_source_is_rejected_without_overwriting_it(self):
        with tempfile.TemporaryDirectory() as scratch:
            source = self.source(scratch)
            file = source / "native/audio_handler.cpp"
            file.write_text("Unrecognized upstream implementation\n")
            before = file.read_bytes()
            with self.assertRaisesRegex(ValueError, "does not match the pinned source"):
                native.apply_native_patches(source)
            self.assertEqual(file.read_bytes(), before)


if __name__ == "__main__":
    unittest.main()
