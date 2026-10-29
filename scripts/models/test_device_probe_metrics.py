import unittest
import subprocess
from types import SimpleNamespace
from unittest.mock import Mock, patch
import measure_device_probe as runner
from device_probe_metrics import (parse_pss, named_pss, parse_proc_stats, parse_proc_status,
                                  parse_battery, battery_endpoints, instrumentation_summary)


class DeviceProbeMetricsTest(unittest.TestCase):
    def test_controller_prefetch_is_fixed_isolated_dual_autonomous_cold_workload(self):
        args = SimpleNamespace(phase='controller-prefetch', cold=False, model_rounds=1, model_hold_ms=0, model_input="short")
        command = runner.instrumentation_command(args, ['adb', '-s', 'phone'])
        self.assertEqual(['adb', '-s', 'phone', 'shell', 'am', 'instrument', '-w', '-r'], command[:8])
        self.assertEqual('app.quietreader.flowprobe.test/app.quietreader.ReleaseDiscoveryInstrumentation', command[-1])
        options = dict(zip(command[9:-1:3], command[10:-1:3]))
        self.assertEqual(dict(phase='energy-setup', stages='DUAL', cache='cold', activePrefetch='true',
                              autonomous='true', verifyPrefetchDiskReuse='true', fixture='energy-replay-100-v2.json'), options)
        self.assertNotIn('modelRounds', command)

    def test_controller_reading_uses_bounded_60_second_actual_replay(self):
        args = SimpleNamespace(phase='controller-reading', cold=False, model_rounds=1, model_hold_ms=0, model_input="short")
        command = runner.instrumentation_command(args, ['adb', '-s', 'phone'])
        options = dict(zip(command[9:-1:3], command[10:-1:3]))
        self.assertEqual('energy-replay', options['phase'])
        self.assertEqual('60', options['replaySeconds'])
        self.assertEqual('DUAL', options['stages'])
        self.assertEqual('true', options['activePrefetch'])
        self.assertEqual('app.quietreader.flowprobe.test/app.quietreader.ReleaseDiscoveryInstrumentation', command[-1])

    def test_existing_foreground_model_command_preserves_bounded_hold(self):
        args = SimpleNamespace(phase='foreground-models', cold=True, model_rounds=4, model_hold_ms=2000, model_input="short")
        command = runner.instrumentation_command(args, ['adb'])
        options = dict(zip(command[7:-1:3], command[8:-1:3]))
        self.assertEqual(dict(cold='true', phase='foreground-models', modelRounds='4', modelHoldMs='2000', modelInput='short'), options)
        self.assertEqual('app.quietreader.flowprobe.test/app.quietreader.ReleaseDiscoveryInstrumentation', command[-1])

    def test_boundary_input_cannot_access_device_or_output_for_other_phase(self):
        args = SimpleNamespace(phase='controller-reading', model_input='boundary')
        with patch.object(runner, 'run_adb') as adb:
            with self.assertRaisesRegex(SystemExit, 'Boundary model inputs'):
                runner.main(args)
        adb.assert_not_called()

    def test_boundary_shape_is_forwarded_only_to_fixed_isolated_workload(self):
        args = SimpleNamespace(phase='foreground-models', cold=False, model_rounds=1, model_hold_ms=0, model_input='boundary')
        command = runner.instrumentation_command(args, ['adb'])
        options = dict(zip(command[7:-1:3], command[8:-1:3]))
        self.assertEqual('boundary', options['modelInput'])
        self.assertEqual('foreground-models', options['phase'])
        self.assertEqual('app.quietreader.flowprobe.test/app.quietreader.ReleaseDiscoveryInstrumentation', command[-1])

    def test_multiple_processes_and_native_java_rss_units(self):
        text = """** MEMINFO in pid 123 [app.quietreader.flowprobe] **
 App Summary
 Java Heap: 1024
 Native Heap: 2048
 TOTAL PSS: 4096 TOTAL RSS: 8192 TOTAL SWAP PSS: 0
** MEMINFO in pid 456 [com.google.android.webview:sandboxed_process0] **
 TOTAL PSS: 128 TOTAL RSS: 256
"""
        rows = parse_pss(text)
        self.assertEqual(4224, named_pss(rows, 0))
        self.assertEqual((1024, 2048, 8192), (rows[0]['javaHeapPssKiB'], rows[0]['nativeHeapPssKiB'], rows[0]['rssKiB']))
        self.assertEqual('com.google.android.webview:sandboxed_process0', rows[1]['name'])
        self.assertIsNone(rows[1]['javaHeapPssKiB'])

    def test_legacy_total_and_missing_values_are_not_zero(self):
        rows = parse_pss('** MEMINFO in pid 1 [a] **\n TOTAL 512 0 0\n** MEMINFO in pid 2 [b] **\n')
        self.assertEqual(512, rows[0]['pssKiB'])
        self.assertIsNone(rows[1]['pssKiB'])
        self.assertIsNone(named_pss(rows, 0))
        self.assertIsNone(named_pss([], 0))
        self.assertIsNone(named_pss(rows[:1], 1))

    def test_duplicate_pid_does_not_double_total(self):
        row = {'pid': 1, 'pssKiB': 256}
        self.assertIsNone(named_pss([row, row], 0))

    def test_proc_cpu_fields_and_lifetime_handle_spaces_parentheses(self):
        tail = ['S'] + ['0'] * 21
        tail[11], tail[12], tail[19] = '150', '25', '123456'
        row = parse_proc_stats('42 (name with ) spaces) ' + ' '.join(tail))[42]
        self.assertEqual(175, row['totalTicks'])
        self.assertEqual(123456, row['startedTicks'])
        self.assertEqual('name with ) spaces', row['comm'])
        tail[19] = '123999'
        self.assertNotEqual(row['startedTicks'], parse_proc_stats('42 (reused) ' + ' '.join(tail))[42]['startedTicks'])

    def test_proc_permission_denial_and_truncation_are_unknown(self):
        self.assertEqual({}, parse_proc_stats('cat: /proc/42/stat: Permission denied\n42 (app) S 0'))
        self.assertEqual({}, parse_proc_status('cat: /proc/42/status: Permission denied'))

    def test_status_high_water_is_per_process_not_simultaneous_total(self):
        rows = parse_proc_status('Name:\tmain\nPid:\t42\nVmHWM:\t2048 kB\nVmRSS:\t1024 kB\nName:\tranking\nPid:\t43\nVmHWM:\t4096 kB\n')
        self.assertEqual({'rssKiB': 1024, 'peakRssKiB': 2048}, rows[42])
        self.assertEqual(4096, rows[43]['peakRssKiB'])
        self.assertIsNone(rows[43]['rssKiB'])

    def test_battery_uses_microamp_hours_and_deci_celsius(self):
        battery = parse_battery(' AC powered: false\n USB powered: true\n Wireless powered: false\n Charge counter: 3800000\n status: 2\n level: 75\n voltage: 4050\n temperature: 309\n')
        self.assertEqual(30.9, battery['temperatureC'])
        self.assertEqual(3800000, battery['chargeCounterUAh'])
        self.assertEqual(4050, battery['voltageMv'])
        self.assertTrue(battery['usbPowered'])
        self.assertIsNone(battery['dockPowered'])

    def test_unknown_battery_or_zero_counter_cannot_support_energy(self):
        empty = parse_battery('unsupported output')
        result = battery_endpoints(empty, {'chargeCounterUAh': 0})
        self.assertIsNone(result['chargeCounterDeltaUAh'])
        self.assertIsNone(result['unpluggedBefore'])
        self.assertFalse(result['energyComparisonVerified'])

    def test_charging_counter_increase_is_not_faked_as_zero_usage(self):
        before = {'chargeCounterUAh': 1000, 'status': 2, 'usbPowered': True}
        after = {'chargeCounterUAh': 1100, 'status': 2, 'usbPowered': True}
        result = battery_endpoints(before, after)
        self.assertEqual(-100, result['chargeCounterDeltaUAh'])
        self.assertFalse(result['unpluggedBefore'])
        self.assertFalse(result['energyComparisonVerified'])

    def test_discharging_endpoints_do_not_certify_controlled_energy(self):
        before = {'chargeCounterUAh': 1000, 'status': 3, 'usbPowered': False, 'acPowered': False, 'wirelessPowered': False}
        after = {**before, 'chargeCounterUAh': 900}
        result = battery_endpoints(before, after)
        self.assertTrue(result['unpluggedBefore'])
        self.assertTrue(result['unpluggedAfter'])
        self.assertEqual(100, result['chargeCounterDeltaUAh'])
        self.assertFalse(result['energyComparisonVerified'])

    def test_only_one_nonempty_success_summary_can_pass(self):
        self.assertTrue(instrumentation_summary('PASS test\nchecks=35 failures=0\nINSTRUMENTATION_CODE: -1\n', 0)['passed'])
        for log in ['', 'checks=0 failures=0', 'checks=1 failures=1', 'checks=1 failures=0\nchecks=2 failures=0', 'checks=1 failures=0\nINSTRUMENTATION_FAILED: unavailable', 'checks=1 failures=0\nshortMsg=Process crashed']:
            self.assertFalse(instrumentation_summary(log, 0)['passed'], log)
        self.assertFalse(instrumentation_summary('checks=1 failures=0', 1)['passed'])
        self.assertFalse(instrumentation_summary('checks=1 failures=0', 0, True)['passed'])

    def test_android_terminal_result_is_required_even_with_success_counts(self):
        counts = 'checks=2 failures=0\n'
        for terminal in ['', 'INSTRUMENTATION_CODE: 0\n', 'INSTRUMENTATION_CODE: -2\n',
                         'INSTRUMENTATION_CODE: -1\nINSTRUMENTATION_CODE: -1\n',
                         'INSTRUMENTATION_CODE: -1\nINSTRUMENTATION_CODE: 0\n']:
            self.assertFalse(instrumentation_summary(counts + terminal, 0)['passed'])
        success = counts + 'INSTRUMENTATION_CODE: -1\n'
        self.assertFalse(instrumentation_summary(success, 1)['passed'])
        self.assertFalse(instrumentation_summary(success, 0, True)['passed'])

    def test_detached_result_uses_recorder_code_without_inventing_raw_output(self):
        counts = 'checks=2 failures=0\n'
        self.assertTrue(instrumentation_summary(counts, 0, recorded_result_code=-1)['passed'])
        for code in [None, 0, -2, '-1', True]:
            self.assertFalse(instrumentation_summary(counts, 0, recorded_result_code=code)['passed'])
        self.assertFalse(instrumentation_summary(counts + 'INSTRUMENTATION_CODE: -1\n', 0,
                                                recorded_result_code=-1)['passed'])


class DeviceProbeAbortTest(unittest.TestCase):
    def test_abort_only_stops_isolated_package_and_checks_known_names(self):
        process = Mock()
        with patch.object(runner, 'run_adb', side_effect=[{'exit': 0}, {'exit': 1, 'stdout': '', 'stderr': ''}]) as adb:
            result = runner.stop_probe(['adb', '-s', 'emulator'], process)
        self.assertEqual(['shell', 'am', 'force-stop', 'app.quietreader.flowprobe'], adb.call_args_list[0].args[1])
        self.assertEqual(['shell', 'pidof', 'app.quietreader.flowprobe', 'app.quietreader.flowprobe:ranking'], adb.call_args_list[1].args[1])
        self.assertTrue(result['namedProcessesAbsentAtCheck'])
        self.assertFalse(result['hostClientKilled'])
        process.kill.assert_not_called()

    def test_unfinished_client_is_killed_then_remote_stop_retried(self):
        process = Mock()
        process.wait.side_effect = [subprocess.TimeoutExpired('adb', 5), 0]
        with patch.object(runner, 'run_adb', side_effect=[{'exit': 0}, {'exit': None, 'error': 'timeout'}, {'exit': 1, 'stdout': '', 'stderr': 'device offline'}]) as adb:
            result = runner.stop_probe(['adb'], process)
        self.assertEqual(2, len(result['deviceStopAttempts']))
        self.assertTrue(result['hostClientKilled'])
        self.assertIsNone(result['namedProcessesAbsentAtCheck'])
        process.kill.assert_called_once()


class RecordedEnvironmentTests(unittest.TestCase):
    def sample(self, at, counter, powered='false'):
        return {'elapsedMs': at, 'battery': {'exit': 0, 'truncated': False,
                'stdout': f'AC powered: false\nUSB powered: {powered}\nWireless powered: false\nstatus: 3\ncharge counter: {counter}\ntemperature: 320\n'},
                'thermal': {'exit': 0, 'truncated': False, 'stdout': 'Thermal Status: 2\n'}}

    def report(self, rows):
        from device_probe_metrics import summarize_environment
        return summarize_environment(rows)

    def test_valid_endpoints_and_mid_run_charging_preserved(self):
        report = self.report([self.sample(0, 1000), self.sample(5000, 1100, 'true'), self.sample(10000, 900)])
        self.assertTrue(report['chargingObserved'])
        self.assertEqual(report['batteryEndpoints']['chargeCounterDeltaUAh'], 100)
        self.assertEqual(report['maximumObservedBatteryTemperatureC'], 32)
        self.assertEqual(report['maximumObservedThermalStatus'], 2)
        self.assertFalse(report['energyComparisonVerified'])

    def test_single_or_invalid_read_cannot_create_zero_usage(self):
        report = self.report([self.sample(0, 1000)])
        self.assertIsNone(report['batteryEndpoints']['chargeCounterDeltaUAh'])
        for key, value in [('exit', 1), ('truncated', True), ('error', 'IOException')]:
            row = self.sample(0, 1000); row['battery'][key] = value; row['thermal'][key] = value
            report = self.report([row])
            self.assertEqual(report['batterySamples'], [])
            self.assertIsNone(report['chargingObserved'])
            self.assertIsNone(report['maximumObservedThermalStatus'])

    def test_unknown_values_are_not_unplugged_evidence(self):
        row = self.sample(0, 1000); row['battery']['stdout'] = 'unsupported'
        row['thermal']['stdout'] = 'Thermal Status: 99'
        report = self.report([row])
        self.assertIsNone(report['chargingObserved'])
        self.assertIsNone(report['maximumObservedThermalStatus'])


if __name__ == '__main__':
    unittest.main()
