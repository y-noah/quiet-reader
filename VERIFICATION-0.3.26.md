# News 0.3.26 verification — 2026-10-04

Package `app.quietreader`, versionCode 37. Default launch remains the total ranking.

## Changes

- Total ranking now uses exactly the five navigation platforms: Zhihu, Weibo, Hupu, CLS and iFanr; capped at 50. Short lists remain short when sources are unavailable or quotas/deduplication reduce the result.
- Removed platforms cannot enter the total ranking, login chooser, internal reading links, restored articles/history or repository requests. Their stored board data, video-filter records and legacy bookmarks are removed on upgrade. Retained platform preferences and cookies are not cleared. Legacy parsers remain as regression fixtures, not supported app entry points.
- Six equally spaced bottom tabs retain 48 dp touch areas and 24 dp drawing size; glyph strokes increase from 1.9/2.15 to 2.4/2.7 for normal/selected states.

## Evidence

1. JVM: 230 tests, zero failures/errors. Debug and release builds succeeded. Release lint: zero errors, 18 existing warnings.
2. Baseline Android migration/navigation verification: 199 checks, zero failures. Synthetic cached feeds and stored upgrade data; real Android lifecycle, repository code and navigation click handlers. Four screenshots checked in dark/light themes and the five-source chooser.
3. After the baseline passed, two further independent runs on the unchanged production code each passed 199 checks with zero failures. Tester and user-review agents reviewed results; user-review checked all eight new screenshots. No known issue was left open merely because two runs had elapsed.
4. Exact signed release APK installed with `adb install -r` on the project API35 emulator, then passed 36 release UI assertions, including Top50, initial total tab, five platform switches and settings return. Original signing certificate preserved.

Local evidence under `artifacts/`: `build-026.txt`, `release-build-026.txt`, `five-source-first-026.txt`, `five-source-026-initial/`, `five-source-026-round1.txt`, `five-source-026-round1/`, `five-source-026-round2.txt`, `five-source-026-round2/`, `tester-026-round1.md`, `tester-026-round2.md`, `user-026-final-checks.md`, `release-ui-026.txt`, `release-ui-026-evidence/`, `signature-026.txt`.

## Artifact

- `artifacts/News-0.3.26.apk` — 183302 bytes.
- SHA-256: `5A796D26AC8741066102C7F5DD7AA5D3EFD62ED0E3E588CBF659F2EBA8D7304B`.
- Signing certificate SHA-256: `ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37`.

Tests cover the requested source restriction, migration and navigation changes. Synthetic cached data is not evidence of live-source availability or authenticated reading. Platform login restrictions still apply. This release was checked on API35, not every phone or Android version.
