# Reading reliability campaign — completed with scoped live verification

## Scope

The user explicitly restricted the 1,000-round campaign to local automation/emulators. The phone is only for a few key checks. The test driver rejects any package other than `app.quietreader.discoveryprobe` and any hardware other than the emulator (`ranchu`). No bulk phone campaign has run. Existing unit-test assertion counts are excluded from round totals.

## Evidence so far

Artifacts: `artifacts/local-ai/reliability-1000-20261031/`.

* `baseline-rounds.jsonl`, `baseline-test.log`: three independent controlled-acquisition scenarios failed before the fix: offline recovery remained blocked for five minutes; transient failure never retried; reserved work notified its old home observer.
* `retry-160-rounds.jsonl`, `retry-160.log`, `retry-160-candidate.json`: 160 completed emulator rounds, zero failures, eight scenario families. These exercise real ArticlePreloader/Repository cache and callback ownership with controlled acquisition results. They are **not** 160 live-source reads or proof of real login permissions.
* `history-permission-baseline.xml`: an additional unit regression reproduced login/error/video flags being dropped by historical-body serialization. This assertion is not counted as a campaign round.
* `retry-180-rounds.jsonl`: 180 rounds passed after adding the stopped-action-window guard and history fix. Nine scenario families.
* `storage-240-rounds.jsonl`: 240 rounds passed across 12 physical storage/history fault-and-recovery scenarios, with varying body structure and fresh identities. Includes corrupt files, insufficient quota preserving the previous body, old account/namespace/version, expired/future timestamps, restricted previews and legacy history migration.
* `scope-baseline-rounds.jsonl`: one further failed reproduction: an obsolete acquisition notified the current-scope failure observer. Fixed by dropping its attempt marker without marking the current account unavailable.
* `retry-200-rounds.jsonl`: 200 rounds passed across ten retry/ownership scenario families, including that obsolete-scope regression.
* `dynamic-220-rounds.jsonl`: 220 rounds passed across 11 real WebView extraction/callback scenarios with intercepted HTML and controlled HTTP errors, including cancellation, ownership gaps, retained snapshots, namespace changes, deadline fallback and 429/503/408 classification. This does not simulate every actual platform challenge.
* `campaign-summary.json`: verifies **1,000 completed passing scenario rounds**, zero bulk rounds on the phone. The four failed baseline rounds and unit assertions are separate. These rounds span successive repair candidates with their hashes recorded; they are not 1,000 live-source reads or 1,000 rounds on one final binary.

## Implemented changes under verification

Typed acquisition failures preserve HTTP status and Retry-After through Repository, PendingArticle, DynamicReader and the preloader. A transient speculative failure gets at most one delayed retry, behind unaffected work. Offline failures do not leave an attempt cooldown. Repeated/unknown failures remain bounded. Server cooldowns prevent immediate WebView fallback. Reserved or retained work stops notifying the former home observer. A failed transient promoted request can get one fresh foreground acquisition; it cannot recursively recover through the promotion callback.

Follow-up review added an action-window guard: `finishWindow()` must prevent a failing in-flight request from creating a new retry during scrolling. This addition is absent from the first 160-round APK and passed in the subsequent 180- and 200-round candidates.

Reading history now preserves restriction flags in its codec, refuses restricted snapshots and rejects old history bodies whose format could have dropped permission flags. Position/expanded-state markers remain. Disk admission, snapshot copying and readback also preserve/reject these flags. The full 928-test unit suite passed; assertions are excluded from campaign totals.

## Remaining work

The final isolated integration regression passed: history selection, scoped account cooldown, source-login import, fresh presentation/reopen stability and failed future-slot replacement. The first phone check then **failed** on a real Hupu article. This is why the round count is not a release decision.

`phone-key-reads.log` records only four actual opens: Baidu (4,851 ms, 31 sections), Zhihu (4,473 ms, five sections), and a Hupu article plus one reopen, both with no body. The Hupu screen said it could not obtain identity/page-matched body state. No taste feedback was created. The 55-second times are the test's observation timeout, not evidence that the App was still performing a network request throughout. Candidate identity is preserved in `phone-candidate1.json`.

## Actual phone defect and final correction

`phone-failed-hupu.log` identified public thread `https://bbs.hupu.com/643478433.html`. The real source reported the matching ID/page, status 0, ALL_SEE, a signed-in viewer boolean and 20 replies. The main content was `<p></p>`; the website itself had no main prose or main images. Public unauthenticated HTML independently confirmed the empty opening post. No cookies or account identifiers were exported from the phone.

HupuDiscussion previously rejected the entire thread as soon as its main post produced no block, discarding otherwise valid comments. It now keeps the identity-matched discussion and only rejects a genuinely empty result at the final content check. Existing identity/audience validation remains in place. SourceSession also tries the shared browser route when the native Hupu result is unavailable, instead of treating the error shell as completed content. Two new unit cases cover an empty main with valid replies, wrong identity and a genuinely empty thread. The final full unit suite passed **930 tests**.

`phone-failed-hupu-recheck.log`: the exact failed thread subsequently loaded **18 sections / 3,806 characters in 757 ms**, then reopened from prepared content in **649 ms**. Both checks passed, and preference feedback count remained unchanged.

`phone-zhihu-final.log`: the source showed profile evidence and no login gate. The reader contained the source question supplement and the complete same-ID first expanded text answer (1,388 normalized characters), with five answer sections and no horizontal overflow. All 13 checks passed. This verifies that real sample; it does not certify every private/audience-gated permission on the platform.

After uninstalling the test driver, an ordinary forced-stop/cold launch showed a populated new batch; reported Activity launch time was 338 ms (not a measurement of all content readiness). One ordinary dot refresh replaced the visible topics and no longer exposed the refreshing indicator. XML evidence: `phone-final-home.xml`, `phone-final-refreshed.xml`. Main-process PSS snapshots were approximately 133 MiB during a reader and 321 MiB after refresh; these are **not** total-App peak RAM or energy measurements.

## Handoff

Phone installed APK SHA-256 matched `6fd1933b561f96cae2d3dd58f6cf86ab2872211bd5087d7e92041839daa4f2c6` (version 1.0.1/code 50). The final Hupu parsing change came after the 1,000-round campaign and was checked by the full unit suite and the original live failure; do not represent all 1,000 rounds as having run on this final binary.

`app.quietreader.test` was uninstalled; only the ordinary App remained under the package prefix. This campaign's SD-card XML/PNG and phone report were removed after local capture. Screen timeout was restored to 30 seconds, test execution stopped and the screen turned off. The emulator started for the campaign was shut down. Accounts, user preferences and the normal App's data were preserved. A copy of the verified APK is in the safe `New-next-trial` output folder. No GitHub push or public release was performed.
