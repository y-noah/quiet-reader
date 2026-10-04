# News 0.3.27 — 2026-10-04

`app.quietreader`, versionCode 38, original signing certificate retained.

## Scope

Removed retired source enums, parsers, request branches, domain/login rules, platform-specific DOM handling and glyph paths. Only Zhihu, Weibo, Hupu, CLS and iFanr remain, with the Top50 total tab. Old preference migration uses string namespaces, so removing enum constants does not break cleanup. Unknown stored item sources are ignored individually and the cleaned bookmark list is persisted. Only the historical `tieba-desktop` preference deletion key remains in production code; old version documentation remains historical evidence.

Navigation now uses native regular-weight Chinese glyphs, equal 48 dp touch areas, aligned baselines and existing colors. Default text size 20 dp, system-scale support capped at 1.3x to fit the navigation row. Reader text scaling is unchanged. Selected underline uses viewport coordinates corrected for TextView scrolling.

## Failures found and fixed

Initial screenshots exposed a missing selected underline. An offscreen View.draw check passed despite the screen defect. That was insufficient, so the old 312/0 runs were not accepted as final checks. The cause was TextView's horizontal content scroll (observed scrollX 524198), not the selected state. Rendering now isolates TextView canvas state and compensates scroll coordinates. Tests require exactly the expected selected tab and compare underline pixels from actual screen screenshots.

The release UI harness initially expected an exact standalone Top50 label, but production includes a source-status suffix. Changed the assertion to require the correct title within actual displayed text. No production change was needed for that harness mismatch. Runner scripts now retain instrument exit status and reject crash/abort output, missing completion summaries and missing screenshot paths.

## Validation

- JVM: 183 tests, zero failures/errors. Retired platform-only tests were removed; shared tests now use retained platforms, and iFanr coverage was added.
- Actual Android WebView regression: dynamic reading 36/0, answer paging 33/0, retained reading position and login-boundary journey 32/0. Synthetic source data, not authenticated-source certification.
- Corrected screen-pixel baseline: 330 checks, zero failures.
- After that baseline, two further checks on unchanged production code: 330/0 at normal resolution, then 330/0 at 360x640 dp with 200% system font. Emulator resolution/density/font restored afterward. Independent user reviewer checked dark/light screenshots, source chooser and visible underline; technical reviewer checked code and test evidence.
- Final minified, signed APK installed with `-r` on the project API35 emulator; release UI passed 34 checks. Original signature retained. Release build and lint succeeded (see local report for warnings).
- Final APK DEX scan found none of the 11 retired source domains. This is supplementary evidence; production source was also inspected for remaining adaptations.

## Artifacts

- APK: `artifacts/News-0.3.27.apk`, 174058 bytes (0.3.26 was 183302; reduced by 9244 bytes).
- SHA-256: `6E99484BE4E4A797914777F6F574FD1F3CC7257005223915A70B9E06742FAF77`.
- Signing certificate SHA-256: `ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37`.
- Local evidence: `qa-027-screen-marker.txt`, `qa-027-final-round1/`, `qa-027-final-round2/`, matching text logs, `dynamic-027.txt`, `paging-027.txt`, `journey-027.txt`, `release-build-027-final.txt`, `release-ui-027-final.txt`, `signature-027.txt`, `tester-027-review.md`, `user-027-review.md` under `artifacts/`.

System font appearance may vary by phone. API26 and physical devices were not retested. Source login/access limits still apply; cached fixtures do not certify live availability. Tests and screenshots establish the stated scope, not absence of every possible defect.
