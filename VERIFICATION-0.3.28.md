# News 0.3.28 — 2026-10-11

Package `app.quietreader`, versionCode 39. Based on upstream main `118c775`.

## Changes

- Before sampling a Zhihu source document, activate the known question-detail / answer full-text controls. Wait while those controls are loading; only then parse and stabilize extracted content. The explicit source-screen read action also waits and cancels obsolete work on navigation or destruction.
- Persist each source's desktop/mobile selection. Use it for HTTP board/article requests, dynamic boards/readers and fresh Zhihu continuation pages. Reusing an existing question WebView preserves its user agent. Sources whose web view has been visited prefer WebView article extraction, keeping ordinary cookies and origin storage in the shared system WebView profile. This preference records a visited source, not a claim of successful login.
- A fallback Zhihu answer API 401/403 no longer immediately terminates the source-page reading flow. The page can still provide answers via its own normal loading. Visible login gates remain login restrictions; a rejected API with no later content is reported separately, preserving prior answers.
- Remove the aggregate board's explanation button and subtitle. Ranking, quotas and the compact cache/source count next to the heading remain.

## Verification performed

On the existing dedicated API35 `quiet-reader-test` emulator:

| Check | Result |
|---|---:|
| JVM regression | 183 tests, 0 failures/errors |
| Dynamic source extraction | 43 checks, 0 failures |
| Synthetic login/session reuse | 19 checks, 0 failures |
| Zhihu answer paging | 38 checks, 0 failures |
| Reader/navigation journey | 33 checks, 0 failures |
| Exact signed and minified release UI | 35 checks, 0 failures |

The new extraction fixture delays the full question supplement for 2.6 seconds after its real source control is clicked. Tests require the final paragraph in both the extracted document and the expanded reader HTML. Existing answer stability and visible-login checks also pass.

The session test uses the actual LoginActivity mode control and a synthetic cookie/storage marker, then reads that marker through a fresh production DynamicReader. It checks all five sources' user-agent and third-party-cookie settings and preservation of an adopted source page's user agent. Only test-owned markers/preferences are cleaned up; no account cookies are exported or cleared.

The new paging fixture returns HTTP403 to the fallback XHR, then supplies a later answer through the source DOM. Both answers reach the production callback. A separate 403/no-new-content case preserves the earlier answer without falsely asserting logout.

The user journey verifies section expansion, old answer retention, login-restricted retry, no horizontal overflow and paragraph position preservation within 2 CSS pixels. Final release screenshots were inspected for the simplified aggregate board and six native tabs.

Debug/release builds and lint passed. Lint reports 0 errors and 19 warnings (including intentional JavaScript in source WebViews and existing presentation/API warnings). `git diff --check` passed.

Evidence in the local checkout's ignored `artifacts/`: `dynamic-028.txt`, `session-028.txt`, `paging-028.txt`, `journey-028.txt`, `release-ui-028.txt`, `release-build-028-final.txt`, `signature-028.txt`, and corresponding screenshot directories.

## Delivered binary

- `News-0.3.28.apk`: 174286 bytes.
- SHA-256: `AF70CC177244F8730958824335FF11CEF083648B1D49B1F9991E15DB4EF060FE`.
- Certificate SHA-256: `ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37`, matching 0.3.27. In-place installation succeeded on the project emulator.

## Limits

These tests exercise actual Android WebViews and production callbacks with controlled source content. They do not certify a real signed-in Zhihu account or unlimited access to all platform content. No user account credentials were used. API26 and physical devices were not retested. Changed website markup, actual authorization limits and platform throttling can still affect reading.

After upgrading, opening the source screen once records the desired browser mode and refreshes the reader on return. Existing cookies and application data are preserved by an in-place installation.
