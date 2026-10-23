#!/usr/bin/env python3
"""Bound Android startup by native readiness, preserving failures instead of dismissing them."""
import argparse
import json
import re
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path


class BootHealthError(RuntimeError):
    """The emulator cannot safely run UI assertions."""


def require_complete_service_dump(output, service):
    """dumpsys can exit zero with a valid prefix followed by native failure output."""
    failed = re.search(
        r'^\s*(?:\*{3}\s+SERVICE\b[^\n]*\bDUMP TIMEOUT\b|Error dumping service info:'
        r'|Can.t find service:|Exception occurred while dumping:)',
        output, re.M | re.I)
    if failed:
        raise BootHealthError(f'Android {service} service dump is incomplete: {output}')


def configure_avd_display(config, width, height, density):
    """Set the owned AVD framebuffer while removing named skins that can override it."""
    if any(not isinstance(value, int) or value <= 0 for value in (width, height, density)):
        raise ValueError('AVD display dimensions and density must be positive integers')
    remaining = {'hw.lcd.width': str(width), 'hw.lcd.height': str(height),
                 'hw.lcd.density': str(density), 'skin.dynamic': 'yes', 'showDeviceFrame': 'no'}
    replaced = set(remaining)
    removed = {'skin.name', 'skin.path', 'skin.path.backup'}
    lines = []
    for line in Path(config).read_text().splitlines():
        key = line.partition('=')[0].strip()
        if key in removed:
            continue
        if key in replaced:
            if key in remaining:
                lines.append(f'{key}={remaining.pop(key)}')
        else:
            lines.append(line)
    lines.extend(f'{key}={value}' for key, value in remaining.items())
    Path(config).write_text('\n'.join(lines) + '\n')


class _BootProbe:
    def __init__(self, adb, serial, report, timeout, run, clock, sleep):
        self.adb, self.serial, self.report = adb, serial, report
        self.run, self.clock, self.sleep = run, clock, sleep
        self.started = clock()
        self.deadline = self.started + timeout
        self.commands = []
        report.mkdir(parents=True, exist_ok=True)

    def remaining(self):
        remaining = self.deadline - self.clock()
        if remaining <= 0:
            raise BootHealthError('Android startup health deadline expired')
        return remaining

    def read(self, label, *arguments, native_wait=False):
        timeout = self.remaining() if native_wait else min(30, self.remaining())
        command = [self.adb, '-s', self.serial, *arguments]
        entry = {'command': command, 'timeout_seconds': timeout}
        self.commands.append(entry)
        try:
            result = self.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                              check=True, timeout=timeout)
            output = result.stdout
        except subprocess.TimeoutExpired as error:
            output = error.output or ''
            if isinstance(output, bytes):
                output = output.decode(errors='replace')
            (self.report / f'{label}.txt').write_text(output)
            raise BootHealthError(f'Android command timed out: {arguments}') from error
        except subprocess.CalledProcessError as error:
            output = error.stdout or ''
            (self.report / f'{label}.txt').write_text(output)
            raise BootHealthError(f'Android command failed: {arguments}: {output}') from error
        (self.report / f'{label}.txt').write_text(output)
        return output.strip()

    def pause(self):
        self.sleep(min(1, self.remaining()))

    def check_crashes(self):
        events = self.read('events', 'logcat', '-b', 'events', '-d', '-v', 'brief',
                           'am_anr:I', 'am_crash:I', '*:S')
        fatal = re.search(r'\bam_(?:anr|crash)\b', events)
        if fatal:
            raise BootHealthError(f'Android startup recorded {fatal.group()}: {events}')
        crashes = self.read('crashes', 'logcat', '-b', 'crash', '-d', '-v', 'brief')
        if re.search(r'FATAL EXCEPTION|Fatal signal|>>> .+ <<<', crashes):
            raise BootHealthError(f'Android startup recorded a process crash: {crashes}')

    def hierarchy(self):
        remote = f'/data/local/tmp/supernutrition-startup-{uuid.uuid4().hex}.xml'
        try:
            output = self.read('hierarchy-dump', 'shell', 'uiautomator', 'dump', remote)
            # AOSP prints the historical "hierchary" spelling; exit zero alone is not success.
            success = {f'UI hierchary dumped to: {remote}', f'UI hierarchy dumped to: {remote}'}
            if re.search(r'^ERROR:', output, re.M | re.I) or not success.intersection(output.splitlines()):
                raise BootHealthError(f'UIAutomator did not produce a fresh hierarchy: {output}')
            xml = self.read('hierarchy', 'shell', 'cat', remote)
        finally:
            self.remove_hierarchy_file(remote)
        (self.report / 'hierarchy.xml').write_text(xml)
        try:
            hierarchy = ET.fromstring(xml)
        except ET.ParseError as error:
            raise BootHealthError('Android returned an invalid UI hierarchy') from error
        for node in hierarchy.iter('node'):
            if node.get('resource-id', '').startswith('android:id/aerr_') or re.search(
                    r'isn[\u2019\']t responding|keeps stopping|has stopped', node.get('text', ''), re.I):
                raise BootHealthError('Android startup has a native ANR/crash error dialog')
        return hierarchy

    def remove_hierarchy_file(self, remote):
        command = [self.adb, '-s', self.serial, 'shell', 'rm', '-f', remote]
        self.commands.append({'command': command, 'timeout_seconds': 3})
        try:
            self.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                     check=True, timeout=3)
        except (OSError, subprocess.SubprocessError) as error:
            # Cleanup has its own short bound even if the health deadline has expired.
            # Retain the original failure and make cleanup failure available in artifacts.
            (self.report / 'hierarchy-cleanup-error.txt').write_text(str(error) + '\n')

    def wait(self):
        while True:
            self.check_crashes()
            boot = self.read('boot-completed', 'shell', 'getprop', 'sys.boot_completed')
            animation = self.read('boot-animation', 'shell', 'getprop', 'init.svc.bootanim')
            animation_ready = animation == 'stopped'
            if not animation:
                disabled = self.read('boot-animation-disabled', 'shell', 'getprop', 'debug.sf.nobootanimation')
                animation_ready = re.fullmatch(r'[0-9]+', disabled) is not None and 0 < int(disabled) <= 2147483647
            user = self.read('user-state', 'shell', 'am', 'get-started-user-state', '0') if boot == '1' else ''
            if boot == '1' and animation_ready and user == 'RUNNING_UNLOCKED':
                break
            self.pause()
        # Android 16 has no command timeout option. The host bounds this native queue wait.
        self.read('broadcast-idle', 'shell', 'am', 'wait-for-broadcast-idle', native_wait=True)
        self.check_crashes()
        home = self.read('home-component', 'shell', 'cmd', 'package', 'resolve-activity', '--brief',
                         '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')
        component = home.splitlines()[-1] if home else ''
        if not re.fullmatch(r'[\w.]+/[\w.$]+', component) or component.startswith('android/'):
            raise BootHealthError(f'Android has no resolved launcher: {home}')
        package = component.split('/')[0]
        launch = self.read('home-start', 'shell', 'am', 'start', '-W', '-a', 'android.intent.action.MAIN',
                           '-c', 'android.intent.category.HOME', '-n', component)
        if not re.search(r'^Status: ok$', launch, re.M):
            raise BootHealthError(f'Android could not start HOME: {launch}')
        while True:
            self.check_crashes()
            # DisplayContent prints current focus in displays; windows only enumerates WindowStates.
            windows = self.read('windows', 'shell', 'dumpsys', 'window', 'displays')
            require_complete_service_dump(windows, 'WindowManager')
            activities = self.read('activities', 'shell', 'dumpsys', 'activity', 'activities')
            require_complete_service_dump(activities, 'ActivityManager')
            hierarchy = self.hierarchy()
            focused = any('mCurrentFocus=' in line and package + '/' in line for line in windows.splitlines())
            resumed = any(('mResumedActivity' in line or 'topResumedActivity' in line)
                          and package + '/' in line for line in activities.splitlines())
            launcher_visible = any(node.get('package') == package for node in hierarchy.iter('node'))
            if focused and resumed and launcher_visible:
                break
            self.pause()
        enabled = self.read('enabled-ime', 'shell', 'ime', 'list', '-s', '--user', '0').splitlines()
        selected = self.read('selected-ime', 'shell', 'settings', '--user', '0', 'get', 'secure',
                             'default_input_method')
        ime_state = self.read('input-method', 'shell', 'dumpsys', 'input_method')
        readiness = re.findall(r'^\s*mSystemReady=(\S+)\s*$', ime_state, re.M)
        require_complete_service_dump(ime_state, 'IME')
        if 'Input Method Manager Service state:' not in ime_state.splitlines() or readiness != ['true']:
            raise BootHealthError(f'Android IME service is not demonstrably ready: {ime_state}')
        if not selected or selected == 'null' or selected not in enabled:
            raise BootHealthError(f'Android selected IME is not enabled: {selected}; enabled={enabled}')
        self.check_crashes()
        return component


def wait_for_healthy_home(adb, serial, report, timeout=360, *, run=subprocess.run,
                          clock=time.monotonic, sleep=time.sleep):
    probe = _BootProbe(adb, serial, Path(report), timeout, run, clock, sleep)
    summary = {'healthy': False, 'serial': serial}
    try:
        summary['home'] = probe.wait()
        summary['healthy'] = True
        return summary
    except BootHealthError as error:
        summary['error'] = str(error)
        raise
    finally:
        summary['elapsed_seconds'] = clock() - probe.started
        summary['commands'] = probe.commands
        (probe.report / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--timeout-seconds', type=float, default=360)
    arguments = parser.parse_args()
    if arguments.timeout_seconds <= 0:
        parser.error('--timeout-seconds must be positive')
    try:
        result = wait_for_healthy_home(arguments.adb, arguments.serial, arguments.report,
                                       arguments.timeout_seconds)
    except BootHealthError as error:
        print(str(error), file=sys.stderr)
        return 1
    print(f"Android native startup healthy: {result['home']} ({result['elapsed_seconds']:.1f}s)")
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
