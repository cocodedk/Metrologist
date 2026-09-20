"""Exercise release refusal against real isolated Git refs, without publishing."""
from pathlib import Path
import subprocess
import tempfile
import unittest

from version import source_version


class SourceVersionTest(unittest.TestCase):
    def setUp(self):
        # Test workspaces remain project-local and disappear when each test ends.
        runtime = Path(__file__).resolve().parents[2] / '.supervisor/release-version-tests'
        runtime.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=runtime)
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.git('init', '--quiet')
        self.git('-c', 'commit.gpgsign=false', '-c', 'user.name=Release Test', '-c', 'user.email=test@example.invalid',
                 'commit', '--allow-empty', '--quiet', '-m', 'Test fixture')
        self.write('0.0.2', '2')

    def git(self, *args):
        subprocess.run(['git', *args], cwd=self.root, check=True, capture_output=True)

    def write(self, name, code):
        (self.root / 'gradle.properties').write_text(f'VERSION_NAME={name}\nVERSION_CODE={code}\n')

    def test_new_version(self):
        self.assertEqual(source_version(self.root), ('0.0.2', '2', 'v0.0.2'))

    def test_existing_release_cannot_be_republished(self):
        self.git('tag', 'v0.0.2')
        with self.assertRaisesRegex(ValueError, 'already exists'):
            source_version(self.root)

    def test_invalid_or_ambiguous_versions_are_rejected(self):
        for name, code in [('0.0.2', '3'), ('0.0.02', '2'), ('0.0.2-tag=evil', '2'),
                           ('0.0.2', '2\nVERSION_CODE=3'), ('0.1000.0', '1000000'),
                           ('0.0.0', '0'), ('2101.0.0', '2101000000')]:
            with self.subTest(name=name, code=code):
                self.write(name, code)
                with self.assertRaises(ValueError):
                    source_version(self.root)


if __name__ == '__main__':
    unittest.main()
