"""Check page geometry against already-built app bytecode, without another Gradle build."""
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tomllib

root = Path(__file__).resolve().parents[2]
classes = root / 'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes'
engine = classes / 'com/cocode/measureapp/geometry/MetrologyEngine.class'
sources = list((root / 'app/src/main/java').rglob('*.kt'))
if not engine.exists() or any(p.stat().st_mtime > engine.stat().st_mtime for p in sources):
    raise SystemExit('Build the current app first; engine bytecode is absent or older than source.')
version = tomllib.loads((root / 'gradle/libs.versions.toml').read_text())['versions']['kotlin']
cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle'))
jars = list((cache / 'caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib' / version).glob('*/*.jar'))
if len(jars) != 1:
    raise SystemExit('Expected one cached Kotlin stdlib matching the project version: ' + version)
directory = root / '.supervisor/calibration-target/engine-probe'
directory.mkdir(parents=True, exist_ok=True)
classpath = os.pathsep.join(map(str, [classes, jars[0], directory]))
with (directory / 'commands.log').open('a') as log:
    def run(command):
        log.write(json.dumps(command) + '\n')
        result = subprocess.run(command, cwd=root, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        log.write(result.stdout + '\n')
        log.flush()
        result.check_returncode()
        return result.stdout

    run(['javac', '-J-Xmx128m', '-cp', classpath, '-d', str(directory), 'tools/calibration/EngineProbe.java'])
    results = []
    for name in ['frontal', 'nonrectangle']:
        path = root / f'app/src/androidTest/assets/calibration-target/{name}.json'
        fixture = json.loads(path.read_text())
        values = [v for point in fixture['objectPixels'] + fixture['referencePixels'] for v in point]
        values += [fixture['intrinsics'][key] for key in ['fx', 'fy', 'cx', 'cy']]
        values += fixture['physicalDown'] + [fixture['referenceLengthMetres'], fixture['referenceWidthMetres']]
        actual = json.loads(run(['java', '-Xmx128m', '-cp', classpath, 'EngineProbe', *map(str, values)]))
        for key, expected in fixture['expected'].items():
            got = actual[key]
            pairs = zip(got, expected) if isinstance(expected, list) else [(got, expected)]
            if any(abs(a - b) > max(abs(b), 1e-12) * 1e-6 for a, b in pairs):
                raise AssertionError(f'{name} {key}: expected {expected}, got {got}')
        wanted = 'RECTANGLE' if name == 'frontal' else 'GRAVITY'
        assert actual['solver'] == wanted, actual
        if name == 'nonrectangle':
            assert actual['confidence'] <= .35, actual
        results.append(dict(name=name, actual=actual, fixture_sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
    record = dict(at=datetime.now(timezone.utc).isoformat(),
                  kind='Compiled production engine, page coordinates, synthetic camera metadata; no UI/camera',
                  engine_class_sha256=hashlib.sha256(engine.read_bytes()).hexdigest(), results=results)
    (directory / 'result.json').write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps(record, indent=2))
