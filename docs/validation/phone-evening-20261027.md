# Find X7 Ultra evening trial — 2026-10-27

## Installed artifact and scope

User connected the phone and authorized continued testing. Device PHY110, Android 16; original ordinary package app.quietreader, v47. In-place adb install -r succeeded. Pulled installed base.apk SHA256 c050a4712b2b9e856d03b96d3f127d5abbd1164fe890b599d8ffad4d229fab9e exactly matches New-next-trial/New-20261027-c050a471.apk. Evidence artifacts/local-ai/phone-evening-20261027. No driver installed, no app data clearing, push or release.

## Observed reading

- Zhihu: real question “为什么中国人都执着于买房而不是租房？” displayed its complete question supplement in the unified reader, then actual answer text after scrolling. Source was inspected, but a definitive account identity / login-exclusive permission comparison was not established. This is not a pass for all Zhihu authentication.
- Hupu: source page contained its signed-in greeting and account controls (account identifiers are not repeated here). After returning, a real Hupu-filtered feed article “这得多恐怖啊！” rendered body image, multiple reply cards, inline emoji and reply images in the unified reader. Four swipes continued through replies without the reported login gate in the inspected range. This does not prove every thread, all replies, or source/native permission equivalence for a gated thread.
- Tieba: hot topic “残局卖女友保装备,洲友惨遭分手” resolved to the actual post “如何评价神人女友因为我丢包要和我分手？”, with text and image. Visible nested reply cards had indentation and the small 楼内回复 label. Emoji were images, not [表情]. The source-relative identity/parent of every reply and logged-in continuation were not exhaustively compared.
- Continuous emoji in one real nested reply occupied separate lines with large spacing. Preserve the example for a parser/layout investigation; source line boundaries still need comparison before flattening them.
- UI dumps during acquisition included source WebView text whereas screenshots showed the native reader. SourceSurface already uses NO_HIDE_DESCENDANTS. Do not call this confirmed TalkBack leakage or change flags blindly; connected touch-exploration verification is needed.

## Memory and background limits

zhihu-ready-mem.txt: main process PSS 188808 KiB. Separately sampled zhihu-webview-mem.txt: owned renderer PID 22606 PSS 352072 KiB. Ownership confirmed by activity-processes.txt, isolated UID host10380 and service connection to app.quietreader. Sum 540880 KiB (~528.2 MiB), from two separate snapshots, not a simultaneous or whole-session peak. Other renderer PID18795 belongs to another UID and was excluded.

Hupu main PSS206344 KiB; Tieba main PSS186548 KiB. These main-process samples do not substitute for all-process totals.

Model status showed 286/286 cached vectors and zero new inference in the reported round. Therefore no effective dual-model peak/ARM inference measurement was obtained. 1GiB whole-App peak acceptance remains open. Phone was charging; no unplugged energy or temperature conclusion.

After HOME, ranking-background-pid.txt is empty. Settled service report still lists a WebView binding: do not claim all resources disappear or background zero use. No network/CPU energy measurement was made.

## Restore and cleanup

The source filter was originally 全部来源, temporarily changed to HUPU then TIEBA, and restored to all. restored-feed.xml has multiple real platforms. One extra BACK left New; immediately returned, deleted the unrelated hupu-feed.xml, and did not use it as evidence. Further navigation stayed in New.

Final phone state returned to DiscoveryActivity. Exact temporary files /data/local/tmp/new-evening-screen.png, new-evening-ui.xml and new-evening-open.sh were deleted; ls confirmed absent. No added test driver to remove. User told phone testing is finished and cable can be removed. No further phone operations authorized by this record until the next testing request.

## Separate unbundled source fix

Code review found MainActivity and SettingsActivity discarded left/right system insets. Both now add side insets to the existing18dp margins, matching DiscoveryActivity. New SettingsInsetsProbe injects left/right, swapped, repeated and reset insets on actual activity roots (24 assertions planned). It is isolated-target guarded. Debug app/test build and Debug+Release Lint passed (settings-insets-20261027/build-after.log).

The baseline APK and new driver were archived, but emulator ADB installs never completed; no instrumentation count or pass is claimed. Sandbox build initially failed on dependency ACL; elevated offline build succeeded. A separate emulator-only ADB server was tried, then closed; stalled emulator shut down through authenticated console, userdata preserved, named emulator processes0. This source fix is not in c050a471 and was not installed on the phone. Runtime regression and a refreshed ordinary candidate are still required.