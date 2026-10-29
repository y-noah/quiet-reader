import copy
import json
import unittest

from evaluate_recommendations import InvalidEvaluation, evaluate, unique_json


def fixture(count=1, origin="synthetic"):
    sessions = []
    for n in range(count):
        ids = [f"session-{n}-event-{i}" for i in range(40)]
        def order(positive):
            front = ids[:positive] + ids[10:30-positive]
            return front + [key for key in ids if key not in front]
        sessions.append({
            "session_id": f"session-{n}", "learning_cutoff_ms": 100, "ranked_at_ms": 300+n,
            "candidates": [{"event_key": key, "observed_at_ms": 200} for key in ids],
            "rankings": {"neutral": order(6), "embedding": order(8), "dual": order(9)},
            "labels": [{"event_key": key, "choice": "want" if i < 10 else "skip", "labeled_at_ms": 400+n}
                       for i, key in enumerate(ids)],
        })
    return {"schema_version": 1, "label_origin": origin, "training_events": [], "sessions": sessions}


class EvaluationTest(unittest.TestCase):
    def test_prior_exposure_exclusion_does_not_fabricate_a_training_label(self):
        data = fixture()
        key = data["sessions"][0]["candidates"][0]["event_key"]
        data["prior_events"] = [{"event_key": key, "known_at_ms": 90}]
        with self.assertRaises(InvalidEvaluation):
            evaluate(data)
        data["prior_events"] = [{"event_key": "different-prior-event", "known_at_ms": 101}]
        with self.assertRaises(InvalidEvaluation):
            evaluate(data)
        data["prior_events"][0]["known_at_ms"] = 90
        self.assertEqual(1, evaluate(data)["summary"]["sessions"])
    def test_known_counts_paired_gains_and_synthetic_cannot_qualify(self):
        summary = evaluate(fixture(5))["summary"]
        self.assertAlmostEqual(summary["paired_modes"]["dual"]["precision"], .45)
        self.assertAlmostEqual(summary["dual_minus_neutral_pp"], 15)
        self.assertAlmostEqual(summary["dual_minus_embedding_pp"], 5)
        self.assertFalse(summary["personal_sample_floor_met"])
        self.assertIsNone(summary["neutral_15pp_target_observed"])

    def test_declared_user_data_floor_and_exact_boundary(self):
        summary = evaluate(fixture(5, "user"))["summary"]
        self.assertTrue(summary["personal_sample_floor_met"])
        self.assertEqual(100, summary["unique_dual_front_events_in_complete_pairs"])
        self.assertTrue(summary["neutral_15pp_target_observed"])
        self.assertTrue(summary["dual_5pp_target_observed"])
        self.assertFalse(evaluate(fixture(4, "user"))["summary"]["personal_sample_floor_met"])

    def test_unknown_labels_never_become_negative_or_partial_precision(self):
        data = fixture()
        data["sessions"][0]["labels"][0]["choice"] = "unsure"
        del data["sessions"][0]["labels"][1]
        report = evaluate(data)
        metrics = report["sessions"][0]["modes"]["dual"]
        self.assertEqual(2, metrics["unknown"])
        self.assertEqual(7, metrics["want"])
        self.assertEqual(11, metrics["skip"])
        self.assertIsNone(metrics["precision"])
        self.assertEqual(0, report["summary"]["paired_complete_sessions"])
        self.assertIsNone(report["summary"]["dual_minus_neutral_pp"])

    def test_repeated_events_cannot_inflate_unique_sample_floor(self):
        data = fixture(1, "user")
        for n in range(1, 5):
            session = copy.deepcopy(data["sessions"][0]); session["session_id"] = f"repeat-{n}"
            data["sessions"].append(session)
        summary = evaluate(data)["summary"]
        self.assertEqual(100, summary["paired_modes"]["dual"]["positions"])
        self.assertEqual(20, summary["unique_dual_front_events_in_complete_pairs"])
        self.assertFalse(summary["personal_sample_floor_met"])

    def test_short_lists_report_actual_denominator(self):
        data = fixture()
        session = data["sessions"][0]
        allowed = {item["event_key"] for item in session["candidates"][:7]}
        session["candidates"] = session["candidates"][:7]
        session["labels"] = session["labels"][:7]
        session["rankings"] = {m: [key for key in order if key in allowed] for m, order in session["rankings"].items()}
        self.assertEqual(7, evaluate(data)["sessions"][0]["modes"]["dual"]["positions"])

    def test_event_leakage_and_time_leakage_are_rejected(self):
        for mutation in ("train_event", "future_train", "old_candidate", "future_candidate", "early_label"):
            with self.subTest(mutation=mutation):
                data = fixture(); session = data["sessions"][0]
                if mutation == "train_event":
                    data["training_events"] = [{"event_key": session["candidates"][0]["event_key"], "labeled_at_ms": 90}]
                elif mutation == "future_train":
                    data["training_events"] = [{"event_key": "other", "labeled_at_ms": 101}]
                elif mutation == "old_candidate":
                    session["candidates"][0]["observed_at_ms"] = 100
                elif mutation == "future_candidate":
                    session["candidates"][0]["observed_at_ms"] = 301
                else:
                    session["labels"][0]["labeled_at_ms"] = 300
                with self.assertRaises(InvalidEvaluation):
                    evaluate(data)

    def test_duplicate_or_different_pool_is_rejected(self):
        for mutation in ("rank_duplicate", "rank_missing", "rank_unknown", "duplicate_candidate", "duplicate_label", "duplicate_session"):
            with self.subTest(mutation=mutation):
                data = fixture(); session = data["sessions"][0]
                if mutation == "rank_duplicate": session["rankings"]["dual"][0] = session["rankings"]["dual"][1]
                elif mutation == "rank_missing": session["rankings"]["dual"].pop()
                elif mutation == "rank_unknown": session["rankings"]["dual"][0] = "not-in-pool"
                elif mutation == "duplicate_candidate": session["candidates"].append(session["candidates"][0])
                elif mutation == "duplicate_label": session["labels"].append(session["labels"][0])
                else: data["sessions"].append(copy.deepcopy(session))
                with self.assertRaises(InvalidEvaluation): evaluate(data)

    def test_explicit_observations_cannot_be_promoted_by_schema_or_origin(self):
        for mutation in ("root_flag", "root_kind", "session_flag", "invalid_flag"):
            with self.subTest(mutation=mutation):
                data = fixture(5, "user")
                if mutation == "root_flag": data["holdout_qualified"] = False
                elif mutation == "root_kind": data["kind"] = "recommendation_trial_observations"
                elif mutation == "session_flag": data["sessions"][0]["holdout_qualified"] = False
                else: data["holdout_qualified"] = "true"
                with self.assertRaises(InvalidEvaluation): evaluate(data)
    def test_json_duplicate_keys_are_rejected(self):
        with self.assertRaises(InvalidEvaluation):
            json.loads('{"label_origin":"synthetic","label_origin":"user"}', object_pairs_hook=unique_json)


if __name__ == "__main__":
    unittest.main()
