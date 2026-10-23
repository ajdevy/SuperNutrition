"""Exercise Android startup ordering and fail-closed behavior without a device."""
import importlib.util
import subprocess
import tempfile
import unittest
from pathlib import Path
from fractions import Fraction

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('android_boot_health', ROOT / 'scripts/android_boot_health.py')
HEALTH = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(HEALTH)

HOME = 'com.google.android.apps.nexuslauncher/.NexusLauncherActivity'
IME = 'com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME'
HEALTHY_XML = '<hierarchy><node package="com.google.android.apps.nexuslauncher" text="Home" /></hierarchy>'


class FakeAndroid:
    def __init__(self):
        self.now = 0
        self.calls = []
        self.boot_states = [('1', 'stopped', 'RUNNING_UNLOCKED')]
        self.boot_index = -1
        self.no_boot_animation = ""
        self.home_started = False
        self.broadcast_timeout = False
        self.events = ''
        self.events_after_home = ''
        self.hierarchy = HEALTHY_XML
        self.focused = True
        self.window_dump_footer = ""
        self.activity_dump_footer = ""
        self.enabled_ime = IME
        self.focus_states = []
        self.dump_outputs = []
        self.remote_files = {}
        self.input_method_dump = 'Input Method Manager Service state:\n  mCurClient=null\n  mSystemReady=true'

    def sleep(self, seconds):
        self.now += seconds

    def run(self, words, **options):
        args = words[3:]
        self.calls.append((args, options['timeout']))
        output = ''
        if args[:2] == ['logcat', '-b']:
            if args[2] == 'events' and not self.home_started:
                self.boot_index = min(self.boot_index + 1, len(self.boot_states) - 1)
            output = self.events_after_home if self.home_started else self.events
        elif args == ['shell', 'getprop', 'sys.boot_completed']:
            output = self.boot_states[self.boot_index][0]
        elif args == ['shell', 'getprop', 'init.svc.bootanim']:
            output = self.boot_states[self.boot_index][1]
        elif args == ['shell', 'getprop', 'debug.sf.nobootanimation']:
            output = self.no_boot_animation
        elif args == ['shell', 'am', 'get-started-user-state', '0']:
            if self.boot_states[self.boot_index][0] != '1':
                raise subprocess.CalledProcessError(20, words, output="cmd: Can't find service: activity")
            output = self.boot_states[self.boot_index][2]
        elif args == ['shell', 'am', 'wait-for-broadcast-idle']:
            if self.broadcast_timeout:
                raise subprocess.TimeoutExpired(words, options['timeout'], output='Waiting for idle...')
            output = 'All broadcast queues are idle!'
        elif args[:4] == ['shell', 'cmd', 'package', 'resolve-activity']:
            output = HOME
        elif args[:4] == ['shell', 'am', 'start', '-W']:
            self.home_started = True
            output = 'Status: ok\nActivity: ' + HOME
        elif args == ['shell', 'dumpsys', 'window', 'windows']:
            # Android16's windows-only section enumerates windows, omitting display focus.
            output = 'WINDOW MANAGER WINDOWS (dumpsys window windows)\n  Window #0 Window{123 u0 ' + HOME + '}'
        elif args == ['shell', 'dumpsys', 'window', 'displays']:
            focused = self.focus_states.pop(0) if self.focus_states else self.focused
            output = 'mCurrentFocus=null' if focused is None else (
                'mCurrentFocus=Window{123 u0 ' + (HOME if focused else 'android/BlockingWindow') + '}')
            output += '\n' + self.window_dump_footer
        elif args == ['shell', 'dumpsys', 'activity', 'activities']:
            output = 'mResumedActivity: ActivityRecord{456 u0 ' + HOME + '}\n' + self.activity_dump_footer
        elif args == ['shell', 'ime', 'list', '-s', '--user', '0']:
            output = self.enabled_ime
        elif args == ['shell', 'settings', '--user', '0', 'get', 'secure', 'default_input_method']:
            output = IME
        elif args == ['shell', 'dumpsys', 'input_method']:
            output = self.input_method_dump
        elif args[:3] == ['shell', 'uiautomator', 'dump']:
            configured = self.dump_outputs.pop(0) if self.dump_outputs else None
            output = configured if configured is not None else 'UI hierchary dumped to: ' + args[-1]
            if configured is None:
                self.remote_files[args[-1]] = self.hierarchy
        elif args[:2] == ['shell', 'cat']:
            if args[-1] not in self.remote_files:
                raise subprocess.CalledProcessError(1, words, output='No such file or directory')
            output = self.remote_files[args[-1]]
        elif args[:3] == ['shell', 'rm', '-f']:
            self.remote_files.pop(args[-1], None)
        else:
            raise AssertionError('Unexpected Android command: ' + repr(args))
        return subprocess.CompletedProcess(words, 0, output + '\n')


class AndroidBootHealthTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.android = FakeAndroid()

    def check(self, timeout=10):
        return HEALTH.wait_for_healthy_home('adb', 'emulator-5554', Path(self.directory.name), timeout,
                                           run=self.android.run, clock=lambda: self.android.now,
                                           sleep=self.android.sleep)

    def commands(self):
        return [command for command, _ in self.android.calls]

    def test_boot_property_alone_cannot_start_tests_before_unlock_animation_and_broadcast_idle(self):
        self.android.boot_states = [('1', 'running', 'RUNNING_LOCKED'),
                                    ('1', 'stopped', 'RUNNING_UNLOCKED')]
        self.check()
        commands = self.commands()
        wait = ['shell', 'am', 'wait-for-broadcast-idle']
        self.assertEqual(commands.count(wait), 1)
        self.assertEqual(commands[:commands.index(wait)].count(
            ['shell', 'am', 'get-started-user-state', '0']), 2)
        starts = [command for command in commands if command[:4] == ['shell', 'am', 'start', '-W']]
        self.assertEqual(len(starts), 1)
        self.assertLess(commands.index(wait), commands.index(starts[0]))
        self.assertTrue((Path(self.directory.name) / 'hierarchy.xml').is_file())

    def test_display_section_exposes_focus_missing_from_windows_section(self):
        self.check(timeout=3)
        self.assertIn(['shell', 'dumpsys', 'window', 'displays'], self.commands())
        self.assertNotIn(['shell', 'dumpsys', 'window', 'windows'], self.commands())

    def test_window_focus_prefix_cannot_hide_a_partial_dump_timeout_or_native_error(self):
        for footer in ["*** SERVICE 'window' DUMP TIMEOUT (10000ms) EXPIRED ***",
                       'Error dumping service info: (Broken pipe) window']:
            with self.subTest(footer=footer):
                self.android = FakeAndroid()
                self.android.window_dump_footer = footer
                with self.assertRaisesRegex(HEALTH.BootHealthError, 'WindowManager'):
                    self.check()

    def test_resumed_activity_prefix_cannot_hide_a_partial_dump_timeout_or_native_error(self):
        for footer in ["*** SERVICE 'activity' DUMP TIMEOUT (10000ms) EXPIRED ***",
                       'Error dumping service info: (Broken pipe) activity']:
            with self.subTest(footer=footer):
                self.android = FakeAndroid()
                self.android.activity_dump_footer = footer
                with self.assertRaisesRegex(HEALTH.BootHealthError, 'ActivityManager'):
                    self.check()

    def test_valid_service_prefix_cannot_hide_a_binder_dump_exception(self):
        exception = 'Exception occurred while dumping:\njava.lang.IllegalStateException: interrupted dump'
        for service, field in [('WindowManager', 'window_dump_footer'),
                               ('ActivityManager', 'activity_dump_footer'),
                               ('IME', 'input_method_dump')]:
            with self.subTest(service=service):
                self.android = FakeAndroid()
                prefix = getattr(self.android, field)
                setattr(self.android, field, prefix + '\n' + exception)
                with self.assertRaisesRegex(HEALTH.BootHealthError, service):
                    self.check()

    def test_absent_current_window_focus_cannot_claim_a_healthy_launcher(self):
        self.android.focused = None
        with self.assertRaisesRegex(HEALTH.BootHealthError, 'deadline|timed out'):
            self.check(timeout=3)
        self.assertEqual(len([c for c in self.commands() if c[:4] == ['shell', 'am', 'start', '-W']]), 1)

    def test_first_adb_connection_can_precede_activity_service_publication(self):
        self.android.boot_states = [('0', 'running', 'BOOTING'),
                                    ('1', 'stopped', 'RUNNING_UNLOCKED')]
        self.check()
        commands = self.commands()
        self.assertEqual(commands.count(['shell', 'am', 'get-started-user-state', '0']), 1)
        self.assertEqual(commands.count(['shell', 'getprop', 'sys.boot_completed']), 2)

    def test_broadcast_hang_has_a_host_deadline_and_never_launches_or_retries_home(self):
        self.android.broadcast_timeout = True
        with self.assertRaisesRegex(HEALTH.BootHealthError, 'timed out'):
            self.check(timeout=7)
        self.assertFalse(self.android.home_started)
        self.assertTrue(all(0 < timeout <= 7 for _, timeout in self.android.calls))

    def test_early_system_anr_is_fatal_before_home_and_is_preserved(self):
        self.android.events = 'I/am_anr( 1000): [0,1492,com.android.systemui,0,executing service]'
        with self.assertRaisesRegex(HEALTH.BootHealthError, 'am_anr'):
            self.check()
        self.assertFalse(self.android.home_started)
        self.assertIn('com.android.systemui', (Path(self.directory.name) / 'events.txt').read_text())

    def test_crash_after_home_launch_is_fatal_without_test_retry(self):
        self.android.events_after_home = 'I/am_crash( 1000): [0,1492,com.android.launcher3,0,java.lang.Error]'
        with self.assertRaisesRegex(HEALTH.BootHealthError, 'am_crash'):
            self.check()
        self.assertEqual(len([c for c in self.commands() if c[:4] == ['shell', 'am', 'start', '-W']]), 1)

    def test_native_error_dialog_is_fatal_even_without_event_record(self):
        self.android.hierarchy = '<hierarchy><node resource-id="android:id/aerr_wait" text="Wait" /></hierarchy>'
        with self.assertRaisesRegex(HEALTH.BootHealthError, 'error dialog'):
            self.check()
        self.assertNotIn('input', str(self.commands()[-1]))

    def test_failed_zero_exit_dump_cannot_reuse_a_previous_healthy_home_hierarchy(self):
        for error in ['ERROR: could not get idle state.',
                      'ERROR: null root node returned by UiTestAutomationBridge.']:
            with self.subTest(error=error):
                self.android = FakeAndroid()
                self.android.focus_states = [False, True]
                self.android.dump_outputs = [None, error]
                with self.assertRaisesRegex(HEALTH.BootHealthError, 'hierarchy|UIAutomator'):
                    self.check()
                dumps = [command for command in self.commands()
                         if command[:3] == ['shell', 'uiautomator', 'dump']]
                self.assertEqual(len(dumps), 2)
                self.assertFalse(self.android.remote_files)

    def test_unresponsive_home_window_exhausts_deadline_without_restarting_home(self):
        self.android.focused = False
        with self.assertRaisesRegex(HEALTH.BootHealthError, 'deadline|timed out'):
            self.check(timeout=3)
        self.assertEqual(len([c for c in self.commands() if c[:4] == ['shell', 'am', 'start', '-W']]), 1)

    def test_home_needs_enabled_selected_ime_but_not_an_active_input_client(self):
        self.check()
        self.android.enabled_ime = 'another.package/.Keyboard'
        with self.assertRaisesRegex(HEALTH.BootHealthError, 'IME'):
            self.check()

    def test_ime_dump_must_prove_actual_ready_service_even_when_registry_is_enabled(self):
        for output in ['Input Method Manager Service state:\n  mSystemReady=false',
                       "Can't find service: input_method",
                       'Input Method Manager Service state:\n  mCurClient=null',
                       'Input Method Manager Service state:\n  mSystemReady=maybe',
                       'unrelated text mSystemReady=true']:
            with self.subTest(output=output):
                self.android = FakeAndroid()
                self.android.input_method_dump = output
                with self.assertRaisesRegex(HEALTH.BootHealthError, 'IME'):
                    self.check()

    def test_ime_ready_prefix_cannot_hide_a_partial_dump_timeout_or_native_error(self):
        prefix = 'Input Method Manager Service state:\n  mSystemReady=true\n'
        for footer in ["*** SERVICE 'input_method' DUMP TIMEOUT (10000ms) EXPIRED ***",
                       'Error dumping service info: (Broken pipe) input_method']:
            with self.subTest(footer=footer):
                self.android = FakeAndroid()
                self.android.input_method_dump = prefix + footer
                with self.assertRaisesRegex(HEALTH.BootHealthError, 'IME'):
                    self.check()

    def test_disabled_animation_native_mode_can_finish_without_a_bootanim_service(self):
        for flag in ['1', '2']:
            with self.subTest(flag=flag):
                self.android = FakeAndroid()
                self.android.boot_states = [('1', '', 'RUNNING_UNLOCKED')]
                self.android.no_boot_animation = flag
                self.check(timeout=3)
                self.assertTrue(self.android.home_started)

    def test_absent_bootanim_service_needs_valid_native_disabled_proof(self):
        for flag in ['', '0', '-1', 'false', 'true', '1garbage', '2147483648']:
            with self.subTest(flag=flag):
                self.android = FakeAndroid()
                self.android.boot_states = [('1', '', 'RUNNING_UNLOCKED')]
                self.android.no_boot_animation = flag
                with self.assertRaisesRegex(HEALTH.BootHealthError, 'deadline|timed out'):
                    self.check(timeout=3)
                self.assertFalse(self.android.home_started)

    def test_running_boot_animation_is_not_ready_even_with_a_disabled_flag(self):
        self.android.boot_states = [('1', 'running', 'RUNNING_UNLOCKED')]
        self.android.no_boot_animation = '1'
        with self.assertRaisesRegex(HEALTH.BootHealthError, 'deadline|timed out'):
            self.check(timeout=3)
        self.assertFalse(self.android.home_started)

    def test_boot_never_unlocks_fails_closed_without_launching_home(self):
        self.android.boot_states = [('1', 'stopped', 'RUNNING_LOCKED')]
        with self.assertRaisesRegex(HEALTH.BootHealthError, 'deadline|timed out'):
            self.check(timeout=3)
        self.assertFalse(self.android.home_started)


class AndroidDisplayConfigurationTest(unittest.TestCase):
    def test_display_override_preserves_dp_viewport_and_other_hardware_budgets(self):
        with tempfile.TemporaryDirectory() as temporary:
            config = Path(temporary) / 'config.ini'
            config.write_text('hw.lcd.width = 1080\nhw.lcd.height=2400\nhw.lcd.density=420\n'
                              'skin.name=pixel_6\nskin.path=/sdk/skins/pixel_6\n'
                              'skin.path.backup=/sdk/skins/pixel_6\nshowDeviceFrame=yes\n'
                              'hw.ramSize=2560\nvm.heapSize=576\nhw.cpu.ncore=2\nhw.gpu.mode=swiftshader\n')
            HEALTH.configure_avd_display(config, 720, 1600, 280)
            values = dict(line.split('=', 1) for line in config.read_text().splitlines())
            self.assertEqual(Fraction(int(values['hw.lcd.width']) * 160, int(values['hw.lcd.density'])),
                             Fraction(1080 * 160, 420))
            self.assertEqual(Fraction(int(values['hw.lcd.height']) * 160, int(values['hw.lcd.density'])),
                             Fraction(2400 * 160, 420))
            self.assertEqual(int(values['hw.lcd.width']) * int(values['hw.lcd.height']), 720 * 1600)
            for key, value in {'hw.ramSize': '2560', 'vm.heapSize': '576', 'hw.cpu.ncore': '2',
                               'hw.gpu.mode': 'swiftshader', 'showDeviceFrame': 'no'}.items():
                self.assertEqual(values[key], value)
            self.assertFalse({'skin.name', 'skin.path', 'skin.path.backup'} & values.keys())
            configured = config.read_text()
            HEALTH.configure_avd_display(config, 720, 1600, 280)
            self.assertEqual(config.read_text(), configured)


if __name__ == '__main__':
    unittest.main()
