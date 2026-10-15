#!/usr/bin/env python3
"""Verify downloaded Native browser artifacts and stage them for a release.

GitHub keeps workflow artifacts for 30 days; a release keeps the exact
archives (and their SHA-256 values) that existing evidence refers to.
Staging refuses archives whose checksum, pinned source or patch set does not
match this tree. A release is storage, not runtime certification.
"""
import argparse
import hashlib
import json
import pathlib
import shutil

import native_browser as native

UNSUPPORTED = {"macos_amd64": "No physical Intel Mac is available; the owner marked macOS Intel unsupported."}


def expected_patches():
    return sorted(
        ({"file": p.name, "sha256": native.digest(p)} for p in (native.ROOT / "native/patches").glob("*.patch")),
        key=lambda p: p["file"],
    )


def accepted_digests():
    """Each patch's LF digest, plus its CRLF digest: Windows checkouts convert line endings."""
    accepted = {}
    for p in (native.ROOT / "native/patches").glob("*.patch"):
        data = p.read_bytes()
        crlf = data.replace(b"\r\n", b"\n").replace(b"\n", b"\r\n")
        accepted[p.name] = {native.digest(p), hashlib.sha256(crlf).hexdigest()}
    return accepted


def verify_platform(dist, target):
    package = dist / ("native-" + target)
    archive = package / (target + ".tar.gz")
    recorded = package / (target + ".tar.gz.sha256")
    source = dist / ("native-evidence-" + target) / "build-source.json"
    for path in (archive, recorded, source):
        if not path.is_file():
            raise ValueError("Missing artifact file: " + str(path.relative_to(dist)))
    checksum = native.digest(archive)
    if recorded.read_text().split()[0].lower() != checksum:
        raise ValueError("SHA-256 does not match the recorded value: " + archive.name)
    manifest = json.loads(source.read_text())
    for key in ("jcef_commit", "cef_version"):
        if manifest.get(key) != native.VERSIONS[key]:
            raise ValueError(target + " was built from a different " + key)
    if manifest.get("platform") != target:
        raise ValueError("Build manifest platform mismatch: " + target)
    recorded_patches = {p.get("file"): p.get("sha256") for p in manifest.get("native_patches", [])}
    accepted = accepted_digests()
    if recorded_patches.keys() != accepted.keys() or any(recorded_patches[f] not in accepted[f] for f in accepted):
        raise ValueError(target + " was built with a different native patch set")
    return {"platform": target, "archive": archive, "checksum_file": recorded, "source": source, "sha256": checksum}


def stage(dist, output, run_id, source_commit):
    results = [verify_platform(dist, target) for target in native.VERSIONS["platforms"]]
    output.mkdir(parents=True, exist_ok=False)
    for r in results:
        shutil.copy2(r["archive"], output / r["archive"].name)
        shutil.copy2(r["checksum_file"], output / r["checksum_file"].name)
        shutil.copy2(r["source"], output / (r["platform"] + "-build-source.json"))
    (output / "SHA256SUMS").write_text("".join(r["sha256"] + "  " + r["archive"].name + "\n" for r in results))
    rows = "\n".join(
        "| `{}` | `{}` | {} |".format(r["platform"], r["sha256"], UNSUPPORTED.get(r["platform"], "Not runtime certified"))
        for r in results
    )
    patches = ", ".join("`{}`".format(p["file"]) for p in expected_patches())
    (output / "release-notes.md").write_text(
        "JCEF `{}` with CEF `{}` and patches {}.\n\n"
        "Built by Native browser run {} from commit `{}`. Archives are byte-identical to that run's artifacts.\n\n"
        "| Platform | SHA-256 | Status |\n| --- | --- | --- |\n{}\n\n"
        "Install with `python scripts/native_browser.py install <archive> --game-dir <fresh dir> --sha256 <value>`. "
        "Compilation and storage are not runtime, input or classroom certification; see `docs/status.md`.\n".format(
            native.VERSIONS["jcef_commit"], native.VERSIONS["cef_version"], patches, run_id, source_commit, rows,
        )
    )
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dist", type=pathlib.Path, required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--source-commit", required=True)
    args = parser.parse_args()
    for r in stage(args.dist.resolve(), args.output.resolve(), args.run_id, args.source_commit):
        print("Verified", r["platform"], r["sha256"], flush=True)


if __name__ == "__main__":
    main()
