# Singular Bot Blocks

A classroom mod for Minecraft Java 1.21.1 / NeoForge: students program robots with Blockly blocks in game, running on CC: Tweaked.

> **Disclaimer.** Singular Bot Blocks is an independent, unofficial project inspired by ComputerCraftEdu. It is not affiliated with, endorsed by or connected to TeacherGaming LLC, Daniel Ratcliffe, the CC: Tweaked project or Mojang. NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.

**Status: gameplay development build with Junior turtles, remote control, appearance, server Blockly/Lua execution, debugger, libraries, teacher controls and guarded restoration. This is not a classroom-ready release.** Full legacy, native input and multiplayer acceptance remain pending.

## The robot

![Three robots whose edge light runs in silver-blue, green and red](docs/images/robot-signals.gif)

A light runs around all twelve edges of every robot, in every body colour, and its colour shows what the robot's program is doing:

| Light | Meaning |
| --- | --- |
| Silver-blue | Idle: no program is running. |
| Green | A program is running (or paused). |
| Red | The program stopped with an error. Clearing the error in the robot's controller turns the light blue again. |

The robot also glows on its own. Its eye screen and edge light stay bright in the dark, and a placed robot lights up the blocks around it as brightly as glowstone (light level 15), so students can find and follow it at night or in a cave. White robots also give off small sparks in the light's colour.

## Fixed versions

| Component | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| Java target | 21 |
| NeoForge | 21.1.252 (build); accepts any later 21.1.x |
| CC: Tweaked | 1.120.2 |
| MCEF (client only) | 2.1.6-1.21.1 |
| Blockly | 11.2.2 |
| JCEF | a78e832f9f13c2c688caea3d04d8b84fcd238d94 |
| CEF | 116.0.27+gd8c85ac+chromium-116.0.5845.190 |

New worlds and a new program format are used; no Minecraft 1.8.9 conversion is included.

## Development

Use a JDK 21, Node.js 22 and Python 3.11 or newer. Development and native deployment instructions are in [docs/install.md](docs/install.md); gameplay instructions are in [繁體中文 / English](docs/gameplay-guide.md). Do not install this development build in a classroom world.

Blockly assets are bundled locally. No CDN is needed during lessons. MCEF's normal binary downloader is currently unavailable, so the native workflow builds the pinned official JCEF source against official CEF distributions. Native packages are prepared before clients are started. A dedicated server requires no MCEF/native browser package.

## License

This project's own code and artwork are released under the [MIT License](LICENSE). Bundled third-party components keep their own licenses (Blockly: Apache-2.0, Noto Sans TC: SIL OFL 1.1, and the Jersey 10 font used for the cover art word mark: SIL OFL 1.1); see [THIRD_PARTY.md](THIRD_PARTY.md).

## Contributing

Issues and pull requests are welcome. Run `./gradlew build`, `npm test --prefix editor` and `python -m unittest discover -s scripts -p 'test_*.py'` before opening a pull request.

## Provenance

Inspired by the original ComputerCraftEdu by Daniel Ratcliffe and TeacherGaming LLC, used only as a reference for behaviour and classroom workflow. No code or artwork from it is included. See [THIRD_PARTY.md](THIRD_PARTY.md).

