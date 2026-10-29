"""Read-only export of local trial observations; never claims independent holdout."""
import argparse
from contextlib import closing
import hashlib
import json
import re
from pathlib import Path
import sqlite3
import time

from evaluate_recommendations import InvalidEvaluation, precision, require, timestamp, unique_json

MODES = ("neutral", "embedding", "dual")


def learning_provenance(value, pool, profile, source, frozen, where):
    """Allowlist metadata only; this does not qualify an independent evaluation."""
    require(isinstance(value, dict) and type(value.get("schema")) is int and value["schema"] == 1
            and value.get("holdout_qualified") is False, where, "invalid learning provenance")
    require(type(value.get("profile_version")) is int and value["profile_version"] == profile
            and type(value.get("source_version")) is int and value["source_version"] == source,
            where, "learning input versions differ")
    cutoff = timestamp(value.get("learning_cutoff_ms"), where)
    latest = timestamp(value.get("latest_feedback_at_ms"), where)
    count = timestamp(value.get("ledger_rows"), where)
    require(latest <= cutoff <= frozen and count <= 100000, where, "learning time or budget invalid")
    hashes = {}
    for key in ("ledger_sha256", "active_feedback_sha256"):
        raw = value.get(key)
        require(isinstance(raw, str) and re.fullmatch(r"[a-f0-9]{64}", raw), where, "invalid learning fingerprint")
        hashes[key] = raw
    learned = value.get("known_learning_identities")
    require(isinstance(learned, list) and len(learned) <= 10000
            and all(isinstance(key, str) and re.fullmatch(r"(?:group-)?[a-f0-9]{64}", key) for key in learned)
            and len(learned) == len(set(learned)), where, "invalid learning identities")
    rows = value.get("candidate_events")
    require(isinstance(rows, list) and len(rows) == len(pool), where, "learning pool differs")
    events, seen = [], set()
    for row in rows:
        require(isinstance(row, dict) and isinstance(row.get("event_key"), str), where, "invalid learning event")
        key = row["event_key"]
        require(key in pool and key not in seen, where, "unknown or duplicate learning event")
        seen.add(key)
        members = row.get("members")
        require(isinstance(members, list) and 0 < len(members) <= 5000, where, "invalid member pool")
        clean, ids = [], set()
        # App URL identity is not independently reconstructed here; private titles
        # and URLs are never copied to the exported provenance.
        for member in members:
            require(isinstance(member, dict), where, "invalid member")
            for field in ("id", "text_hash"):
                require(isinstance(member.get(field), str) and re.fullmatch(r"[a-f0-9]{64}", member[field]), where, "invalid member hash")
            first = timestamp(member.get("first_seen_ms"), where)
            require(0 < first <= cutoff and member["id"] not in ids, where, "invalid first observation")
            ids.add(member["id"])
            clean.append({"id": member["id"], "text_hash": member["text_hash"], "first_seen_ms": first})
        event = {"event_key": key, "members": clean}
        if "identity_aliases" in row:
            aliases = row["identity_aliases"]
            require(isinstance(aliases, list) and len(aliases) <= 10000
                    and all(isinstance(alias, str) and re.fullmatch(r"(?:group-)?[a-f0-9]{64}", alias) for alias in aliases)
                    and len(aliases) == len(set(aliases)), where, "invalid event identity aliases")
            event["identity_aliases"] = aliases
        events.append(event)
    return {"schema": 1, "holdout_qualified": False, "learning_cutoff_ms": cutoff,
            "profile_version": profile, "source_version": source, "ledger_rows": count,
            "latest_feedback_at_ms": latest, **hashes, "known_learning_identities": learned,
            "candidate_events": events}


def prospective_provenance(value, learning, model, where):
    require(isinstance(value, dict) and type(value.get("protocol_schema")) is int
            and value["protocol_schema"] == 1 and value.get("holdout_qualified") is False,
            where, "invalid prospective baseline")
    result = {"protocol_schema": 1, "holdout_qualified": False}
    for key in ("id", "policy", "ranking_policies"):
        result[key] = identity(value.get(key), where)
    for key in ("cutoff_ms", "app_epoch_ms", "profile_version"):
        result[key] = timestamp(value.get(key), where)
    require(result["app_epoch_ms"] > 0 and result["cutoff_ms"] < learning["learning_cutoff_ms"],
            where, "prospective time window invalid")
    for key in ("model_revision", "source_config_sha256", "ledger_sha256", "active_feedback_sha256"):
        raw = value.get(key)
        require(isinstance(raw, str) and re.fullmatch(r"[a-f0-9]{64}", raw), where, "invalid baseline hash")
        result[key] = raw
    require(result["model_revision"] == model and result["profile_version"] == learning["profile_version"]
            and all(result[key] == learning[key] for key in ("ledger_sha256", "active_feedback_sha256")),
            where, "prospective model or learning state changed")
    for key in ("known_identities", "known_text_hashes", "used_identities", "used_text_hashes"):
        values = value.get(key)
        require(isinstance(values, list) and len(values) <= 20000 and all(
            isinstance(item, str) and re.fullmatch(r"(?:group-)?[a-f0-9]{64}", item) for item in values)
            and len(values) == len(set(values)), where, "invalid baseline identities")
        result[key] = values
    known = set(result["known_identities"]) | set(result["used_identities"])
    hashes = set(result["known_text_hashes"]) | set(result["used_text_hashes"])
    for event in learning["candidate_events"]:
        require(event["event_key"] not in known and known.isdisjoint(event.get("identity_aliases", [])), where, "prospective event already known")
        for member in event["members"]:
            require(member["first_seen_ms"] > result["cutoff_ms"] and member["id"] not in known
                    and member["text_hash"] not in hashes, where, "prospective member already known")
    return result


def identity(value, where):
    require(isinstance(value, str) and bool(value.strip()), where, "nonempty identity required")
    return value


def events(rows, where, maximum):
    require(isinstance(rows, list) and 0 < len(rows) <= maximum, where, "invalid event count")
    result = {}
    for n, row in enumerate(rows):
        loc = f"{where}[{n}]"
        require(isinstance(row, dict), loc, "event object required")
        key = identity(row.get("event_key"), loc)
        require(key not in result, loc, "duplicate event")
        candidate = row.get("candidate")
        require(isinstance(candidate, dict), loc, "candidate required")
        timestamp(candidate.get("observedAt"), loc + ".observedAt")
        result[key] = candidate
    return result


def session_observation(session_id, at, payload, stored_labels):
    identity(session_id, "session.id")
    timestamp(at, "session.at")
    require(isinstance(payload, dict) and type(payload.get("trial_schema")) is int
            and payload["trial_schema"] == 1, session_id, "expected trial_schema 1")
    require(payload.get("holdout_qualified") is False, session_id,
            "this exporter accepts only explicitly non-holdout observations")
    require(payload.get("kind") in ("single_snapshot", "paired_snapshot"), session_id, "unknown trial kind")
    frozen = timestamp(payload.get("frozen_at_ms"), session_id + ".frozen_at_ms")
    require(at == frozen, session_id, "stored freeze time differs from payload")
    paired = payload["kind"] == "paired_snapshot"
    selected = events(payload.get("items"), session_id + ".items", 60 if paired else 20)
    pool = events(payload.get("pool"), session_id + ".pool", 100) if paired else selected
    orders = payload.get("rankings") if paired else {"displayed": list(selected)}
    require(isinstance(orders, dict) and set(orders) == (set(MODES) if paired else {"displayed"}),
            session_id, "unexpected ranking modes")
    front = set()
    for mode, order in orders.items():
        require(isinstance(order, list) and all(isinstance(key, str) for key in order), session_id, "invalid order")
        require(len(order) == len(set(order)) and set(order) == set(pool), session_id, "unequal or duplicate ranking pool")
        front.update(order[:20])
    require(set(selected) == front, session_id, "selected events differ from blinded fronts")
    for key, candidate in selected.items():
        require(candidate == pool[key], session_id, "blinded display differs from frozen pool")
    labels = {}
    times = {}
    for key, choice, labeled_at in stored_labels:
        require(key in selected and key not in labels, session_id, "unknown or repeated labeled event")
        require(choice in ("want", "skip", "unsure"), session_id, "invalid choice")
        timestamp(labeled_at, session_id + ".label.at")
        require(labeled_at >= frozen, session_id, "label precedes frozen list")
        labels[key], times[key] = choice, labeled_at
    provenance = {}
    for key in ("source_version", "profile_version"):
        provenance[key] = timestamp(payload.get(key), session_id + "." + key)
    if paired:
        for key in ("model_revision", "profile_signature", "semantic_policy", "platform_policy"):
            provenance[key] = identity(payload.get(key), session_id + "." + key)
        provenance["generation"] = timestamp(payload.get("generation"), session_id + ".generation")
        provenance["finite_rerank_scores"] = timestamp(payload.get("finite_rerank_scores"), session_id + ".scores")
    else:
        for key in ("snapshot", "filter", "mode"):
            require(key in payload, session_id, "missing snapshot provenance")
            provenance[key] = payload[key]
    if "learning_provenance" in payload:
        require(paired, session_id, "learning capture requires paired comparison")
        provenance["learning"] = learning_provenance(payload["learning_provenance"], pool,
            provenance["profile_version"], provenance["source_version"], frozen, session_id)
    if "prospective_protocol" in payload:
        require(paired and "learning" in provenance, session_id, "prospective learning capture missing")
        provenance["prospective"] = prospective_provenance(payload["prospective_protocol"],
            provenance["learning"], provenance["model_revision"], session_id)
    return {
        "session_id": session_id, "kind": payload["kind"], "holdout_qualified": False,
        "frozen_at_ms": frozen, "provenance": provenance,
        "candidates": [{"event_key": key, "observed_at_ms": candidate["observedAt"]}
                       for key, candidate in pool.items()],
        "rankings": orders,
        "labels": [{"event_key": key, "choice": labels[key], "labeled_at_ms": times[key]}
                   for key in selected if key in labels],
        "complete": len(labels) == len(selected),
        "observational_counts": {mode: precision(order, labels) for mode, order in orders.items()},
    }


def export_observations(database, exported_at_ms=None):
    database = Path(database).resolve()
    require(database.is_file(), "database", "existing SQLite file required")
    # mode=ro preserves normal WAL visibility; immutable=1 would silently omit live WAL data.
    with closing(sqlite3.connect(database.as_uri() + "?mode=ro", uri=True)) as db:
        db.execute("PRAGMA query_only=ON")
        db.execute("BEGIN")
        require(db.execute("PRAGMA user_version").fetchone()[0] in (1, 2), "database", "expected trial DB version 1 or 2")
        require(db.execute("SELECT COUNT(*) FROM sessions").fetchone()[0] <= 30, "database", "trial retention limit exceeded")
        headers = db.execute("SELECT id,at,length(payload) FROM sessions ORDER BY at,rowid").fetchall()
        require(len(headers) <= 30, "database", "trial retention limit exceeded")
        require(all(type(size) is int and 0 < size <= 2 * 1024 * 1024 for _, _, size in headers),
                "database", "payload exceeds export budget")
        require(db.execute("SELECT COUNT(*) FROM labels").fetchone()[0] <= 1800,
                "database", "label budget exceeded")
        require(db.execute("SELECT COUNT(*) FROM labels WHERE session NOT IN (SELECT id FROM sessions)").fetchone()[0] == 0,
                "database", "orphan labels")
        sessions = []
        for session_id, at, _ in headers:
            raw = db.execute("SELECT payload FROM sessions WHERE id=?", (session_id,)).fetchone()[0]
            payload = json.loads(raw, object_pairs_hook=unique_json)
            labels = db.execute("SELECT event,choice,at FROM labels WHERE session=? ORDER BY event", (session_id,)).fetchall()
            sessions.append(session_observation(session_id, at, payload, labels))
    # Logical digest identifies exactly the exported observations, including unverified labels.
    digest = hashlib.sha256(json.dumps(sessions, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    return {
        "observation_schema": 1, "kind": "recommendation_trial_observations",
        "holdout_qualified": False, "label_origin": "unverified_app_choices",
        "exported_at_ms": timestamp(int(time.time() * 1000) if exported_at_ms is None else exported_at_ms, "export time"),
        "logical_sha256": digest, "sessions": sessions,
        "limits": [
            "Observations only: optional pre-ranking/prospective records do not establish independent event adjudication or authenticated label origin.",
            "Same-pool counts describe these exposed candidates; they do not prove personalization gain or generalization.",
            "Missing and unsure remain unknown; no personal sample-floor or target-success claim is emitted.",
            "Source SQLite is read only; no cookies, article body, candidate title, URL or learning feedback are exported.",
            "The independent evaluator must reject this envelope; changing its schema or origin is not qualification.",
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("database", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.resolve() == args.database.resolve():
        parser.error("output must differ from source database")
    # Do not overwrite SQLite sidecars either, including a currently active WAL.
    if args.output.resolve() in (Path(str(args.database.resolve()) + "-wal"), Path(str(args.database.resolve()) + "-shm")):
        parser.error("output must not be a SQLite sidecar")
    try:
        report = export_observations(args.database)
    except (InvalidEvaluation, sqlite3.Error, json.JSONDecodeError, OSError) as error:
        parser.error(str(error))
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"sessions": len(report["sessions"]), "holdout_qualified": False, "logical_sha256": report["logical_sha256"]}))


if __name__ == "__main__":
    main()
