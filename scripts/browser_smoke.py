#!/usr/bin/env python3
"""Prepare dependencies, then bound the real Minecraft browser startup separately."""
import argparse, json, os, pathlib, platform, signal, subprocess

root = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--game-dir', type=pathlib.Path, default=root / 'run-1.21.1')
parser.add_argument('--source-commit')
parser.add_argument('--offline', action='store_true')
args = parser.parse_args()
game = args.game_dir.resolve()
gradle_args = ['-PbrowserSmoke', '-PgameDir=' + str(game), '--no-daemon']
if args.source_commit:
    gradle_args.append('-PsourceCommit=' + args.source_commit)
if args.offline:
    gradle_args.append('--offline')
report = game / 'browser-smoke.json'
if report.exists():
    raise SystemExit('Use a fresh test instance; existing evidence is preserved')
wrapper = str(root / ('gradlew.bat' if platform.system() == 'Windows' else 'gradlew'))
try:
    prepared = subprocess.run([wrapper, 'prepareClientRun'] + gradle_args, cwd=root, timeout=1200)
except subprocess.TimeoutExpired:
    raise SystemExit('Dependency preparation timed out; retain CI logs')
if prepared.returncode != 0:
    raise SystemExit('Client dependency preparation failed')

command = [wrapper, 'runClient'] + gradle_args
if platform.system() == 'Linux':
    command = ['xvfb-run', '-a', '-s', '-screen 0 1280x720x24'] + command
options = {'creationflags': subprocess.CREATE_NEW_PROCESS_GROUP} if platform.system() == 'Windows' else {'start_new_session': True}
game.mkdir(parents=True, exist_ok=True)
log = game / 'browser-launch.log'
with log.open('w') as stream:
    process = subprocess.Popen(command, cwd=root, stdout=stream, stderr=subprocess.STDOUT, **options)
    try:
        code = process.wait(timeout=300)
    except subprocess.TimeoutExpired:
        if platform.system() == 'Windows':
            subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], check=False)
        else:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                pass
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        raise SystemExit('Prepared game startup/shutdown timed out; see browser-launch.log')
print(log.read_text(errors='replace')[-6000:])
if code != 0 or not report.exists():
    raise SystemExit('Game launch failed or no observed browser report was produced')
data = json.loads(report.read_text())
output = log.read_text(errors='replace')
model_errors = [line for line in output.splitlines() if any(marker in line for marker in (
    "Exception loading blockstate definition: 'singularbots:",
    'Missing textures in model singularbots:',
    "Unable to load model: 'singularbots:"))]
data['modelResourcesClean'] = not model_errors
data['modelResourceErrors'] = model_errors
report.write_text(json.dumps(data, indent=2) + '\n')
print(json.dumps(data, indent=2))
if model_errors:
    raise SystemExit('Singular Bot Blocks models/textures failed to load; see browser-launch.log')
if data.get('passed') is not True or data.get('fontLoaded') is not True:
    raise SystemExit('In-game browser smoke failed')
expected = {'minecraft': '1.21.1', 'neoforge': '21.1.252', 'computercraft': '1.120.2', 'mcef': '2.1.6-1.21.1', 'blockly': '11.2.2'}
if any(data.get(key) != version for key, version in expected.items()) or not data.get('java', '').startswith('21.'):
    raise SystemExit('Browser report does not match the pinned NeoForge/Java 21 target')
