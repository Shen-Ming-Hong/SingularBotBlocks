# Patches to the pinned JCEF source

The upstream base remains CinemaMod/java-cef commit
`a78e832f9f13c2c688caea3d04d8b84fcd238d94` with Chromium 116.

`0001-audio-class-loader.patch` replaces the audio callback's two raw JNI
`FindClass` lookups with JCEF's existing `ScopedJNIClass` helper. The helper
resolves through the class loader registered by JCEF, which can see MCEF's
classes inside NeoForge. The observed failure and upstream-source investigation
are recorded in the project's native acceptance notes.

Preparation accepts the original source or an already-applied patch, and rejects
an unrecognized implementation. Each runtime manifest records the applied patch
filename and SHA-256. Packages include the patch alongside upstream license and
notice files. The upstream authors and license remain unchanged.

The installer and compilation checks do not certify browser interaction or exit.
Rebuild the affected platform package and repeat actual browser open/close/reopen,
audio callback and normal-game exit acceptance before marking this fix verified.

The test fixture in `scripts/fixtures/jcef-a78e832-audio-handler.cpp` is the
unchanged upstream `native/audio_handler.cpp` from the same commit, SHA-256
`727bcd660513bc85c87ffaa54eaa82987c8f25aff508e00b5b60a2320b0c12ea`.
Its original Chromium Embedded Framework BSD-style notice is preserved.

`0002-macos-keyboard.patch` repairs the pinned fork's macOS key translation:
committed UTF-16 CHAR events bypass the US physical-key lookup, raw editing
keys receive CEF virtual key codes, and Home/End receive native mappings.
Minecraft omits Return from its character callback; the mod's browser subclass
also forwards its character event on macOS. Browser navigation/zoom shortcuts
are disabled in the connected editor to keep its Minecraft GUI scale stable.

The unchanged upstream keyboard fixture is
`scripts/fixtures/jcef-a78e832-browser-keyboard.cpp`, SHA-256
`43926dd340c674480541d23d4ae3e659e124161be917a22fd7d7ac6e51cf8910`.
Patch SHA-256:
`ee4c45b3e6ab2daeeeafb73d29bceae04105b9d6932fd61cd2213d6913260fb5`.
The Apple Silicon archive was rebuilt with this patch; native Minecraft observed
Return inserting a newline and Backspace deleting text. Command selection,
clipboard, committed Chinese input and IME composition remain unverified.
Native browser run 37810163139 (`ac5acfab`) rebuilt all four packages with
both patches. The changed keyboard implementation is macOS-only: Linux only
gains two unused JNI constants and Windows code is unchanged. Only the Apple
Silicon package has native-game evidence; earlier Linux/Windows CI startup
evidence does not certify the rebuilt packages.

For the current macOS SDK, Apple Silicon CEF targets macOS 11.0 rather than the
Intel 10.13 minimum. Preparation passes the invoking Python executable to CMake;
use the existing Python 3.11 runtime because upstream gsutil does not support
Python 3.14. Compiler/tool versions and the retained failed attempts are recorded
in the UI alignment evidence rather than treated as gameplay passes.
