#!/usr/bin/env python3
"""Launch a hash-verified Mojang client, without Fabric or any client mods.

Uses existing Loom downloads read-only; writes only into the supplied QA run directory.
No downloads, Microsoft credentials, Java agents, mixins, or game-code hooks are used.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import subprocess
import sys
import uuid


def digest(path):
    with path.open('rb') as handle:
        return hashlib.file_digest(handle, 'sha1').hexdigest()


def allowed(rules):
    result = not rules
    for rule in rules or []:
        condition = rule.get('os', {})
        matches = condition.get('name', 'linux') == 'linux'
        if 'arch' in condition:
            matches &= condition['arch'] == platform.machine()
        if 'version' in condition:
            matches &= bool(re.search(condition['version'], platform.release()))
        # Feature-gated arguments are supplied explicitly below.
        matches &= not bool(rule.get('features'))
        if matches:
            result = rule['action'] == 'allow'
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', default='26.3')
    parser.add_argument('--gradle-cache', type=Path, default=Path.home() / '.gradle/caches')
    parser.add_argument('--run-dir', type=Path, default=Path(__file__).resolve().parent / 'vanilla-client')
    parser.add_argument('--asset-index', type=Path, help='Optional exact official asset index; checksum remains mandatory.')
    parser.add_argument('--java', type=Path, default=Path.home() / '.gradle/jdks/microsoft-25-amd64-linux.2/bin/java')
    parser.add_argument('--server', help='Optional loopback address, e.g. 127.0.0.1:25585')
    parser.add_argument('--username', default='brbremoteqa')
    parser.add_argument('--launch', action='store_true', help='Without this flag only audit and prepare the exact launch.')
    parser.add_argument('--x11', action='store_true', help='Use SDL X11 backend so the external control helper can see the window.')
    args = parser.parse_args()
    if sys.platform != 'linux':
        parser.error('This local QA launcher currently supports Linux only.')
    if args.server and not re.fullmatch(r'(127\.0\.0\.1|localhost):[0-9]{1,5}', args.server):
        parser.error('Offline QA is restricted to an explicit loopback server and port.')
    if not re.fullmatch(r'[A-Za-z0-9_]{1,16}', args.username):
        parser.error('Invalid Minecraft username.')
    cache = args.gradle_cache.resolve()
    loom = cache / 'fabric-loom'
    manifest = loom / args.version / 'mojang_minecraft_info.json'
    info = json.loads(manifest.read_text())
    if info['id'] != args.version:
        raise RuntimeError('Manifest version differs from requested client.')
    run = args.run_dir.resolve()
    run.mkdir(parents=True, exist_ok=True)
    if (run / 'mods').exists() and any((run / 'mods').iterdir()):
        raise RuntimeError('Refusing a QA run directory containing client mods.')
    client = loom / args.version / 'minecraft-client.jar'
    expected = info['downloads']['client']['sha1']
    if digest(client) != expected:
        raise RuntimeError('Mojang client JAR checksum mismatch.')
    jars = []
    evidence = [{'path': str(client), 'sha1': expected, 'type': 'mojang-client'}]
    for lib in info['libraries']:
        if not allowed(lib.get('rules')):
            continue
        artifact = lib['downloads']['artifact']
        group, name, version, *_ = lib['name'].split(':')
        candidates = list((cache / 'modules-2/files-2.1' / group / name / version).glob('*/' + Path(artifact['path']).name))
        found = next((p for p in candidates if digest(p) == artifact['sha1']), None)
        if found is None:
            raise RuntimeError('Missing official dependency from local cache: ' + lib['name'])
        jars.append(str(found))
        evidence.append({'path': str(found), 'sha1': artifact['sha1'], 'type': 'mojang-library'})
    source_assets = loom / 'assets'
    source_index = args.asset_index.resolve() if args.asset_index else source_assets / 'indexes' / (args.version + '-' + info['assetIndex']['id'] + '.json')
    if digest(source_index) != info['assetIndex']['sha1']:
        raise RuntimeError('Official asset index checksum mismatch.')
    index = json.loads(source_index.read_text())
    missing_assets = [key for key, value in index['objects'].items()
                      if not (source_assets / 'objects' / value['hash'][:2] / value['hash']).is_file()]
    if missing_assets:
        raise RuntimeError(f'Missing {len(missing_assets)} official assets; populate Loom assets before running.')
    assets = run / 'assets'
    (assets / 'indexes').mkdir(parents=True, exist_ok=True)
    (assets / 'indexes' / (info['assetIndex']['id'] + '.json')).write_bytes(source_index.read_bytes())
    objects = assets / 'objects'
    if not objects.exists():
        objects.symlink_to(source_assets / 'objects', target_is_directory=True)
    player_id = bytearray(hashlib.md5(('OfflinePlayer:' + args.username).encode()).digest())
    player_id[6] = (player_id[6] & 0x0f) | 0x30
    player_id[8] = (player_id[8] & 0x3f) | 0x80
    native = run / 'natives'
    variables = {
        'natives_directory': str(native),
        'launcher_name': 'brb-vanilla-qa',
        'launcher_version': '1',
        'classpath': os.pathsep.join(jars + [str(client)]),
    }
    command = [str(args.java), '-Xms512M', '-Xmx2G']
    for argument in info['arguments']['jvm']:
        if isinstance(argument, dict):
            if not allowed(argument.get('rules')):
                continue
            argument = argument['value']
        for value in argument if isinstance(argument, list) else [argument]:
            for key, replacement in variables.items():
                value = value.replace('${' + key + '}', replacement)
            command.append(value)
    command += [info['mainClass'], '--username', args.username, '--version', args.version,
                '--gameDir', str(run), '--assetsDir', str(assets), '--assetIndex', info['assetIndex']['id'],
                '--uuid', str(uuid.UUID(bytes=bytes(player_id))), '--accessToken', '0',
                '--versionType', 'release', '--width', '1280', '--height', '800']
    if args.server:
        command += ['--quickPlayMultiplayer', args.server]
    options = run / 'options.txt'
    if not options.exists():
        options.write_text('onboardAccessibility:false\nrenderDistance:2\nsimulationDistance:5\n'
                           'guiScale:2\ntutorialStep:none\nmaxFps:30\npauseOnLostFocus:false\n'
                           'fullscreen:false\n')
    audit = {'version': args.version, 'main_class': info['mainClass'],
             'client_mods': [], 'java_agents': [], 'libraries': evidence,
             'asset_index_sha1': info['assetIndex']['sha1'], 'asset_index_source': str(source_index), 'asset_count': len(index['objects']),
             'server': args.server, 'username': args.username, 'command': command,
             'status': 'prepared-only; no connection test performed'}
    (run / 'vanilla-launch-audit.json').write_text(json.dumps(audit, indent=2) + '\n')
    print(f'Verified Mojang {args.version} client and {len(jars)} official libraries; {len(index["objects"])} assets present.')
    print(f'Audit: {run / "vanilla-launch-audit.json"}')
    if args.launch:
        print(f'Launching unmodified client; log: {run / "console.log"}', flush=True)
        with (run / 'console.log').open('a') as log:
            environment = os.environ.copy()
            if args.x11:
                environment['SDL_VIDEODRIVER'] = 'x11'
            process = subprocess.Popen(command, cwd=run, env=environment, stdout=log, stderr=subprocess.STDOUT)
            (run / 'client.pid').write_text(str(process.pid) + '\n')
            try:
                return process.wait()
            except KeyboardInterrupt:
                process.terminate()
                return process.wait(timeout=20)
    return 0


if __name__ == '__main__':
    sys.exit(main())
