"""Measure isolated diagnostic workloads; sampled named PSS is not a whole-App peak proof."""
import argparse
import json
import math
import re
import subprocess
import time
from pathlib import Path

from device_probe_metrics import (parse_pss, named_pss, parse_proc_stats, parse_battery,
                                  battery_endpoints, instrumentation_summary, parse_proc_status,
                                  attributed_pss)

PACKAGE = "app.quietreader.flowprobe"
FLAGS = getattr(subprocess, "CREATE_NO_WINDOW", 0)


def run_adb(adb, arguments, timeout=12):
    try:
        value = subprocess.run(adb + arguments, capture_output=True, text=True,
                               encoding="utf-8", errors="replace", timeout=timeout, creationflags=FLAGS)
        return {"exit": value.returncode, "stdout": value.stdout, "stderr": value.stderr}
    except subprocess.TimeoutExpired:
        return {"exit": None, "stdout": "", "stderr": "", "error": "timeout"}
    except OSError as error:
        return {"exit": None, "stdout": "", "stderr": "", "error": type(error).__name__}


def thermal_status(result):
    match = re.search(r"^\s*Thermal Status:\s*(\d+)\s*$", result["stdout"], re.M)
    return int(match.group(1)) if result["exit"] == 0 and match else None


def battery_snapshot(adb):
    raw = run_adb(adb, ["shell", "dumpsys", "battery"])
    return {"read": raw, "values": parse_battery(raw["stdout"] if raw["exit"] == 0 else "")}


def memory_snapshot(adb):
    before = run_adb(adb, ["shell", "dumpsys", "activity", "processes", PACKAGE])
    memory = run_adb(adb, ["shell", "dumpsys", "meminfo", "--package", PACKAGE])
    after = run_adb(adb, ["shell", "dumpsys", "activity", "processes", PACKAGE])
    return before, memory, after, attributed_pss(before, memory, after, PACKAGE)


def stop_probe(adb, process):
    # Only this fixed isolated package; never the user's ordinary App.
    attempts = [run_adb(adb, ["shell", "am", "force-stop", PACKAGE])]
    client_killed = False
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        process.kill()
        client_killed = True
        process.wait(timeout=5)
        # A start/stop race can leave a remote instrumentation after killing its host client.
        attempts.append(run_adb(adb, ["shell", "am", "force-stop", PACKAGE]))
    named = run_adb(adb, ["shell", "pidof", PACKAGE, PACKAGE + ":ranking"])
    if named["exit"] == 1 and not named["stdout"].strip() and not named["stderr"].strip():
        absent = True
    elif named["exit"] == 0 and named["stdout"].strip():
        absent = False
    else:
        absent = None
    return {"deviceStopCommandExit": attempts[-1]["exit"],
            "deviceStopAttempts": [{"exit": a["exit"], "error": a.get("error")} for a in attempts],
            "hostClientKilled": client_killed, "namedProcessesAbsentAtCheck": absent,
            "scope": "Fixed main/ranking process names checked once; not every isolated renderer or a guarantee against subsequent starts."}


def instrumentation_command(args, adb):
    command = adb + ["shell", "am", "instrument", "-w", "-r"]
    if args.cold:
        command += ["-e", "cold", "true"]
    if args.phase in ("controller-prefetch", "controller-reading"):
        # Existing isolated fixture, ordinary foreground controller and real models.
        # Fixed scope: synthetic cached prose, not live network acquisition/energy.
        command += ["-e", "phase", "energy-replay" if args.phase == "controller-reading" else "energy-setup", "-e", "stages", "DUAL",
                    "-e", "cache", "cold", "-e", "activePrefetch", "true",
                    "-e", "autonomous", "true", "-e", "verifyPrefetchDiskReuse", "true",
                    "-e", "fixture", "energy-replay-100-v2.json"]
        if args.phase == "controller-reading":
            command += ["-e", "replaySeconds", "60"]
    elif args.phase != "models":
        command += ["-e", "phase", args.phase]
    if args.phase == "foreground-models":
        command += ["-e", "modelRounds", str(args.model_rounds), "-e", "modelHoldMs", str(args.model_hold_ms), "-e", "modelInput", args.model_input]
    return command + [PACKAGE + ".test/app.quietreader.ReleaseDiscoveryInstrumentation"]


def main(args):
    if args.model_input not in ("short", "boundary") or args.model_input != "short" and args.phase != "foreground-models":
        raise SystemExit("Boundary model inputs require isolated foreground-models phase")
    if not 1 <= args.model_rounds <= 4 or not 0 <= args.model_hold_ms <= 2000:
        raise SystemExit("Model sampling requires 1..4 rounds and 0..2000ms hold")
    if args.phase != "foreground-models" and (args.model_rounds != 1 or args.model_hold_ms != 0):
        raise SystemExit("Model sampling options require isolated foreground-models phase")
    if not math.isfinite(args.timeout) or args.timeout <= 0:
        raise SystemExit("Timeout must be finite and positive")
    if not math.isfinite(args.environment_interval) or args.environment_interval < 5:
        raise SystemExit("Environment sampling interval must be finite and at least five seconds")
    prefix = args.output
    prefix.parent.mkdir(parents=True, exist_ok=True)
    report_path = Path(str(prefix) + "-memory.json")
    log_path = Path(str(prefix) + "-instrumentation.txt")
    raw_path = Path(str(prefix) + "-samples.ndjson")
    if any(p.exists() for p in (report_path, log_path, raw_path)):
        raise SystemExit("Choose a fresh output prefix; evidence is never overwritten")
    adb = [str(args.adb), "-s", args.serial]
    clock_result = run_adb(adb, ["shell", "getconf", "CLK_TCK"])
    ticks = clock_result["stdout"].strip()
    ticks = int(ticks) if clock_result["exit"] == 0 and ticks.isdigit() and int(ticks) > 0 else None
    before = battery_snapshot(adb)
    thermal_before = run_adb(adb, ["shell", "dumpsys", "thermalservice"])
    command = instrumentation_command(args, adb)
    samples, changes = [], []
    previous = set()
    aborted, stop = None, None
    next_environment = args.environment_interval
    started = time.monotonic()
    print("Starting isolated device probe; diagnostic sampling is not a RAM/energy certification.", flush=True)
    with log_path.open("x", encoding="utf-8") as output, raw_path.open("x", encoding="utf-8") as raw:
        process = subprocess.Popen(command, stdout=output, stderr=subprocess.STDOUT, creationflags=FLAGS)
        try:
            while process.poll() is None:
                if time.monotonic() - started >= args.timeout:
                    aborted = "workload_timeout"
                    break
                sample_started = time.monotonic()
                processes_before, memory, processes_after, attribution = memory_snapshot(adb)
                rows = parse_pss(memory["stdout"])
                pids = sorted({row["pid"] for row in rows})
                cpu = run_adb(adb, ["shell", "cat"] + ["/proc/" + str(pid) + "/stat" for pid in pids]) if pids else {"exit": None, "stdout": "", "stderr": "", "error": "no_processes"}
                cpu_rows = parse_proc_stats(cpu["stdout"])
                status = run_adb(adb, ["shell", "cat"] + ["/proc/" + str(pid) + "/status" for pid in pids]) if pids else {"exit": None, "stdout": "", "stderr": "", "error": "no_processes"}
                status_rows = parse_proc_status(status["stdout"])
                for row in rows:
                    row["cpu"] = cpu_rows.get(row["pid"])
                    row["procStatus"] = status_rows.get(row["pid"])
                current = {(r["pid"], r["name"]) for r in rows}
                elapsed = round(sample_started - started, 3)
                if current != previous:
                    changes.append({"elapsedSeconds": elapsed, "added": sorted(current - previous),
                                    "removed": sorted(previous - current)})
                    previous = current
                sample = {"elapsedSeconds": elapsed, "querySeconds": round(time.monotonic() - sample_started, 3),
                          "processes": rows, "commandExit": memory["exit"], "cpuCommandExit": cpu["exit"],
                          "namedPssKiB": named_pss(rows, memory["exit"]), "attribution": attribution}
                if memory.get("error"):
                    sample["error"] = memory["error"]
                environment = None
                if time.monotonic() - started >= next_environment:
                    battery = battery_snapshot(adb)
                    thermal = run_adb(adb, ["shell", "dumpsys", "thermalservice"])
                    sample["battery"] = battery["values"]
                    sample["thermalStatus"] = thermal_status(thermal)
                    environment = {"battery": battery["read"], "thermal": thermal}
                    next_environment = time.monotonic() - started + args.environment_interval
                sample["querySeconds"] = round(time.monotonic() - sample_started, 3)
                samples.append(sample)
                raw.write(json.dumps({"elapsedSeconds": elapsed, "processesBefore": processes_before,
                                      "meminfo": memory, "processesAfter": processes_after,
                                      "procStat": cpu, "procStatus": status, "environment": environment}) + "\n")
                raw.flush()
                # Interval is the delay AFTER queries, not a promised sampling frequency.
                if process.poll() is None:
                    time.sleep(args.interval)
        except KeyboardInterrupt:
            aborted = "user_interrupted"
        except BaseException:
            stop = stop_probe(adb, process) if process.poll() is None else None
            raise
        finally:
            if aborted and process.poll() is None:
                stop = stop_probe(adb, process)
        exit_code = process.wait()
    after = battery_snapshot(adb)
    thermal_after = run_adb(adb, ["shell", "dumpsys", "thermalservice"])
    summary = instrumentation_summary(log_path.read_text(encoding="utf-8"), exit_code, bool(aborted))
    totals = [s["namedPssKiB"] for s in samples if s["namedPssKiB"] is not None]
    usable = bool(totals) and max(totals) > 0
    attributed = [s["attribution"]["pssKiB"] for s in samples if s["attribution"]["complete"]]
    battery_values = [before["values"]] + [s["battery"] for s in samples if "battery" in s] + [after["values"]]
    temperatures = [b["temperatureC"] for b in battery_values if b.get("temperatureC") is not None]
    heat = [thermal_status(thermal_before)] + [s["thermalStatus"] for s in samples if "thermalStatus" in s] + [thermal_status(thermal_after)]
    heat = [h for h in heat if h is not None]
    report = {"schema": 3, "package": PACKAGE, "phase": args.phase, "coldRequested": args.cold,
              "modelRounds": args.model_rounds, "modelHoldMs": args.model_hold_ms, "modelInput": args.model_input,
              "timeoutSeconds": args.timeout, "timeoutMeaning": "checked between bounded ADB query rounds; not a hard real-time deadline",
              "rssScope": "VmHWM is since process birth and may predate this run; per-process high-water values are not simultaneous and must not be summed",
              "intervalSeconds": args.interval, "intervalMeaning": "delay after queries; inspect querySeconds and actual timestamps",
              "selection": "dumpsys meminfo --package", "clockTicksPerSecond": ticks,
              "elapsedSeconds": round(time.monotonic() - started, 3), "instrumentationCommandExit": exit_code,
              "instrumentation": summary, "aborted": aborted, "abortStopAttempt": stop,
              "scope": "Periodic PSS of processes returned for the named package. Isolated processes and between-sample peaks may be missing. CPU counters may be denied; PID plus startedTicks identifies a lifetime. Not whole-App RAM or energy certification.",
              "wholeAppPeakVerified": False, "energyComparisonVerified": False,
              "environmentIntervalSeconds": args.environment_interval,
              "maximumObservedBatteryTemperatureC": max(temperatures, default=None),
              "maximumObservedThermalStatus": max(heat, default=None),
              "chargingObserved": any(b.get("status") == 2 or any(b.get(k) is True for k in ("acPowered", "usbPowered", "wirelessPowered", "dockPowered")) for b in battery_values),
              "samples": samples, "processSetChanges": changes,
              "completeNamedSamples": len(totals), "incompleteNamedSamples": len(samples) - len(totals),
              "sampledNamedPssPeakKiB": max(totals, default=None), "memoryEvidenceAvailable": usable,
              "completeAttributedSamples": len(attributed), "incompleteAttributedSamples": len(samples) - len(attributed),
              "sampledAttributedPssPeakKiB": max(attributed, default=None), "attributedEvidenceAvailable": bool(attributed),
              "batteryBefore": before, "batteryAfter": after,
              "batteryEndpoints": battery_endpoints(before["values"], after["values"]),
              "thermalBefore": {"status": thermal_status(thermal_before), "read": thermal_before},
              "thermalAfter": {"status": thermal_status(thermal_after), "read": thermal_after}}
    with report_path.open("x", encoding="utf-8") as output:
        json.dump(report, output, indent=2)
        output.write("\n")
    print(json.dumps({**summary, "samples": len(samples), "completeNamedSamples": len(totals),
                      "sampledNamedPssPeakKiB": report["sampledNamedPssPeakKiB"],
                      "memoryEvidenceAvailable": usable, "aborted": aborted}), flush=True)
    if aborted == "user_interrupted":
        raise SystemExit(130)
    if not summary["passed"]:
        raise SystemExit(1)
    if not usable:
        raise SystemExit(2)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", type=Path, required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--phase", choices=["models", "foreground-models", "controller-prefetch", "controller-reading", "reading-flow", "reading-core"], default="models")
    parser.add_argument("--cold", action="store_true")
    parser.add_argument("--model-input", choices=["short", "boundary"], default="short")
    parser.add_argument("--model-rounds", type=int, default=1)
    parser.add_argument("--model-hold-ms", type=int, default=0)
    parser.add_argument("--interval", type=float, choices=[0.5, 1.0, 2.0, 5.0, 10.0, 30.0], default=2.0)
    parser.add_argument("--timeout", type=float, default=900)
    parser.add_argument("--environment-interval", type=float, default=15)
    try:
        main(parser.parse_args())
    except OSError as error:
        raise SystemExit("Probe I/O failed: " + type(error).__name__)
