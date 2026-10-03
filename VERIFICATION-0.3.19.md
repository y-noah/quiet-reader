# News 0.3.19 — 2026-10-02

## Scope

- Replace the six square-font/rounded-background tabs with custom vector monoline lettermarks. Rounded stroke caps, joins and corners belong to the glyphs themselves; no background tile. Actual character text and full platform accessibility labels are retained.
- Distinct blue/teal/coral/amber/violet/cyan colors, lightened in dark mode. Selected tab uses a slightly heavier stroke and small underline. Six equal 48 dp tap targets remain on one row.
- No new dependencies, font downloads or functionality. Existing article extraction, login, cache and settings behavior are not changed. Fresh launch remains Zhihu.

## Verification

- 166 JVM tests, 0 failures. Signed release build and APK signature verification passed. Lint: 0 errors, 18 warnings; the additional ViewConstructor warning is for this intentionally programmatic, non-XML custom view.
- Dedicated API 35 emulator `quiet-reader-test` / `emulator-5556`, `home-cleanup`: 33 checks passed. Includes all six background-free marks in both themes, actual tab taps, and cached article open/back scroll restoration. Synthetic board preview is explicitly labeled, not a live-platform claim. Main/appearance/board preferences restored.
- Fresh light/dark screenshots visually reviewed: `artifacts/experience-emulator-5556-20261002-210403/run-1790946243221/`.
- Exact signed release `test-release.ps1 -KeepData -Mode branding`: 27 assertions passed. News identity, initial Zhihu, compact home, overflow settings return, six platform taps. Evidence and reviewed release screenshot: `artifacts/release-emulator-5556-20261002-210557/`.
- No physical phone, cookie clearing, real account login, all-platform extraction sweep or Git push.

## Independent user/tester pass

At the user's request, an independent user/tester agent completed one bounded pass on the signed release. No reproducible defect requiring a change was found; no extra feature, production patch or follow-up iteration was added.

- Confirmed fresh Zhihu, all six tabs visible and clickable, correct selection, distinguishable rounded strokes/colors and no background tiles. Dark display was observed in the actual app; light appearance was inspected in this turn's synthetic preview, not claimed as a live-source test.
- Opened the first real Zhihu board entry, read body content, used Next to reach two different authors' answers, then returned to the board.
- Opened a real Wallstreet board article, read its body and returned without crash or persistent loading.
- Opened the existing overflow reading settings and display-mode chooser; no duplicate settings/search UI. Dialog dismissed without changing existing preferences.
- Evidence: `artifacts/user-review-0.3.19/`, especially `01-home.png`, `02-settings.png`, `04-zhihu-reader.png`, `05-next-answer.png`, `07-wallstreet-reader.png`. Primary agent also visually reviewed the latter two reading screenshots.
- Limits: no real-account authentication check, failure-recovery test, measured cold-start latency or all-platform reading sweep. `03-loading.png` actually caught the returned homepage; `06-wallstreet-open.png` captured an opening transition. Neither certifies loading animation behavior.
- Subjective recommendation: current treatment meets the requested rounded glyph/no-tile/distinct-color direction; stop here rather than iterate on further stylistic preferences.

## Delivery

`artifacts/News-0.3.19.apk`, 169726 bytes. SHA256: `FC2BEA52640A29684AD426DFE29406CAC203FDC322041F155937F7296D0AE027`.

Same package/signature for in-place upgrade; old versioned APKs and the stable-0.3.15 fallback remain unchanged.
