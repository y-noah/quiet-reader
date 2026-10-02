# 微博自动读取与原帖全文取证

2026-10-02，独立读者测试。仅项目 quiet-reader-test / emulator-5556，保留数据，不输入账户、不清Cookie、不触及真机。使用实际生产读取与真实pointer；不替换生产parse/callback，不猜API，不放宽平台地址规则。审查技能要求的本机Python3预检不可用，已手动确认无保存context；采用本项目原生截图，不生成替代网页。

## 1. 原导航判定的UA对照：两种方式均未完成正文

session88834终态exit0表示两次观察完成，不表示正文通过。证据目录`artifacts/weibo-reading-20261002-134418/run-1790919859461`；debug SHA256 `C4612E2D5A4E7EB572A6DD5C7CE7674A968C45571A9A02C8CD5E8FFAD8B03794`。这次debug已包含根正在开发的全文入口候选，但DynamicReader仍原0.3.9的UA/导航逻辑；**不是精确R8 0.3.9验收**。

取真实缓存微博榜首Item（非本轮重新取榜），同一对象先desktop后系统mobile。构造返回的同UIturn、生产queuedload之前，仅修改UA；图片请求仍禁用、第三方Cookie仍false。代理原WebViewClient并转发事件，另700ms读取只含DOM计数/布尔，不能把该额外观察耗时当纯生产性能。

- Desktop：实际转到`https://passport.weibo.com/sso/signin`，5.160秒观察点77字符、0正文selector、loginWords和captchaWords为true；19.872秒生产failure“页面未提供可提取正文”。这比App泛化提示多了真实来源证据，但不代表已认证或登录一定解决。
- Mobile：1.047秒处理`http://m.weibo.cn/search`升级，1.054秒旧`https://s.weibo.com/weibo`的finish到达，1.066秒生产failure“来源跳转到登录、其他问题或不支持的页面”；没有新HTTPS页面commit。证明仅改UA仍有升级期间提前判失败的问题，不能宣称只换UA即可成功。

两张图均已查看，但它们只是诊断摘要，第一版标题与状态栏有轻微重叠；不是原站画面或产品排版缺陷，不作为正文可读证明。后续摘要增加顶部间距。

## 2. 精确0.3.9来源非视频「全文」：同帖可公开打开，增量有限

session65578终态exit1，保留原自动读取失败。命令`test-release.ps1 -KeepData -Mode sources -Source WEIBO -WeiboFulltextProbe`，目录`artifacts/release-emulator-5556-20261002-134815-110/sources-1790920095101`。精确R8 SHA256 `8A2C1E3E36924E1B936A0727E755071BD69C282DFF3ACD416B02D35D71F95F34`，文件未改。全部5张新图逐张查看。

1. **热榜→自动读取，受阻。** 榜单7.070秒，正文21.798秒后协助页，未称整轮通过。
2. **真实点来源，移动话题可见。** 原生深色外框和移动页面都保持深色。来源首卡明确有播放图标，不选择此卡做全文对照。
3. **选择真实非视频卡，点击全文成功。** 仅在非视频owner的.weibo-text中找真实a链接；将其滚入视口后，核对当前源URL、精确href、可见bounds与焦点，在同UI回调注入真实pointer，不用JS click。链接属性是relative；解析后目标`https://m.weibo.cn/status/:id`，ID为16位numeric。未输出其值。
4. **详情页，仍是同一literal ID。** 最终/status/:id，与目标ID逐字相等，未做bid/mid猜测转换。1.5秒观察点已出现1个正文selector；持续到12秒无loginWords/captchaWords，最终`.weibo-text`143字，原摘要144字，grew=false。内存比较确认原前50字保留，末尾改变，详情不再有“全文”标记。截图显示原来“（…全文”变为实际署名，正文之后有多张实际图片。因此只是证明这条公开详情可达并补齐尾部，不是大段长文获取证明。

![真实非视频卡全文入口](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-134815-110/sources-1790920095101/source-0-fulltext-before.png)

![真实同帖详情：署名补齐，来源图可见](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-134815-110/sources-1790920095101/source-0-fulltext-detail.png)

正文、href、ID仅用于内存比较，日志只保留安全host/path形状、字符数和布尔；没有保存源HTML、Cookie、Storage或账号信息。来源图片清楚可见，但未在此轮读回自有reader，更不能称图片完整提取、长文完整或返回原话题保位已验收。

## 3. 候选导航修复后A/B/A：移动读取成功，两个桌面对照仍失败

session42260终态exit0，目录`artifacts/weibo-reading-20261002-135102/run-1790920263897`，debug SHA256 `ADEC94B8F3D8F2FFA69D4F4FACA246ED35F67319E83D4386FDF1DD913D17FDD5`。候选生产加入导航revision/待提交门控与实际页面baseURL解析；正式APK仍是上述8A2C。本轮显式设置desktop/mobile/desktop，不能因新生产默认已mobile而误做三个mobile。

| 同一实际缓存Item、同一保留会话 | 实际观察 | 生产回调 |
| --- | --- | --- |
| Desktop A1 | passport登录页77字、0正文selector | 19.594秒failure |
| Mobile B | 旧finish后未提前失败；2.792秒新HTTPS commit，3.796秒观察2019字/13selectors | 5.880秒success，11blocks/6sections/625正文字符，过滤5 |
| Desktop A2 | 仍为passport登录页77字、0正文selector | 17.737秒failure |

这支持本样本的**移动UA加升级门控修复组合**；A2仍失败使“只是前次建立访客会话”的解释不符合本轮结果。但串行缓存/平台响应差异仍在，不是随机性能实验，也不是所有微博/冷启动速度保证。无HTTP/SSL错误日志不等于所有网络都无问题；本轮不是抓包。生产callback是真实Document，不是observer自行解析或替代回调。

![候选移动读取真实回调结果，仅诊断摘要](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/weibo-reading-20261002-135102/run-1790920263897/02-mobile-summary.png)

三张诊断摘要均已查看，明确不是正文视觉图。当前新自有“继续读取原帖”入口、其正式包全文内容/返回保位、跨帖身份与图片保留还需后续验证。此前5张来源图不能冒作候选自有reader的新功能截图。

## 交接与下一步

本轮共10张新图已逐张查看（2旧判定摘要、5准确正式源流程、3候选摘要）。session42260终态后已归还Gradle/ADB，无活动session；模拟器当前为候选debug，正式文件仍8A2C未覆盖。根代理接管构建/回归。测试文件及本报告稳定，未修改生产实现。

下一步应验证候选正式包从热榜自动读取、实际“继续读取原帖”及回到原展开段落的完整读者路径，不以本轮计数替代视觉与返回验收。
