"""Preserve the latest finished check's reports before another Gradle run replaces them."""
import hashlib
import json
from pathlib import Path
import shutil
import sys

from journal import event

root = Path.cwd()
state = json.loads((root / '.supervisor/state.json').read_text())
task = sys.argv[1]
record = state['tasks'][task]
if record['status'] not in ('failed', 'passed'):
    raise SystemExit('Wait for the check to finish before preserving its reports.')
destination = root / '.supervisor/reports' / f"{task}-{record['attempt']}"
destination.mkdir(parents=True, exist_ok=False)
sources = [
    'app/build/test-results/testDebugUnitTest', 'app/build/reports/tests/testDebugUnitTest',
    'app/build/outputs/androidTest-results/connected', 'app/build/reports/androidTests/connected',
    'app/build/reports/lint-results-debug.xml', 'app/build/reports/lint-results-debug.html',
]
files = {}
for source in sources:
    path = root / source
    for file in path.rglob('*') if path.is_dir() else [path]:
        if not file.is_file() or file.is_symlink():
            continue
        relative = file.relative_to(root)
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(file, target)
        digest = hashlib.sha256(file.read_bytes()).hexdigest()
        assert digest == hashlib.sha256(target.read_bytes()).hexdigest()
        files[str(relative)] = dict(sha256=digest, source_mtime=file.stat().st_mtime)
manifest = dict(task=task, attempt=record['attempt'], status=record['status'],
                started_at=record['started_at'], ended_at=record['ended_at'], files=files,
                note='Snapshot, not a pass claim. If Gradle failed early, some reports may belong to earlier checks.')
(destination / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
event(root / '.supervisor', 'reports-preserved', task=task, attempt=record['attempt'],
      artifact_dir=str(destination.relative_to(root)), files=len(files))
print(f'Preserved and SHA-256 verified {len(files)} report files at {destination.relative_to(root)}')
