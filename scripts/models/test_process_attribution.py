import copy
import json
import unittest

from device_probe_metrics import attributed_pss, parse_package_processes
from detached_device_probe import summarize
from unittest.mock import patch
import measure_device_probe as runner

PACKAGE = 'app.quietreader.flowprobe'
RENDERER = 'com.google.android.webview:sandboxed_process0:org.chromium.content.app.SandboxedProcessService0:0'


def process(pid, name, uid, members, sequence=7):
    return (f'  *APP* UID {uid} ProcessRecord{{abc {pid}:{name}/u0a222}}\n'
            f'    packageList={{{members}}}\n    startSeq={sequence}\n')


def roster(*blocks):
    return 'ACTIVITY MANAGER RUNNING PROCESSES (dumpsys activity processes)\n  All known processes:\n' + ''.join(blocks) + '\n  OOM levels:\n'


def read(text):
    return dict(exit=0, stdout=text, truncated=False)


class ProcessAttributionTests(unittest.TestCase):
    def sample(self):
        processes = roster(process(12, PACKAGE, 10222, PACKAGE),
                           process(13, RENDERER, 99623, PACKAGE),
                           process(14, RENDERER, 99624, PACKAGE + '.other'))
        memory = f'** MEMINFO in pid 12 [{PACKAGE}] **\n TOTAL PSS: 123\n** MEMINFO in pid 13 [{RENDERER}] **\n TOTAL PSS: 456\n'
        return dict(before=read(processes), memory=read(memory), after=read(processes), package=PACKAGE)

    def test_exact_membership_includes_isolated_renderer_excludes_other_app(self):
        data = self.sample()
        rows = parse_package_processes(data['before']['stdout'], PACKAGE)
        self.assertTrue(rows['available'])
        self.assertEqual([12, 13], [r['pid'] for r in rows['processes']])
        result = attributed_pss(**data)
        self.assertTrue(result['complete'])
        self.assertEqual(579, result['pssKiB'])
        self.assertFalse(result['wholeAppPeakVerified'])
        self.assertEqual([], result['incompleteReasons'])

    def test_no_process_response_is_diagnostic_not_zero_or_complete_absence(self):
        data = self.sample()
        data['before'] = data['after'] = read('ACTIVITY MANAGER RUNNING PROCESSES (dumpsys activity processes)\n  OOM levels:\n')
        data['memory'] = read('No process found for: ' + PACKAGE + '\n')
        result = attributed_pss(**data)
        self.assertEqual({'before_membership_unavailable','after_membership_unavailable','pss_total_unavailable','meminfo_no_process_response'}, set(result['incompleteReasons']))
        self.assertFalse(result['complete'])
        self.assertIsNone(result['pssKiB'])
        for change in ({'exit': 1}, {'truncated': True}, {'error': 'timeout'}):
            data['memory'].update(change)
            self.assertNotIn('meminfo_no_process_response', attributed_pss(**data)['incompleteReasons'])
        data['memory'] = read('No process found for: ' + PACKAGE + '.other\n')
        self.assertNotIn('meminfo_no_process_response', attributed_pss(**data)['incompleteReasons'])

    def test_diagnostics_distinguish_churn_missing_renderer_and_bad_reads(self):
        data = self.sample()
        data['after']['stdout'] = data['after']['stdout'].replace('startSeq=7','startSeq=8')
        self.assertIn('membership_changed', attributed_pss(**data)['incompleteReasons'])
        data = self.sample();data['memory'] = read(f'** MEMINFO in pid 12 [{PACKAGE}] **\n TOTAL PSS: 123\n')
        self.assertIn('pss_missing_members', attributed_pss(**data)['incompleteReasons'])
        data['memory']['error'] = 'timeout'
        self.assertIn('memory_read_unavailable', attributed_pss(**data)['incompleteReasons'])

    def test_connected_sampler_retains_bracketing_queries_and_renderer_membership(self):
        data = self.sample()
        with patch.object(runner, 'run_adb', side_effect=[data['before'], data['memory'], data['after']]) as adb:
            before, memory, after, result = runner.memory_snapshot(['adb', '-s', 'isolated-emulator'])
        self.assertIs(before, data['before'])
        self.assertIs(memory, data['memory'])
        self.assertIs(after, data['after'])
        self.assertEqual(579, result['pssKiB'])
        self.assertEqual([['shell', 'dumpsys', 'activity', 'processes', PACKAGE],
                          ['shell', 'dumpsys', 'meminfo', '--package', PACKAGE],
                          ['shell', 'dumpsys', 'activity', 'processes', PACKAGE]],
                         [call.args[1] for call in adb.call_args_list])

    def test_connected_sampler_cannot_certify_missing_renderer_or_process_churn(self):
        for missing in (True, False):
            data = self.sample()
            if missing:
                data['memory'] = read('** MEMINFO in pid 12 [' + PACKAGE + '] **\n TOTAL PSS: 123\n')
            else:
                data['after'] = read(data['after']['stdout'].replace('startSeq=7', 'startSeq=8'))
            with patch.object(runner, 'run_adb', side_effect=[data['before'], data['memory'], data['after']]):
                result = runner.memory_snapshot(['adb'])[-1]
            self.assertFalse(result['complete'])
            self.assertIsNone(result['pssKiB'])
    def test_package_reference_outside_membership_is_not_attribution(self):
        raw = roster(process(14, RENDERER, 99624, PACKAGE + '.other'))
        raw += f'PID #12: ProcessRecord{{abc 12:{PACKAGE}/u0a222}}\n packageList={{{PACKAGE}}}\n'
        self.assertEqual([], parse_package_processes(raw, PACKAGE)['processes'])

    def test_multiple_package_membership_requires_exact_token(self):
        raw = roster(process(13, RENDERER, 99623, 'other.app, ' + PACKAGE))
        self.assertEqual([13], [p['pid'] for p in parse_package_processes(raw, PACKAGE)['processes']])

    def test_missing_renderer_cannot_pass_using_main_pss(self):
        data = self.sample(); data['memory'] = read(f'** MEMINFO in pid 12 [{PACKAGE}] **\n TOTAL PSS: 123\n')
        result = attributed_pss(**data)
        self.assertFalse(result['complete'])
        self.assertEqual([13], result['missingPids'])
        self.assertIsNone(result['pssKiB'])

    def test_birth_exit_uid_change_and_pid_reuse_invalidate_sample(self):
        for replacement in (roster(process(12, PACKAGE, 10222, PACKAGE)),
                            roster(process(12, PACKAGE, 10222, PACKAGE), process(13, RENDERER, 99623, PACKAGE, 8)),
                            roster(process(12, PACKAGE, 10222, PACKAGE), process(13, RENDERER, 99625, PACKAGE)),
                            roster(process(12, PACKAGE, 10222, PACKAGE), process(13, RENDERER, 99623, PACKAGE), process(15, PACKAGE + ':ranking', 10222, PACKAGE))):
            data = self.sample(); data['after'] = read(replacement)
            result = attributed_pss(**data)
            self.assertFalse(result['membershipStable'])
            self.assertIsNone(result['pssKiB'])

    def test_truncated_malformed_duplicate_or_unknown_roster_is_unavailable(self):
        data = self.sample(); raw = data['before']['stdout']
        for value in ('unsupported', raw.split('  OOM levels:')[0],
                      raw.replace('startSeq=7', 'startSeq=unknown'),
                      raw.replace('13:', '12:'), raw.replace('packageList=', 'missingList=')):
            self.assertFalse(parse_package_processes(value, PACKAGE)['available'], value)

    def test_failed_or_truncated_queries_are_never_zero(self):
        for key in ('before', 'memory', 'after'):
            for failure in (dict(exit=1), dict(exit=None), dict(truncated=True), dict(error='IOException')):
                data = self.sample(); data[key].update(failure)
                self.assertIsNone(attributed_pss(**data)['pssKiB'])

    def test_wrong_name_extra_duplicate_and_missing_pss_rejected(self):
        original = self.sample()['memory']['stdout']
        for value in (original.replace(RENDERER, 'unrelated.renderer'), original + original,
                      original.replace('TOTAL PSS: 456', 'missing'), original + '** MEMINFO in pid 99 [other] **\n TOTAL PSS: 1\n'):
            data = self.sample(); data['memory'] = read(value)
            self.assertIsNone(attributed_pss(**data)['pssKiB'])

    def test_empty_or_renderer_only_roster_is_not_complete_app(self):
        for raw in (roster(), roster(process(13, RENDERER, 99623, PACKAGE))):
            data = self.sample(); data['before'] = data['after'] = read(raw)
            self.assertIsNone(attributed_pss(**data)['pssKiB'])

    def test_summary_retains_old_lower_bound_and_counts_attribution_separately(self):
        data = self.sample()
        record = dict(elapsedMs=100, processesBefore=data['before'], meminfo=data['memory'], processesAfter=data['after'])
        state = dict(runId='attribution', phase='reading-core', state='COMPLETE', samples=1,
                     resultCode=-1, passed=True, stream='checks=1 failures=0\n')
        report = summarize(state, json.dumps(record))
        self.assertEqual(579, report['sampledAttributedPssPeakKiB'])
        self.assertEqual(1, report['completeAttributedSamples'])
        self.assertTrue(report['attributedEvidenceAvailable'])
        self.assertFalse(report['wholeAppPeakVerified'])
        old = copy.deepcopy(record); del old['processesBefore']; del old['processesAfter']
        report = summarize(state, json.dumps(old))
        self.assertEqual(579, report['sampledNamedPssPeakKiB'])
        self.assertEqual(0, report['completeAttributedSamples'])
        self.assertIsNone(report['sampledAttributedPssPeakKiB'])
        self.assertFalse(report['attributedEvidenceAvailable'])
        state['samples'] = 2
        self.assertFalse(summarize(state, json.dumps(record))['attributedEvidenceAvailable'])


if __name__ == '__main__':
    unittest.main()
