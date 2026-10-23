"""Exercise Maestro's failure output and the workflow's artifact boundary without a device."""
import glob
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RUNNER = ROOT / 'scripts/run-ui-journeys-android.sh'
WORKFLOW = ROOT / '.github/workflows/android-ui-coverage.yml'


class AndroidMaestroDiagnosticsTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='android maestro diagnostics ')
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.repo = self.directory / 'repo with spaces'
        (self.repo / 'scripts').mkdir(parents=True)
        shutil.copyfile(ROOT / 'scripts/verify_ui_test_reports.py',
                        self.repo / 'scripts/verify_ui_test_reports.py')
        self.runner_temp = self.directory / 'runner temp'
        self.report = self.runner_temp / 'supernutrition-ui-android/report'
        (self.report / 'debug').mkdir(parents=True)
        self.default_debug = self.directory / 'default maestro output'
        self.maestro = self.directory / 'stub maestro'
        self.maestro.write_text('''#!/usr/bin/env python3
import os
import sys
from pathlib import Path

args = sys.argv[1:]
if args[0] == 'check-syntax':
    sys.exit(0)

def option(name, default):
    return args[args.index(name) + 1] if name in args else default

# An early CLI exception writes the diagnostic log before any flow command runs.
# --test-output-dir does not select the diagnostic log's location.
debug_root = Path(option('--debug-output', os.environ['DEFAULT_MAESTRO_DEBUG']))
debug = (debug_root / 'maestro.log' if '--flatten-debug-output' in args else
         debug_root / '.maestro/tests/session/maestro.log')
debug.parent.mkdir(parents=True, exist_ok=True)
debug.write_text('Driver initialization exception: diagnostic detail\\n')
report = Path(option('--output', 'report.xml'))
report.parent.mkdir(parents=True, exist_ok=True)
outcome = os.environ['MAESTRO_OUTCOME']
if outcome != 'missing-report':
    failure = '<failure>Unknown error</failure>' if outcome == 'failure' else ''
    report.write_text('<testsuite><testcase name="capture">' + failure + '</testcase></testsuite>')
sys.exit(37 if outcome == 'failure' else 0)
''')
        self.maestro.chmod(0o755)
        # Execute the real runner's Maestro phase, substituting only the external CLI
        # and device/build prerequisites. The real fail-closed report verifier runs.
        tail = RUNNER.read_text().split('maestro_bin="$(ensure_maestro_cli', 1)
        self.assertEqual(len(tail), 2, 'The runner must retain its Maestro phase')
        self.harness = self.directory / 'maestro phase.sh'
        self.harness.write_text('''#!/usr/bin/env bash
set -euo pipefail
repo_root="$1"
temp_root="$2"
report_root="$temp_root/report"
device_serial=emulator-test
ensure_maestro_cli() { printf '%s\\n' "$STUB_MAESTRO"; }
''' + 'maestro_bin="$(ensure_maestro_cli' + tail[1])

    def run_phase(self, outcome):
        environment = dict(os.environ,
                           STUB_MAESTRO=str(self.maestro),
                           DEFAULT_MAESTRO_DEBUG=str(self.default_debug),
                           MAESTRO_OUTCOME=outcome)
        return subprocess.run(['bash', str(self.harness), str(self.repo),
                               str(self.report.parent)],
                              env=environment, text=True, capture_output=True, check=False)

    def uploaded_files(self):
        upload = WORKFLOW.read_text().split('- name: Upload Android UI reports\n', 1)[1]
        self.assertIn('if: always()', upload, 'Failure diagnostics must upload after a failed test step')
        paths = upload.split('path: |\n', 1)[1].split('          if-no-files-found:', 1)[0]
        files = set()
        for entry in paths.splitlines():
            pattern = entry.strip().replace('${{ runner.temp }}', str(self.runner_temp))
            if not pattern:
                continue
            if not Path(pattern).is_absolute():
                pattern = str(self.repo / pattern)
            for match in glob.glob(pattern, recursive=True):
                path = Path(match)
                candidates = path.rglob('*') if path.is_dir() else [path]
                # upload-artifact excludes hidden files/directories by default.
                files.update(candidate for candidate in candidates
                             if not any(part.startswith('.') for part in
                                        candidate.relative_to(path.parent).parts))
        return {path for path in files if path.is_file()}

    def test_early_maestro_failure_retains_diagnostic_detail_and_original_exit(self):
        result = self.run_phase('failure')
        self.assertEqual(result.returncode, 37, result.stderr)
        self.assertNotIn('JUnit tests passed', result.stdout)
        logs = list((self.report / 'debug').rglob('maestro.log'))
        self.assertEqual(len(logs), 1, 'Early Maestro exception detail escaped the report directory')
        self.assertIn('Driver initialization exception', logs[0].read_text())
        self.assertIn(logs[0], self.uploaded_files())

    def test_upload_allowlist_retains_nested_debug_logs_after_test_failure(self):
        diagnostic = self.report / 'debug/session/maestro.log'
        diagnostic.parent.mkdir(parents=True)
        diagnostic.write_text('Nested CLI diagnostic detail\n')
        self.assertIn(diagnostic, self.uploaded_files())

    def test_success_still_requires_a_passing_junit_report(self):
        result = self.run_phase('success')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('Android Maestro: 1/1 JUnit tests passed', result.stdout)
        self.assertIn(self.report / 'junit.xml', self.uploaded_files())

    def test_zero_exit_without_junit_report_is_rejected(self):
        result = self.run_phase('missing-report')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('no JUnit testcases found', result.stderr)


if __name__ == '__main__':
    unittest.main()
