"""Read-only ordinary New memory observation; never installs, starts or stops the App."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import subprocess
import time
from device_probe_metrics import attributed_pss

PACKAGE = "app.quietreader"

def summarize(rows):
    complete = [r["attribution"] for r in rows if r["attribution"].get("complete") is True]
    model = [a for a in complete if any(p["name"] == PACKAGE + ":ranking" for p in a["processes"])]
    reasons = Counter(reason for row in rows if not row["attribution"].get("complete")
                      for reason in row["attribution"].get("incompleteReasons", ["unclassified_legacy_sample"]))
    return {"samples": len(rows), "complete": len(complete), "incomplete": len(rows)-len(complete),
            "incompleteReasonCounts": dict(sorted(reasons.items())),
            "completeWithRanking": len(model),
            "maxObservedPssKiB": max((a["pssKiB"] for a in complete), default=None),
            "maxObservedWithRankingPssKiB": max((a["pssKiB"] for a in model), default=None),
            "wholeAppPeakVerified": False,
            "scope": "Sequential bracketing package membership/PSS observation, not simultaneous or between-sample peak. Incomplete is unknown. Cached-only/no-model samples cannot prove model-load memory. Sampling adds overhead; no energy attribution."}

def collection_exit(result, require_model=False):
    """Collection/coverage status only, never a memory-budget pass."""
    return 1 if result["interrupted"] or not result["complete"] or require_model and not result["completeWithRanking"] else 0


def replay(path):
    """Diagnose stored commands offline; refuse to silently revise old decisions.

    No adb, installation, or device access. The caller chooses a fresh report
    path; original observations and their historical summary stay untouched.
    """
    raw = path.read_bytes()
    rows = []
    for line in raw.decode("utf-8-sig").splitlines():
        if not line.strip():
            continue
        row = json.loads(line)
        if not isinstance(row, dict) or any(not isinstance(row.get(key), dict) for key in ("before", "memory", "after", "attribution")):
            raise ValueError("Replay requires the complete original three reads and attribution")
        current = attributed_pss(row["before"], row["memory"], row["after"], PACKAGE)
        previous = row["attribution"]
        if type(previous.get("complete")) is not bool or "pssKiB" not in previous or any(previous.get(key) != current[key] for key in ("complete", "pssKiB")):
            raise ValueError("Stored memory decision differs; preserve and investigate, never silently reinterpret")
        rows.append({**row, "attribution": current})
    result = summarize(rows)
    result.update(sourceSha256=hashlib.sha256(raw).hexdigest(), originalDecisionsChanged=0,
                  deviceAccessed=False, normalIdleRecoveryVerified=False)
    return result

def run(adb, args):
    try:
        value = subprocess.run(adb + args, capture_output=True, text=True, encoding="utf-8", errors="replace",
                               timeout=12, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        return {"exit": value.returncode, "stdout": value.stdout, "stderr": value.stderr}
    except subprocess.TimeoutExpired:
        return {"exit": None, "stdout": "", "error": "timeout"}
    except OSError as error:
        return {"exit": None, "stdout": "", "error": type(error).__name__}

def main(args):
    if not 1 <= args.seconds <= 300:
        raise ValueError("Observation must be between 1 and 300 seconds")
    args.output.mkdir(parents=True, exist_ok=False)
    adb = [str(args.adb), "-s", args.serial]
    rows = []
    start = time.monotonic()
    started_wall_ms = round(time.time() * 1000)
    interrupted = False
    try:
        with (args.output / "observations.jsonl").open("x", encoding="utf-8") as out:
            while time.monotonic() - start < args.seconds:
                sample_started = time.monotonic() - start
                before = run(adb, ["shell", "dumpsys", "activity", "processes", PACKAGE])
                memory = run(adb, ["shell", "dumpsys", "meminfo", "--package", PACKAGE])
                after = run(adb, ["shell", "dumpsys", "activity", "processes", PACKAGE])
                row = {"elapsedSeconds": time.monotonic() - start, "before": before, "memory": memory, "after": after,
                       "sampleStartedSeconds": sample_started, "wallTimeUnixMs": round(time.time() * 1000),
                       "attribution": attributed_pss(before, memory, after, PACKAGE)}
                rows.append(row)
                out.write(json.dumps(row, ensure_ascii=False) + "\n")
                out.flush()
                remaining = args.seconds - (time.monotonic() - start)
                if remaining > 0:
                    time.sleep(min(args.interval, remaining))
    except KeyboardInterrupt:
        interrupted = True
    finally:
        result = summarize(rows)
        result.update({"interrupted": interrupted, "requestedSeconds": args.seconds,
                       "actualSeconds": time.monotonic() - start, "intervalSeconds": args.interval,
                       "startedAtUnixMs": started_wall_ms,
                       "serial": args.serial, "package": PACKAGE, "modelCoverageRequired": args.require_model})
        (args.output / "summary.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(result))
    return collection_exit(result, args.require_model)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True, type=Path)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--require-model", action="store_true", help="Fail collection coverage when no complete observation includes the model worker; never proves a peak budget")
    parser.add_argument("--seconds", type=float, default=90)
    parser.add_argument("--interval", type=float, choices=[1.0, 2.0, 5.0], default=2.0)
    raise SystemExit(main(parser.parse_args()))
