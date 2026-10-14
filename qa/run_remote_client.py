#!/usr/bin/env python3
"""Prepare or run packaged BRB against an owned loopback vanilla server.

Default: prepare a hash-recorded launch. --launch runs the bounded remote fixture,
whose commands affect only its operator-enabled QA player.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

PROJECT = Path(__file__).resolve().parents[1]
WORKSPACE = PROJECT.parents[1]
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
JDK = Path.home() / '.gradle/jdks/microsoft-25-amd64-linux.2/bin/java'


def artifact(group, name, version):
    candidates = sorted((CACHE / group / name / version).glob('*/' + name + '-' + version + '.jar'))
    if not candidates:
        raise FileNotFoundError('Missing retained dependency: ' + ':'.join((group, name, version)))
    return candidates[0]


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def retained_asset_index():
    manifest = json.loads((Path.home() / '.gradle/caches/fabric-loom/26.3/mojang_minecraft_info.json').read_text())
    expected = manifest['assetIndex']['sha1']
    for path in sorted((PROJECT / 'qa/remote-inputs').glob('34*.json')):
        if hashlib.sha1(path.read_bytes()).hexdigest() == expected:
            return path
    raise RuntimeError('Missing verified retained asset index: ' + manifest['assetIndex']['url'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', required=True)
    parser.add_argument('--run', required=True, type=Path)
    parser.add_argument('--jar', required=True, type=Path, help='Exact installable BRB release JAR.')
    parser.add_argument('--username', default='brbremoteqa')
    parser.add_argument('--java', type=Path, default=JDK)
    parser.add_argument('--asset-index', type=Path, help='Exact official asset index; defaults to a checksum-matching retained index.')
    parser.add_argument('--launch', action='store_true')
    parser.add_argument('--modmenu', action='store_true', help='Also verify the optional Mod Menu integration.')
    args = parser.parse_args()
    run = args.run.resolve()
    release = args.jar.resolve()
    with zipfile.ZipFile(release) as archive:
        metadata = json.loads(archive.read('fabric.mod.json'))
        if metadata['id'] != 'brb' or 'architectury' in metadata.get('depends', {}):
            raise RuntimeError('Expected packaged BRB without Architectury dependency.')
        if not metadata.get('jars'):
            raise RuntimeError('Expected nested Cloth Config dependency.')
    subprocess.run([sys.executable, str(PROJECT / 'qa/vanilla_client.py'), '--server', args.server,
                    '--username', args.username, '--run-dir', str(run), '--java', str(args.java),
                    '--asset-index', str(args.asset_index or retained_asset_index())], check=True)
    vanilla_audit = json.loads((run / 'vanilla-launch-audit.json').read_text())
    command = vanilla_audit['command']
    command[command.index('net.minecraft.client.main.Main')] = 'net.fabricmc.loader.impl.launch.knot.KnotClient'
    command[1:1] = ['-Dbrb.remoteQa=true', '-XX:ActiveProcessorCount=2']
    loader = artifact('net.fabricmc', 'fabric-loader', '0.19.5')
    libraries = [str(loader)]
    with zipfile.ZipFile(loader) as archive:
        profile = json.loads(archive.read('fabric-installer.json'))
        for library in profile['libraries']['common'] + profile['libraries']['client']:
            path = artifact(*library['name'].split(':'))
            if hashlib.sha1(path.read_bytes()).hexdigest() != library['sha1']:
                raise RuntimeError('Fabric dependency checksum differs: ' + library['name'])
            libraries.append(str(path))
    index = command.index('-cp') + 1
    command[index] = os.pathsep.join(libraries + command[index].split(os.pathsep))
    mods = run / 'mods'
    mods.mkdir(exist_ok=True)
    api = WORKSPACE / 'Builds/Minecraft/Fabric API/Main Plugin/0.161.0+26.3/fabric-api-0.161.0+26.3.jar'
    if not api.is_file():
        api = artifact('net.fabricmc.fabric-api', 'fabric-api', '0.161.0+26.3')
    sources = [release, api]
    if args.modmenu:
        sources.append(artifact('maven.modrinth', 'modmenu', '21.0.0'))
    mod_audit = []
    for source in sources:
        shutil.copy2(source, mods / source.name)
        mod_audit.append({'file': source.name, 'source': str(source), 'sha256': sha256(source)})
    fixture = mods / 'brb-remote-qa.jar'
    classes = PROJECT / 'ported/build/classes/java/clientqa'
    fixture_classes = sorted(classes.rglob('RemoteClientQa*.class'))
    if not fixture_classes:
        raise RuntimeError('Compile compileClientqaJava before preparing the fixture.')
    with zipfile.ZipFile(fixture, 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('fabric.mod.json', json.dumps({
            'schemaVersion': 1, 'id': 'brb_remoteqa', 'version': '1',
            'name': 'BRB Remote QA Fixture', 'environment': 'client',
            'entrypoints': {'client': ['marsh.town.brb.clientqa.RemoteClientQa']},
            'depends': {'brb': '*', 'fabric-api': '*', 'fabricloader': '>=0.19.5'}}))
        for path in fixture_classes:
            archive.write(path, path.relative_to(classes))
    audit = {'command': command, 'mods': mod_audit, 'fixture_sha256': sha256(fixture),
             'server': args.server, 'username': args.username, 'release_version': metadata['version'],
             'status': 'launch requested' if args.launch else 'prepared only; no connection performed'}
    (run / 'launch-audit.json').write_text(json.dumps(audit, indent=2) + '\n')
    print('BRB_REMOTE_CLIENT_RUN ' + str(run), flush=True)
    if not args.launch:
        print('Prepared only; Minecraft has not been launched.', flush=True)
        return 0
    environment = os.environ.copy()
    environment['SDL_VIDEODRIVER'] = 'x11'
    with (run / 'console.log').open('w') as log:
        process = subprocess.Popen(command, cwd=run, env=environment, stdout=log, stderr=subprocess.STDOUT)
        (run / 'client.pid').write_text(str(process.pid) + '\n')
        try:
            code = process.wait(timeout=240)
        except subprocess.TimeoutExpired:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=15)
            print('Remote fixture exceeded its 240-second limit.', file=sys.stderr)
            return 1
    result_path = run / 'remoteqa-result.txt'
    result = result_path.read_text() if result_path.exists() else 'RESULT: FAIL; fixture did not write a report'
    print(result, flush=True)
    audit['status'] = 'passed' if code == 0 and 'RESULT: PASS;' in result else 'failed'
    audit['exit_code'] = code
    (run / 'launch-audit.json').write_text(json.dumps(audit, indent=2) + '\n')
    return 0 if audit['status'] == 'passed' else 1


if __name__ == '__main__':
    raise SystemExit(main())
