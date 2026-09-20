"""Run the retained browser check through Playwright CLI; preserve its exact output."""
from pathlib import Path
import argparse
import json
import struct
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--export-fixtures', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
name = 'export-fixtures' if args.export_fixtures else 'browser-check'
script = Path(__file__).with_name(name + '.js').read_text()
directory = root / '.supervisor/calibration-target'
directory.mkdir(parents=True, exist_ok=True)
assets = root / 'app/src/androidTest/assets/calibration-target'
if args.export_fixtures:
    assets.mkdir(parents=True, exist_ok=True)
command = ['npx', '--yes', '--package', '@playwright/mcp', 'playwright-cli',
           '-s=measure-calibration-check', 'run-code', script]
result = subprocess.run(command, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
with (directory / (name + '.log')).open('a') as output:
    output.write(result.stdout)
if result.returncode or '### Error' in result.stdout or '### Result\n' not in result.stdout:
    raise SystemExit('Browser command failed; see ' + str(directory / (name + '.log')))
payload = json.loads(result.stdout.split('### Result\n', 1)[1].split('\n### ', 1)[0])
if args.export_fixtures:
    for fixture in payload['fixtures']:
        png = (assets / (fixture['name'] + '.png')).read_bytes()
        dimensions = struct.unpack('>II', png[16:24])
        expected = (fixture['imageWidth'], fixture['imageHeight'])
        if png[:8] != b'\x89PNG\r\n\x1a\n' or dimensions != expected:
            raise SystemExit(f"Fixture PNG dimensions {dimensions} differ from metadata {expected}")
        (assets / (fixture['name'] + '.json')).write_text(json.dumps(fixture, indent=2) + '\n')
print(json.dumps(payload, indent=2))
