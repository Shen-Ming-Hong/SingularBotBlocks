# Third-party sources

| Component | Source | License / attribution |
| --- | --- | --- |
| ComputerCraftEdu | https://www.curseforge.com/minecraft/mc-mods/computercraftedu | Daniel Ratcliffe, TeacherGaming LLC. Behaviour and workflow reference only; no code or artwork from it ships in this mod. |
| CC: Tweaked | https://github.com/cc-tweaked/CC-Tweaked/tree/v1.21.1-1.120.2 | Upstream contains MPL-2.0 and other per-file notices; used as a separate mod/API dependency. |
| MCEF | https://github.com/CinemaMod/mcef/tree/1.21.1 | LGPL-2.1-or-later. Used as a separate, client-only dependency. |
| CinemaMod JCEF | https://github.com/CinemaMod/java-cef/tree/a78e832f9f13c2c688caea3d04d8b84fcd238d94 | Preserve upstream JCEF, CEF and Chromium notices in native packages. |
| CEF | https://cef-builds.spotifycdn.com/index.html | Preserve CEF/Chromium distribution notices. |
| Blockly 11.2.2 | https://github.com/RaspberryPiFoundation/blockly/releases/tag/blockly-v11.2.2 | Apache-2.0. Bundle the upstream LICENSE and notices with editor assets. |
| Noto Sans TC | https://github.com/google/fonts/tree/3be1884c48c3e45b52ecc725676a08f87776373e/ofl/notosanstc | SIL Open Font License 1.1. Copyright 2014–2021 Adobe. Full OFL bundled in editor/assets/OFL.txt. |

Bundled font SHA-256: `864727d210d54f2537bbe23b3a839436c3992af72de9322af5270897246bd44f`.
Blockly's npm tarball omits LICENSE; the bundled Apache license comes from the exact upstream `blockly-v11.2.2` tag.

Do not package CC: Tweaked inside this mod. Native archives contain their original notices and a source manifest.

## Artwork

Every texture in this mod (robot bodies, decorations, controller panel, buttons, tabs,
inventory wells, text field, preview and the hidden remote item) is built from the source art in
`art/codex/`, which was generated for this project with OpenAI Codex image generation on a fixed
pixel grid and shared palette (`art/codex/standard.json`; process and checks in
`art/codex/REPORT.md`). `scripts/generate_textures.py` shrinks it to game size, recolours the
robot paint for the 16 colours and places each face on the robot model UVs. Nothing is copied,
traced or recoloured from ComputerCraftEdu, ComputerCraft, CC: Tweaked, Minecraft, Create or any
other mod. Rebuild with `python3 scripts/generate_textures.py` (Pillow).

The cover art in `art/branding/` (Modrinth and CurseForge icons, cover, GitHub social preview) and
the mod list logo `src/main/resources/logo.png` are drawn by `scripts/generate_branding.py` from
the same robot faces, so they match the game. The word mark uses the Jersey 10 font by The Soft
Type Project Authors, included as `art/branding/Jersey10-Regular.ttf` under the SIL Open Font
License 1.1 (`art/branding/OFL.txt`). No Minecraft logo, font, texture or screenshot is used.

The robot models `models/block/robot_body.json` and `robot_decoration.json` and their texture
layout were written for this project. New Java implementations are written for this addon; the
original 1.8.9 Java code has not been copied into the new runtime.
