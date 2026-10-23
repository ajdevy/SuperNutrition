"""Exercise the Xcode/Gradle boundary without running Gradle or requiring Xcode."""
import json
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
HELPER = ROOT / 'mobile/apps/iosApp/build-sharedkit-framework.sh'
PROJECT = ROOT / 'mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj/project.pbxproj'
SYNC_TASK = ':apps:sharedKit:syncComposeResourcesForIos'


class IosFrameworkResourcesTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='ios framework resources ')
        self.addCleanup(self.temporary.cleanup)
        self.mobile = Path(self.temporary.name) / 'mobile'
        self.project = self.mobile / 'apps/iosApp'
        self.project.mkdir(parents=True)
        shutil.copyfile(HELPER, self.project / HELPER.name)
        self.invocation = self.mobile / 'gradle-invocation.json'
        gradle = self.mobile / 'gradlew'
        gradle.write_text('''#!/usr/bin/env python3
import json
import os
import sys
from pathlib import Path
Path(os.environ['INVOCATION']).write_text(json.dumps({
    'args': sys.argv[1:],
    'bundle': os.environ.get('BUILT_PRODUCTS_DIR'),
    'resources': os.environ.get('UNLOCALIZED_RESOURCES_FOLDER_PATH'),
}))
sys.exit(int(os.environ.get('GRADLE_EXIT_CODE', '0')))
''')
        gradle.chmod(0o755)

    def run_helper(self, target='iosArm64', configuration='Debug', mode=None, missing=(), **extra):
        environment = {key: value for key, value in os.environ.items() if key not in {
            'BUILT_PRODUCTS_DIR', 'UNLOCALIZED_RESOURCES_FOLDER_PATH', 'PLATFORM_NAME', 'ARCHS',
        }}
        environment.update({
            'PROJECT_DIR': str(self.project),
            'CONFIGURATION': configuration,
            'KOTLIN_TARGET': target,
            'INVOCATION': str(self.invocation),
        })
        if mode == '--xcode-resources':
            environment.update({
                'BUILT_PRODUCTS_DIR': str(self.mobile / 'products with spaces'),
                'UNLOCALIZED_RESOURCES_FOLDER_PATH': 'Super Nutrition.app',
                'PLATFORM_NAME': 'iphoneos' if target.lower() == 'iosarm64' else 'iphonesimulator',
                'ARCHS': 'arm64',
            })
        environment.update(extra)
        for variable in missing:
            environment.pop(variable, None)
        arguments = ['/bin/sh', str(self.project / HELPER.name)]
        if mode is not None:
            arguments.append(mode)
        return subprocess.run(arguments, env=environment, text=True, capture_output=True, check=False)

    def recorded(self):
        return json.loads(self.invocation.read_text())

    def test_standalone_framework_builds_remain_available_for_all_targets_and_configurations(self):
        for target, suffix, intel in [('iosArm64', 'IosArm64', 'false'),
                                      ('IosSimulatorArm64', 'IosSimulatorArm64', 'false'),
                                      ('iosX64', 'IosX64', 'true')]:
            for configuration in ['Debug', 'Release']:
                with self.subTest(target=target, configuration=configuration):
                    result = self.run_helper(target=target, configuration=configuration)
                    self.assertEqual(result.returncode, 0, result.stderr)
                    args = self.recorded()['args']
                    self.assertIn(f':apps:sharedKit:link{configuration}Framework{suffix}', args)
                    self.assertIn(f'-Psupernutrition.enableIntelIosX64={intel}', args)
                    self.assertIn('--dependency-verification=strict', args)
                    self.assertNotIn(SYNC_TASK, args)

    def test_xcode_build_requests_transitive_resource_sync_and_preserves_bundle_paths(self):
        for target in ['iosArm64', 'iosSimulatorArm64']:
            with self.subTest(target=target):
                result = self.run_helper(target=target, mode='--xcode-resources')
                self.assertEqual(result.returncode, 0, result.stderr)
                invocation = self.recorded()
                self.assertIn(SYNC_TASK, invocation['args'])
                self.assertEqual(invocation['bundle'], str(self.mobile / 'products with spaces'))
                self.assertEqual(invocation['resources'], 'Super Nutrition.app')

    def test_xcode_build_rejects_missing_or_empty_bundle_inputs_before_gradle(self):
        for variable in ['BUILT_PRODUCTS_DIR', 'UNLOCALIZED_RESOURCES_FOLDER_PATH', 'PLATFORM_NAME', 'ARCHS']:
            for missing in [True, False]:
                with self.subTest(variable=variable, missing=missing):
                    self.invocation.unlink(missing_ok=True)
                    arguments = {'missing': [variable]} if missing else {variable: ''}
                    result = self.run_helper(mode='--xcode-resources', **arguments)
                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn(variable, result.stderr)
                    self.assertFalse(self.invocation.exists())

    def test_x64_resource_sync_reports_pinned_compose_limitation_before_gradle(self):
        result = self.run_helper(target='iosX64', mode='--xcode-resources')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('iosX64', result.stderr)
        self.assertFalse(self.invocation.exists())

    def test_unknown_mode_does_not_silently_build_without_resources(self):
        result = self.run_helper(mode='--xcode-resource')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('--xcode-resources', result.stderr)
        self.assertFalse(self.invocation.exists())

    def test_gradle_failure_propagates_to_xcode(self):
        result = self.run_helper(mode='--xcode-resources', GRADLE_EXIT_CODE='37')
        self.assertEqual(result.returncode, 37)

    def test_both_xcode_hosts_opt_into_the_shared_resource_packaging_path(self):
        project = PROJECT.read_text()
        phase_calls = [line for line in project.splitlines()
                       if 'shellScript =' in line and 'build-sharedkit-framework.sh' in line]
        self.assertEqual(len(phase_calls), 2)
        for phase in phase_calls:
            self.assertIn('--xcode-resources', phase)


if __name__ == '__main__':
    unittest.main()
