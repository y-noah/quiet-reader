import copy
import hashlib
import json
from pathlib import Path
import sqlite3
import subprocess
import sys
import tempfile
import unittest

from evaluate_recommendations import InvalidEvaluation, evaluate
from export_trial_observations import export_observations
from review_trial_evaluation import ATTESTATIONS, digest, evaluate_review, file_digest, template


def sha(value):
    return hashlib.sha256(value.encode()).hexdigest()


EVIDENCE = b"synthetic local learning audit only; no real user choices or credentials"
EVIDENCE_HASH = hashlib.sha256(EVIDENCE).hexdigest()


def raw_session(index):
    frozen = 300 + index * 400
    pool = [{"event_key": sha(f"candidate-{index}-{n}"), "candidate": {
        "observedAt": 200 + index * 400, "title": "PRIVATE_TITLE", "url": "https://private.invalid/DO_NOT_EXPORT"}}
        for n in range(30)]
    keys = [row["event_key"] for row in pool]
    protocol = {"protocol_schema": 1, "holdout_qualified": False, "id": "protocol-one",
                "policy": "prospective-v1", "ranking_policies": "prospective-v1/semantic/platform/normalizer",
                "cutoff_ms": 100, "app_epoch_ms": 1, "profile_version": 2,
                "model_revision": sha("model"), "source_config_sha256": sha("sources"),
                "ledger_sha256": sha("ledger"), "active_feedback_sha256": sha("active"),
                "known_identities": [sha("prior")], "known_text_hashes": [sha("prior text")],
                "used_identities": [], "used_text_hashes": []}
    learning = {"schema": 1, "holdout_qualified": False, "learning_cutoff_ms": frozen - 1,
                "profile_version": 2, "source_version": index + 1, "ledger_rows": 1,
                "latest_feedback_at_ms": 90, "ledger_sha256": sha("ledger"),
                "active_feedback_sha256": sha("active"), "known_learning_identities": [sha("prior")],
                "candidate_events": [{"event_key": key, "members": [
                    {"id": key, "text_hash": sha(f"text-{index}-{n}"), "first_seen_ms": 150 + index * 400}]}
                    for n, key in enumerate(keys)]}
    return {"trial_schema": 1, "kind": "paired_snapshot", "holdout_qualified": False,
            "frozen_at_ms": frozen, "source_version": index + 1, "profile_version": 2,
            "generation": index, "model_revision": sha("model"), "profile_signature": sha("profile"),
            "semantic_policy": "semantic", "platform_policy": "platform", "finite_rerank_scores": 30,
            "pool": pool, "items": copy.deepcopy(pool), "learning_provenance": learning,
            "prospective_protocol": protocol,
            "rankings": {"neutral": keys, "embedding": keys[5:] + keys[:5], "dual": keys[10:] + keys[:10]}}


class HumanReviewTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.db_path = self.root / "private trials.db"
        self.db = sqlite3.connect(self.db_path)
        self.db.executescript("PRAGMA user_version=2; CREATE TABLE sessions(id TEXT PRIMARY KEY,at INTEGER,payload TEXT); CREATE TABLE labels(session TEXT,event TEXT,choice TEXT,at INTEGER);")
        self.add_session(0)

    def tearDown(self):
        self.db.close()
        self.temp.cleanup()

    def add_session(self, index):
        raw = raw_session(index)
        sid = f"session-{index}"
        self.db.execute("INSERT INTO sessions VALUES(?,?,?)", (sid, raw["frozen_at_ms"], json.dumps(raw)))
        self.db.executemany("INSERT INTO labels VALUES(?,?,?,?)", [
            (sid, row["event_key"], "want" if n < 8 else "skip", raw["frozen_at_ms"] + 10)
            for n, row in enumerate(raw["pool"])])
        self.db.commit()

    def source(self):
        return export_observations(self.db_path, 5000)

    def review(self, source):
        review = template(source, "protocol-one")
        review.update(reviewer_id="synthetic-reviewer", label_origin="synthetic", reviewed_at_ms=6000,
                      local_learning_evidence_sha256=EVIDENCE_HASH)
        review["attestations"] = {key: True for key in ATTESTATIONS}
        for entry in review["identity_map"]:
            entry["canonical_event_key"] = "canonical-" + entry["identity"]
        return review

    def evaluate(self, source, review):
        return evaluate_review(source, review, EVIDENCE_HASH)

    def test_actual_readonly_export_to_review_to_metrics_without_private_content(self):
        source = self.source()
        review = self.review(source)
        report = self.evaluate(source, review)
        self.assertEqual("human_declared_not_authenticated", report["qualification"])
        self.assertEqual(1, report["metrics"]["summary"]["paired_complete_sessions"])
        self.assertFalse(report["metrics"]["summary"]["personal_sample_floor_met"])
        self.assertEqual(-40, report["metrics"]["summary"]["dual_minus_neutral_pp"])
        for private in ("PRIVATE_TITLE", "private.invalid", "DO_NOT_EXPORT", "synthetic local learning audit"):
            self.assertNotIn(private, json.dumps(report) + json.dumps(template(source, "protocol-one")))
        with self.assertRaises(InvalidEvaluation):
            evaluate(source)

    def test_template_cannot_be_used_as_a_completed_human_review(self):
        source = self.source()
        with self.assertRaises(InvalidEvaluation):
            self.evaluate(source, template(source, "protocol-one"))
        review = self.review(source)
        review["identity_map"][0]["canonical_event_key"] = None
        with self.assertRaises(InvalidEvaluation):
            self.evaluate(source, review)

    def test_rejects_stale_digest_missing_mapping_and_false_attestations(self):
        source = self.source()
        original = self.review(source)
        for mutate in (lambda r: r.update(source_logical_sha256=sha("other")),
                       lambda r: r["identity_map"].pop(),
                       lambda r: r["identity_map"].append(r["identity_map"][0]),
                       lambda r: r["attestations"].update(complete_learning_history_reviewed=False),
                       lambda r: r.update(local_learning_evidence_sha256=sha("different evidence")),
                       lambda r: r.update(reviewed_at_ms=4000)):
            review = copy.deepcopy(original)
            mutate(review)
            with self.assertRaises(InvalidEvaluation):
                self.evaluate(source, review)

    def test_human_mapping_detects_old_events_and_same_event_under_different_ids(self):
        source = self.source()
        review = self.review(source)
        old = next(entry for entry in review["identity_map"] if entry["scope"] == "prior")
        fresh = [entry for entry in review["identity_map"] if entry["scope"] == "candidate"]
        fresh[0]["canonical_event_key"] = old["canonical_event_key"]
        with self.assertRaises(InvalidEvaluation):
            self.evaluate(source, review)
        review = self.review(source)
        fresh = [entry for entry in review["identity_map"] if entry["scope"] == "candidate"]
        fresh[1]["canonical_event_key"] = fresh[0]["canonical_event_key"]
        with self.assertRaises(InvalidEvaluation):
            self.evaluate(source, review)

    def test_additional_unrecoverable_learning_event_excludes_the_candidate(self):
        source = self.source()
        review = self.review(source)
        fresh = next(entry for entry in review["identity_map"] if entry["scope"] == "candidate")
        review["additional_prior_events"] = [{"event_key": fresh["canonical_event_key"], "known_at_ms": 90}]
        with self.assertRaises(InvalidEvaluation):
            self.evaluate(source, review)

    def test_changed_source_metadata_even_with_recomputed_digest_requires_review_again(self):
        source = self.source()
        review = self.review(source)
        source["sessions"][0]["labels"][0]["choice"] = "unsure"
        source["logical_sha256"] = digest(source["sessions"])
        with self.assertRaises(InvalidEvaluation):
            self.evaluate(source, review)
        source["sessions"][0]["rankings"]["dual"].pop()
        source["logical_sha256"] = digest(source["sessions"])
        with self.assertRaises(InvalidEvaluation):
            template(source, "protocol-one")

    def test_uncertain_missing_labels_stay_unknown_and_synthetic_floor_stays_false(self):
        self.db.execute("UPDATE labels SET choice='unsure' WHERE rowid=1")
        self.db.execute("DELETE FROM labels WHERE rowid=2")
        self.db.commit()
        source = self.source()
        report = self.evaluate(source, self.review(source))
        self.assertIsNone(report["metrics"]["sessions"][0]["modes"]["neutral"]["precision"])
        self.assertFalse(report["metrics"]["summary"]["personal_sample_floor_met"])

    def test_all_sessions_share_model_profile_policy_and_baseline(self):
        self.add_session(1)
        source = self.source()
        self.assertEqual(2, self.evaluate(source, self.review(source))["metrics"]["summary"]["sessions"])
        for field, bad in (("profile_signature", sha("other profile")), ("semantic_policy", "other")):
            modified = copy.deepcopy(source)
            modified["sessions"][1]["provenance"][field] = bad
            modified["logical_sha256"] = digest(modified["sessions"])
            with self.assertRaises(InvalidEvaluation):
                template(modified, "protocol-one")

    def test_semantic_repetition_across_sessions_is_rejected_instead_of_inflating_floor(self):
        self.add_session(1)
        source = self.source()
        review = self.review(source)
        one = source["sessions"][0]["candidates"][0]["event_key"]
        two = source["sessions"][1]["candidates"][0]["event_key"]
        mapping = {entry["identity"]: entry for entry in review["identity_map"]}
        mapping[two]["canonical_event_key"] = mapping[one]["canonical_event_key"]
        with self.assertRaises(InvalidEvaluation):
            self.evaluate(source, review)

    def test_group_members_require_same_human_event_without_silent_splitting(self):
        source = self.source()
        session = source["sessions"][0]
        original = session["candidates"][0]["event_key"]
        group = "group-" + sha("group")
        session["candidates"][0]["event_key"] = group
        for order in session["rankings"].values():
            order[order.index(original)] = group
        for label in session["labels"]:
            if label["event_key"] == original:
                label["event_key"] = group
        for event in session["provenance"]["learning"]["candidate_events"]:
            if event["event_key"] == original:
                event["event_key"] = group
        source["logical_sha256"] = digest(source["sessions"])
        review = self.review(source)
        with self.assertRaises(InvalidEvaluation):
            self.evaluate(source, review)
        mapping = {entry["identity"]: entry for entry in review["identity_map"]}
        mapping[original]["canonical_event_key"] = mapping[group]["canonical_event_key"]
        self.assertEqual(30, self.evaluate(source, review)["metrics"]["sessions"][0]["candidate_events"])

    def test_five_synthetic_sessions_cannot_claim_personal_sample_floor(self):
        for index in range(1, 5):
            self.add_session(index)
        source = self.source()
        report = self.evaluate(source, self.review(source))
        summary = report["metrics"]["summary"]
        self.assertEqual(5, summary["paired_complete_sessions"])
        self.assertEqual(100, summary["unique_dual_front_events_in_complete_pairs"])
        self.assertFalse(summary["personal_sample_floor_met"])
        self.assertIsNone(summary["neutral_15pp_target_observed"])

    def test_bridged_alias_requires_human_mapping_to_the_same_frozen_event(self):
        raw = raw_session(0)
        alias = "group-" + sha("weak headline behind complete metadata")
        event = raw["learning_provenance"]["candidate_events"][0]
        key = event["event_key"]
        event["identity_aliases"] = [alias]
        self.db.execute("UPDATE sessions SET payload=? WHERE id='session-0'", (json.dumps(raw),))
        self.db.commit()
        source = self.source()
        review = self.review(source)
        mapping = {entry["identity"]: entry for entry in review["identity_map"]}
        self.assertIn(alias, mapping)
        with self.assertRaises(InvalidEvaluation):
            self.evaluate(source, review)
        mapping[alias]["canonical_event_key"] = mapping[key]["canonical_event_key"]
        self.assertEqual(30, self.evaluate(source, review)["metrics"]["sessions"][0]["candidate_events"])

    def test_cli_binds_evidence_bytes_and_refuses_overwriting_inputs_or_reports(self):
        source = self.source()
        source_path, review_path, evidence_path = self.root / "observations.json", self.root / "review.json", self.root / "evidence.txt"
        source_path.write_text(json.dumps(source), encoding="utf-8")
        review_path.write_text(json.dumps(self.review(source)), encoding="utf-8")
        evidence_path.write_bytes(EVIDENCE)
        output = self.root / "report.json"
        command = [sys.executable, str(Path(__file__).with_name("review_trial_evaluation.py")), str(source_path),
                   "--review", str(review_path), "--learning-evidence", str(evidence_path), "--output"]
        result = subprocess.run(command + [str(output)], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(EVIDENCE_HASH, file_digest(evidence_path))
        before = output.read_bytes()
        for target in (output, source_path, review_path, evidence_path, Path(str(evidence_path) + "-wal")):
            result = subprocess.run(command + [str(target)], capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
        self.assertEqual(before, output.read_bytes())
        evidence_path.write_bytes(b"changed review evidence")
        result = subprocess.run(command + [str(self.root / "rejected.json")], capture_output=True, text=True)
        self.assertNotEqual(0, result.returncode)
        self.assertFalse((self.root / "rejected.json").exists())


if __name__ == "__main__":
    unittest.main()
