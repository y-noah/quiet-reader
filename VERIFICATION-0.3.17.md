# News 0.3.17 — 2026-10-02

Scope: remove the standalone homepage Settings button and the temporary board-title search box. News and the overflow menu remain in the top bar. Settings are still accessible via 更多选项 → 阅读设置. Removed title filtering and obsolete query-state restoration; retained per-platform scroll anchors, six platform badges and the 0.3.16 login-return fix. No new dependencies or event aggregation/model integration.

Checks:

- Build, 161 unit tests and signed release verification passed. Lint: 0 errors, 17 warnings.
- Dedicated API 35 emulator `quiet-reader-test` / `emulator-5556` only, no uninstall, cookie clearing or phone access.
- `test-experience.ps1 -Mode home-cleanup`: 33 checks, 0 failures. Synthetic middle-of-board article open/back preserves its visible position; six platform controls fit and switch in both themes. Original test-modified board/main/appearance preferences restored. Evidence: `artifacts/experience-emulator-5556-20261002-203622/run-1790944582357/`.
- `test-release.ps1 -KeepData -Mode branding`: 26 assertions passed on the exact signed release, including absence of standalone settings/search, overflow settings navigation and six platform switches. Evidence: `artifacts/release-emulator-5556-20261002-203700/`.
- Legacy search-specific UI journeys are historical and not used for this release; this focused run is not an all-platform/live-login certification. No API 26 rerun.

Delivery: `artifacts/News-0.3.17.apk`, 167526 bytes. SHA256 `CA7CAC137D39E45B94E66F3A12D72C23570F57B1B17EFFCD962A40969939D4CE`. Original package/signature retained for in-place upgrade. Prior versioned APKs and the stable-0.3.15 baseline were not changed. No Git commit/push.
