#!/usr/bin/env python3
"""Build and install the exact JCEF runtime embedded in MCEF 2.1.6.

No platform is certified by this script: compilation and runtime acceptance
are separate. All downloads stay in the selected build directory.
"""
import argparse
import hashlib
import json
import os
import pathlib
import platform
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
VERSIONS = json.loads((ROOT / "native/versions.json").read_text())


def run(*args, cwd=None):
    subprocess.run([str(a) for a in args], cwd=cwd, check=True)


def digest(path, algorithm="sha256"):
    h = hashlib.new(algorithm)
    with pathlib.Path(path).open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def download(url, destination, expected=None, algorithm="sha256"):
    destination = pathlib.Path(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists() and (expected is None or digest(destination, algorithm) == expected):
        return destination
    temporary = destination.with_name(destination.name + ".partial")
    print("Downloading", url, flush=True)
    try:
        with urllib.request.urlopen(url, timeout=120) as response, temporary.open("wb") as out:
            shutil.copyfileobj(response, out, length=1024 * 1024)
        if expected and digest(temporary, algorithm) != expected:
            raise ValueError("Checksum mismatch: " + destination.name)
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)
    return destination


def extract(archive, destination):
    destination.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive) as t:
        # Python's data filter rejects paths and symlinks escaping the target.
        t.extractall(destination, filter="data")


def host_platform():
    systems = {"Darwin": "macos", "Linux": "linux", "Windows": "windows"}
    machines = {"x86_64": "amd64", "AMD64": "amd64", "aarch64": "arm64", "arm64": "arm64"}
    value = systems.get(platform.system(), "unsupported") + "_" + machines.get(platform.machine(), "unsupported")
    if value not in VERSIONS["platforms"]:
        raise ValueError("Unsupported platform: " + value)
    return value


def apply_native_patches(source):
    """Apply recorded fixes to the pinned upstream tree; refuse ambiguous patches."""
    applied = []
    for patch in sorted((ROOT / "native/patches").glob("*.patch")):
        check = subprocess.run(["git", "-C", str(source), "apply", "--check", str(patch)], capture_output=True)
        if check.returncode == 0:
            run("git", "-C", source, "apply", patch)
        else:
            reverse = subprocess.run(["git", "-C", str(source), "apply", "--reverse", "--check", str(patch)], capture_output=True)
            if reverse.returncode != 0:
                raise ValueError("Native patch does not match the pinned source: " + patch.name)
        applied.append({"file": patch.name, "sha256": digest(patch)})
    return applied


def prepare(work, target):
    if target != host_platform():
        raise ValueError("Build on the matching OS/architecture, not a differently labelled host")
    source = work / "source"
    repository = "https://github.com/CinemaMod/java-cef.git"
    # JCEF generates its version header from real Git metadata. A source
    # tarball alone cannot build. Fetch the exact upstream commit, including
    # when upgrading a previously extracted build directory.
    if not (source / ".git").exists():
        existing = source.exists()
        source.mkdir(parents=True, exist_ok=True)
        run("git", "init", source)
        run("git", "-C", source, "remote", "add", "origin", repository)
        run("git", "-C", source, "fetch", "--depth=1", "origin", VERSIONS["jcef_commit"])
        if existing:
            run("git", "-C", source, "reset", "--mixed", VERSIONS["jcef_commit"])
        else:
            run("git", "-C", source, "checkout", "--detach", VERSIONS["jcef_commit"])
    commit = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    if commit != VERSIONS["jcef_commit"]:
        raise ValueError("JCEF checkout does not match the pinned commit")
    native_patches = apply_native_patches(source)
    cfg = VERSIONS["platforms"][target]
    distribution = "cef_binary_" + VERSIONS["cef_version"] + "_" + cfg["cef"]
    cef_archive = download(
        VERSIONS["cef_base_url"] + "/" + urllib.parse.quote(distribution + ".tar.bz2", safe=""),
        work / "cache" / (distribution + ".tar.bz2"), cfg["sha1"], "sha1",
    )
    cef_root = source / "third_party/cef" / distribution
    if not cef_root.exists():
        extract(cef_archive, source / "third_party/cef")
    if not (cef_root / "include/cef_version.h").is_file():
        raise ValueError("Missing CEF headers after extraction")
    # The original macOS post-build invokes Ant to create a demo Java app.
    # MCEF already bundles matching Java classes. Create only its expected
    # native bundle, keeping all helper/framework copy commands unchanged.
    if target.startswith("macos"):
        cmake = source / "native/CMakeLists.txt"
        text = cmake.read_text()
        first = "    COMMAND ant\n"
        end = "    # Copy the CEF framework into the main app bundle."
        if first in text:
            start = text.index(first)
            stop = text.index(end, start)
            text = text[:start] + '    COMMAND ${CMAKE_COMMAND} -E make_directory "${JCEF_APP}/Contents/Java"\n' + text[stop:]
            cmake.write_text(text)
        if target == "macos_arm64":
            # Apple Silicon starts at macOS 11. The pinned CEF CMake file still
            # advertises Intel's 10.13 minimum, rejected by current SDK libc++.
            variables = cef_root / "cmake/cef_variables.cmake"
            text = variables.read_text().replace('set(CEF_TARGET_SDK               "10.13")', 'set(CEF_TARGET_SDK               "11.0")')
            variables.write_text(text)
    (work / "build-source.json").write_text(json.dumps({
        "jcef_commit": VERSIONS["jcef_commit"], "cef_version": VERSIONS["cef_version"],
        "platform": target, "cef_archive_sha256": digest(cef_archive),
        "source_repository": repository,
        "packaging_patch": "macOS skips unused Ant demo Java bundle" if target.startswith("macos") else "none",
        "native_patches": native_patches,
        "macos_arm64_minimum": "11.0" if target == "macos_arm64" else None,
    }, indent=2) + "\n")
    return source


def build(work, target, jobs):
    source = prepare(work, target)
    build_dir = work / "jcef_build"
    args = ["cmake", "-S", source, "-B", build_dir, "-G", "Ninja", "-DCMAKE_BUILD_TYPE=Release", "-DPYTHON_EXECUTABLE=" + sys.executable]
    if target.startswith("macos"):
        arch = "arm64" if target.endswith("arm64") else "x86_64"
        args += ["-DPROJECT_ARCH=" + arch, "-DCMAKE_OSX_ARCHITECTURES=" + arch]
    if os.environ.get("JAVA_HOME"):
        args.append("-DJAVA_DIR=" + os.environ["JAVA_HOME"])
    run(*args)
    run("cmake", "--build", build_dir, "--config", "Release", "--parallel", jobs)


def package(work, target, output):
    source = work / "source"
    release = work / "jcef_build/native/Release"
    if not release.is_dir():
        raise ValueError("Native output does not exist: " + str(release))
    required = {
        "windows_amd64": ["jcef.dll", "libcef.dll", "jcef_helper.exe", "icudtl.dat"],
        "linux_amd64": ["libjcef.so", "libcef.so", "jcef_helper", "icudtl.dat"],
        "macos_amd64": ["jcef_app.app/Contents/Java/libjcef.dylib"],
        "macos_arm64": ["jcef_app.app/Contents/Java/libjcef.dylib"],
    }[target]
    for name in required:
        if not (release / name).is_file():
            raise ValueError("Incomplete native build: " + name)
    if target.startswith("macos"):
        bundle = release / "jcef_app.app"
        plist = bundle / "Contents/Info.plist"
        import plistlib
        plist.write_bytes(plistlib.dumps({"CFBundleIdentifier": "org.jcef.jcef", "CFBundleName": "jcef_app", "CFBundlePackageType": "APPL", "CFBundleVersion": "116"}))
        run("codesign", "--force", "--deep", "--sign", "-", bundle)
        run("lipo", "-info", bundle / "Contents/Java/libjcef.dylib")
    elif target.startswith("linux"):
        run("file", release / "libjcef.so")
    # Keep JCEF and CEF license/notice files with every runtime distribution.
    notices = release / "computercraftedu-notices"
    notices.mkdir(exist_ok=True)
    for base, prefix in [(source, "jcef"), (next((source / "third_party/cef").glob("cef_binary_*")), "cef")]:
        for p in base.iterdir():
            if p.is_file() and any(token in p.name.lower() for token in ["license", "notice", "readme", "authors"]):
                shutil.copy2(p, notices / (prefix + "-" + p.name))
    for patch in (ROOT / "native/patches").glob("*.patch"):
        shutil.copy2(patch, notices / patch.name)
    manifest = json.loads((work / "build-source.json").read_text())
    manifest["runtime_certified"] = False
    (release / "computercraftedu-native.json").write_text(json.dumps(manifest, indent=2) + "\n")
    output.mkdir(parents=True, exist_ok=True)
    archive = output / (target + ".tar.gz")
    with tarfile.open(archive, "w:gz", dereference=False) as t:
        t.add(release, arcname=target)
    checksum = digest(archive)
    archive.with_name(archive.name + ".sha256").write_text(checksum + "  " + archive.name + "\n")
    print("Built", archive, "SHA-256", checksum, flush=True)


def install(archive, game_dir, expected, target=None):
    target = target or host_platform()
    if digest(archive) != expected.lower():
        raise ValueError("Native archive SHA-256 mismatch")
    with tempfile.TemporaryDirectory(prefix="ccedu-install-") as scratch:
        staging = pathlib.Path(scratch)
        extract(archive, staging)
        contents = list(staging.iterdir())
        if len(contents) != 1 or contents[0].name != target:
            raise ValueError("Archive does not match " + target)
        manifest = json.loads((contents[0] / "computercraftedu-native.json").read_text())
        if manifest.get("platform") != target or manifest.get("jcef_commit") != VERSIONS["jcef_commit"]:
            raise ValueError("Native manifest does not match the pinned runtime")
        destination = game_dir / "mods/mcef-libraries" / target
        destination.parent.mkdir(parents=True, exist_ok=True)
        if destination.exists():
            raise ValueError("Existing native install is preserved; use a fresh game directory: " + str(destination))
        shutil.copytree(contents[0], destination, symlinks=True)
        (destination / "computercraftedu-install.json").write_text(json.dumps({
            "archive_sha256": expected.lower(), "platform": target,
            "jcef_commit": VERSIONS["jcef_commit"],
        }, indent=2) + "\n")
    config = game_dir / "config/mcef/mcef.properties"
    config.parent.mkdir(parents=True, exist_ok=True)
    lines = config.read_text().splitlines() if config.exists() else []
    lines = [l for l in lines if not l.strip().startswith("skip-download=")]
    lines.append("skip-download=true")
    config.write_text("\n".join(lines) + "\n")
    print("Installed", target, "into", game_dir)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    for command in ["prepare", "build", "package"]:
        p = commands.add_parser(command)
        p.add_argument("--platform", choices=VERSIONS["platforms"], required=True)
        p.add_argument("--work", type=pathlib.Path, default=ROOT / "native-build")
        if command == "build":
            p.add_argument("--jobs", type=int, default=4)
        if command == "package":
            p.add_argument("--output", type=pathlib.Path, default=ROOT / "native-artifacts")
    p = commands.add_parser("install")
    p.add_argument("archive", type=pathlib.Path)
    p.add_argument("--game-dir", type=pathlib.Path, required=True)
    p.add_argument("--sha256", required=True)
    args = parser.parse_args()
    if args.command == "prepare":
        prepare(args.work.resolve(), args.platform)
    elif args.command == "build":
        build(args.work.resolve(), args.platform, args.jobs)
    elif args.command == "package":
        package(args.work.resolve(), args.platform, args.output.resolve())
    else:
        install(args.archive, args.game_dir.resolve(), args.sha256)


if __name__ == "__main__":
    main()
