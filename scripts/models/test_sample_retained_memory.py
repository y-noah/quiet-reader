import unittest
import json
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch
from sample_retained_memory import summarize, collection_exit, replay

class RetainedMemorySummaryTest(unittest.TestCase):
    def row(self, complete, pss, names):
        return {"attribution": {"complete": complete, "pssKiB": pss,
                "processes": [{"name": name} for name in names]}}

    def test_unknown_is_not_zero_or_an_overflow_observation(self):
        rows = [self.row(False, 9000000, ["app.quietreader:ranking"]),
                self.row(True, 600000, ["app.quietreader"])]
        result = summarize(rows)
        self.assertEqual(600000, result["maxObservedPssKiB"])
        self.assertEqual(1, result["incomplete"])
        self.assertEqual(0, result["completeWithRanking"])
        self.assertIsNone(result["maxObservedWithRankingPssKiB"])
        self.assertFalse(result["wholeAppPeakVerified"])

    def test_model_coverage_requires_a_complete_matching_process_set(self):
        result = summarize([self.row(True, 900000, ["app.quietreader", "app.quietreader:ranking"]),
                            self.row(True, 400000, ["app.quietreader"])])
        self.assertEqual(1, result["completeWithRanking"])
        self.assertEqual(900000, result["maxObservedWithRankingPssKiB"])
        self.assertFalse(result["wholeAppPeakVerified"])

    def test_required_model_coverage_rejects_an_otherwise_complete_collection(self):
        result = summarize([self.row(True, 400000, ["app.quietreader"])])
        result["interrupted"] = False
        self.assertEqual(0, collection_exit(result))
        self.assertEqual(1, collection_exit(result, require_model=True))

    def test_model_coverage_does_not_override_interruption(self):
        result = summarize([self.row(True, 900000, ["app.quietreader", "app.quietreader:ranking"])])
        result["interrupted"] = False
        self.assertEqual(0, collection_exit(result, require_model=True))
        self.assertFalse(result["wholeAppPeakVerified"])
        result["interrupted"] = True
        self.assertEqual(1, collection_exit(result, require_model=True))

    def test_no_observation_stays_unknown(self):
        result = summarize([])
        self.assertEqual(0, result["complete"])
        self.assertIsNone(result["maxObservedPssKiB"])
        self.assertIsNone(result["maxObservedWithRankingPssKiB"])
        self.assertFalse(result["wholeAppPeakVerified"])

    def test_replay_diagnoses_raw_absence_without_touching_device_or_old_report(self):
        roster = {"exit": 0, "stdout": "ACTIVITY MANAGER RUNNING PROCESSES (dumpsys activity processes)\n OOM levels:\n"}
        row = {"before": roster, "after": roster,
               "memory": {"exit": 0, "stdout": "No process found for: app.quietreader\n"},
               "attribution": {"complete": False, "pssKiB": None}}
        with TemporaryDirectory() as folder:
            source = Path(folder) / 'observations.jsonl'
            raw = json.dumps(row).encode();source.write_bytes(raw)
            with patch('sample_retained_memory.run') as adb:
                result = replay(source)
            adb.assert_not_called()
            self.assertEqual(raw, source.read_bytes())
        self.assertEqual(1, result['incompleteReasonCounts']['meminfo_no_process_response'])
        self.assertEqual(0, result['originalDecisionsChanged'])
        self.assertIsNone(result['maxObservedPssKiB'])
        self.assertFalse(result['normalIdleRecoveryVerified'])
        self.assertFalse(result['deviceAccessed'])

    def test_replay_rejects_reinterpreted_zero_and_incomplete_raw_records(self):
        with TemporaryDirectory() as folder:
            source = Path(folder) / 'observations.jsonl'
            for row in ({'attribution': {'complete': False, 'pssKiB': None}},
                        {'before': {}, 'memory': {}, 'after': {}, 'attribution': {'complete': False, 'pssKiB': 0}}):
                source.write_text(json.dumps(row))
                with self.assertRaises(ValueError), patch('sample_retained_memory.run') as adb:
                    replay(source)
                adb.assert_not_called()

if __name__ == "__main__":
    unittest.main()
