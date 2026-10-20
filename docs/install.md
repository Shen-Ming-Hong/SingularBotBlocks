# Development and native deployment

This is a gameplay development build, not a classroom release. Full classroom acceptance is still pending. See `gameplay-guide.md` for remote, appearance, program and teacher controls.

## Native build

The **Native browser** GitHub Actions workflow runs on Windows x64, Linux x64 and Apple Silicon for ready pull requests that change the native sources, or by hand. Native build artifacts and source manifests are retained in the private repository's Actions tab for 30 days. To keep a run's exact archives, dispatch the **Native release** workflow with that run id: it checks each archive against its recorded SHA-256, the pinned JCEF/CEF versions and the current patch set, then publishes the archives, build manifests and `SHA256SUMS` as a private prerelease. Native artifact creation and release storage are not runtime certification.

macOS Intel (`macos_amd64`) is unsupported: no physical Intel Mac is available for acceptance. CI no longer builds it (owner decision 2026-10-15, to save macOS runner minutes); earlier packages stay in their releases for reference, and Rosetta results remain supplemental Apple Silicon evidence. `browser-smoke.yml` and a local `scripts/native_browser.py build --platform macos_amd64` on Intel hardware can still produce it.

For a local build, install Python 3.12, JDK 17, CMake 3.31.6 and Ninja 1.11.1.4. Use MSVC on Windows, Xcode command-line tools on macOS, and GCC plus GTK/X11 development libraries on Linux. Set `JAVA_HOME` to the JDK 17 directory.

```sh
python scripts/native_browser.py build --platform macos_arm64
python scripts/native_browser.py package --platform macos_arm64
```

Other supported platform identifiers: `windows_amd64`, `linux_amd64`, `macos_amd64`. The build refuses a mismatched host architecture. CEF archives are verified against fixed upstream SHA-1 values, then recorded with SHA-256. Output native archives use SHA-256.

The macOS packaging patch skips the upstream demo Java/Ant app bundle: MCEF already contains the matching Java classes. JNI code and native helper targets are unchanged. The generated runtime has the directory layout MCEF expects and retains upstream notices.

## Prepare an isolated client

Create a new Minecraft 1.21.1 / NeoForge game directory using Java 21. The mod is built against NeoForge 21.1.252 and accepts any 21.1.x from 21.1.252 (21.1.262 has been tested on a client); use the same NeoForge line on the server. Install the 1.21.1 NeoForge artifacts of CC: Tweaked 1.120.2 and MCEF 2.1.6. CC: Tweaked retains "forge" in this NeoForge artifact filename. Verify the loader and Minecraft version, not just the filename.

Obtain the runtime archive and its `.sha256` file from the same native workflow run. Replace the checksum below with the value from that file:

```sh
python scripts/native_browser.py install native-artifacts/macos_arm64.tar.gz \
  --game-dir /absolute/path/to/new-instance \
  --sha256 ACTUAL_ARCHIVE_SHA256
```

The installer checks the checksum, architecture and pinned JCEF source version. It installs below `mods/mcef-libraries` and sets `skip-download=true` in `config/mcef/mcef.properties`. It preserves unrelated settings and refuses to replace an existing native installation. Keep archives for offline installation. Minecraft's own assets and mod dependencies must also be prepared before disconnecting the internet.

## Dedicated server

Server deployment includes NeoForge, CC: Tweaked 1.120.2 and the same Singular Bot Blocks JAR as the clients; other mods in a pack that add blocks or items must also match on both sides. MCEF and Chromium are client-only and must not be required on the server. Dedicated servers on NeoForge 21.1.256 and 21.1.262 boot with this mod. The mod does not keep chunks loaded: a robot runs only while a player keeps its chunk loaded, so a program stops when its owner leaves and nobody else is nearby. Headless development server boot/shutdown without either dependency has passed locally; classroom multiplayer acceptance remains pending. Gameplay GameTests also run without MCEF/JCEF.

## Verification record

For each runtime target, record the repository commit, OS/CPU, Java version, archive checksum, browser initialization, actual in-game rendering, Blockly input, Java/JavaScript roundtrip, close/reopen and offline behavior. A generated native archive alone is insufficient.

## Build this mod

Use JDK 21 and Node.js 22. `./gradlew build` produces the NeoForge 1.21.1 JAR.
`./gradlew runClient` uses `run-1.21.1/`; `./gradlew runServer` uses
`run-server-1.21.1/` and excludes MCEF from its development runtime.
The pinned Chromium 116 JNI packages are built with JDK 17 and validated with
Java 21 in the game. Keep native build and game Java requirements distinct.
