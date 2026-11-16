"""Exercise local build preflight without invoking SDK/device tooling."""
import json
import pathlib
import subprocess
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]


class LocalBuildTest(unittest.TestCase):
    def test_dry_run_leaves_output_root_empty_and_has_no_release_steps(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = subprocess.run([sys.executable, str(ROOT / 'tools/build_local.py'),
                                '--output-root', tmp, '--version-code', '30',
                                '--version-name', '2.1.6-jaggu.14-source-only', '--dry-run'],
                               capture_output=True, text=True, cwd=tmp)
            self.assertEqual(p.returncode, 0, p.stderr)
            plan = json.loads(p.stdout)
            self.assertEqual(plan['network'], 'none')
            self.assertEqual(plan['device_actions'], 'none')
            self.assertEqual(plan['cleanup'], 'none; fresh directory each build')
            self.assertEqual(list(pathlib.Path(tmp).iterdir()), [])


if __name__ == '__main__':
    unittest.main()
