import contextlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).parents[1]))
from monitor import activity, inspect
from validate import BUILD_KEYS, check_graph


class GraphToolTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.addCleanup(self.temp.cleanup)

    def graph(self):
        folder = self.root / 'docs/logic-contracts'
        folder.mkdir(parents=True)

        def note(name, node, body):
            fields = {key: 'null' for key in BUILD_KEYS}
            fields.update(id=node, build_status='not-built')
            text = '---\n' + ''.join(f'{k}: {v}\n' for k, v in fields.items()) + '---\n' + body
            (folder / f'{name}.md').write_text(text)

        edges = [f'C{i:02d}' for i in range(1, 16)]
        note('producer', 'N01', ''.join(
            f'### {c} Guarantee\n**Consumer:** [[consumer]].\n**Edge assertion:** observable behavior.\n'
            for c in edges))
        note('consumer', 'N02', '\n'.join(f'[[producer#{c} Guarantee]]' for c in edges))
        note('00-logic-map', 'N00', ''.join(
            f'| {c} | 01 | 02 | [[producer#{c} Guarantee]] |\nN01 -->|"{c} Guarantee"| N02\n'
            for c in edges))
        return folder

    def test_complete_graph_accepts_matching_edges(self):
        self.graph()
        self.assertEqual(check_graph(self.root)['contracts'], 15)

    def test_wrong_consumer_in_diagram_is_rejected(self):
        folder = self.graph()
        file = folder / '00-logic-map.md'
        file.write_text(file.read_text().replace('"C07 Guarantee"| N02', '"C07 Guarantee"| N01'))
        with self.assertRaisesRegex(ValueError, 'disagree'):
            check_graph(self.root)

    def test_broken_contract_heading_is_rejected(self):
        folder = self.graph()
        file = folder / 'consumer.md'
        file.write_text(file.read_text().replace('C07 Guarantee', 'C07 Missing'))
        with self.assertRaisesRegex(ValueError, 'Broken heading'):
            check_graph(self.root)

    def test_monitor_reports_undeclared_write_and_handles_no_evidence(self):
        runtime = self.root / '.supervisor'
        runtime.mkdir()
        state = {'mode': 'blocked', 'tasks': {'task': {'status': 'failed', 'attempt': 1, 'evidence': []}}}
        (runtime / 'state.json').write_text(json.dumps(state))
        plan = {'tasks': [{'id': 'task', 'files': ['allowed/'], 'specs': []}]}
        path = self.root / 'plan.json'
        path.write_text(json.dumps(plan))
        event = {'type': 'assistant', 'message': {'content': [
            {'type': 'tool_use', 'name': 'Write', 'input': {'file_path': 'outside.kt'}},
        ]}}
        (runtime / 'task-1-worker.log').write_text(json.dumps(event) + '\n{"partial":')
        with contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertEqual(inspect(self.root, path), 1)
        self.assertIn('undeclared write outside.kt', output.getvalue())
        saved = json.loads((runtime / 'events.jsonl').read_text())
        self.assertEqual(saved['violations'], ['task: undeclared write outside.kt'])

    def test_provider_side_tools_remain_visible_without_invented_pid_or_model(self):
        path = self.root / 'worker.log'
        start = {'type': 'assistant', 'timestamp': 'start', 'message': {'content': [
            {'type': 'server_tool_use', 'id': 'remote1', 'name': 'advisor', 'input': {}}]}}
        end = {'type': 'assistant', 'timestamp': 'end', 'message': {'content': [
            {'type': 'advisor_tool_result', 'tool_use_id': 'remote1',
             'content': {'type': 'advisor_redacted_result', 'encrypted_content': 'opaque'}}]}}
        path.write_text(json.dumps(start) + '\n')
        entry = activity(self.root, {}, path)[-1]['remote1']
        self.assertEqual(entry['status'], 'waiting')
        self.assertIsNone(entry['pid'])
        path.write_text(json.dumps(start) + '\n' + json.dumps(end) + '\n')
        entry = activity(self.root, {}, path)[-1]['remote1']
        self.assertEqual(entry['status'], 'returned')
        self.assertEqual(entry['ended_at'], 'end')
        self.assertEqual(entry['result_type'], 'advisor_redacted_result')
        self.assertNotIn('opaque', json.dumps(entry))
