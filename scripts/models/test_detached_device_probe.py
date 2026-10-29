import json
import unittest
from detached_device_probe import summarize, summarize_operations, watcher_live, COMPONENT, validate_replay_environment, summarize_background


class WatcherIdentityTests(unittest.TestCase):
    def test_expected_exec_transition_preserves_identity(self):
        expected=dict(pid=12,startTicks='100',cmdline='/system/bin/sh /system/bin/am instrument '+COMPONENT)
        current=dict(pid=12,startTicks='100',cmdline='app_process com.android.commands.am.Am instrument '+COMPONENT)
        self.assertTrue(watcher_live(expected,current))
        self.assertFalse(watcher_live(expected,dict(current,startTicks='101')))
        self.assertFalse(watcher_live(expected,dict(current,cmdline='unrelated process')))
        self.assertFalse(watcher_live(expected,None))


class DetachedSummaryTests(unittest.TestCase):
    def state(self):
        return dict(runId='unit', phase='reading-core', state='COMPLETE', samples=1,
                    resultCode=-1, passed=True, stream='checks=35 failures=0\n')

    def raw(self, **extra):
        read = dict(exit=0, stdout='** MEMINFO in pid 12 [app.quietreader.flowprobe] **\n TOTAL PSS: 123\n', truncated=False)
        read.update(extra)
        return json.dumps(dict(elapsedMs=10, meminfo=read))

    def test_valid_read_is_sampled_only(self):
        r = summarize(self.state(), self.raw())
        self.assertTrue(r['functionalPassed'])
        self.assertEqual(r['sampledNamedPssPeakKiB'], 123)
        self.assertFalse(r['wholeAppPeakVerified'])
        self.assertFalse(r['energyComparisonVerified'])

    def test_partial_and_failed_queries_are_unknown(self):
        for extra in (dict(truncated=True), dict(exit=1), dict(error='IOException'), dict(exit=None)):
            r = summarize(self.state(), self.raw(**extra))
            self.assertIsNone(r['sampledNamedPssPeakKiB'])
            self.assertFalse(r['memoryEvidenceAvailable'])

    def test_record_loss_is_not_complete(self):
        r = summarize(self.state(), '')
        self.assertFalse(r['recordCountMatches'])
        self.assertFalse(r['memoryEvidenceAvailable'])

    def test_duplicate_summary_rejected(self):
        s = self.state(); s['stream'] += 'checks=1 failures=0\n'
        self.assertFalse(summarize(s, self.raw())['functionalPassed'])

    def test_cancel_cannot_pass_even_with_stale_summary(self):
        s = self.state(); s['state'] = 'CANCELLED'
        self.assertFalse(summarize(s, self.raw())['functionalPassed'])


class ReplayEvidenceTests(unittest.TestCase):
    def evidence(self):
        measurement = dict(stages='NONE', cache='cold', fixtureSha256='a' * 64, replaySeconds=60)
        state = dict(runId='replay', measurement=measurement)
        rows = [dict(kind='identity', runId='replay', stages='NONE', cache='cold',
                     fixtureSha256='a' * 64, durationSeconds=60)]
        rows += [dict(kind='operation', index=i, event=str(i), scheduledMs=i*600,
                      startedMs=i*600, durationMs=10, passed=True, error='') for i in range(100)]
        rows += [dict(kind='complete', viewed=100, opened=6, elapsedMs=60000)]
        return state, rows

    def evaluate(self, state, rows):
        return summarize_operations(state, '\n'.join(json.dumps(r) for r in rows))['passed']

    def test_complete_same_mode(self):
        self.assertTrue(self.evaluate(*self.evidence()))

    def test_wrong_mode_or_fixture(self):
        for key, value in (('stages', 'DUAL'), ('fixtureSha256', 'b'*64), ('runId', 'other')):
            state, rows = self.evidence(); rows[0][key] = value
            self.assertFalse(self.evaluate(state, rows))

    def test_prefetch_conditions_cannot_be_mixed(self):
        state, rows = self.evidence(); state['measurement']['activePrefetch'] = True
        self.assertFalse(self.evaluate(state, rows))
        rows[0]['activePrefetchEnabled'] = True
        self.assertTrue(self.evaluate(state, rows))

    def offline_evidence(self):
        state, rows = self.evidence()
        state['measurement']['networkProfile'] = 'offline-cache'
        rows[0].update(networkProfile='offline-cache', networkAbsentAtStart=True)
        rows[-1]['networkAbsentAtEnd'] = True
        for row in rows[1:-1]:
            row.update(networkAbsentBefore=True, networkAbsentAfter=True)
        return state, rows

    def test_offline_profile_requires_each_checkpoint(self):
        self.assertTrue(self.evaluate(*self.offline_evidence()))
        for key in ('networkAbsentBefore', 'networkAbsentAfter'):
            for value in (None, False, 1):
                state, rows = self.offline_evidence(); rows[51][key] = value
                self.assertFalse(self.evaluate(state, rows))
        state, rows = self.offline_evidence(); del rows[-1]['networkAbsentAtEnd']
        self.assertFalse(self.evaluate(state, rows))
        state, rows = self.offline_evidence(); del rows[0]['networkAbsentAtStart']
        self.assertFalse(self.evaluate(state, rows))

    def test_network_profiles_cannot_be_mixed(self):
        state, rows = self.offline_evidence(); rows[0]['networkProfile'] = 'uncontrolled'
        self.assertFalse(self.evaluate(state, rows))
        state, rows = self.evidence(); state['measurement']['networkProfile'] = 'unknown'
        rows[0]['networkProfile'] = 'unknown'
        self.assertFalse(self.evaluate(state, rows))

    def test_long_uncontrolled_replay_rejected(self):
        validate_replay_environment(300, 'uncontrolled')
        validate_replay_environment(1800, 'offline-cache')
        with self.assertRaises(ValueError):
            validate_replay_environment(301, 'uncontrolled')
        state, rows = self.evidence()
        state['measurement']['replaySeconds'] = 1800; rows[0]['durationSeconds'] = 1800
        for i, row in enumerate(rows[1:-1]):
            row['scheduledMs'] = row['startedMs'] = i*18000
        rows[-1]['elapsedMs'] = 1800000
        self.assertFalse(self.evaluate(state, rows))
        state['measurement']['networkProfile'] = rows[0]['networkProfile'] = 'offline-cache'
        rows[0]['networkAbsentAtStart'] = True
        rows[-1]['networkAbsentAtEnd'] = True
        for row in rows[1:-1]:
            row.update(networkAbsentBefore=True, networkAbsentAfter=True)
        self.assertTrue(self.evaluate(state, rows))

    def test_missing_duplicate_or_failed_operations(self):
        state, rows = self.evidence(); rows.pop(31)
        self.assertFalse(self.evaluate(state, rows))
        state, rows = self.evidence(); rows[31]['event'] = rows[30]['event']
        self.assertFalse(self.evaluate(state, rows))
        state, rows = self.evidence(); rows[31]['passed'] = False
        self.assertFalse(self.evaluate(state, rows))

    def test_incomplete_or_overlapping_timeline(self):
        state, rows = self.evidence(); rows.pop()
        self.assertFalse(self.evaluate(state, rows))
        state, rows = self.evidence(); rows[30]['startedMs'] = 0
        self.assertFalse(self.evaluate(state, rows))
        state, rows = self.evidence(); rows[-1]['elapsedMs'] = 59999
        self.assertFalse(self.evaluate(state, rows))



class BackgroundEvidenceTests(unittest.TestCase):
    def evidence(self):
        measurement = dict(stages='DUAL', cache='cold', fixtureSha256='a'*64,
                           replaySeconds=60, activePrefetch=True, networkProfile='offline-cache')
        state = dict(runId='quiet', measurement=measurement)
        rows = [dict(kind='identity', runId='quiet', stages='DUAL', cache='cold',
                     fixtureSha256='a'*64, quietSeconds=60, activePrefetchEnabled=True,
                     networkProfile='offline-cache', activityStopped=True,
                     graceMs=10000, observedStopMs=80, pollIntervalMs=2000)]
        rows += [dict(kind='sample', index=i, scheduledMs=i*2000, elapsedMs=i*2000+5,
                      mainPresent=True, rankingPids=[], returnedForeground=False,
                      networkAbsent=True, quiet=True) for i in range(31)]
        rows += [dict(kind='complete', samples=31, elapsedMs=60010, quiet=True)]
        return state, rows

    def evaluate(self, state, rows):
        return summarize_background(state, '\n'.join(json.dumps(r) for r in rows))

    def test_complete_background_is_still_sampled_scope(self):
        report = self.evaluate(*self.evidence())
        self.assertTrue(report['passed'])
        self.assertTrue(report['modelProcessAbsentAtCheckpoints'])
        self.assertFalse(report['continuousBackgroundQuietVerified'])
        self.assertFalse(report['energyComparisonVerified'])

    def test_missing_stop_grace_or_wrong_identity_rejected(self):
        for key, value in [('activityStopped', False), ('graceMs', 9000), ('observedStopMs', 5001),
                           ('pollIntervalMs', 10000), ('stages', 'NONE'), ('quietSeconds', 1800)]:
            state, rows = self.evidence(); rows[0][key] = value
            self.assertFalse(self.evaluate(state, rows)['passed'])

    def test_active_unknown_returned_or_reconnected_rejected(self):
        for key, value in [('rankingPids', [55]), ('mainPresent', False), ('mainPresent', 1),
                           ('returnedForeground', True), ('networkAbsent', False), ('quiet', False)]:
            state, rows = self.evidence(); rows[10][key] = value
            self.assertFalse(self.evaluate(state, rows)['passed'])
        state, rows = self.evidence(); del rows[10]['rankingPids']
        self.assertFalse(self.evaluate(state, rows)['passed'])

    def test_partial_duplicate_and_short_timeline_rejected(self):
        state, rows = self.evidence(); rows.pop(8)
        self.assertFalse(self.evaluate(state, rows)['passed'])
        state, rows = self.evidence(); rows[10]['index'] = 8
        self.assertFalse(self.evaluate(state, rows)['passed'])
        state, rows = self.evidence(); rows[10]['elapsedMs'] = 1
        self.assertFalse(self.evaluate(state, rows)['passed'])
        state, rows = self.evidence(); rows[-1]['elapsedMs'] = 59999
        self.assertFalse(self.evaluate(state, rows)['passed'])


if __name__ == '__main__':
    unittest.main()
