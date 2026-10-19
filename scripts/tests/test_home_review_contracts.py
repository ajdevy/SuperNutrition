"""Offline evidence-tool regressions; these tests never connect to a device."""
import contextlib
import importlib.util
import io
import json
import runpy
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
DIGEST = 'a' * 64


def generator():
    spec = importlib.util.spec_from_file_location('popular_home_test', ROOT / 'scripts/generate-popular-home-design-review.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class GalleryContractTest(unittest.TestCase):
    def render(self, change=None, digest_for_case=None):
        module = generator()
        profiles = [{'id': f'profile-{i}', 'geometry': f'geometry-{i}', 'label': str(i),
                     'share_percent': '1', 'kind': 'phone' if i < 6 else 'tablet'} for i in range(9)]
        selection = {'profiles': profiles, 'font_scales': [1.0, 2.0], 'expected_apk_sha256': DIGEST}
        if change:
            change(selection)
        catalog = {'unique_geometries': {p['geometry']: {'key': p['geometry']} for p in profiles}}
        calls = []
        written = {}

        def read(path):
            return {'selection.json': selection, 'sdk-device-catalog.json': catalog,
                    'original-review-preservation.json': {'source': '.', 'files': {}, 'count': 0}}[path.name]

        def capture(directory, leaf, requested, scale, inspections, phase):
            index = len(calls)
            calls.append(index)
            return {'status': 'fit', 'status_label': 'Помещается', 'inspection_verified': True,
                    'png': str(leaf.relative_to(directory) / 'home.png'),
                    'apk_sha256': digest_for_case(index) if digest_for_case else DIGEST}

        def write(path, text, *args, **kwargs):
            written[path.name] = text
            return len(text)

        with tempfile.TemporaryDirectory() as temporary:
            with patch.object(module.gallery, 'read_json', side_effect=read), \
                 patch.object(module.gallery, 'load_inspections', return_value={}), \
                 patch.object(module.gallery, 'capture_record', side_effect=capture), \
                 patch.object(module.gallery, 'capture_figure', return_value=''), \
                 patch.object(Path, 'write_text', autospec=True, side_effect=write), \
                 contextlib.redirect_stdout(io.StringIO()):
                module.build(Path(temporary))
        return json.loads(written['gallery-data.json'])

    def test_complete_nine_profile_two_scale_matrix_is_accepted(self):
        result = self.render()
        self.assertTrue(result['complete'])
        self.assertEqual(len(result['cases']), 18)

    def test_missing_profile_cannot_claim_complete(self):
        with self.assertRaisesRegex(ValueError, 'nine|9'):
            self.render(lambda selection: selection['profiles'].pop())

    def test_missing_enlarged_scale_cannot_claim_complete(self):
        with self.assertRaisesRegex(ValueError, '100|200|scale'):
            self.render(lambda selection: selection.update(font_scales=[1.0]))

    def test_duplicate_geometry_cannot_claim_distinct_profiles(self):
        with self.assertRaisesRegex(ValueError, 'distinct|unique'):
            self.render(lambda selection: selection['profiles'][1].update(geometry='geometry-0'))

    def test_mixed_apks_cannot_claim_complete(self):
        with self.assertRaisesRegex(ValueError, 'APK|digest'):
            self.render(digest_for_case=lambda index: 'b' * 64 if index == 0 else DIGEST)

    def test_consistent_wrong_apk_is_rejected_against_expected_build(self):
        with self.assertRaisesRegex(ValueError, 'APK|digest'):
            self.render(digest_for_case=lambda index: 'b' * 64)


class BaselineCaptureContractTest(unittest.TestCase):
    def test_before_capture_accepts_fitting_legacy_layout_without_new_tags(self):
        hierarchy = ET.Element('hierarchy')
        content = ET.SubElement(hierarchy, 'node', {'resource-id': 'home_screen', 'bounds': '[0,48][720,1512]'})

        def node(tag='', text='', frame='[48,200][672,240]', description='', clickable='false'):
            return ET.SubElement(content, 'node', {'resource-id': tag, 'text': text, 'bounds': frame,
                                                  'content-desc': description, 'clickable': clickable})

        node('home_title', 'Home')
        node(text='Super Nutrition')
        node('home_date', 'Monday, October 19')
        for tag, frame in [('home_profile', '[576,64][672,160]'),
                           ('home_add_food', '[48,250][312,346]'),
                           ('home_view_goals', '[328,250][672,346]'),
                           ('home_summary', '[48,700][672,1100]'),
                           ('home_metric_fiber', '[60,800][310,896]'),
                           ('home_metric_water', '[330,800][660,896]'),
                           ('home_metric_protein', '[60,920][310,1016]'),
                           ('home_metric_movement', '[330,920][660,1016]'),
                           ('home_next_step', '[48,1100][672,1300]')]:
            node(tag, frame=frame)
        for text in ['6 day rhythm', 'What kind of care do you need today?', 'Add a meal', 'View goals',
                     'Today’s rhythm', 'You’re building a good day', 'Fiber', '12 / 25 g', 'Water',
                     '1.2 / 2 L', 'Protein', '72 / 90 g', 'Movement', '32 / 45 min',
                     'Your next helpful step', 'Add one leafy green to dinner']:
            node(text=text)
        node(frame='[500,1160][620,1256]', description='Do it', clickable='true')
        node(frame='[210,380][510,710]', description='Capybara mascot')
        for tag in ['camera_action', 'capture_tab', 'rewards_tab']:
            node(tag, frame='[48,1380][200,1476]')
        xml = ET.tostring(hierarchy, encoding='unicode')

        def fake_run(words, **kwargs):
            command = ' '.join(words)
            if words[0] == 'android':
                output = ''
            elif command.endswith('devices -l'):
                output = 'List of devices attached\nemulator-5590 device\n'
            elif command.endswith('emu avd name'):
                output = 'SN_Test\nOK\n'
            elif 'getprop ro.kernel.qemu' in command:
                output = '1\n35\nx86_64\n'
            elif 'uiautomator dump' in command:
                output = xml
            elif 'dumpsys window' in command:
                output = 'mCurrentFocus=Window{app.supernutrition/Test}\n'
            elif 'pm path' in command:
                output = 'package:/data/app/fixture/base.apk\n'
            elif 'stat -c' in command:
                output = '100:200:300\n'
            elif 'sha256sum' in command:
                output = DIGEST + ' /data/app/fixture/base.apk\n'
            elif 'wm size' in command and 'wm density' in command and 'settings get' in command:
                output = 'Physical size: 720x1560\nPhysical density: 320\n1.0\n'
            elif 'settings put system' in command or 'am start' in command:
                output = ''
            else:
                raise AssertionError('Unexpected mocked command: ' + command)
            return subprocess.CompletedProcess(words, 0, output)

        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory / 'baseline-SN_Test.json').write_text('{}')
            (directory / 'sdk-device-catalog.json').write_text(json.dumps({'unique_geometries': {
                '720x1560-320dpi': {'key': '720x1560-320dpi', 'width_px': 720, 'height_px': 1560,
                                    'density_dpi': 320, 'devices': []}}}))
            argv = ['review-home-viewport.py', 'capture', '--phase', 'before', '--serial', 'emulator-5590',
                    '--avd', 'SN_Test', '--output', str(directory)]
            with patch.object(sys, 'argv', argv), patch.object(subprocess, 'run', side_effect=fake_run), \
                 contextlib.redirect_stdout(io.StringIO()):
                runpy.run_path(str(ROOT / 'scripts/review-home-viewport.py'), run_name='__main__')
            result = json.loads((directory / 'before/720x1560-320dpi/font-100/metadata.json').read_text())
            self.assertTrue(result['native_initial_fit'], result)
            self.assertFalse(result['missing_initial_tags'])


if __name__ == '__main__':
    unittest.main()
