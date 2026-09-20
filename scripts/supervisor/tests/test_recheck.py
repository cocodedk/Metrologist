import sys

from support import SupervisorCase


class RecheckTests(SupervisorCase):
    def test_failed_check_can_retry_without_another_worker(self):
        task = self.task('a')
        task['check'] = [sys.executable, '-c', "from pathlib import Path; assert Path('allow').exists()"]
        self.plan([task])
        self.assertEqual(self.start().wait(timeout=8), 1)
        first = self.root.joinpath('started-a').stat().st_mtime_ns
        self.root.joinpath('allow').touch()
        self.assert_ok(self.command('recheck', 'a'))
        current = self.state()['tasks']['a']
        self.assertEqual(current['status'], 'passed')
        self.assertEqual(current['attempt'], 2)
        self.assertTrue(current['verification_only'])
        self.assertEqual(current['history'][0]['status'], 'failed')
        self.assertEqual(self.root.joinpath('started-a').stat().st_mtime_ns, first)
        self.assertTrue((self.root / '.supervisor/a-1-check.log').exists())
        self.assertTrue((self.root / '.supervisor/a-2-check.log').exists())
        self.assertFalse((self.root / '.supervisor/a-2-worker.log').exists())

    def test_worker_failure_cannot_skip_to_verification(self):
        self.plan([self.task('a', exit_code=1)])
        self.assertEqual(self.start().wait(timeout=8), 1)
        self.assertEqual(self.command('recheck', 'a').returncode, 2)
        self.assertEqual(self.state()['tasks']['a']['attempt'], 1)

    def test_full_worker_retry_after_a_recheck_clears_verification_only(self):
        task = self.task('a')
        task['check'] = [sys.executable, '-c', "from pathlib import Path; assert Path('allow').exists()"]
        self.plan([task])
        self.assertEqual(self.start().wait(timeout=8), 1)
        self.assertEqual(self.command('recheck', 'a').returncode, 1)
        self.assertTrue(self.state()['tasks']['a']['verification_only'])
        self.root.joinpath('allow').touch()
        self.assert_ok(self.command('retry', 'a'))
        self.assertEqual(self.start().wait(timeout=8), 0)
        self.assertNotIn('verification_only', self.state()['tasks']['a'])
        self.assertTrue((self.root / '.supervisor/a-3-worker.log').exists())
