"""Run an isolated, read-only Android emulator with durable logs and process ownership."""
import argparse
import json
import os
from pathlib import Path
import re
import signal
import socket
import subprocess

from journal import event
from state import now, process
from avd import from_template


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("avd", help="An existing AVD; its disk is opened read-only")
    parser.add_argument("--port", type=int, default=5556)
    parser.add_argument("--memory", type=int, default=2048, help="Guest RAM in MiB")
    parser.add_argument("--image", help="Create a fresh project-local AVD from this installed SDK image package")
    parser.add_argument("--template", help="Use only an existing AVD's hardware config and system image; start with fresh data")
    args = parser.parse_args()
    if args.port % 2 or not 5554 <= args.port <= 5682:
        parser.error("Choose an even emulator console port from 5554 to 5682")
    if not re.fullmatch(r'[A-Za-z0-9_-]+', args.avd) or (args.template and not re.fullmatch(r'[A-Za-z0-9_-]+', args.template)):
        parser.error("AVD names must contain only letters, digits, underscores or hyphens")
    if args.image and args.template:
        parser.error("Choose either --image or --template")
    root = Path.cwd()
    runtime = root / '.supervisor'
    runtime.mkdir(exist_ok=True)
    for port in (args.port, args.port + 1):
        with socket.socket() as connection:
            connection.settimeout(.2)
            if connection.connect_ex(('127.0.0.1', port)) == 0:
                raise ValueError(f"Port {port} is already occupied; refusing to reuse another emulator")
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or Path.home() / 'Android/Sdk')
    stamp = now().replace(':', '').replace('-', '')
    folder = runtime / 'devices' / f'{stamp}-{args.port}'
    folder.mkdir(parents=True, exist_ok=False)
    serial = f'emulator-{args.port}'
    environment = os.environ.copy()
    if args.template:
        avd_home = folder / 'avd'
        avd = from_template(sdk, avd_home, args.avd, args.template)
        environment['ANDROID_AVD_HOME'] = str(avd_home)
        event(runtime, 'emulator-create', avd=args.avd, template=args.template,
              artifact_dir=str(avd.relative_to(root)), data='fresh; source data not copied')
    if args.image:
        avd_home = folder / 'avd'
        avd_home.mkdir()
        environment['ANDROID_AVD_HOME'] = str(avd_home)
        create = [str(sdk / 'cmdline-tools/latest/bin/avdmanager'), 'create', 'avd',
                  '--name', args.avd, '--package', args.image, '--path', str(avd_home / (args.avd + '.avd'))]
        with (folder / 'avd-create.log').open('x') as output:
            subprocess.run(create, input='no\n', text=True, stdout=output, stderr=subprocess.STDOUT,
                           env=environment, check=True)
        event(runtime, 'emulator-create', avd=args.avd, image=args.image, artifact_dir=str(folder.relative_to(root)))
    command = [str(sdk / 'emulator/emulator'), '-avd', args.avd, '-read-only',
               '-port', str(args.port), '-no-window', '-no-audio', '-no-snapshot',
               '-no-boot-anim', '-memory', str(args.memory), '-cores', '2', '-gpu', 'swiftshader']
    record = dict(serial=serial, avd=args.avd, command=command, started_at=now(),
                  wrapper_pid=os.getpid(), log=str((folder / 'emulator.log').relative_to(root)))
    with (folder / 'emulator.log').open('x') as output:
        child = subprocess.Popen(command, stdout=output, stderr=subprocess.STDOUT,
                                 env=environment, start_new_session=True)
        record.update(pid=child.pid, process_start=(process(child.pid) or {}).get('start'))
        (folder / 'process.json').write_text(json.dumps(record, indent=2) + '\n')
        (runtime / 'emulator.json').write_text(json.dumps(record, indent=2) + '\n')
        (runtime / 'android-serial').write_text(serial + '\n')
        event(runtime, 'emulator-launch', **record)

        def stop(_signum, _frame):
            if child.poll() is None:
                os.killpg(child.pid, signal.SIGTERM)

        old = {sig: signal.signal(sig, stop) for sig in (signal.SIGTERM, signal.SIGINT, signal.SIGHUP)}
        try:
            print(f'{serial}: PID {child.pid}; log {record["log"]}', flush=True)
            code = child.wait()
        finally:
            stop(None, None)
            try:
                child.wait(timeout=10)
            except subprocess.TimeoutExpired:
                os.killpg(child.pid, signal.SIGKILL)
                child.wait()
            for sig, previous in old.items():
                signal.signal(sig, previous)
            record.update(ended_at=now(), exit_code=child.returncode)
            (folder / 'process.json').write_text(json.dumps(record, indent=2) + '\n')
            (runtime / 'emulator.json').write_text(json.dumps(record, indent=2) + '\n')
            event(runtime, 'emulator-exit', **record)
        return code


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error))
