#!/usr/bin/env python3
"""Use dedicated server processes to verify persisted Junior gameplay data.

prepare and verify end with a clean stop. crash is killed without saving the
world once its report exists, and recover checks what the next start finds.
"""
import argparse, datetime, json, os, pathlib, platform, signal, subprocess, time

root = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--game-dir', type=pathlib.Path, default=root / 'run-persistence-1.21.1')
parser.add_argument('--offline', action='store_true')
args = parser.parse_args()
game = args.game_dir.resolve()
if game.exists():
    raise SystemExit('Use a fresh test instance; existing worlds and evidence are preserved')
game.mkdir()
# An empty flat generator is invalid ("No key layers") and makes initial spawn
# searching depend on the random seed. Use an explicit, reproducible test world.
flat = json.dumps({'biome': 'minecraft:plains', 'layers': [
    {'block': 'minecraft:bedrock', 'height': 1},
    {'block': 'minecraft:dirt', 'height': 2},
    {'block': 'minecraft:grass_block', 'height': 1}]}, separators=(',', ':'))
(game / 'server.properties').write_text(
    'server-ip=127.0.0.1\nserver-port=0\nview-distance=2\nsimulation-distance=2\n'
    'level-name=persistence-world\nlevel-type=minecraft:flat\nlevel-seed=1137\n'
    'generate-structures=false\ngenerator-settings=' + flat + '\n')
def kill(process):
    if platform.system() == 'Windows':
        subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], check=False)
    else:
        os.killpg(process.pid, signal.SIGKILL)

def complete(report):
    try:
        json.loads(report.read_text())
        return True
    except ValueError:
        return False

wrapper = str(root / ('gradlew.bat' if platform.system() == 'Windows' else 'gradlew'))
phases = []
for mode in ('prepare', 'verify', 'crash', 'recover'):
    crash = mode == 'crash'
    report = game / ('persistence-' + mode + '.json')
    log = game / ('persistence-' + mode + '.log')
    command = [wrapper, 'runServer', '-PpersistenceSmoke=' + mode, '-PgameDir=' + str(game), '--no-daemon']
    if args.offline:
        command.append('--offline')
    options = {'creationflags': subprocess.CREATE_NEW_PROCESS_GROUP} if platform.system() == 'Windows' else {'start_new_session': True}
    requested_stop = False
    with log.open('w') as stream:
        process = subprocess.Popen(command, cwd=root, stdin=subprocess.PIPE, stdout=stream, stderr=subprocess.STDOUT, text=True, **options)
        deadline = time.monotonic() + 600
        while process.poll() is None:
            if report.exists() and not requested_stop and (not crash or complete(report)):
                if crash:
                    # Simulate a crash or a host's forced stop: no world save, no shutdown hooks.
                    kill(process)
                else:
                    process.stdin.write('stop\n'); process.stdin.flush()
                requested_stop = True
            if time.monotonic() > deadline:
                kill(process)
                raise SystemExit('Persistence probe timed out; retain logs')
            time.sleep(0.5)
    if (process.returncode != 0 and not crash) or not report.exists():
        raise SystemExit('Persistence phase failed: ' + mode + '; see ' + str(log))
    data = json.loads(report.read_text())
    data['cleanShutdown'] = 'All dimensions are saved' in log.read_text(errors='replace')
    data['killed'] = crash
    if not data['passed'] or data['cleanShutdown'] == crash:
        raise SystemExit('Persistence phase did not pass: ' + json.dumps(data))
    phases.append(data)
    print('Persistence phase passed:', mode, flush=True)
classpath = (root / 'build/moddev/serverLegacyClasspath.txt').read_text().lower()
without_browser = not any(name in classpath for name in ('mcef-', 'jcef'))
result = {'passed': without_browser, 'withoutBrowserRuntime': without_browser,
          'sourceCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
          'sourceWorkingTreeModified': bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=root, text=True).strip()),
          'observedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'phases': phases,
          'runtimeCertified': False, 'classroomRelease': False,
          'verified': ['two real dedicated-server processes and clean shutdowns', 'stable identity and position', 'owner, name, colour and decoration', 'inventory and selected slot', 'teacher settings and lock', 'workspace, stable block IDs and selected program', 'execute persisted Blockly after restart', 'restore persisted snapshot after restart',
                       'two students: separate libraries and history', 'class lua/sharing settings',
                       'robot in a chunk unloaded on a dedicated server keeps identity, name, owner and inventory',
                       'program saved before a killed server (no world save) is complete and leaves no temporary files'],
          'limitations': 'Isolated automated server test; actual client reconnect, GUI and multiplayer acceptance remain pending'}
(game / 'persistence-smoke.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result, indent=2))
if not result['passed']:
    raise SystemExit('Browser runtime was unexpectedly loaded')
