import copy
import json
from pathlib import Path
import sqlite3
import subprocess
import sys
import tempfile
import unittest

from evaluate_recommendations import InvalidEvaluation, evaluate
from export_trial_observations import export_observations


def paired():
    pool = [{"event_key": f"event-{n}", "candidate": {"observedAt": 200, "title": "PRIVATE_TITLE", "url": "https://example.test/private"}} for n in range(30)]
    ids = [row["event_key"] for row in pool]
    return {"trial_schema": 1, "kind": "paired_snapshot", "holdout_qualified": False,
            "frozen_at_ms": 300, "source_version": 2, "profile_version": 3,
            "generation": 4, "model_revision": "model", "profile_signature": "signature",
            "semantic_policy": "semantic", "platform_policy": "platform", "finite_rerank_scores": 30,
            "pool": pool, "items": copy.deepcopy(pool),
            "rankings": {"neutral": ids, "embedding": ids[5:] + ids[:5], "dual": ids[10:] + ids[:10]}}


class ObservationExportTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / "trial data.db"
        self.db = sqlite3.connect(self.path)
        self.db.executescript("PRAGMA user_version=1; CREATE TABLE sessions(id TEXT PRIMARY KEY,at INTEGER,payload TEXT); CREATE TABLE labels(session TEXT,event TEXT,choice TEXT,at INTEGER); CREATE TABLE private_cookie(value TEXT);")
        self.db.execute("INSERT INTO private_cookie VALUES('DO_NOT_EXPORT_SECRET')")
        self.value = paired()
        self.save()

    def tearDown(self):
        self.db.close()
        self.temp.cleanup()

    def save(self):
        self.db.execute("INSERT OR REPLACE INTO sessions VALUES('one',300,?)", (json.dumps(self.value),))
        self.db.commit()

    def labels(self, count=30):
        self.db.executemany("INSERT INTO labels VALUES('one',?,?,400)", [(f"event-{n}", "want" if n < 5 else "skip") for n in range(count)])
        self.db.commit()

    def add_learning(self):
        self.value["learning_provenance"] = {
            "schema": 1, "holdout_qualified": False, "learning_cutoff_ms": 250,
            "profile_version": 3, "source_version": 2, "ledger_rows": 2,
            "latest_feedback_at_ms": 150, "ledger_sha256": "a" * 64,
            "active_feedback_sha256": "b" * 64, "known_learning_identities": ["c" * 64],
            "candidate_events": [{"event_key": row["event_key"], "members": [
                {"id": f"{n:064x}", "text_hash": "d" * 64, "first_seen_ms": 100}]}
                for n, row in enumerate(self.value["pool"])],
            "private_extra": "DO_NOT_EXPORT_SECRET"}
        self.save()

    def test_learning_metadata_preserved_but_never_qualifies_holdout(self):
        self.add_learning()
        result = export_observations(self.path, 500)
        learning = result["sessions"][0]["provenance"]["learning"]
        self.assertEqual(100, learning["candidate_events"][0]["members"][0]["first_seen_ms"])
        self.assertEqual("a" * 64, learning["ledger_sha256"])
        self.assertNotIn("DO_NOT_EXPORT_SECRET", json.dumps(result))
        self.assertFalse(learning["holdout_qualified"])
        with self.assertRaises(InvalidEvaluation):
            evaluate(result)

    def test_group_aliases_are_preserved_and_malformed_aliases_rejected(self):
        self.add_learning()
        event = self.value["learning_provenance"]["candidate_events"][0]
        alias = "group-" + "1" * 64
        event["identity_aliases"] = [alias]
        self.save()
        result = export_observations(self.path, 500)
        self.assertEqual([alias], result["sessions"][0]["provenance"]["learning"]["candidate_events"][0]["identity_aliases"])
        for bad in ([alias, alias], ["private title or URL"], None):
            event["identity_aliases"] = bad
            self.save()
            with self.assertRaises(InvalidEvaluation):
                export_observations(self.path, 500)

    def test_consumed_group_alias_rejects_a_new_event_key_and_member(self):
        self.add_prospective()
        alias = "group-" + "1" * 64
        self.value["learning_provenance"]["candidate_events"][0]["identity_aliases"] = [alias]
        for field in ("known_identities", "used_identities"):
            self.value["prospective_protocol"][field] = [alias]
            self.save()
            with self.assertRaises(InvalidEvaluation):
                export_observations(self.path, 500)
            self.value["prospective_protocol"][field] = []

    def test_learning_rejects_future_times_stale_versions_and_unknown_events(self):
        self.add_learning()
        original = copy.deepcopy(self.value)
        for field, bad in (("learning_cutoff_ms", 301), ("latest_feedback_at_ms", 251),
                           ("profile_version", 4), ("holdout_qualified", True),
                           ("ledger_sha256", "unverified")):
            with self.subTest(field=field):
                self.value = copy.deepcopy(original)
                self.value["learning_provenance"][field] = bad
                self.save()
                with self.assertRaises(InvalidEvaluation):
                    export_observations(self.path, 500)
        self.value = copy.deepcopy(original)
        self.value["learning_provenance"]["candidate_events"][0]["event_key"] = "unknown"
        self.save()
        with self.assertRaises(InvalidEvaluation):
            export_observations(self.path, 500)

    def add_prospective(self):
        self.add_learning()
        self.value["model_revision"] = "e" * 64
        self.value["prospective_protocol"] = {
            "protocol_schema": 1, "holdout_qualified": False, "id": "protocol-one",
            "policy": "prospective-v1", "ranking_policies": "fixed-policies",
            "cutoff_ms": 50, "app_epoch_ms": 1, "profile_version": 3,
            "model_revision": "e" * 64, "source_config_sha256": "f" * 64,
            "ledger_sha256": "a" * 64, "active_feedback_sha256": "b" * 64,
            "known_identities": ["c" * 64], "known_text_hashes": [],
            "used_identities": [], "used_text_hashes": [],
            "private_extra": "DO_NOT_EXPORT_SECRET"}
        self.db.execute("PRAGMA user_version=2")
        self.save()

    def test_v2_prospective_envelope_preserves_baseline_but_is_not_independent(self):
        self.add_prospective()
        result = export_observations(self.path, 500)
        protocol = result["sessions"][0]["provenance"]["prospective"]
        self.assertEqual(50, protocol["cutoff_ms"])
        self.assertEqual("protocol-one", protocol["id"])
        self.assertNotIn("DO_NOT_EXPORT_SECRET", json.dumps(result))
        with self.assertRaises(InvalidEvaluation):
            evaluate(result)

    def test_prospective_rejects_known_or_consumed_members_and_changed_learning(self):
        self.add_prospective()
        original = copy.deepcopy(self.value)
        for field, bad in (("known_identities", ["0" * 64]), ("used_identities", ["0" * 64]),
                           ("known_text_hashes", ["d" * 64]), ("used_text_hashes", ["d" * 64]),
                           ("cutoff_ms", 100), ("model_revision", "f" * 64),
                           ("ledger_sha256", "f" * 64), ("holdout_qualified", True)):
            with self.subTest(field=field):
                self.value = copy.deepcopy(original)
                self.value["prospective_protocol"][field] = bad
                self.save()
                with self.assertRaises(InvalidEvaluation):
                    export_observations(self.path, 500)

    def test_same_pool_counts_provenance_and_no_sensitive_content(self):
        self.labels()
        before = self.path.read_bytes()
        result = export_observations(self.path, 500)
        self.assertEqual(before, self.path.read_bytes())
        self.assertFalse(result["holdout_qualified"])
        self.assertEqual("unverified_app_choices", result["label_origin"])
        session = result["sessions"][0]
        self.assertEqual("signature", session["provenance"]["profile_signature"])
        self.assertEqual(5, session["observational_counts"]["neutral"]["want"])
        self.assertEqual(0, session["observational_counts"]["dual"]["want"])
        self.assertEqual(.25, session["observational_counts"]["neutral"]["precision"])
        self.assertTrue(session["complete"])
        encoded = json.dumps(result)
        for private in ("PRIVATE_TITLE", "example.test", "DO_NOT_EXPORT_SECRET", "personal_sample_floor_met"):
            self.assertNotIn(private, encoded)
        with self.assertRaises(InvalidEvaluation):
            evaluate(result)

    def test_unknown_and_incomplete_choices_stay_unknown(self):
        self.labels(1)
        self.db.execute("UPDATE labels SET choice='unsure'")
        self.db.commit()
        value = export_observations(self.path)["sessions"][0]
        self.assertFalse(value["complete"])
        self.assertEqual(20, value["observational_counts"]["neutral"]["unknown"])
        self.assertIsNone(value["observational_counts"]["neutral"]["precision"])
        self.assertEqual("unsure", value["labels"][0]["choice"])

    def test_duplicate_unequal_or_changed_blinded_events_are_rejected(self):
        original = copy.deepcopy(self.value)
        for mutation in ("duplicate", "missing", "changed", "front"):
            with self.subTest(mutation=mutation):
                self.value = copy.deepcopy(original)
                if mutation == "duplicate": self.value["rankings"]["dual"][0] = self.value["rankings"]["dual"][1]
                elif mutation == "missing": self.value["rankings"]["dual"].pop()
                elif mutation == "changed": self.value["items"][0]["candidate"]["title"] = "changed"
                else: self.value["items"].pop()
                self.save()
                with self.assertRaises(InvalidEvaluation): export_observations(self.path)

    def test_label_outside_pool_duplicate_or_pre_freeze_is_rejected(self):
        for mutation in ("outside", "duplicate", "early", "invalid"):
            with self.subTest(mutation=mutation):
                self.db.execute("DELETE FROM labels")
                self.db.execute("INSERT INTO labels VALUES('one',?,?,?)", ("unknown" if mutation == "outside" else "event-0", "LESS" if mutation == "invalid" else "want", 299 if mutation == "early" else 400))
                if mutation == "duplicate": self.db.execute("INSERT INTO labels VALUES('one','event-0','skip',401)")
                self.db.commit()
                with self.assertRaises(InvalidEvaluation): export_observations(self.path)

    def test_provenance_cannot_be_promoted_to_holdout(self):
        self.value["holdout_qualified"] = True
        self.save()
        with self.assertRaises(InvalidEvaluation): export_observations(self.path)
        self.value["holdout_qualified"] = False
        del self.value["model_revision"]
        self.save()
        with self.assertRaises(InvalidEvaluation): export_observations(self.path)

    def test_json_duplicate_keys_and_wrong_schema_are_rejected(self):
        raw = json.dumps(self.value).replace('"trial_schema": 1', '"trial_schema": 1, "trial_schema": 1')
        self.db.execute("UPDATE sessions SET payload=?", (raw,))
        self.db.commit()
        with self.assertRaises(InvalidEvaluation): export_observations(self.path)
        self.save()
        self.db.execute("PRAGMA user_version=99")
        with self.assertRaises(InvalidEvaluation): export_observations(self.path)

    def test_single_snapshot_and_empty_database_are_honest(self):
        self.value = {key: self.value[key] for key in ("trial_schema", "holdout_qualified", "frozen_at_ms", "source_version", "profile_version")}
        self.value.update(kind="single_snapshot", snapshot=1, filter="all", mode="cold", items=paired()["items"][:3])
        self.save()
        result = export_observations(self.path)["sessions"][0]
        self.assertEqual({"displayed"}, set(result["rankings"]))
        self.db.execute("DELETE FROM sessions")
        self.db.commit()
        self.assertEqual([], export_observations(self.path)["sessions"])

    def test_wal_snapshot_includes_committed_but_not_uncommitted_labels(self):
        self.db.execute("PRAGMA journal_mode=WAL")
        self.db.execute("INSERT INTO labels VALUES('one','event-0','want',400)")
        self.db.commit()
        self.db.execute("INSERT INTO labels VALUES('one','event-1','skip',401)")
        result = export_observations(self.path)["sessions"][0]
        self.assertEqual(1, len(result["labels"]))
        self.db.rollback()

    def test_orphans_and_budget_overflow_are_rejected(self):
        self.db.execute("INSERT INTO labels VALUES('absent','event','want',400)")
        self.db.commit()
        with self.assertRaises(InvalidEvaluation): export_observations(self.path)
        self.db.execute("DELETE FROM labels")
        self.db.execute("UPDATE sessions SET payload=?", (" " * (2 * 1024 * 1024 + 1),))
        self.db.commit()
        with self.assertRaises(InvalidEvaluation): export_observations(self.path)

    def test_cli_writes_report_and_refuses_overwriting_database_or_wal(self):
        script = Path(__file__).with_name("export_trial_observations.py")
        output = Path(self.temp.name) / "observations.json"
        success = subprocess.run([sys.executable, str(script), str(self.path), "--output", str(output)], capture_output=True)
        self.assertEqual(0, success.returncode, success.stderr.decode())
        self.assertFalse(json.loads(output.read_text())["holdout_qualified"])
        for target in (self.path, Path(str(self.path) + "-wal")):
            rejected = subprocess.run([sys.executable, str(script), str(self.path), "--output", str(target)], capture_output=True)
            self.assertNotEqual(0, rejected.returncode)


if __name__ == "__main__":
    unittest.main()
