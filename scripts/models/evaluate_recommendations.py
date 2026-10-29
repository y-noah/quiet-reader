"""Offline event/time holdout metrics. No models, network or inferred user labels."""
import argparse
import json
from pathlib import Path

MODES = ("neutral", "embedding", "dual")


class InvalidEvaluation(ValueError):
    pass


def require(condition, location, message):
    if not condition:
        raise InvalidEvaluation(f"{location}: {message}")


def text(value, location):
    require(isinstance(value, str) and bool(value.strip()), location, "nonempty string required")
    return value


def timestamp(value, location):
    require(type(value) is int and value >= 0, location, "nonnegative epoch milliseconds required")
    return value


def objects(value, location):
    require(isinstance(value, list), location, "array required")
    require(all(isinstance(v, dict) for v in value), location, "object entries required")
    return value


def unique_json(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "JSON", "duplicate object key")
        result[key] = value
    return result


def precision(order, labels, k=20):
    front = order[:k]
    yes = sum(labels.get(key) == "want" for key in front)
    no = sum(labels.get(key) == "skip" for key in front)
    unknown = len(front) - yes - no
    return {
        "positions": len(front), "want": yes, "skip": no, "unknown": unknown,
        "label_coverage": (yes + no) / len(front) if front else None,
        # Partial labels must never silently become negative labels or an exact P@20.
        "precision": yes / len(front) if front and not unknown else None,
    }


def evaluate(data):
    require(isinstance(data, dict), "root", "object required")
    require(data.get("kind") != "recommendation_trial_observations" and data.get("holdout_qualified", True) is True,
            "provenance", "non-holdout observations cannot be evaluated as independent")
    require(type(data.get("schema_version")) is int and data["schema_version"] == 1,
            "schema_version", "expected 1")
    origin = data.get("label_origin")
    require(origin in ("user", "developer", "synthetic"), "label_origin", "unknown label provenance")
    training = objects(data.get("training_events"), "training_events")
    train = {}
    for n, item in enumerate(training):
        loc = f"training_events[{n}]"
        key = text(item.get("event_key"), loc + ".event_key")
        at = timestamp(item.get("labeled_at_ms"), loc + ".labeled_at_ms")
        train[key] = max(train.get(key, 0), at)

    # Conservative exclusions may include prior exposures without a training label.
    # Keep their actual meaning rather than fabricating a labeled_at timestamp.
    prior = {}
    for n, item in enumerate(objects(data.get("prior_events", []), "prior_events")):
        loc = f"prior_events[{n}]"
        key = text(item.get("event_key"), loc + ".event_key")
        at = timestamp(item.get("known_at_ms"), loc + ".known_at_ms")
        prior[key] = max(prior.get(key, 0), at)

    sessions = objects(data.get("sessions"), "sessions")
    require(bool(sessions), "sessions", "at least one session required")
    seen_sessions, evaluated, front_labeled = set(), set(), set()
    results, paired = [], []
    repeat_events = set()
    for n, session in enumerate(sessions):
        loc = f"sessions[{n}]"
        require(session.get("holdout_qualified", True) is True, loc, "session explicitly lacks holdout qualification")
        session_id = text(session.get("session_id"), loc + ".session_id")
        require(session_id not in seen_sessions, loc, "duplicate session identity")
        seen_sessions.add(session_id)
        cutoff = timestamp(session.get("learning_cutoff_ms"), loc + ".learning_cutoff_ms")
        ranked_at = timestamp(session.get("ranked_at_ms"), loc + ".ranked_at_ms")
        require(ranked_at > cutoff, loc, "ranking must follow learning cutoff")
        require(all(at <= cutoff for at in train.values()), loc, "training label is after learning cutoff")
        require(all(at <= cutoff for at in prior.values()), loc, "prior exposure is after learning cutoff")
        candidates = objects(session.get("candidates"), loc + ".candidates")
        require(bool(candidates), loc, "empty candidate pool")
        pool = set()
        for i, candidate in enumerate(candidates):
            where = f"{loc}.candidates[{i}]"
            key = text(candidate.get("event_key"), where + ".event_key")
            require(key not in pool, where, "duplicate event; merge source entries before evaluation")
            require(key not in train, where, "event overlaps learning set")
            require(key not in prior, where, "event overlaps prior exposure set")
            observed = timestamp(candidate.get("observed_at_ms"), where + ".observed_at_ms")
            require(cutoff < observed <= ranked_at, where, "candidate is outside holdout time window")
            pool.add(key)
        repeat_events.update(evaluated & pool)
        evaluated.update(pool)

        labels = {}
        for i, label in enumerate(objects(session.get("labels"), loc + ".labels")):
            where = f"{loc}.labels[{i}]"
            key = text(label.get("event_key"), where + ".event_key")
            require(key in pool, where, "label is not in candidate pool")
            require(key not in labels, where, "duplicate label")
            require(label.get("choice") in ("want", "skip", "unsure"), where, "invalid choice")
            at = timestamp(label.get("labeled_at_ms"), where + ".labeled_at_ms")
            require(at > ranked_at, where, "holdout label must follow frozen rankings")
            labels[key] = label["choice"]

        rankings = session.get("rankings")
        require(isinstance(rankings, dict) and set(rankings) == set(MODES), loc + ".rankings",
                "neutral, embedding and dual required")
        metrics = {}
        for mode in MODES:
            order = rankings[mode]
            where = loc + ".rankings." + mode
            require(isinstance(order, list) and all(isinstance(v, str) for v in order), where, "string array required")
            require(len(order) == len(set(order)), where, "duplicate ranked event")
            require(set(order) == pool, where, "all modes must rank the same complete candidate pool")
            metrics[mode] = precision(order, labels)
            front_labeled.update(key for key in order[:20] if labels.get(key) in ("want", "skip"))
        # Only fully labeled paired top-20s can contribute to a quality difference.
        comparable = all(metrics[m]["precision"] is not None for m in MODES)
        result = {"session_id": session_id, "candidate_events": len(pool), "modes": metrics,
                  "paired_complete": comparable}
        results.append(result)
        if comparable:
            paired.append(result)

    aggregates = {}
    for mode in MODES:
        values = [row["modes"][mode] for row in paired]
        count = sum(v["positions"] for v in values)
        aggregates[mode] = {"positions": count, "want": sum(v["want"] for v in values),
                            "precision": sum(v["want"] for v in values) / count if count else None}
    paired_front, paired_dual_front = set(), set()
    for session, result in zip(sessions, results):
        if result["paired_complete"]:
            paired_dual_front.update(session["rankings"]["dual"][:20])
            for mode in MODES:
                paired_front.update(session["rankings"][mode][:20])
    user_ready = (origin == "user" and len(paired) >= 5 and len(paired_dual_front) >= 100
                  and all(aggregates[m]["positions"] >= 100 for m in MODES))
    gain_neutral = (aggregates["dual"]["precision"] - aggregates["neutral"]["precision"]) if paired else None
    gain_embedding = (aggregates["dual"]["precision"] - aggregates["embedding"]["precision"]) if paired else None
    return {
        "schema_version": 1, "label_origin": origin, "sessions": results,
        "summary": {
            "sessions": len(results), "paired_complete_sessions": len(paired),
            "unique_evaluated_events": len(evaluated), "repeated_events_across_sessions": len(repeat_events),
            "unique_labeled_front_events": len(front_labeled),
            "unique_front_events_in_complete_pairs": len(paired_front),
            "unique_dual_front_events_in_complete_pairs": len(paired_dual_front),
            "paired_modes": aggregates,
            "dual_minus_neutral_pp": gain_neutral * 100 if gain_neutral is not None else None,
            "dual_minus_embedding_pp": gain_embedding * 100 if gain_embedding is not None else None,
            "personal_sample_floor_met": user_ready,
            "neutral_15pp_target_observed": gain_neutral >= 0.15 - 1e-12 if user_ready else None,
            "dual_5pp_target_observed": gain_embedding >= 0.05 - 1e-12 if user_ready else None,
        },
        "limits": [
            "Label provenance and canonical event identities are supplied by the evaluator, not authenticated by this tool.",
            "Only paired fully labeled front lists contribute to aggregate precision; missing/unsure are unknown.",
            "Repeated events are counted once for the unique-event sample floor; per-session metrics retain their actual positions.",
            "Threshold observations do not prove stable benefit, human preference generalization, CPU, memory or battery compliance.",
            "This tool neither trains models nor feeds holdout labels back into the app. Reserve an independent new set after tuning.",
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.output and args.input.resolve() == args.output.resolve():
        parser.error("output must differ from input")
    try:
        with args.input.open(encoding="utf-8-sig") as stream:
            report = evaluate(json.load(stream, object_pairs_hook=unique_json))
    except (InvalidEvaluation, json.JSONDecodeError) as error:
        parser.error(str(error))
    encoded = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.write_text(encoded, encoding="utf-8")
    else:
        print(encoded, end="")


if __name__ == "__main__":
    main()
