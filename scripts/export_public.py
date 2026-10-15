#!/usr/bin/env python3
"""Export the public Singular Bot Blocks source tree from this private repository.

Only committed files are exported (git ls-files at HEAD). The private repository keeps the
acceptance evidence, internal QA tooling and maintenance notes; the public tree gets the mod,
editor, art sources, build and test tooling, CI and the user-facing docs. Files in public/
replace their counterparts at the export root (README.md, THIRD_PARTY.md).

Every exported text file is scanned for names and paths that must not be published; the export
fails and lists each hit. Usage:

    python3 scripts/export_public.py <empty output directory>
"""
import fnmatch, pathlib, re, shutil, subprocess, sys

root = pathlib.Path(__file__).resolve().parents[1]

INCLUDE = [
    '.gitattributes', '.gitignore', 'LICENSE', 'build.gradle', 'settings.gradle', 'gradle.properties',
    'gradlew', 'gradlew.bat', 'gradle/*', 'src/*', 'editor/*', 'art/*', 'native/*', '.github/workflows/*',
    'docs/install.md', 'docs/gameplay-guide.md', 'docs/classroom-wording.md',
    'scripts/generate_textures.py', 'scripts/native_browser.py', 'scripts/native_release.py',
    'scripts/install_smoke_native.py', 'scripts/browser_smoke.py', 'scripts/server_smoke.py',
    'scripts/persistence_smoke.py', 'scripts/export_public.py', 'scripts/fixtures/*',
    'scripts/test_native_browser.py', 'scripts/test_native_release.py', 'scripts/test_native_patches.py',
]
OVERLAY = root / 'public'

# Lines that may legitimately mention the inspiration or keep a historical/internal identifier.
FORBIDDEN = re.compile(r'computercraftedu|ccedu1\.79|reconstructed|/Users/|prism|docs/evidence|teachergaming', re.I)
ALLOWED = [
    re.compile(r'computercraftedu-(native|install)\.json|computercraftedu-notices'),  # native package file names
    re.compile(r'inspired by (the original )?ComputerCraftEdu', re.I),
    re.compile(r'curseforge\.com/minecraft/mc-mods/computercraftedu'),
    re.compile(r'TeacherGaming(,| LLC)'),  # attribution in the disclaimer and third-party notices
    re.compile(r'^\| `[a-z_.]+` \| ComputerCraftEdu '),  # classroom-wording: the "before" column
    re.compile(r'from ComputerCraftEdu, ComputerCraft, CC: Tweaked'),  # THIRD_PARTY: what the art is not copied from
]


def exported():
    files = subprocess.run(['git', 'ls-files', '-z'], cwd=root, check=True, capture_output=True).stdout.decode().split('\0')
    return [f for f in files if f and any(f == p or fnmatch.fnmatchcase(f, p) for p in INCLUDE)]


def scan(out):
    hits = []
    for path in sorted(p for p in out.rglob('*') if p.is_file()):
        try:
            text = path.read_text(encoding='utf-8')
        except UnicodeDecodeError:
            continue
        if path.name == 'export_public.py':
            continue  # the patterns themselves
        for number, line in enumerate(text.splitlines(), 1):
            if FORBIDDEN.search(line) and not any(a.search(line) for a in ALLOWED):
                hits.append(f'{path.relative_to(out)}:{number}: {line.strip()[:160]}')
    return hits


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    out = pathlib.Path(sys.argv[1]).resolve()
    if out.exists() and any(out.iterdir()):
        sys.exit(f'{out} is not empty')
    for name in exported():
        target = out / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(root / name, target)
    for overlay in OVERLAY.rglob('*'):
        if overlay.is_file():
            target = out / overlay.relative_to(OVERLAY)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(overlay, target)
    hits = scan(out)
    if hits:
        sys.exit('Names or paths that must not be published:\n' + '\n'.join(hits))
    print(f'Exported {sum(1 for p in out.rglob("*") if p.is_file())} files to {out}')


if __name__ == '__main__':
    main()
