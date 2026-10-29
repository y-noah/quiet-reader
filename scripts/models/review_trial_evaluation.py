"""Local human event-review gate for prospective observations. No model or network."""
import argparse
import hashlib
import json
from pathlib import Path
import re

from evaluate_recommendations import InvalidEvaluation, evaluate, require, timestamp, unique_json
from export_trial_observations import learning_provenance, prospective_provenance

POLICY = "manual-event-review-v1"
ATTESTATIONS = (
    "complete_learning_history_reviewed",
    "cross_platform_event_identity_reviewed",
    "label_origin_confirmed",
    "no_post_cutoff_learning_used",
)
MAX_FILE = 32 * 1024 * 1024


def digest(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True,
                                    separators=(",", ":")).encode()).hexdigest()


def named(value, where):
    require(isinstance(value, str) and re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._:-]{0,127}", value),
            where, "bounded pseudonymous identity required")
    return value


def source_sessions(data, protocol_id):
    """Revalidate the sanitized export, never trust its observational counts."""
    require(isinstance(data, dict) and data.get("kind") == "recommendation_trial_observations"
            and type(data.get("observation_schema")) is int and data["observation_schema"] == 1 and data.get("holdout_qualified") is False
            and data.get("label_origin") == "unverified_app_choices", "source", "observation envelope required")
    sessions = data.get("sessions")
    require(isinstance(sessions, list) and 0 < len(sessions) <= 30, "source", "invalid session count")
    require(data.get("logical_sha256") == digest(sessions), "source", "logical digest differs")
    timestamp(data.get("exported_at_ms"), "source.exported_at_ms")
    selected, seen, baseline, rank_policy = [], set(), None, None
    for session in sessions:
        require(isinstance(session, dict), "session", "object required")
        sid = named(session.get("session_id"), "session.id")
        require(sid not in seen, sid, "duplicate session")
        seen.add(sid)
        provenance = session.get("provenance")
        require(isinstance(provenance, dict), sid, "provenance required")
        prospective = provenance.get("prospective")
        if not isinstance(prospective, dict) or prospective.get("id") != protocol_id:
            continue
        require(session.get("kind") == "paired_snapshot" and session.get("holdout_qualified") is False,
                sid, "paired non-holdout source required")
        frozen = timestamp(session.get("frozen_at_ms"), sid)
        require(frozen <= data["exported_at_ms"], sid, "ranking follows source export")
        candidates = session.get("candidates")
        require(isinstance(candidates, list) and 0 < len(candidates) <= 100, sid, "invalid candidate count")
        pool, observed = {}, {}
        for row in candidates:
            require(isinstance(row, dict), sid, "candidate object required")
            key = named(row.get("event_key"), sid)
            require(key not in pool, sid, "duplicate candidate event")
            observed[key] = timestamp(row.get("observed_at_ms"), sid)
            require(observed[key] <= frozen, sid, "future candidate observation")
            pool[key] = {}
        profile, source = timestamp(provenance.get("profile_version"), sid), timestamp(provenance.get("source_version"), sid)
        learning = learning_provenance(provenance.get("learning"), pool, profile, source, frozen, sid)
        protocol = prospective_provenance(prospective, learning, provenance.get("model_revision"), sid)
        require(protocol["policy"] == "prospective-v1", sid, "unsupported prospective policy")
        signature = provenance.get("profile_signature")
        require(isinstance(signature, str) and re.fullmatch(r"[a-f0-9]{64}", signature), sid, "invalid profile signature")
        semantic, platform = provenance.get("semantic_policy"), provenance.get("platform_policy")
        require(isinstance(semantic, str) and bool(semantic) and isinstance(platform, str) and bool(platform), sid, "ranking policies required")
        require(protocol["ranking_policies"].startswith(f"prospective-v1/{semantic}/{platform}/"), sid, "frozen ranking policies differ")
        policy = (signature, semantic, platform)
        if rank_policy is None:
            rank_policy = policy
        require(policy == rank_policy, sid, "profile signature or ranking policies changed")
        immutable = {k: v for k, v in protocol.items() if k not in ("used_identities", "used_text_hashes")}
        if baseline is None:
            baseline = immutable
        require(immutable == baseline, sid, "protocol identity was reused with a different baseline")
        orders = session.get("rankings")
        require(isinstance(orders, dict) and set(orders) == {"neutral", "embedding", "dual"}, sid, "three orders required")
        front = set()
        for order in orders.values():
            require(isinstance(order, list) and all(isinstance(key, str) for key in order)
                    and len(order) == len(set(order)) and set(order) == set(pool), sid, "unequal ranking pool")
            front.update(order[:20])
        labels = session.get("labels")
        require(isinstance(labels, list) and len(labels) <= 60, sid, "invalid label count")
        labeled = set()
        for label in labels:
            require(isinstance(label, dict), sid, "label object required")
            key = label.get("event_key")
            require(isinstance(key, str) and key in front and key not in labeled and label.get("choice") in ("want", "skip", "unsure"), sid, "invalid label")
            at = timestamp(label.get("labeled_at_ms"), sid)
            require(frozen < at <= data["exported_at_ms"], sid, "label must follow ranking and precede export")
            labeled.add(key)
        selected.append({"id": sid, "frozen": frozen, "protocol": protocol, "learning": learning,
                         "orders": orders, "labels": labels, "observed": observed})
    require(bool(selected), "protocol", "no prospective sessions for requested protocol")
    return selected, baseline


def review_scope(sessions, baseline):
    prior = set(baseline["known_identities"])
    identities = set(prior)
    for session in sessions:
        prior.update(session["learning"]["known_learning_identities"])
        for event in session["learning"]["candidate_events"]:
            identities.add(event["event_key"])
            identities.update(member["id"] for member in event["members"])
            identities.update(event.get("identity_aliases", []))
    identities.update(prior)
    require(len(identities) <= 30000, "review", "identity budget exceeded")
    return identities, prior


def template(data, protocol_id):
    sessions, baseline = source_sessions(data, protocol_id)
    identities, prior = review_scope(sessions, baseline)
    return {"review_schema": 1, "policy": POLICY, "source_logical_sha256": data["logical_sha256"],
            "protocol_id": protocol_id, "reviewer_id": None, "label_origin": None,
            "reviewed_at_ms": None, "local_learning_evidence_sha256": None,
            "attestations": {key: False for key in ATTESTATIONS},
            "identity_map": [{"identity": key, "canonical_event_key": None,
                              "scope": "prior" if key in prior else "candidate"} for key in sorted(identities)],
            "additional_prior_events": [],
            "instructions": [
                "Review locally against the complete unchanged learning history and original frozen candidate content.",
                "Map the same real event across URLs, titles and platforms to one canonical key; never split it to improve metrics.",
                "All members of one frozen group must map to one event. Duplicate events within a frozen pool require a new trial, not silent rank editing.",
                "Add prior events whose learning identity was not recoverable from app targets. Empty additional_prior_events is a human declaration, not automatic evidence.",
                "Confirm who actually made the choices. Synthetic/developer choices must never be labeled user.",
                "Use a pseudonymous reviewer ID. The evidence SHA binds a local review file; it does not authenticate human diligence or preference labels.",
            ]}


def evaluate_review(data, review, evidence_sha256):
    require(isinstance(review, dict) and type(review.get("review_schema")) is int and review["review_schema"] == 1
            and review.get("policy") == POLICY, "review", "expected review schema 1")
    protocol_id = named(review.get("protocol_id"), "review.protocol_id")
    sessions, baseline = source_sessions(data, protocol_id)
    require(review.get("source_logical_sha256") == data["logical_sha256"], "review", "review belongs to different source")
    reviewer = named(review.get("reviewer_id"), "review.reviewer_id")
    origin = review.get("label_origin")
    require(origin in ("user", "developer", "synthetic"), "review", "explicit actual choice provenance required")
    reviewed = timestamp(review.get("reviewed_at_ms"), "review.reviewed_at_ms")
    require(reviewed >= data["exported_at_ms"], "review", "review must follow frozen export")
    evidence = review.get("local_learning_evidence_sha256")
    require(isinstance(evidence, str) and re.fullmatch(r"[a-f0-9]{64}", evidence), "review", "local learning evidence digest required")
    require(evidence == evidence_sha256, "review", "local learning evidence bytes differ from the reviewed digest")
    attestations = review.get("attestations")
    require(isinstance(attestations, dict) and set(attestations) == set(ATTESTATIONS)
            and all(value is True for value in attestations.values()), "review", "all human review declarations required")
    expected, prior_ids = review_scope(sessions, baseline)
    entries = review.get("identity_map")
    require(isinstance(entries, list) and len(entries) == len(expected), "review", "complete identity mapping required")
    mapping = {}
    for entry in entries:
        require(isinstance(entry, dict), "mapping", "object required")
        key = entry.get("identity")
        require(isinstance(key, str) and key in expected and key not in mapping, "mapping", "unknown or duplicate identity")
        require(entry.get("scope") == ("prior" if key in prior_ids else "candidate"), "mapping", "identity scope differs")
        mapping[key] = named(entry.get("canonical_event_key"), "mapping.event")
    prior = {mapping[key]: baseline["cutoff_ms"] for key in prior_ids}
    extras = review.get("additional_prior_events")
    require(isinstance(extras, list) and len(extras) <= 10000, "review", "additional prior event array required")
    for extra in extras:
        require(isinstance(extra, dict), "prior", "object required")
        key = named(extra.get("event_key"), "prior.event")
        at = timestamp(extra.get("known_at_ms"), "prior.time")
        require(at <= baseline["cutoff_ms"], "prior", "prior event is after cutoff")
        prior[key] = max(prior.get(key, 0), at)
    projected, evaluated_events = [], set()
    for session in sessions:
        candidates, seen = [], set()
        for event in session["learning"]["candidate_events"]:
            canonical = mapping[event["event_key"]]
            require(all(mapping[member["id"]] == canonical for member in event["members"]), session["id"], "frozen group contains distinct human-adjudicated events")
            require(all(mapping[alias] == canonical for alias in event.get("identity_aliases", [])), session["id"], "frozen group aliases map to distinct human-adjudicated events")
            require(canonical not in seen, session["id"], "duplicate human event in frozen pool; collect a corrected trial")
            require(canonical not in evaluated_events, session["id"], "human event repeated across prospective sessions; collect a new independent trial")
            require(canonical not in prior, session["id"], "candidate overlaps adjudicated prior/learning event")
            seen.add(canonical)
            # Preserve earliest member entry, not the latest refresh's observedAt.
            observed = min(member["first_seen_ms"] for member in event["members"])
            candidates.append({"event_key": canonical, "observed_at_ms": observed})
        evaluated_events.update(seen)
        projected.append({"session_id": session["id"], "learning_cutoff_ms": baseline["cutoff_ms"],
                          "ranked_at_ms": session["frozen"], "candidates": candidates,
                          "rankings": {mode: [mapping[key] for key in order] for mode, order in session["orders"].items()},
                          "labels": [{**label, "event_key": mapping[label["event_key"]]} for label in session["labels"]]})
    metrics = evaluate({"schema_version": 1, "label_origin": origin, "training_events": [],
                        "prior_events": [{"event_key": key, "known_at_ms": at} for key, at in sorted(prior.items())],
                        "sessions": projected})
    return {"kind": "reviewer_declared_trial_report", "qualification": "human_declared_not_authenticated",
            "source_logical_sha256": data["logical_sha256"], "review_sha256": digest(review),
            "protocol_sha256": digest(baseline), "local_learning_evidence_sha256": evidence,
            "reviewer_id": reviewer, "reviewed_at_ms": reviewed, "protocol_id": protocol_id,
            "metrics": metrics, "limits": [
                "Human event identities, history completeness, review evidence and choice origin are declarations, not authenticated facts.",
                "No automatic promotion of observations: this report requires a complete separate human review and remains conditional on its accuracy.",
                "A qualifying sample floor or observed target does not prove stable personal benefit, battery or resource compliance.",
            ]}


def read_json(path):
    require(path.stat().st_size <= MAX_FILE, "file", "review input exceeds budget")
    with path.open(encoding="utf-8-sig") as stream:
        return json.load(stream, object_pairs_hook=unique_json)


def file_digest(path):
    require(0 < path.stat().st_size <= 64 * 1024 * 1024, "evidence", "empty or oversized local review evidence")
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("observations", type=Path)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--template", metavar="PROTOCOL_ID")
    group.add_argument("--review", type=Path)
    parser.add_argument("--learning-evidence", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if bool(args.review) != bool(args.learning_evidence):
        parser.error("--review requires --learning-evidence; template mode does not read learning evidence")
    inputs = [p for p in (args.observations, args.review, args.learning_evidence) if p]
    reserved = {alias.resolve() for p in inputs for alias in (p, Path(str(p) + "-wal"), Path(str(p) + "-shm"))}
    if args.output.resolve() in reserved:
        parser.error("output must differ from all input files and SQLite sidecars")
    if args.output.exists():
        parser.error("output already exists; use a new path to preserve the review trail")
    try:
        source = read_json(args.observations)
        result = template(source, args.template) if args.template else evaluate_review(source, read_json(args.review), file_digest(args.learning_evidence))
        with args.output.open("x", encoding="utf-8") as stream:
            stream.write(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    except (InvalidEvaluation, OSError, json.JSONDecodeError) as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
