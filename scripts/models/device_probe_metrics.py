"""Pure parsers for Android diagnostic output; missing data stays unknown."""
import re

HEADER = re.compile(r"\*\* MEMINFO in pid (\d+) \[([^\]]+)\] \*\*")


def _number(text, pattern):
    match = re.search(pattern, text, re.M)
    return int(match.group(1)) if match else None


def parse_pss(text):
    matches = list(HEADER.finditer(text))
    rows = []
    for index, match in enumerate(matches):
        section = text[match.end():matches[index + 1].start() if index + 1 < len(matches) else len(text)]
        total = _number(section, r"TOTAL PSS:\s*(\d+)")
        if total is None:
            total = _number(section, r"^\s*TOTAL\s+(\d+)\s")
        rows.append({"pid": int(match.group(1)), "name": match.group(2), "pssKiB": total,
                     "rssKiB": _number(section, r"TOTAL RSS:\s*(\d+)"),
                     "javaHeapPssKiB": _number(section, r"^\s*Java Heap:\s*(\d+)"),
                     "nativeHeapPssKiB": _number(section, r"^\s*Native Heap:\s*(\d+)")})
    return rows


def named_pss(rows, command_exit):
    pids = [row["pid"] for row in rows]
    if command_exit != 0 or not rows or len(pids) != len(set(pids)):
        return None
    if any(row["pssKiB"] is None for row in rows):
        return None
    return sum(row["pssKiB"] for row in rows)


def parse_package_processes(text, package):
    """Exact packageList membership, including isolated-UID WebView services.

    Only the bounded 'All known processes' section is authoritative. Later
    service references and PID mappings cannot invent additional membership.
    startSeq distinguishes a reused PID within the same system-server lifetime.
    """
    start = re.search(r'^\s*All known processes:\s*$', text, re.M)
    end = re.search(r'^\s*OOM levels:\s*$', text, re.M)
    invalid = {'available': False, 'processes': []}
    if 'ACTIVITY MANAGER RUNNING PROCESSES' not in text or not start or not end or end.start() <= start.end():
        return invalid
    section = text[start.end():end.start()]
    blocks = re.split(r'(?=^\s*\*APP\* UID )', section, flags=re.M)
    rows, seen = [], set()
    for block in blocks:
        if '*APP* UID ' not in block:
            continue
        header = re.search(r'^\s*\*APP\* UID (\d+) ProcessRecord\{\S+ (\d+):([^/\s}]+)/[^\s}]+\}', block, re.M)
        members = re.search(r'^\s*packageList=\{([^}\n]*)\}\s*$', block, re.M)
        sequence = _number(block, r'^\s*startSeq=(\d+)\s*$')
        if not header or not members or not sequence:
            return invalid
        pid = int(header.group(2))
        if pid <= 0 or pid in seen:
            return invalid
        seen.add(pid)
        if package not in {name.strip() for name in members.group(1).split(',')}:
            continue
        rows.append({'pid': pid, 'name': header.group(3), 'uid': int(header.group(1)), 'startSeq': sequence})
    return {'available': True, 'processes': rows}


def attributed_pss(before, memory, after, package):
    """Validate bracketing membership and complete PSS, never a continuous peak."""
    def valid(read):
        return read.get('exit') == 0 and read.get('truncated', False) is False and 'error' not in read

    first = parse_package_processes(before.get('stdout', ''), package) if valid(before) else {'available': False, 'processes': []}
    last = parse_package_processes(after.get('stdout', ''), package) if valid(after) else {'available': False, 'processes': []}
    identity = lambda rows: {(r['pid'], r['name'], r['uid'], r['startSeq']) for r in rows}
    stable = first['available'] and last['available'] and bool(first['processes']) and identity(first['processes']) == identity(last['processes'])
    rows = parse_pss(memory.get('stdout', ''))
    expected = {(r['pid'], r['name']) for r in first['processes']}
    measured = {(r['pid'], r['name']) for r in rows}
    total = named_pss(rows, 0 if valid(memory) else 1)
    complete = stable and any(r['name'] == package for r in first['processes']) and measured == expected and total is not None and total > 0
    reasons = []
    if not complete:
        for label, read in (('before', before), ('memory', memory), ('after', after)):
            if not valid(read):
                reasons.append(label + '_read_unavailable')
        for label, membership in (('before', first), ('after', last)):
            if not membership['available']:
                reasons.append(label + '_membership_unavailable')
            elif not any(r['name'] == package for r in membership['processes']):
                reasons.append(label + '_main_missing')
        if first['available'] and last['available'] and identity(first['processes']) != identity(last['processes']):
            reasons.append('membership_changed')
        if expected - measured:
            reasons.append('pss_missing_members')
        if measured - expected:
            reasons.append('pss_unexpected_members')
        if total is None:
            reasons.append('pss_total_unavailable')
        elif total <= 0:
            reasons.append('pss_total_nonpositive')
        if valid(memory) and re.fullmatch(r'\s*No process found for:\s*' + re.escape(package) + r'\s*', memory.get('stdout', '')):
            # A single response never proves that all shared/isolated resources
            # are absent or that normal App idle consumes zero.
            reasons.append('meminfo_no_process_response')
    return {'membershipAvailableBefore': first['available'], 'membershipAvailableAfter': last['available'],
            'membershipStable': bool(stable), 'complete': bool(complete),
            'incompleteReasons': reasons,
            'pssKiB': total if complete else None, 'processes': first['processes'],
            'missingPids': sorted(pid for pid, name in expected - measured),
            'unexpectedPids': sorted(pid for pid, name in measured - expected),
            'wholeAppPeakVerified': False,
            'scope': 'Exact packageList members with stable PID/name/UID/startSeq around sequential PSS. Periodic observation, not simultaneous or between-sample peak; system/GPU shared services may be unassigned.'}


def parse_proc_stats(text):
    result = {}
    for line in text.splitlines():
        match = re.match(r"^(\d+)\s+\((.*)\)\s+(.+)$", line)
        if not match:
            continue
        fields = match.group(3).split()
        try:
            user, system, started = (int(fields[n]) for n in (11, 12, 19))
            if min(user, system, started) < 0:
                continue
        except (IndexError, ValueError):
            continue
        result[int(match.group(1))] = {"comm": match.group(2), "userTicks": user,
                                      "systemTicks": system, "totalTicks": user + system,
                                      "startedTicks": started}
    return result


def parse_battery(text):
    values = {}
    for line in text.splitlines():
        if ":" in line:
            key, value = line.strip().split(":", 1)
            values[key.lower()] = value.strip()
    def integer(key):
        value = values.get(key, "")
        return int(value) if re.fullmatch(r"-?\d+", value) else None
    def boolean(key):
        return {"true": True, "false": False}.get(values.get(key, "").lower())
    temperature = integer("temperature")
    return {"acPowered": boolean("ac powered"), "usbPowered": boolean("usb powered"),
            "wirelessPowered": boolean("wireless powered"), "dockPowered": boolean("dock powered"),
            "status": integer("status"), "levelPercent": integer("level"),
            "chargeCounterUAh": integer("charge counter"), "voltageMv": integer("voltage"),
            "temperatureC": temperature / 10 if temperature is not None else None}


def battery_endpoints(before, after):
    a, b = before.get("chargeCounterUAh"), after.get("chargeCounterUAh")
    counter_known = a is not None and b is not None and a > 0 and b > 0
    def unplugged(snapshot):
        powered = [snapshot.get(k) for k in ("acPowered", "usbPowered", "wirelessPowered", "dockPowered")]
        if True in powered or snapshot.get("status") == 2:
            return False
        if snapshot.get("status") == 3 and all(v is False for v in powered[:3]):
            return True
        return None
    return {"chargeCounterDeltaUAh": a - b if counter_known else None,
            "unpluggedBefore": unplugged(before), "unpluggedAfter": unplugged(after),
            "energyComparisonVerified": False,
            "scope": "Endpoint device battery readings only; charging between endpoints, screen/network conditions and other apps are not controlled. Not App energy use."}


def instrumentation_summary(text, command_exit, aborted=False, *, recorded_result_code=None):
    """Require the Android result, from raw am output or the detached recorder.

    adb can exit zero even when instrumentation is cancelled. Check text alone
    may also survive a crash after the final assertion but before finish().
    """
    matches = re.findall(r"^checks=(\d+) failures=(\d+)\s*$", text, re.M)
    codes = re.findall(r"^INSTRUMENTATION_CODE:\s*(-?\d+)\s*$", text, re.M)
    if recorded_result_code is None:
        result_ok = codes == ['-1']
    else:
        result_ok = type(recorded_result_code) is int and recorded_result_code == -1 and not codes
    failure = re.search(r"INSTRUMENTATION_(?:FAILED|ABORTED)|shortMsg=Process crashed", text)
    checks, failures = (map(int, matches[0]) if len(matches) == 1 else (None, None))
    return {"checks": checks, "failures": failures,
            "passed": command_exit == 0 and result_ok and not aborted and failure is None
                      and checks is not None and checks > 0 and failures == 0}


def parse_proc_status(text):
    rows = {}
    for section in re.split(r"(?=^Name:)", text, flags=re.M):
        pid = _number(section, r"^Pid:\s*(\d+)\s*$")
        if pid is None:
            continue
        rows[pid] = {"rssKiB": _number(section, r"^VmRSS:\s*(\d+)\s+kB\s*$"),
                     "peakRssKiB": _number(section, r"^VmHWM:\s*(\d+)\s+kB\s*$")}
    return rows


def summarize_environment(rows):
    """Keep recorded device battery/thermal context; never label it App energy."""
    batteries, heat = [], []
    for row in rows:
        def valid(read):
            return (read.get('exit') == 0 and read.get('truncated') is False
                    and 'error' not in read)
        battery = row.get('battery', {})
        if valid(battery):
            batteries.append({'elapsedMs': row.get('elapsedMs'), 'values': parse_battery(battery.get('stdout', ''))})
        thermal = row.get('thermal', {})
        if valid(thermal):
            status = _number(thermal.get('stdout', ''), r'^\s*Thermal Status:\s*(\d+)\s*$')
            if status is not None and 0 <= status <= 6:
                heat.append({'elapsedMs': row.get('elapsedMs'), 'status': status})
    endpoints = battery_endpoints(batteries[0]['values'], batteries[-1]['values']) if len(batteries) >= 2 else battery_endpoints({}, {})
    known = [b['values'] for b in batteries]
    temperatures = [b['temperatureC'] for b in known if b.get('temperatureC') is not None]
    charging = any(b.get('status') == 2 or any(b.get(k) is True for k in ('acPowered', 'usbPowered', 'wirelessPowered', 'dockPowered')) for b in known)
    all_unplugged = bool(known) and all(battery_endpoints(b, b)['unpluggedBefore'] is True for b in known)
    return {'batterySamples': batteries, 'thermalSamples': heat, 'batteryEndpoints': endpoints,
            'chargingObserved': True if charging else False if all_unplugged else None,
            'maximumObservedBatteryTemperatureC': max(temperatures, default=None),
            'maximumObservedThermalStatus': max((r['status'] for r in heat), default=None),
            'energyComparisonVerified': False,
            'scope': 'Recorded device battery/thermal checkpoints only. Other apps, charging between samples and sampling overhead are not isolated; not App energy.'}
