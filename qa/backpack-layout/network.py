#!/usr/bin/env python3
"""Connected backpack crafting and recipe-book clicks with authoritative conservation checks."""
from pathlib import Path
import argparse, hashlib, json, os, shutil, socket, struct, subprocess, sys, time, zipfile

FIXTURES = Path(__file__).resolve().parent
PROJECT = FIXTURES.parents[1]
WORKSPACE = PROJECT.parents[1]
ROOT = WORKSPACE / 'Minecraft/thenathe-mod-suite'
sys.path.insert(0, str(ROOT / 'tools'))
from workspace_paths import mapstitch_launch, project_path

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def freeport():
    with socket.socket() as sock: sock.bind(('127.0.0.1', 0)); return sock.getsockname()[1]
def save_server(path, port, policy):
    # Vanilla's own servers.dat, no loader/agent needed to accept the real server pack.
    def string(value):
        encoded = value.encode(); return struct.pack('>H', len(encoded)) + encoded
    data = b'\x0a\x00\x00\x09' + string('servers') + b'\x0a' + struct.pack('>i', 1)
    for key, value in [('name', 'Atlas Maintenance QA'), ('ip', f'127.0.0.1:{port}')]: data += b'\x08' + string(key) + string(value)
    if policy != 'prompt': data += b'\x01' + string('acceptTextures') + (b'\x01' if policy == 'accept' else b'\x00')
    path.write_bytes(data + b'\x00\x00')
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--label', required=True)
    parser.add_argument('--profiles', default='suite')
    parser.add_argument('--without-polymer', action='store_true')
    parser.add_argument('--brbr', type=Path)
    parser.add_argument('--backpack', action='store_true')
    parser.add_argument('--delay-grid', action='store_true')
    parser.add_argument('--baseline', action='store_true')
    parser.add_argument('--cycles', type=int, default=1)
    parser.add_argument('--compile-only', action='store_true', help='Compile/package observation fixtures without launching any runtime')
    args = parser.parse_args()
    if args.profiles != 'suite': parser.error('This fixture requires the native suite client; vanilla boundary is tested separately')
    if args.without_polymer and args.profiles != 'suite': parser.error('Without Polymer requires the suite-only profile')
    run = FIXTURES / 'runs' / args.label; run.mkdir(parents=True, exist_ok=False)
    control = run / 'control'; control.mkdir()
    fixtures = run / 'fixtures'; fixtures.mkdir(); classes = fixtures / 'classes'; classes.mkdir()
    executed = run / 'executed-source'; executed.mkdir()
    for source in FIXTURES.glob('CraftingNetwork*.java'): shutil.copy2(source, executed / source.name)
    shutil.copy2(Path(__file__), executed / 'network.py')
    launch = mapstitch_launch(WORKSPACE); suite = run / args.jar.name; shutil.copy2(args.jar.resolve(), suite)
    dependencies = [ROOT / 'libs' / name for name in (
        'codecui-26.3-1.4.3-fabric.jar',
        'fabric-language-kotlin-1.14.1+kotlin.2.4.20.jar', 'fzzy_config-0.7.7+fix3+26.3.jar', 'mixson-2.2.1-multiloader.jar')]
    api = launch.artifact('net.fabricmc.fabric-api', 'fabric-api', '0.161.0+26.3')
    dependencies += [api, launch.artifact('me.shedaniel.cloth', 'cloth-config-fabric', '26.3.159')]
    polymer = WORKSPACE / 'Builds/Minecraft/Polymer/Main Plugin/0.18.2+26.3/polymer-bundled-0.18.2+26.3.jar'
    server_audit, client_audit = launch.audits()
    paths = launch.cp(server_audit['command']) + launch.cp(client_audit['command']) + list(map(str, [suite, *dependencies, polymer]))
    paths += [str(launch.artifact('io.github.llamalad7', 'mixinextras-fabric', '0.5.5'))]
    patched = next((project_path(WORKSPACE, 'SSO-backpack-toolpouch-mapstitch-shim') / '.gradle/loom-cache').rglob('minecraft-merged-*-26.3.jar')); paths.insert(0, str(patched))
    def nested(jar):
        with zipfile.ZipFile(jar) as archive:
            for name in archive.namelist():
                if name.endswith('.jar'):
                    content = archive.read(name); target = fixtures / (hashlib.sha256(content).hexdigest()[:12] + '-' + Path(name).name)
                    if not target.exists(): target.write_bytes(content); paths.append(str(target)); nested(target)
    for jar in [suite, *dependencies, polymer]: nested(jar)
    subprocess.run(['/usr/lib/jvm/java-27-openjdk/bin/javac', '--release', '25', '-proc:none', '-cp', os.pathsep.join(dict.fromkeys(paths)), '-d', str(classes), *map(str, FIXTURES.glob('CraftingNetwork*.java'))], check=True)
    for side in ('server', 'client'):
        entry = 'CraftingNetwork' + side.title()
        metadata = {'schemaVersion': 1, 'id': 'suite_feature_network_qa_' + side, 'version': '1', 'environment': side,
                    'entrypoints': {'main' if side == 'server' else 'client': [entry]}, 'depends': {'fabric-api': '*'}}
        with zipfile.ZipFile(fixtures / (side + '.jar'), 'w') as archive:
            if side == 'client' and args.brbr:
                metadata['mixins'] = ['crafting_network_observer.mixins.json']
                archive.writestr('crafting_network_observer.mixins.json', json.dumps({'required':True,'package':'qa.backpackcrafting.mixin','compatibilityLevel':'JAVA_25','client':['CraftingNetworkBrbrTrace'],'injectors':{'defaultRequire':1}}))
            archive.writestr('fabric.mod.json', json.dumps(metadata))
            for file in classes.rglob('*.class'): archive.write(file, file.relative_to(classes))
    if args.compile_only:
        print('PASS observation fixtures compiled; no runtime launched',flush=True); return
    with zipfile.ZipFile(suite) as archive: version=json.loads(archive.read('fabric.mod.json'))['version']
    children=[]; observations=[]; env=os.environ.copy()
    env.update(DISPLAY=env.get('DISPLAY', ':1'), SDL_VIDEODRIVER='x11', SDL_VIDEO_X11_XINPUT2='0', LP_NUM_THREADS='2')
    def start(directory,command,mods):
        (directory/'mods').mkdir(parents=True,exist_ok=True)
        for jar in mods: shutil.copy2(jar,directory/'mods'/jar.name)
        (directory/'audit.json').write_text(json.dumps({'command':command,'mods':[{'file':p.name,'sha256':sha(p)} for p in mods]},indent=2))
        log=(directory/'console.log').open('w');process=subprocess.Popen(command,cwd=directory,env=env,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,text=True)
        children.append((process,log));return process
    def read(name,side):
        try:return json.loads((control/(name+'-'+side+'.json')).read_text())
        except (FileNotFoundError,json.JSONDecodeError):return {}
    def wait(condition,process,label,timeout=140):
        deadline=time.time()+timeout
        while time.time()<deadline:
            if (control/'failure').exists():raise RuntimeError((control/'failure').read_text())
            if process.poll() is not None:raise RuntimeError(label+': process exited')
            if condition():return
            time.sleep(.5)
        raise TimeoutError(label)
    def stop(process,server=False):
        if process.poll() is None:
            if server:process.stdin.write('stop\n');process.stdin.flush()
            else:process.terminate()
            try:process.wait(timeout=25)
            except subprocess.TimeoutExpired:process.kill();process.wait()
    try:
        directory=run/'server';directory.mkdir();port=freeport();launch.copy_accepted_eula(directory)
        (directory/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nwhite-list=false\nenforce-secure-profile=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\ndifficulty=peaceful\nlevel-type=minecraft:flat\ngenerator-settings={{"layers":[{{"block":"minecraft:bedrock","height":1}}],"biome":"minecraft:plains"}}\ngenerate-structures=false\n')
        command=launch.base_command('server',directory,port);command[0]='/usr/lib/jvm/java-25-openjdk/bin/java';command.insert(1,'-Dcrafting.qa.control='+str(control))
        server=start(directory,command,[suite,*dependencies,fixtures/'server.jar'] + ([] if args.without_polymer else [polymer]))
        wait(lambda:'Done (' in (directory/'console.log').read_text(),server,'server startup')
        for profile in args.profiles.split(','):
            name='FeatureNet'+profile.title();clientdir=run/name;clientdir.mkdir()
            save_server(clientdir/'servers.dat',port,'decline')
            (clientdir/'options.txt').write_text('tutorialStep:none\npauseOnLostFocus:false\nguiScale:2\ngraphicsMode:0\nrenderDistance:2\nmaxFps:25\nmaxFpsInactive:25\nsoundCategory_master:0.0\njoinedFirstServer:true\n')
            mods=[api,fixtures/'client.jar'] if profile=='fabric' else [suite,*dependencies,fixtures/'client.jar']
            command=launch.base_command('native',clientdir,port);command[0]='/usr/lib/jvm/java-25-openjdk/bin/java';command.insert(1,'-Dcrafting.qa.control='+str(control))
            command[command.index('--username')+1]=name
            if args.brbr: mods.append(args.brbr.resolve())
            if args.delay_grid: command.insert(1, '-Dcrafting.qa.delayGrid=true')
            client=start(clientdir,command,mods)
            wait(lambda:read(name,'client').get('ticks',0)>=40,client,name+' connected')
            click_observations=[]
            action_index=0
            for case_index,(tier,size) in enumerate(([('baseline',3)] if args.baseline else [(tier,size) for tier in ('leather','netherite') for size in (2,3)]) * args.cycles):
                previous_menu=read(name,'client').get('menu_id',-1)
                (control/(name+'-open.json')).write_text(json.dumps({'size':size,'backpack':args.backpack,'tier':tier,'baseline':args.baseline,'cycles':args.cycles}))
                wait(lambda:read(name,'client').get('menu_id',-1)!=previous_menu and read(name,'client').get('size')==size and read(name,'client').get('capacity')==(0 if args.baseline else 27 if tier=='leather' else 78),client,name+' crafting grid opened')
                def act(kind,right=False,maximum=False):
                    nonlocal action_index
                    action_index+=1
                    (control/(name+'-action.json')).write_text(json.dumps({'type':kind,'id':action_index,'right':right,'maximum':maximum}))
                    wait(lambda:any(x.get('id')==action_index for x in read(name,'client').get('actions',[])),client,kind)
                act('book')
                act('controls')
                act('settings-cancel')
                act('settings-save')
                act('resize-narrow')
                act('controls')
                act('settings-cancel')
                act('resize-wide')
                act('controls')
                time.sleep(1)
                act('recipe')
                wait(lambda:read(name,'server').get('result_count')==1,client,'recipe book fills real crafting grid')
                act('take')
                wait(lambda:read(name,'server').get('crafted_tables',0)>=1 and read(name,'server').get('result_count')==0 and read(name,'client').get('output')==0,client,'shift click receives crafting table and both sides converge')
                settled_tick=read(name,'client')['ticks']
                wait(lambda:read(name,'client').get('ticks',0)>=settled_tick+10,client,'settle previous crafting acknowledgement')
                row=read(name,'server')
                assert row['crafted_tables']*4+row['planks']+row['grid_planks']+row['stored_planks']==12, row
                if args.brbr:
                    previous_tables=row['crafted_tables']
                    maximum=case_index>=4
                    act('recipe',right=True,maximum=maximum)
                    wait(lambda:read(name,'server').get('crafted_tables',0)>=(3 if maximum else previous_tables+1),client,'BRBR right-click instant crafting')
                    row=read(name,'server')
                    assert row['crafted_tables']*4+row['planks']+row['grid_planks']+row['stored_planks']==12, row
                act('cake')
                if size==3:
                    wait(lambda:read(name,'client').get('ghost_slots',0)>0,client,'uncraftable recipe shows ghost ingredients')
                    act('snapshot')
                row=read(name,'server')
                assert row['crafted_tables']*4+row['planks']+row['grid_planks']+row['stored_planks']==12, row
                click_observations.append({'tier':tier,'size':size,'client':read(name,'client'),'server':row})
            observations.append({'profile':profile,'clicks':click_observations})
            stop(client)
            print('PASS '+profile+': recipe book clicks and server conservation',flush=True)
        stop(server,True)
        delayed_checks=sum((run/('FeatureNet'+profile.title())/'console.log').read_text().count('CRAFTING_QA_DELAYED_GRID') for profile in args.profiles.split(','))
        if args.delay_grid: assert delayed_checks >= 3, 'synthetic unsettled-grid mode did not execute'
        (run/'result.json').write_text(json.dumps({'passed':True,'version':version,'bundle_sha256':sha(suite),'backpack_ingredients':args.backpack,'delayed_grid':args.delay_grid,'delayed_predicate_checks':delayed_checks,'baseline':args.baseline,'cycles':args.cycles,'brbr_sha256':sha(args.brbr) if args.brbr else None,'polymer':not args.without_polymer,'profiles':args.profiles.split(','),'observations':observations,'scope':'BRB buttons follow the actual backpack recipe-book origin in wide and narrow layouts; mouse settings Save/Cancel and instant-toggle preserve storage. Connected recipe placement/crafting and server ingredient conservation.'},indent=2)+'\n')
        print('PASS backpack crafting GUI checks',flush=True)
    finally:
        for process,log in reversed(children):
            if process.poll() is None:
                process.terminate()
                try:process.wait(timeout=15)
                except subprocess.TimeoutExpired:process.kill();process.wait()
            log.close()

if __name__=='__main__':main()
