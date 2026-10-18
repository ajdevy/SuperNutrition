#!/usr/bin/env python3
"""Capture one owned-emulator Home frame; inspect its PNG before the next capture."""
import argparse
import datetime
import hashlib
import json
import os
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / '_bmad-output/design-review/home-no-scroll-2026-10-17'
SDK = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Library/Android/sdk')))
PACKAGE = 'app.supernutrition'
ACTIVITY = PACKAGE + '/com.supernutrition.feature.photoanalysis.PhotoAnalysisComposeActivity'
ENV = os.environ.copy()
ENV.update({'ADB_SERVER_SOCKET': 'tcp:localhost:5041', 'ANDROID_ADB_SERVER_PORT': '5041', 'ADB_MDNS_AUTO_CONNECT': '0'})
PARSER = argparse.ArgumentParser(description=__doc__)
PARSER.add_argument('action', choices=['init', 'capture', 'restore'])
PARSER.add_argument('--phase', default='before')
PARSER.add_argument('--geometry', default='720x1560-320dpi')
PARSER.add_argument('--font', type=float, default=1.0)
PARSER.add_argument('--serial', default='emulator-5554')
PARSER.add_argument('--avd', default='SuperNutrition_API_35_x86_64')
ARGS = PARSER.parse_args()
TIMINGS = []


def command(words, *, timeout=35):
    started = time.monotonic()
    result = subprocess.run(words, env=ENV, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout)
    TIMINGS.append({'command': words, 'seconds': round(time.monotonic() - started, 3)})
    if result.returncode:
        raise RuntimeError(f'{words!r}: {result.stdout}')
    return result.stdout.strip()


def adb(*words, timeout=35):
    return command([str(SDK / 'platform-tools/adb'), '-P', '5041', '-s', ARGS.serial, *words], timeout=timeout)


def shell_script(lines, *, timeout=60):
    return adb('shell', shlex.join(['sh', '-c', 'set -e\n' + '\n'.join(lines)]), timeout=timeout)


def stamp():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def guard():
    devices = command([str(SDK / 'platform-tools/adb'), '-P', '5041', 'devices', '-l'])
    online = [line.split()[0] for line in devices.splitlines()[1:] if len(line.split()) > 1 and line.split()[1] == 'device']
    assert online == [ARGS.serial], f'Exactly one owned guest is required: {devices}'
    properties = shell_script(['getprop ro.kernel.qemu', 'getprop ro.build.version.sdk',
                               'getprop ro.product.cpu.abi']).splitlines()
    assert properties == ['1', '35', 'x86_64'], 'Only the owned API35 x86_64 emulator may be mutated'
    assert adb('emu', 'avd', 'name').splitlines()[0] == ARGS.avd


def foreground():
    deadline = time.monotonic() + 30
    while True:
        window = adb('shell', 'dumpsys', 'window')
        rows = [line.strip() for line in window.splitlines() if 'mCurrentFocus=' in line]
        if any(PACKAGE + '/' in line for line in rows):
            return window, rows
        # Recreating an owned Activity can briefly leave focus null. Wait read-only;
        # an actual different foreground still stops every subsequent UI action.
        assert rows == ['mCurrentFocus=null'] and time.monotonic() < deadline, f'Unexpected foreground: {rows}'
        time.sleep(.5)


def restore_setting(key, value):
    if value in {'null', ''}:
        adb('shell', 'settings', 'delete', 'system', key)
    else:
        adb('shell', 'settings', 'put', 'system', key, value)


def bounds(value):
    parts = [int(v) for v in re.findall(r'-?\d+', value)]
    return parts if len(parts) == 4 else None


guard()
OUTPUT.mkdir(parents=True, exist_ok=True)
SESSION = OUTPUT / f'baseline-{ARGS.avd}.json'
if ARGS.action == 'init':
    assert not SESSION.exists(), 'Do not overwrite a settings baseline'
    settings = {key: adb('shell', 'settings', 'get', 'system', key) for key in ['font_scale', 'accelerometer_rotation', 'user_rotation', 'screen_off_timeout']}
    record = {'captured_at': stamp(), 'avd': ARGS.avd, 'serial': ARGS.serial, 'wm_size': adb('shell', 'wm', 'size'), 'wm_density': adb('shell', 'wm', 'density'), 'settings': settings}
    SESSION.write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps(record, indent=2))
elif ARGS.action == 'restore':
    record = json.loads(SESSION.read_text())
    for key, value in record['settings'].items():
        restore_setting(key, value)
    for kind, field in [('size', 'wm_size'), ('density', 'wm_density')]:
        override = [line.split(': ', 1)[1] for line in record[field].splitlines() if line.startswith('Override')]
        adb('shell', 'wm', kind, override[-1] if override else 'reset')
    adb('shell', 'am', 'start', '-W', '-f', '0x10008000', '-n', ACTIVITY, timeout=90)
    result = {'restored_at': stamp(), 'avd': ARGS.avd, 'wm_size': adb('shell', 'wm', 'size'), 'wm_density': adb('shell', 'wm', 'density'), 'settings': {key: adb('shell', 'settings', 'get', 'system', key) for key in record['settings']}}
    assert result['wm_size'] == record['wm_size'] and result['wm_density'] == record['wm_density']
    assert result['settings'] == record['settings']
    (OUTPUT / f'restored-{ARGS.avd}.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))
else:
    assert SESSION.exists(), 'Save the guest baseline before any resize'
    catalog = json.loads((OUTPUT / 'sdk-device-catalog.json').read_text())
    geometry = catalog['unique_geometries'][ARGS.geometry]
    assert ARGS.font in {1.0, 1.3, 2.0}
    assert ARGS.phase in {'before', 'after', 'separate-avd', 'diagnostic'}
    leaf = OUTPUT / ARGS.phase / ARGS.geometry / ('font-' + str(round(ARGS.font * 100)))
    assert not leaf.exists(), f'Keep immutable prior evidence: {leaf}'
    leaf.mkdir(parents=True)
    started = stamp()
    expected_pixels = f"{geometry['width_px']}x{geometry['height_px']}"
    shell_script(['settings put system screen_off_timeout 1800000',
                  'settings put system accelerometer_rotation 0', 'settings put system user_rotation 0',
                  'wm size ' + shlex.quote(expected_pixels),
                  'wm density ' + shlex.quote(str(geometry['density_dpi'])),
                  'settings put system font_scale ' + shlex.quote(str(ARGS.font)), 'input keyevent WAKEUP'])
    adb('shell', 'am', 'start', '-W', '-f', '0x10008000', '-n', ACTIVITY, timeout=90)
    remote_xml = '/sdcard/supernutrition-home-review.xml'
    deadline = time.monotonic() + 40
    while True:
        foreground()
        xml = shell_script(['uiautomator dump ' + shlex.quote(remote_xml) + ' >/dev/null',
                            'cat ' + shlex.quote(remote_xml)], timeout=45)
        if 'home_title' in xml:
            break
        assert time.monotonic() < deadline, 'Home did not render; no screenshot pass'
        time.sleep(.5)
    (leaf / 'hierarchy.xml').write_text(xml)
    tree = ET.fromstring(xml)
    parents = {child: parent for parent in tree.iter() for child in parent}
    nodes = [dict(node.attrib) for node in tree.iter('node')]
    window, focused = foreground()
    (leaf / 'window.txt').write_text(window)
    controls = [node for node in nodes if node.get('resource-id') in {'camera_action', 'capture_tab', 'rewards_tab'}]
    assert len(controls) == 3, f'Missing main controls: {controls}'
    controls_top = min(bounds(node['bounds'])[1] for node in controls)
    action_nodes = [node for node in tree.iter('node') if node.get('content-desc') == 'Do it']
    action_element = action_nodes[0] if len(action_nodes) == 1 else None
    while action_element is not None and action_element.get('clickable') != 'true':
        action_element = parents.get(action_element)
    action = bounds(action_element.get('bounds', '')) if action_element is not None else None
    card_nodes = [node for node in nodes if node.get('resource-id') == 'home_next_step']
    next_card = bounds(card_nodes[0]['bounds']) if len(card_nodes) == 1 else None
    required = ['Super Nutrition', '6 day rhythm', 'What kind of care do you need today?', 'Add a meal', 'View goals', 'Today’s rhythm', 'You’re building a good day', 'Fiber', '12 / 25 g', 'Water', '1.2 / 2 L', 'Protein', '72 / 90 g', 'Movement', '32 / 45 min', 'Your next helpful step', 'Add one leafy green to dinner', 'Do it']
    text = {node.get('text', '') for node in nodes}
    descriptions = {node.get('content-desc', '') for node in nodes}
    missing = [value for value in required if value not in text and value not in descriptions]
    scroll_nodes = [node for node in nodes if node.get('scrollable') == 'true']
    density = geometry['density_dpi'] / 160
    required_tags = ['home_date', 'home_profile', 'home_add_food', 'home_view_goals',
                     'home_summary', 'home_metric_fiber', 'home_metric_water',
                     'home_metric_protein', 'home_metric_movement', 'home_next_step']
    tagged_bounds = {tag: [bounds(node['bounds']) for node in nodes if node.get('resource-id') == tag]
                     for tag in required_tags}
    missing_tags = [tag for tag, frames in tagged_bounds.items() if len(frames) != 1]
    content_frames = [bounds(node['bounds']) for node in nodes if node.get('resource-id') == 'home_screen']
    content_frame = content_frames[0] if len(content_frames) == 1 else None
    mascot_frames = [bounds(node['bounds']) for node in nodes if node.get('content-desc') == 'Capybara mascot']
    mascot_frame = mascot_frames[0] if len(mascot_frames) == 1 else None
    unsafe_frames = [tag for tag, frames in tagged_bounds.items() if len(frames) == 1 and content_frame
                     and (frames[0][1] < content_frame[1] or frames[0][3] > controls_top - 8 * density)]
    undersized_actions = [tag for tag in ['home_profile', 'home_add_food', 'home_view_goals']
                          if len(tagged_bounds[tag]) == 1
                          and (tagged_bounds[tag][0][2] - tagged_bounds[tag][0][0] < 48 * density - 1
                               or tagged_bounds[tag][0][3] - tagged_bounds[tag][0][1] < 48 * density - 1)]
    fits = (
        not missing and not scroll_nodes and action is not None and next_card is not None
        and next_card[3] + 8 * density <= controls_top
        and action[3] - action[1] >= 48 * density - 1
        and not missing_tags and not unsafe_frames and not undersized_actions
        and mascot_frame is not None and mascot_frame[3] - mascot_frame[1] >= 120 * density - 1
    )
    package_path = adb('shell', 'pm', 'path', PACKAGE).removeprefix('package:')
    assert package_path.startswith('/data/app/') and '\n' not in package_path
    fingerprint = adb('shell', 'stat', '-c', '%s:%Y:%i', package_path)
    verification_path = OUTPUT / 'apk-verifications.json'
    verifications = json.loads(verification_path.read_text()) if verification_path.exists() else {}
    verification_key = ARGS.avd + ':' + package_path + ':' + fingerprint
    if verification_key not in verifications:
        verifications[verification_key] = {
            'sha256': adb('shell', 'sha256sum', package_path, timeout=90).split()[0],
            'verified_at': stamp(), 'installed_path': package_path, 'file_fingerprint': fingerprint,
        }
        verification_path.write_text(json.dumps(verifications, indent=2) + '\n')
    installed_sha = verifications[verification_key]['sha256']
    runtime = shell_script(['wm size', 'wm density', 'settings get system font_scale']).splitlines()
    wm_size = '\n'.join(line for line in runtime if line.startswith(('Physical size:', 'Override size:')))
    wm_density = '\n'.join(line for line in runtime if line.startswith(('Physical density:', 'Override density:')))
    assert 'Override size: ' + expected_pixels in wm_size or wm_size == 'Physical size: ' + expected_pixels
    assert wm_density.endswith(str(geometry['density_dpi']))
    assert float(runtime[-1]) == ARGS.font
    metadata = {'captured_at': started, 'avd': ARGS.avd, 'serial': ARGS.serial, 'api': 35, 'abi': 'x86_64', 'evidence_kind': 'native emulator viewport override', 'device_equivalences': geometry['devices'], 'requested_geometry': geometry, 'wm_size': wm_size, 'wm_density': wm_density, 'font_scale': ARGS.font, 'apk_sha256': installed_sha, 'portrait_locked_app': True, 'foreground': focused, 'controls_top_px': controls_top, 'helpful_action_bounds': action, 'helpful_card_bounds': next_card, 'missing_initial_text': missing, 'native_scrollable': bool(scroll_nodes), 'native_initial_fit': fits, 'outcome': 'initial-frame fit' if fits else 'designer-required: readable content exceeds initial frame', 'note': 'No text-size override or forced screenshot scaling. Native XML fit is combined with focused Compose complete-frame/readability evidence; it is not a physical-device test.'}
    metadata.update({'required_tag_bounds': tagged_bounds, 'missing_initial_tags': missing_tags,
                     'unsafe_initial_frames': unsafe_frames, 'undersized_initial_actions': undersized_actions,
                     'home_content_bounds': content_frame, 'mascot_bounds': mascot_frame,
                     'capture_command_timings': TIMINGS})
    (leaf / 'metadata.json').write_text(json.dumps(metadata, indent=2, ensure_ascii=False) + '\n')
    print(json.dumps({'png': str(leaf / 'home.png'), 'metadata': str(leaf / 'metadata.json'), 'outcome': metadata['outcome'], 'font_scale': ARGS.font, 'geometry': ARGS.geometry}, ensure_ascii=False), flush=True)
    # This is deliberately the final guest action; the caller must inspect the PNG now.
    command(['android', 'screen', 'capture', '--device', ARGS.serial, '-o', str(leaf / 'home.png')], timeout=45)
