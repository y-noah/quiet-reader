# 0.3.6 剩余来源独立实网检查

2026-10-02，目标 APK SHA256 `354C70A1733DEA00F1F09019DE3C4C123FF9C15DDFD53BB79B231BD61A52A9C3`，开始时已核验。唯一在线设备 emulator-5556，AVD quiet-reader-test、boot_completed=1、ro.kernel.qemu=1。只操作该专用模拟器，KeepData、不输入账号、不清数据、不碰真机。

本轮顺序微博 → 华尔街见闻 → Hacker News。每源真实命令终态后独立查看全部新截图，再进行下一源；采用 audit 的截图证据流程。来源观察脚本PASS不等于全文可读或登录验证。仅记录本轮新证据，非冷启动性能基准，不将缓存/网络时延变化称优化。

## 1. 微博

- session54927终态exit1，原因是明确来源受阻，不是runner崩溃。证据：`artifacts/release-emulator-5556-20261002-120605-994/sources-1790913965984`。
- 本轮2图board/blocked均已逐张审阅。热搜榜8.831秒显示；首条“总书记谈家国同心”22.163秒后未取得正文，UI显示“需要来源页面协助 / 页面未提供可提取正文，请登录后重试”，提供“登录 / 加载后读取”。
- 榜单和受阻页深色一致、文字/按钮清楚。没有正文，不能认证微博阅读或折叠成功；未进入或提交真实认证，不断言登录成功或登录一定能解决。没有绕过此样本换另一个当通过。

## 2. 华尔街见闻

- session4226终态exit0。证据 `artifacts/release-emulator-5556-20261002-120815-447/sources-1790914095439`，board/expanded/first-image/image-preview/image-preview-returned共5图逐张独立审阅。
- 榜单1.621秒；首篇 `https://wallstreetcn.com/articles/3782859` 实际展开1.617秒，47段非preview文字。正文有明确收起入口，深色和大字号正文可辨，无来源App下载横幅进入自有阅读页。
- 8张图片中首张债券走势图594ms观测点实际解码1071×630，图和预览均可见；真实触摸“查看大图”、关闭回原文章/同图/位置通过。初始正文图空白只属尚未解码，后续已见图，不报图片失败。
- 本轮只验证首图，不代表其余7张图片/全部段落逐个阅读或付费、账号限制内容可读；未实际做本源文章返回榜单，不泛化完整返回链路。

## 3. Hacker News

- session34654终态exit0。证据 `artifacts/release-emulator-5556-20261002-121004-666/sources-1790914204654`，board/expanded两图全部独立审阅。
- 榜单1.852秒，Pi 1.0 讨论 `https://news.ycombinator.com/item?id=49926069` 首条评论触摸展开2.526秒。3段真实英文评论，作者FacelessJim与收起入口清楚，长行换行、深色正常。
- 这是HN当前页讨论，不是外部链接文章全文。日志有244个回复身份卡，但只实际展开首评论，不能称全部回复/外部新闻已读或视频过滤实网覆盖。没有把评论伪称新闻正文。

## 本轮结论及交接

三源均已等待实际session终态，共9张新截图（微博2、见闻5、HN2）逐张看完。微博正文受阻，见闻本样本文字/首图/预览返回可用，HN本讨论首评论可读。微博的原因只确认App来源协助提示，未看到真实来源登录墙，不能断言缺登录就是原因或登录必定解决。

本轮未修改生产或runner，全部同一正式APK；结束时再次核验哈希不变。未认证真实账号，未主动测试新视频样本，不将所有平台/全文视作通过。Gradle/ADB已交还根代理，无活动session。

## 关键界面证据

1. 微博正文：未通过。以下是静读自己的受阻提示，不是真实来源登录墙。

![微博未取得正文](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-120605-994/sources-1790913965984/source-0-blocked.png)

2. 华尔街见闻：本样本正文和首图可读；图片原色不等于页面浅色。

![见闻深色正文与首图](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-120815-447/sources-1790914095439/source-4-first-image.png)

3. Hacker News：首条讨论可读；不能据此认证外部新闻正文或全部评论。

![HN深色评论展开](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-121004-666/sources-1790914204654/source-5-expanded.png)
