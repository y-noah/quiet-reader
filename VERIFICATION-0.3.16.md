# News 0.3.16 — 2026-10-02

## Scope

- Fix source/login return: RESULT_CANCELED is also delivered when a user has logged in and simply presses Back. Previously it invalidated the memory cache without refreshing the visible article. Now returning reloads the current item through the existing shared WebView session. It does not assume authentication succeeded or remove genuine platform restrictions.
- Stop pre-login in-flight readers/preload work so late results cannot restore an anonymous snapshot. Explicit “读取到 News” imports remain intact. Avoid a duplicate refresh from onResume.
- Capitalize application label, homepage, source-view controls and red vector wordmark to News. Package identity, signing key and stored sessions are unchanged.
- Six platform tabs retain single-character labels and 48dp-high hit targets, with 36dp rounded color badges. Selected tabs are solid; others use subdued theme-aware tints. No font asset, model, server or runtime dependency added.

## Verification

- 161 JVM tests: 0 failures, 0 errors.
- Release build and APK signature verification passed. Lint: 0 errors, 17 warnings.
- Dedicated API 35 AVD `quiet-reader-test`, serial `emulator-5556`; no phone access, uninstall, account login or clearing of real cookies.
- Targeted `test-experience.ps1 -Mode login-return`: 37 checks, 0 failures. Tests synthetic Tieba login-return session reuse, stale-warning replacement, cache invalidation, explicit import, six platform switches and both themes. The fake reply is served by test interception, not a remote authenticated Tieba reply. The uniquely named test cookie is expired afterwards; original appearance, board and main preferences are restored.
- First run's 12 bounds assertions ran in the same UI turn that created the views, before layout. Screenshots and switching were correct. Moved measurement after layout, then reran the entire targeted mode successfully. No production change was needed for this test timing issue.
- Exact signed release `test-release.ps1 -KeepData -Mode branding`: 11 assertions passed, including installed News label, version 0.3.16/code 27, red/white adaptive icon and homepage/settings return.
- Inspected dark/light badge screenshots and rendered release icon. Six badges fit on one row without enlarged persistent controls.

Evidence:

- `artifacts/experience-emulator-5556-20261002-202933/run-1790944173153/`
- `artifacts/release-emulator-5556-20261002-203043/`

## Delivery and limits

- `artifacts/News-0.3.16.apk`: 168526 bytes.
- SHA256: `2DD892C656EE4A89C1476D673AA19AD72C7CE52B07A07B21BA08FEF4D9A56264`.
- Existing `artifacts/stable-0.3.15/` was not modified; old APK hash is still `93BBC17F45B304CB3CEBBBA8E859612067D70B5D795DCBF9B04A1D552EFC736F`.
- Actual user-account Tieba replies on the OPPO phone remain unverified. The confirmed app-side return defect is fixed; platform login/session restrictions are not claimed resolved for every account. No all-platform live sweep or API 26 rerun for this small change.
- No commit/push performed. Event timeline and model experiments remain outside the production app.
