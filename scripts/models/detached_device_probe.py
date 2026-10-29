"""Bounded isolated probe launcher. Default emulator only; no wireless configuration."""
import argparse
import hashlib
import json
import re
import shlex
import subprocess
from pathlib import Path

from device_probe_metrics import parse_pss, named_pss, instrumentation_summary, summarize_environment, attributed_pss


def validate_replay_environment(duration, network):
    if network not in ('uncontrolled', 'offline-cache'):
        raise ValueError('Unknown replay network profile')
    if duration > 300 and network != 'offline-cache':
        raise ValueError('Replay longer than300 seconds requires explicit offline-cache profile; online fixtures expire')


def summarize_operations(state, raw):
    """Reject incomplete, reordered, wrong-mode and duplicated replay evidence."""
    rows = [json.loads(line) for line in raw.splitlines() if line.strip()]
    expected = state.get('measurement') or {}
    identities = [r for r in rows if r.get('kind') == 'identity']
    operations = [r for r in rows if r.get('kind') == 'operation']
    completions = [r for r in rows if r.get('kind') == 'complete']
    identity = identities[0] if len(identities) == 1 else {}
    identity_ok = identity.get('runId') == state['runId'] and all(
        identity.get(key) == expected.get(key) for key in ('stages', 'cache', 'fixtureSha256'))
    duration = expected.get('replaySeconds', 0)
    identity_ok &= duration >= 60 and identity.get('durationSeconds') == duration
    identity_ok &= identity.get('activePrefetchEnabled', False) == expected.get('activePrefetch', False)
    network = expected.get('networkProfile', 'uncontrolled')
    identity_ok &= identity.get('networkProfile', 'uncontrolled') == network
    try:
        validate_replay_environment(duration, network)
        network_ok = network != 'offline-cache' or (
            identity.get('networkAbsentAtStart') is True
            and all(r.get('networkAbsentBefore') is True and r.get('networkAbsentAfter') is True for r in operations))
    except ValueError:
        network_ok = False
    order_ok = [r.get('index') for r in operations] == list(range(100))
    events = [r.get('event') for r in operations]
    timeline_ok = True
    previous_end = 0
    for index, operation in enumerate(operations):
        start, elapsed = operation.get('startedMs', -1), operation.get('durationMs', -1)
        timeline_ok &= (operation.get('scheduledMs') == duration * 1000 * index // 100
                        and start >= max(previous_end, operation.get('scheduledMs', -1)) and elapsed >= 0)
        previous_end = start + elapsed
    completion = completions[0] if len(completions) == 1 else {}
    complete_ok = (completion.get('viewed') == 100 and completion.get('opened') == 6
                   and completion.get('elapsedMs', -1) >= max(duration * 1000, previous_end))
    network_ok &= network != 'offline-cache' or completion.get('networkAbsentAtEnd') is True
    passed = (identity_ok and network_ok and order_ok and timeline_ok and complete_ok
              and len(set(events)) == 100 and all(isinstance(e, str) and e for e in events)
              and all(r.get('passed') is True and r.get('error') == '' for r in operations)
              and len(rows) == 102 and rows[0] == identity and rows[-1] == completion)
    return dict(passed=bool(passed), operationCount=len(operations), identityMatches=bool(identity_ok),
                complete=bool(complete_ok), activePrefetchEnabled=expected.get('activePrefetch', False),
                prefetchWorkload=('configured-but-offline-paused' if network == 'offline-cache' and expected.get('activePrefetch', False) else 'ordinary-online' if expected.get('activePrefetch', False) else 'disabled'),
                networkProfile=network, networkCheckpointsPassed=bool(network_ok) if network == 'offline-cache' else None, continuousNetworkIsolationVerified=False,
                scope='Synthetic disk-cached text; programmatic viewport. Prefetch flag is not cold-download evidence; no login/media/network/energy certification')


def summarize_background(state, raw):
    """Separate actual-HOME model-process quiet checkpoints from foreground replay."""
    rows = [json.loads(line) for line in raw.splitlines() if line.strip()]
    expected = state.get('measurement') or {}
    identity = rows[0] if rows and rows[0].get('kind') == 'identity' else {}
    completion = rows[-1] if rows and rows[-1].get('kind') == 'complete' else {}
    samples = [r for r in rows if r.get('kind') == 'sample']
    duration = expected.get('replaySeconds', 0)
    network = expected.get('networkProfile', 'uncontrolled')
    identity_ok = (identity.get('runId') == state['runId']
                   and all(identity.get(k) == expected.get(k) for k in ('stages', 'cache', 'fixtureSha256'))
                   and identity.get('networkProfile', 'uncontrolled') == network
                   and network in ('uncontrolled', 'offline-cache')
                   and identity.get('activePrefetchEnabled', False) == expected.get('activePrefetch', False)
                   and 60 <= duration <= 3000 and identity.get('quietSeconds') == duration
                   and identity.get('pollIntervalMs') == 2000
                   and identity.get('activityStopped') is True and identity.get('graceMs') == 10000
                   and 0 <= identity.get('observedStopMs', -1) <= 5000)
    count = duration * 1000 // 2000 + 1
    timeline_ok = len(samples) == count and [r.get('index') for r in samples] == list(range(count))
    previous = 0
    quiet = True
    for index, row in enumerate(samples):
        elapsed = row.get('elapsedMs', -1)
        timeline_ok &= row.get('scheduledMs') == index * 2000 and elapsed >= max(previous, index * 2000)
        previous = elapsed
        quiet &= (row.get('mainPresent') is True and row.get('rankingPids') == []
                  and row.get('returnedForeground') is False and row.get('quiet') is True
                  and (network != 'offline-cache' or row.get('networkAbsent') is True))
    complete = (completion.get('samples') == count and completion.get('quiet') is True
                and completion.get('elapsedMs', -1) >= max(duration * 1000, previous))
    passed = identity_ok and timeline_ok and quiet and complete and len(rows) == count + 2
    return dict(passed=bool(passed), identityMatches=bool(identity_ok), checkpointCount=len(samples),
                complete=bool(complete), networkProfile=network,
                modelProcessAbsentAtCheckpoints=bool(quiet and samples),
                continuousBackgroundQuietVerified=False, energyComparisonVerified=False,
                scope='Actual HOME/onStop; two-second model-process checks after ten-second grace. Not screen-off, wakeup trace, between-checkpoint activity or energy proof.')


def summarize(state, raw):
    rows = [json.loads(line) for line in raw.splitlines() if line.strip()]
    samples = []
    for row in rows:
        read = row.get('meminfo', {})
        processes = parse_pss(read.get('stdout', ''))
        valid_read = read.get('exit') == 0 and not read.get('truncated', False) and 'error' not in read
        total = named_pss(processes, 0 if valid_read else 1)
        attribution = attributed_pss(row.get('processesBefore', {}), read, row.get('processesAfter', {}), PACKAGE)
        samples.append({'elapsedMs': row.get('elapsedMs'), 'namedPssKiB': total, 'processes': processes, 'attribution': attribution})
    checks = instrumentation_summary(state.get('stream', ''), 0 if state.get('resultCode') == -1 else 1,
                                     state.get('state') != 'COMPLETE', recorded_result_code=state.get('resultCode'))
    counts_match = len(rows) == state.get('samples')
    totals = [s['namedPssKiB'] for s in samples if s['namedPssKiB'] is not None]
    attributed = [s['attribution']['pssKiB'] for s in samples if s['attribution']['complete']]
    return {'schema': 2, 'runId': state['runId'], 'phase': state['phase'], 'measurement': state.get('measurement'),
            'functionalPassed': checks['passed'] and state.get('passed') is True,
            'recordCountMatches': counts_match, 'rawRecords': len(rows), 'completeNamedSamples': len(totals),
            'sampledNamedPssPeakKiB': max(totals, default=None),
            'completeAttributedSamples': len(attributed), 'sampledAttributedPssPeakKiB': max(attributed, default=None),
            'attributedEvidenceAvailable': counts_match and bool(attributed),
            'memoryEvidenceAvailable': counts_match and any(t > 0 for t in totals),
            'wholeAppPeakVerified': False, 'energyComparisonVerified': False,
            'environment': summarize_environment(rows),
            'scope': 'Periodic package-selected PSS only. Missing processes and between-sample peaks unknown; not RAM or energy certification.',
            'samples': samples}


PACKAGE = 'app.quietreader.flowprobe'
COMPONENT = PACKAGE + '.test/app.quietreader.ReleaseDiscoveryInstrumentation'


def watcher_live(expected, current):
    # /system/bin/am execs app_process without changing PID/start ticks. The
    # executable prefix may change during launch; the instrumentation target may not.
    return bool(expected and current
                and (expected.get('pid'), expected.get('startTicks')) ==
                    (current.get('pid'), current.get('startTicks'))
                and COMPONENT in expected.get('cmdline', '')
                and COMPONENT in current.get('cmdline', ''))


def valid_id(value):
    if not re.fullmatch(r'[a-z0-9][a-z0-9-]{0,39}', value):
        raise argparse.ArgumentTypeError('Invalid run identifier')
    return value


def main():
    p = argparse.ArgumentParser(__doc__)
    p.add_argument('action', choices=['start', 'status', 'collect'])
    p.add_argument('--adb', required=True)
    p.add_argument('--serial', default='emulator-5560')
    p.add_argument('--allow-phone', action='store_true')
    p.add_argument('--run', required=True, type=valid_id)
    p.add_argument('--phase', choices=['reading-core', 'models', 'control-idle', 'energy-setup', 'energy-replay', 'energy-background'], default='reading-core')
    p.add_argument('--stages', choices=['NONE', 'EMBEDDING', 'DUAL'], default='DUAL')
    p.add_argument('--cache', choices=['cold', 'warm'], default='cold')
    p.add_argument('--fixture', choices=['energy-replay-100.json', 'energy-replay-100-v2.json'], default='energy-replay-100-v2.json')
    p.add_argument('--replay-seconds', type=int, default=1800)
    p.add_argument('--active-prefetch', action='store_true', help='Enable ordinary foreground disk-cache warming; not cold-download certification')
    p.add_argument('--network', choices=['uncontrolled', 'offline-cache'], default='uncontrolled', help='Requires already disconnected target; never changes radios')
    p.add_argument('--timeout', type=int, default=300)
    p.add_argument('--interval', type=int, default=5)
    p.add_argument('--evidence', required=True, type=Path)
    a = p.parse_args()
    if not a.serial.startswith('emulator-') and not a.allow_phone:
        p.error('Physical device requires explicit --allow-phone')
    if not 1 <= a.timeout <= 3600 or not 2 <= a.interval <= 30:
        p.error('Invalid timeout or interval')
    if a.action == 'start' and a.phase.startswith('energy-'):
        if not 60 <= a.replay_seconds <= 3000:
            p.error('Invalid replay duration')
        if a.phase in ('energy-replay', 'energy-background') and a.timeout < a.replay_seconds + 240:
            p.error('Replay timeout must include240 seconds preparation margin')
    if a.action == 'start' and a.phase == 'energy-replay':
        try:
            validate_replay_environment(a.replay_seconds, a.network)
        except ValueError as error:
            p.error(str(error))
    adb = [a.adb, '-s', a.serial]

    def shell(command):
        r = subprocess.run(adb + ['shell', command], capture_output=True, text=True, encoding="utf-8", errors="strict", timeout=25)
        if r.returncode:
            raise RuntimeError('Device query failed; run state is unknown')
        return r.stdout.strip()

    def identity(pid):
        # Identity checks must include start ticks: PIDs alone can be reused.
        raw = shell(f'cat /proc/{pid}/stat 2>/dev/null || true')
        tail = raw[raw.rfind(")") + 2:].split()
        if len(tail) < 20:
            return None
        cmd = shell(f'cat /proc/{pid}/cmdline 2>/dev/null || true').replace('\x00', ' ')
        return {'pid': pid, 'startTicks': tail[19], 'cmdline': cmd}

    a.evidence.mkdir(parents=True, exist_ok=True)
    handle = a.evidence / (a.run + '-handle.json')
    remote = '/sdcard/Android/data/' + PACKAGE + '/files/device-run-' + a.run
    if a.action == 'start':
        measurement = None
        if a.phase.startswith('energy-'):
            fixture = Path(__file__).resolve().parents[2] / 'app/src/androidTest/assets' / a.fixture
            measurement = dict(stages=a.stages, cache=a.cache, fixture=a.fixture, replaySeconds=a.replay_seconds,
                               activePrefetch=a.active_prefetch, networkProfile=a.network,
                               fixtureSha256=hashlib.sha256(fixture.read_bytes()).hexdigest())
        processes = shell('ps -A -o PID,ARGS')
        if any(COMPONENT in line for line in processes.splitlines()):
            raise RuntimeError('An isolated instrumentation watcher already exists; inspect it instead')
        # Reserve locally before launching. Unknown launch results must never auto-retry.
        with handle.open('x', encoding='utf-8') as f:
            json.dump({'runId': a.run, 'serial': a.serial, 'phase': a.phase, 'measurement': measurement, 'launchState': 'UNKNOWN'}, f)
        shell('mkdir -p ' + shlex.quote(remote.rsplit('/', 1)[0]))
        exists = shell('for f in ' + shlex.quote(remote) + '*; do [ ! -e "$f" ] || echo EXISTS; done')
        if exists:
            raise RuntimeError('Run already has device evidence')
        args = ['am', 'instrument', '-w', '-e', 'detachedRun', a.run, '-e', 'phase', a.phase,
                '-e', 'cancelNotification', 'true', '-e', 'runTimeoutSeconds', str(a.timeout), '-e', 'sampleIntervalSeconds', str(a.interval)]
        if measurement:
            for key, value in measurement.items():
                args += ['-e', key, str(value).lower() if isinstance(value, bool) else str(value)]
        command = 'set -C; exec ' + shlex.join(args) + ' -e watcherPid "$$" ' + shlex.quote(COMPONENT) + ' >' + shlex.quote(remote + '-watcher.txt') + ' 2>&1'
        launch = 'nohup sh -c ' + shlex.quote(command) + ' </dev/null >/dev/null 2>&1 & echo $!'
        pid = shell(launch)
        if not pid.isdigit():
            raise RuntimeError('Launch identity unavailable; do not retry this run')
        current = identity(int(pid))
        record = {'runId': a.run, 'serial': a.serial, 'phase': a.phase, 'measurement': measurement, 'watcher': current,
                  'launchState': 'STARTED' if current and COMPONENT in current['cmdline'] else 'UNKNOWN'}
        handle.write_text(json.dumps(record, indent=2), encoding='utf-8')
        print(json.dumps(record))
        return
    record = json.loads(handle.read_text(encoding='utf-8'))
    if (record['runId'], record['serial'], record['phase']) != (a.run, a.serial, a.phase):
        raise RuntimeError('Handle identity mismatch')
    raw = shell('cat ' + shlex.quote(remote + '-state.json') + ' 2>/dev/null')
    state = json.loads(raw)
    if (state['runId'], state['phase'], state['package']) != (a.run, a.phase, PACKAGE):
        raise RuntimeError('Device state identity mismatch')
    if state.get('measurement') != record.get('measurement'):
        raise RuntimeError('Device measurement identity mismatch')
    watcher = record.get('watcher')
    now = identity(watcher['pid']) if watcher else None
    live = watcher_live(watcher, now)
    if a.action == 'collect':
        if state['state'] not in ('COMPLETE', 'TIMED_OUT', 'CANCELLED'):
            raise RuntimeError('Run has no terminal evidence yet')
        watcher_output = shell('cat ' + shlex.quote(remote + '-watcher.txt') + ' 2>/dev/null || true')
        samples = shell('cat ' + shlex.quote(remote + '-samples.ndjson') + ' 2>/dev/null')
        report = summarize(state, samples)
        if state.get('stallsFile'):
            stalls = shell('cat ' + shlex.quote(remote + '-stalls.ndjson') + ' 2>/dev/null')
            with (a.evidence / (a.run + '-stalls.ndjson')).open('x', encoding='utf-8') as f:
                f.write(stalls)
        if a.phase == 'energy-replay':
            operations = shell('cat ' + shlex.quote(remote + '-operations.ndjson') + ' 2>/dev/null || true')
            report['readingReplay'] = summarize_operations(state, operations)
            report['functionalPassed'] &= report['readingReplay']['passed']
            with (a.evidence / (a.run + '-operations.ndjson')).open('x', encoding='utf-8') as f:
                f.write(operations + '\n')
        if a.phase == 'energy-background':
            background = shell('cat ' + shlex.quote(remote + '-background.ndjson') + ' 2>/dev/null || true')
            report['backgroundQuiet'] = summarize_background(state, background)
            report['functionalPassed'] &= report['backgroundQuiet']['passed']
            with (a.evidence / (a.run + '-background.ndjson')).open('x', encoding='utf-8') as f:
                f.write(background + '\n')
        # Never overwrite a prior collection; retain the exact raw records.
        with (a.evidence / (a.run + '-state.json')).open('x', encoding='utf-8') as f:
            f.write(raw + '\n')
        with (a.evidence / (a.run + '-samples.ndjson')).open('x', encoding='utf-8') as f:
            f.write(samples + '\n')
        with (a.evidence / (a.run + '-watcher.txt')).open('x', encoding='utf-8') as f:
            f.write(watcher_output + '\n')
        with (a.evidence / (a.run + '-resources.json')).open('x', encoding='utf-8') as f:
            json.dump(report, f, indent=2)
    print(json.dumps({'state': state, 'watcherIdentityVerifiedLive': live}))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Exception strings can include private workspace paths; keep console output bounded.
        print('Probe operation failed: ' + type(error).__name__ + '; inspect retained evidence; do not auto-restart.')
        raise SystemExit(1)
