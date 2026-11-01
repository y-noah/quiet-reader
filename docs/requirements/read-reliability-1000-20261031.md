# Reading reliability campaign

User goal: at least 1,000 independently recorded test rounds, finding and fixing intermittent reading, cache and login-retry failures. Passing assertions alone do not count as rounds. Previous test totals are not part of this campaign.

## Device boundary

The user clarified that the 1,000 rounds belong on the computer and emulator. The Find X7 Ultra is available overnight only for a small number of decisive checks: existing real login sessions, a representative failed article, cache handoff and the final installed build. Never run a bulk campaign on the user's phone. Preserve accounts, history and preferences; synthetic accounts and deliberate network faults belong exclusively in the isolated emulator package.

## Round definition and evidence

Each round has a fresh scenario setup, a complete action sequence, explicit outcome checks and cleanup. Record round ID, scenario/seed, build identity, elapsed time, acquisition and terminal-callback counts and any failure in JSONL. Count completed scenario rounds, separately from assertions and infrastructure failures. Keep failing baseline evidence, then repeat against the repaired candidate. A failure must be diagnosed; repetition alone is not a fix.

Exercise mixed action ordering and repeated sequences: refresh, open during acquisition, fast back/reopen, cancelled work delivering late, interrupted network and recovery, login/account changes, native-to-WebView fallback, disk restoration and bounded retry/skip. Failure cases must not block unrelated articles, spin indefinitely, cache permission shells or revive cancelled work. Foreground failure recovery remains finite. Honor server rate limits, lifecycle cancellation and account/version boundaries.

Phone checks cannot prove every platform available. Separate own-reader output from the hidden extraction WebView. Finish test readers without generating artificial preference feedback. Restore screen timeout and remove temporary phone artifacts/drivers when finished. Do not publish a release as part of this campaign.
