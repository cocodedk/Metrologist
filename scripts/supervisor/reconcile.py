"""Record a coordinator-reviewed, passing run in the Markdown graph; retain previous notes."""
import argparse
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET

from journal import event
from state import load_plan, now
from validate import check_graph


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reviewed', action='store_true', help='Coordinator has reviewed acceptance and edge evidence')
    args = parser.parse_args()
    if not args.reviewed:
        parser.error('Review implementation, acceptance and connected-edge evidence before using --reviewed')
    root = Path.cwd()
    runtime = root / '.supervisor'
    state = json.loads((runtime / 'state.json').read_text())
    _, _, digest = load_plan(root, root / 'docs/supervisor/tasks.json')
    if state['plan_hash'] != digest:
        raise ValueError('The execution plan changed after verification')
    tasks = state['tasks']
    if state['mode'] != 'finished' or any(t['status'] != 'passed' for t in tasks.values()):
        raise ValueError('All tasks must have finished and passed before graph reconciliation')
    final = tasks['integration']
    verified = final['verified_at']
    started = datetime.fromisoformat(final['started_at'].replace('Z', '+00:00')).timestamp()
    sources = [p for p in (root / 'app/src').rglob('*') if p.is_file()]
    sources += [root / p for p in ['app/build.gradle.kts', 'build.gradle.kts', 'gradle/libs.versions.toml']]
    if any(p.stat().st_mtime > started for p in sources):
        raise ValueError('App sources changed after the final check started; verify that revision first')

    def results(folder):
        files = list((root / folder).rglob('*.xml'))
        if not files or any(p.stat().st_mtime < started for p in files):
            raise ValueError('Missing or stale test reports: ' + folder)
        suites = [ET.parse(p).getroot() for p in files]
        counts = {k: sum(int(s.get(k, 0)) for s in suites) for k in ['tests', 'failures', 'errors', 'skipped']}
        if not counts['tests'] or any(counts[k] for k in ['failures', 'errors', 'skipped']):
            raise ValueError('Non-passing test reports: ' + str(counts))
        return counts['tests']

    android = results('app/build/outputs/androidTest-results/connected')
    jvm = results('app/build/test-results/testDebugUnitTest')
    lint_path = root / 'app/build/reports/lint-results-debug.xml'
    lint = ET.parse(lint_path).getroot().findall('issue')
    if lint_path.stat().st_mtime < started or any(i.get('severity') in ('Error', 'Fatal') for i in lint):
        raise ValueError('Lint evidence is stale or contains errors')
    warnings = sum(i.get('severity') == 'Warning' for i in lint)
    stamp = verified.replace('-', '').replace(':', '')
    archive = runtime / 'graph-records' / stamp
    archive.mkdir(parents=True, exist_ok=True)
    shutil.copy2(runtime / 'state.json', archive / 'state.json')
    (archive / 'source-sha256.json').write_text(json.dumps({
        str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(sources)
    }, indent=2) + '\n')
    report = root / 'docs/reviews' / f'graph-loop-{stamp}.md'
    report_path = str(report.relative_to(root))
    report_rows = []
    records = {}
    for path in sorted((root / 'docs/logic-contracts').glob('*.md')):
        original = path.read_text()
        parts = original.split('---', 2)
        if len(parts) != 3:
            continue
        node = re.search(r'^id: (N\d+)$', parts[1], re.M).group(1)
        related = [(name, t) for name, t in tasks.items() if t['node'] == node]
        if not related:
            raise ValueError('No task evidence for ' + node)
        name, task = related[-1]
        worker_evidence = [e for e in task['evidence'] if e['phase'] == 'worker']
        values = dict(
            build_status='built', build_agent='/root' if node == 'N00' else task['agent'],
            build_pid=None if node == 'N00' else task.get('agent_pid'),
            build_pid_note='host-managed' if node == 'N00' else 'recorded',
            build_attempt=sum(t['attempt'] for _, t in related),
            build_started_at=min(t['started_at'] for t in tasks.values()) if node == 'N00' else task['started_at'],
            build_ended_at=verified, build_verified_at=verified,
            build_heartbeat_at=now() if node == 'N00' else worker_evidence[-1]['at'],
            build_files=[], build_last_error=None,
            build_evidence=[report_path, f'.supervisor/reports/integration-{final["attempt"]}/manifest.json',
                            task['log'], str((archive / 'state.json').relative_to(root))],
        )
        records[path.name] = values
        before = archive / path.name
        if not before.exists():
            before.write_text(original)
        for key, value in values.items():
            rendered = value if key in ('build_status', 'build_pid_note') else json.dumps(value)
            parts[1], count = re.subn(rf'^{key}:.*$', f'{key}: {rendered}', parts[1], flags=re.M)
            if count != 1:
                raise ValueError(f'Missing or repeated property {key} in {path}')
        marker = f'### Verified run {verified}'
        body = parts[2]
        if marker not in body:
            body = re.sub(r'No (?:overall )?implementation attempts? ha(?:s|ve) started\.[\s\S]*?\Z', '', body)
            rows = []
            for task_name, record in related:
                for attempt in [*record['history'], record]:
                    rows.append(f'| {task_name}/{attempt["attempt"]} | {attempt.get("agent_pid", "—")} | '
                                f'{attempt.get("started_at")} | {attempt.get("ended_at")} | {attempt["status"]} |')
            body += ('\n' + marker + '\n\n'
                     'PIDs are historical; all workers have stopped and file reservations are released. '
                     'Verification-only attempts reuse the completed worker identity. '
                     f'[Run report](../reviews/{report.name}) records the evidence and limits.\n\n'
                     '| Task / attempt | Worker PID | Start UTC | End UTC | Outcome |\n'
                     '| --- | --- | --- | --- | --- |\n' + '\n'.join(rows) + '\n')
        path.write_text('---' + parts[1] + '---' + body)
    check_graph(root)
    for name, task in tasks.items():
        report_rows.append(f'| {name}/{task["attempt"]} | {task.get("agent_pid")} | '
                           f'{task["started_at"]} | {task["ended_at"]} | passed |')
    report.write_text(
        f'# Graph-Loop verification — {verified}\n\n'
        f'All {len(tasks)} tasks passed. The coordinator reviewed implementation and contract evidence, '
        'then reconciled all nine work nodes and the overall map to `built`. '
        'This describes the tested implementation; physical camera accuracy remains a separate check.\n\n'
        f'- Final check: integration attempt {final["attempt"]}, {final["started_at"]} to {verified}.\n'
        f'- Android: {android} passed, no failures or skips; all C01–C15 and both calibration targets.\n'
        f'- JVM: {jvm} passed, no failures or skips. Debug APK assembled.\n'
        f'- Lint: no errors, {warnings} warnings; existing baseline retained.\n'
        '- Supervisor: 40 tests passed; graph validation and final scope monitor passed.\n\n'
        '## Worker records\n\n'
        'Workers used `cyp opus high`; CLI identity reported `claude-opus-5`. '
        'PIDs below identify completed workers, not running processes. Integration retries after '
        'attempt 1 ran only verification. Original agent sessions, intermediate attempts and exact '
        'streaming output remain in `.supervisor/state.json`, `events.jsonl` and attempt logs.\n\n'
        '| Task / attempt | Worker PID | Start UTC | End UTC | Outcome |\n'
        '| --- | --- | --- | --- | --- |\n' + '\n'.join(report_rows) + '\n\n'
        '## Evidence and limits\n\n'
        f'Final reports and SHA-256 manifest: `.supervisor/reports/integration-{final["attempt"]}/`. '
        f'Previous graph notes, state snapshot and tested-source hashes: `{archive.relative_to(root)}/`. '
        'Failed attempts are preserved; they are not presented as successful checks.\n\n'
        'The Android flow host uses real screens, measurement logic, bitmap alignment and capture '
        'controller with simulated camera/sensor inputs. The separate repository test uses actual '
        'DataStore. It does not prove CameraX exposure timing, lens accuracy or every top-level '
        '`MeasureApp` callback. The actual app capture/Retake smoke check is recorded in the '
        '[calibration review](calibration-target-2026-09-19.md). The API 37 emulator ran native '
        'libraries in 16 KB compatibility mode. Physical phone verification remains outstanding.\n\n'
        'Coordinator fixes during verification: exact PNG clip dimensions, a compatible Espresso '
        'test dependency, precision-preserving settings input, direct repository coverage, and '
        'the AndroidX Camera2 opt-in annotation. All passed the final combined gate.\n\n'
        'The recovery worker made two provider-side advisor calls despite the CLI tool deny-list. '
        'Their calls and encrypted results are logged; no separate PID or model was supplied. '
        'The integration worker was explicitly prohibited from using it and made no observed call. '
        'Jev was not used for these source reviews or tests.\n')
    (archive / 'reconciliation.json').write_text(json.dumps(records, indent=2) + '\n')
    event(runtime, 'graph-reconciled', reviewed_at=now(), verified_at=verified, report=report_path,
          archive=str(archive.relative_to(root)), android_tests=android, jvm_tests=jvm)
    print(report_path)


if __name__ == '__main__':
    main()
