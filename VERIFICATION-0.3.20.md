# News 0.3.20 — 2026-10-02

## Changes

- Replace Tieba and Wallstreet navigation entries with CLS and GeekPark. Final order: Zhihu, GeekPark, CLS, SMZDM, Hupu, Weibo, shared by bottom tabs and the overflow source menu. Retired enum names remain solely for legacy data compatibility; retired-source state restoration is redirected to Zhihu. Old cookies are not cleared.
- Shrink monoline marks from 28 dp to 24 dp and TextView text semantics from 20 sp to 18 sp. Keep 48 dp tap targets, six distinct colors, no background tiles, round strokes and selected underline. Add 极 and 财 glyphs.
- No aggregate Top 100, new model, server or dependency. Aggregation remains a proposal pending user direction.

## Source verification

- CLS official [hot page](https://api3.cls.cn/quote/toplist?app=cailianpress&os=android&sv=835&tab=1) and its public `quote_toplist_2025081.js` identify `/v1/hot_list` as the information/news ranking, separate from `/v1/hot_stock`. Official `sign.js` uses a public MD5(SHA1(query)) checksum, not an account credential. Verified successful anonymous response; preserve server order, not a re-sort by read count. Construct the official public `/share/article/{id}` links, extract only main `section.content-box > .content`, excluding related article boxes and download UI. Keep actual returned metadata and inline links. Recognized video schema rows are excluded.
- GeekPark's public [frontend script](https://www.geekpark.net/dist/app.2488a2673c597d06144d.js) labels `posts/hot_in_week?per=7` as 七日热门 and provides the `mainssl.geekpark.net/api/v1` host. Confirmed [official ranking](https://mainssl.geekpark.net/api/v1/posts/hot_in_week?per=7) and public news article. The root homepage returned HTTP 403 during inspection; public tag page, ranking and sampled article were accessible. No challenge bypass. Source/login entry uses the accessible `/tags/AI` page containing the site's seven-day-hot component. Extract only `#article-body .article-content`; filter video/pure_video entries and observed main video wrappers.
- Official ranking labels are retained; seven-day popular is not represented as real-time search volume. Host lookalikes and non-HTTPS navigation remain rejected.

## Tests and limits

- 173 JVM tests passed, including seven new tests for final order/retirement, CLS error/ordering/deduplication/media handling, GeekPark video filtering, body-vs-recommendation isolation, inline links and allowed hosts. Release build/signature verification passed; lint 0 errors, 18 warnings (unchanged from 0.3.19).
- Exact signed release on dedicated `quiet-reader-test` / `emulator-5556`: branding 34 assertions passed, including six tabs' left-to-right order, retired entries absent, initial Zhihu and settings return. Screenshot reviewed: `artifacts/release-emulator-5556-20261002-213342/`.
- CLS live refresh, first real entry, 34 substantive body paragraphs and reader Back passed. Evidence: `artifacts/release-emulator-5556-20261002-213354-635/sources-1790948034634/`.
- GeekPark live refresh, first real entry, 64 substantive body paragraphs and reader Back passed. Evidence: `artifacts/release-emulator-5556-20261002-213414-944/sources-1790948054944/`.
- Both new reader screenshots visually reviewed. Test observed GeekPark image URLs but did not wait for all image decoding; image success is not certified. No login, paid-article, all-platform regression, latency guarantee or physical-phone test. No cookie reset, uninstall, Git commit/push, or new independent-agent iteration.

## Delivery

`artifacts/News-0.3.20.apk`, 171010 bytes. SHA256: `730C294ACEE0840FAABC643A14AB8226E25615821DF0D9853DBCF7CF6C286205`.

Same package/signature for in-place upgrade. Old versioned APKs and stable-0.3.15 baseline retained.
