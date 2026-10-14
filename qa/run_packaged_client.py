#!/usr/bin/env python3
"""Run BRBR's full client fixture against exact installable integration JARs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

from run_remote_client import PROJECT, JDK, artifact, sha256, retained_asset_index


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run', type=Path, required=True)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--client-mod', type=Path, action='append', default=[])
    parser.add_argument('--fixture', type=Path, default=PROJECT / 'ported/build/qa/brb-client-qa-1.jar')
    parser.add_argument('--java', type=Path, default=JDK)
    parser.add_argument('--asset-index', type=Path, help='Exact official asset index; defaults to a checksum-matching retained index.')
    parser.add_argument('--suite', default='all',
                        choices=('all', 'recipes', 'creative', 'crafting', 'pins', 'workstation', 'lifecycle', 'config'))
    parser.add_argument('--timeout', type=int, default=900)
    parser.add_argument('--prepare-only', action='store_true')
    args = parser.parse_args()
    if args.timeout <= 0:
        parser.error('--timeout must be positive')
    run = args.run.resolve()
    run.mkdir(parents=True, exist_ok=False)
    production = args.jar.resolve(strict=True)
    executed = run / 'executed-source'
    source_files = [PROJECT / 'qa' / name for name in
                    ('run_packaged_client.py', 'run_remote_client.py', 'vanilla_client.py')]
    source_files.extend(path for path in (PROJECT / 'ported/src/clientqa').rglob('*') if path.is_file())
    source_audit = []
    for source in sorted(source_files):
        relative = source.relative_to(PROJECT)
        destination = executed / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
        source_audit.append({'path': str(relative), 'sha256': sha256(destination)})
    (run / 'executed-source.json').write_text(json.dumps(source_audit, indent=2) + '\n')
    with zipfile.ZipFile(production) as archive:
        metadata = json.loads(archive.read('fabric.mod.json'))
        if metadata['id'] != 'brb' or not metadata.get('jars'):
            raise RuntimeError('Expected exact BRBR installable JAR with bundled Cloth Config.')
    subprocess.run([sys.executable, str(PROJECT / 'qa/vanilla_client.py'),
                    '--username', 'brbpackagedqa', '--run-dir', str(run),
                    '--java', str(args.java), '--asset-index', str(args.asset_index or retained_asset_index())], check=True)
    vanilla = json.loads((run / 'vanilla-launch-audit.json').read_text())
    command = vanilla['command']
    command[command.index('net.minecraft.client.main.Main')] = 'net.fabricmc.loader.impl.launch.knot.KnotClient'
    command[1:1] = ['-Dfabric.client.gametest', '-Dbrb.qaSuite=' + args.suite,
                    '-XX:ActiveProcessorCount=2']
    loader = artifact('net.fabricmc', 'fabric-loader', '0.19.5')
    libraries = [loader]
    with zipfile.ZipFile(loader) as archive:
        profile = json.loads(archive.read('fabric-installer.json'))
        for library in profile['libraries']['common'] + profile['libraries']['client']:
            path = artifact(*library['name'].split(':'))
            if hashlib.sha1(path.read_bytes()).hexdigest() != library['sha1']:
                raise RuntimeError('Fabric dependency checksum differs: ' + library['name'])
            libraries.append(path)
    index = command.index('-cp') + 1
    command[index] = os.pathsep.join([*map(str, libraries), command[index]])
    mods = run / 'mods'
    mods.mkdir()
    sources = [production, artifact('net.fabricmc.fabric-api', 'fabric-api', '0.161.0+26.3'),
               artifact('net.fabricmc.fabric-api', 'fabric-client-gametest-api-v1', '6.0.7+4be74c3f5d'),
               args.fixture.resolve(strict=True),
               *[path.resolve(strict=True) for path in args.client_mod]]
    audits = []
    identities = set()
    for source in sources:
        destination = mods / source.name
        if destination.exists():
            raise RuntimeError('Duplicate client JAR filename: ' + source.name)
        shutil.copy2(source, destination)
        with zipfile.ZipFile(destination) as archive:
            mod = json.loads(archive.read('fabric.mod.json'))
        if mod['id'] in identities:
            raise RuntimeError('Duplicate client mod ID: ' + mod['id'])
        identities.add(mod['id'])
        audits.append({'id': mod['id'], 'version': mod['version'], 'source': str(source),
                       'file': source.name, 'sha256': sha256(destination)})
    (run / 'options.txt').write_text('tutorialStep:none\npauseOnLostFocus:false\nrenderDistance:2\n'
                                    'simulationDistance:5\nmaxFps:30\nsoundCategory_master:0.0\n')
    environment = os.environ.copy()
    environment.update(DISPLAY=environment.get('DISPLAY', ':1'), SDL_VIDEODRIVER='x11',
                       SDL_VIDEO_X11_XINPUT2='0', LP_NUM_THREADS='2')
    audit = {'command': command, 'mods': audits, 'suite': args.suite,
             'release_version': metadata['version'],
             'environment': {key: environment.get(key) for key in
                             ('DISPLAY', 'LIBGL_ALWAYS_SOFTWARE', 'SDL_VIDEODRIVER', 'LP_NUM_THREADS')},
             'status': 'prepared only' if args.prepare_only else 'running'}
    audit_path = run / 'launch-audit.json'
    audit_path.write_text(json.dumps(audit, indent=2) + '\n')
    if args.prepare_only:
        print('Prepared exact packaged profile; no Minecraft process launched.', flush=True)
        return 0
    with (run / 'console.log').open('w') as log:
        process = subprocess.Popen(command, cwd=run, env=environment,
                                   stdout=log, stderr=subprocess.STDOUT)
        (run / 'client.pid').write_text(str(process.pid) + '\n')
        try:
            code = process.wait(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
            code = -1
    result_path = run / 'clientqa-result.txt'
    result = result_path.read_text() if result_path.exists() else 'RESULT: FAIL; no fixture report'
    audit.update(exit_code=code, status='passed' if code == 0 and 'RESULT: PASS;' in result else 'failed')
    audit_path.write_text(json.dumps(audit, indent=2) + '\n')
    print(result, flush=True)
    return 0 if audit['status'] == 'passed' else 1


if __name__ == '__main__':
    raise SystemExit(main())
