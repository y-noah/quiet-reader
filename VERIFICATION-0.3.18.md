# News 0.3.18 — 2026-10-02

## Scope and evidence

User reports Tieba still requests login after login; exact phone-side cause is not confirmed. A question asking whether the original source page itself shows replies is still unanswered. This release fixes independently identified app-side issues, not a certified real-account login result:

- Tieba login mode was not retained and the reader always used desktop UA. `SourceSession` now keeps the explicit Tieba mobile/desktop choice consistent between login and extraction. Other platform defaults are unchanged.
- Previous warning detection searched all page text, including hidden UI and quoted prose. Tieba now recognizes the observed `.login-guard-mask` structure; live WebView snapshots exclude computed-style-hidden masks from a detached clone, without changing the actual site. Visible restrictions still produce an explicit notice, and all forum reading retains the partial-content disclaimer. Login UI is never article prose.
- A stable initial login mask could end extraction after two snapshots, before delayed session/reply rendering. Gated Tieba snapshots now get at least five polling attempts; they are not retained in the ten-minute article cache.
- Source Back on the same Tieba post first imports its actually loaded unrestricted content, preserving page-local state rather than rebuilding that page. Invalid/restricted snapshots fall back to the existing fresh-read path. URL identity checks and a bounded return timeout remain in place.
- Explicit Tieba login navigation allows only the existing passport host plus the official mobile auth host `wappass.baidu.com`; neither is an article host. HTTPS-only and lookalike-host rejection remain. Official public host verified at https://wappass.baidu.com/passport/login and referenced by https://passport.baidu.com/passApi/js/wrapper.js . No account API, credential or cookie export was used.
- A new Activity launch starts on Zhihu (first tab), ignoring the last-platform preference. Configuration changes and existing reading state still restore normally.

## Tests

- 166 JVM tests passed; release build and signing verification passed. Lint: 0 errors, 17 warnings. The initial partial-content notice test caught wording removal; the neutral incomplete-content warning was retained, without restoring broad login text matching.
- API 35 dedicated AVD `quiet-reader-test`, `emulator-5556`: targeted `tieba-auth` run passed 25 checks. Includes old Weibo preference → fresh Zhihu launch; delayed synthetic gated page → actual synthetic session reply; matching mobile/desktop UA; restricted cache exclusion; computed CSS-hidden gate; explicit source Back bringing the page's unique reply into MainActivity with no redundant reload. Synthetic cookie expired and source-session/main/board/appearance preferences restored. No real account logged in, no cookies cleared, no phone touched.
- Two earlier source-Back fixture runs were unsuccessful (first did not retain imported content; second returned a null snapshot during navigation). Test now waits for Activity resume and exact readable fixture identity before Back, and uses intercepted ordinary URL loading instead of a data document. Final return checks pass; these failures are retained in artifact history, not represented as real-platform failures or successes.
- Targeted evidence: `artifacts/experience-emulator-5556-20261002-205513/run-1790945712702/`.
- Exact signed release: `test-release.ps1 -KeepData -Mode branding`, 27 assertions passed, including fresh selected Zhihu, compact homepage, settings return and six platform switches. Evidence: `artifacts/release-emulator-5556-20261002-205603/`.
- Screenshot of synthetic preserved reply visually reviewed. No all-platform sweep or API 26 rerun. Authenticated Tieba access on the user's OPPO remains to be checked.

## Delivery

`artifacts/News-0.3.18.apk`, 168510 bytes. SHA256: `F8F7000EAE0A5007E15AA571FD032717581B2C96E1DB47EF16AB530036A51B15`.

Same package/signature; in-place upgrade preserves stored login data. Prior versioned APKs and stable-0.3.15 baseline unchanged. No server/model/dependency added, no Git commit/push.
