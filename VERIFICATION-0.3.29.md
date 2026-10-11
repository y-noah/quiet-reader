# News 0.3.29 — 2026-10-11

Package `app.quietreader`, versionCode 40. Includes the 0.3.28 reading/session fixes.

## Login viewport fix

The explicit source/login WebView now enables native pinch zoom, a wide viewport and overview loading. A separate native row provides 缩小 / 适应屏幕 / 放大. Desktop mode keeps a desktop layout, fitting its width to the visible WebView; the mode preference and ordinary session configuration are unchanged.

SourceViewport adjusts only viewport metadata. A head-only MutationObserver restores zoom permission if the site replaces the viewport during a later modal/SPA update. It does not inspect form values, cookies or account storage. Fitting and zoom controls do not navigate or reload the document. Asynchronous fitting is guarded by the current URL, navigation revision and Activity lifetime.

## Verification

- JVM: 183 tests, zero failures/errors.
- Login zoom: 25 checks, zero failures, on the dedicated API35 emulator. The local synthetic page has a 1280px desktop layout, a centered fixed-width dialog, and a viewport initially disabling zoom. Checks cover initial full-dialog visibility, both native zoom buttons, a real two-pointer pinch on the dialog, retained visibility after pinch, refitting, late site viewport replacement, preserved synthetic form input, no source reload, desktop/mobile switching and Activity recreation.
- Login/session regression: 19 checks, zero failures. Shared synthetic cookie/storage and source user-agent settings still work after adding viewport controls.
- Exact signed/minified 0.3.29 release: in-place installation succeeded; startup, version, aggregate UI and native navigation passed 35 checks.
- Release build and lint passed: zero errors, 19 warnings. `git diff --check` passed.
- Initial and pinched login screenshots were visually inspected. An earlier gesture sample zoomed empty space below a top-positioned test dialog; the final test uses a centered modal, pinches on the dialog and positively verifies it stays visible.

Evidence in local ignored `artifacts/`: `login-zoom-029-final.txt`, `login-zoom-029-final/`, `session-029.txt`, `release-ui-029.txt`, `release-build-029.txt`, `signature-029.txt`.

These are actual Android/WebView interaction checks using a synthetic login layout, not certification of real platform authentication. No real account login was performed. API26 and physical devices were not retested.

## Binary

- `News-0.3.29.apk`: 175422 bytes.
- SHA-256: `15ECCB75395256127F2CF6E7D3EA7F922388232A59ADF8931F8D13A7AEA6AEF5`.
- Certificate SHA-256: `ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37`, unchanged from previous releases.

Use desktop mode on the source screen, then tap 适应屏幕 to see the whole page. Pinch on the login window or use 放大 to enlarge it. The mode button names the mode it will switch to: 手机版 means the page is currently in desktop mode.
