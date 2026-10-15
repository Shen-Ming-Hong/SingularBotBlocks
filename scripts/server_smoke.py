#!/usr/bin/env python3
"""Boot a fresh headless NeoForge server without MCEF and stop it cleanly."""
import argparse, json, os, pathlib, platform, signal, subprocess, time

root = pathlib.Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('--game-dir',type=pathlib.Path,default=root/'run-server-1.21.1')
parser.add_argument('--offline',action='store_true')
args=parser.parse_args()
game=args.game_dir.resolve()
if game.exists():
    raise SystemExit('Use a fresh server test instance; existing worlds/evidence are preserved')
game.mkdir()
(game / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nview-distance=2\nsimulation-distance=2\nlevel-name=smoke-world\n')
log = game / 'server-smoke.log'
command = [str(root / ('gradlew.bat' if platform.system() == 'Windows' else 'gradlew')), 'runServer', '-PgameDir='+str(game), '--no-daemon']
if args.offline: command.append('--offline')
options = {'creationflags': subprocess.CREATE_NEW_PROCESS_GROUP} if platform.system() == 'Windows' else {'start_new_session': True}
started = stopped = False
with log.open('w') as stream:
    process = subprocess.Popen(command, cwd=root, stdin=subprocess.PIPE, stdout=stream, stderr=subprocess.STDOUT, text=True, **options)
    deadline = time.monotonic() + 600
    while process.poll() is None:
        output = log.read_text(errors='replace')
        if not started and 'Done (' in output and 'For help' in output:
            started = True
            process.stdin.write('stop\n'); process.stdin.flush()
            print('Headless server started; requested clean shutdown', flush=True)
        if time.monotonic() > deadline:
            if platform.system() == 'Windows':
                subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], check=False)
            else:
                os.killpg(process.pid, signal.SIGTERM)
            raise SystemExit('Server smoke timed out; retain logs and mark it failing')
        time.sleep(0.5)
    code = process.wait()
output = log.read_text(errors='replace')
stopped = 'All dimensions are saved' in output
classpath_file = root / 'build/moddev/serverLegacyClasspath.txt'
classpath = classpath_file.read_text() if classpath_file.exists() else ''
without_browser = bool(classpath) and not any(name in classpath.lower() for name in ['mcef-', 'jcef'])
versions_loaded = all(name in output for name in ['Minecraft 1.21.1 (minecraft)', 'NeoForge 21.1.252 (neoforge)', 'CC: Tweaked 1.120.2 (computercraft)', 'Singular Bot Blocks'])
report = {'passed': code == 0 and started and stopped and without_browser and versions_loaded,
          'started': started, 'cleanShutdown': stopped, 'withoutBrowserRuntime': without_browser,
          'versionsLoaded': versions_loaded, 'javaTarget': 21,
          'sourceCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
          'observedAt': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
          'runtimeCertified': False,
          'limitations': 'Headless development server boot/shutdown only; multiplayer classroom acceptance remains pending'}
(game / 'server-smoke.json').write_text(json.dumps(report, indent=2))
print(json.dumps(report, indent=2))
if not report['passed']:
    raise SystemExit('Headless server smoke failed; see server-smoke.log')
