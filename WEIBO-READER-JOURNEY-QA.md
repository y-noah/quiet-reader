# 微博真实图片与阅读旅程：0.3.10

2026-10-02。独立读者测试，仅quiet-reader-test/emulator-5556，保留数据，无账户输入、无手机、无生产改动。产品审查技能要求以本轮原生截图为依据；Python3偏好预检不可用，已手动确认无保存context，未新建替代网页。

## 身份与终态

`test-release.ps1 -KeepData -Mode sources -Source WEIBO -WeiboReaderJourney`，session53165实际exit0；原签名准确R8包SHA256 `1D58A8DA92D8F9C54A1BA363DC3701BC41BE2A1DB0250AF4CDB30089683FFCF5`。证据目录`artifacts/release-emulator-5556-20261002-141034-110/sources-1790921434102`，5张新图全部逐张查看。

本轮添加独立开关，不改变默认源测试；图片观察复用严格section/来源/首图身份绑定、JS观察后关闭、真实pointer。新模式输出计数与安全host/path，不输出rawbody、Cookie或query。即使inline12秒未解码也会保留失败再尝试caption，预览成功不能抹掉inline失败；本轮实际没有触发失败分支。

## 实际流程

1. **热榜→自动正文，成功但仍需等待。** 榜单7.123秒；点首话题到展开并验证文字9.780秒。本轮无需人工来源协助，深色保持。初次文字验证时所选part-0有1段文字、9张图片地址，已解码数0；初展截图确实仅看到图注，不能把当时视为图片通过。
2. **首图等待，实际解码。** 截完初展图后开始独立12秒上限观察，第1次29ms观察点首图已`complete=true`、`naturalWidth=360`、`naturalHeight=615`，CSS高度615，至少部分在视口内；这29ms是额外观察时刻，不是下载时间。实际首图截图清楚呈现文字海报，不是URL占位。未证明全部9图均已解码。
3. **真实点查看大图→关闭，图片可见。** 首图caption绑定同一src后真实pointer，原生状态“图片已显示”；截图的图片内容与正文首图一致。关闭后同自有reader revision、来源与首图身份检查通过。**本轮旧helper在关闭后会为图片观察主动滚动，因此不能据此称关闭位置恢复已通过**；返回截图仅作图/文档身份与可见性证据。
4. **原帖全文→返回，未覆盖。** 所选真实part-0没有“继续读取原帖”操作，明确记录`CONTINUATION_UNCOVERED`。没有注入链接或换成合成正文来制造成功；未验证其他卡是否带入口，也未验证该条路径的返回位置。

![本轮初展：正文有字，图片尚未显示](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-141034-110/sources-1790921434102/source-0-expanded.png)

![随后首图真正显示](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-141034-110/sources-1790921434102/source-0-first-image.png)

![真实大图弹窗：可见但360像素源图偏糊](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-141034-110/sources-1790921434102/source-0-image-preview.png)

## 独立发现与边界

- **图片质量风险确认。** 实际首图路径为sinaimg.cn的`/orj360/`，360×615源图放大后海报文字明显柔糊/像素化。能看到不等于高清；本轮未猜CDN大图路径，未做源图字段或原站大图取证。CSS宽未记录，不能补造数值。
- **没有复现持续图片下载失败。** 图片由初展未显示变为真正可见，首图及预览均成功。不能把旧0解码截图推断为403、Referer错误或加载永远失败；本轮没有HTTP下载拦截证据。
- **关闭保位测试缺口已识别。** 已离线增强新模式：关闭后先用不滚动的自有DOM读scrollY/首图top及CSS宽高，再截图、检查与点击前坐标差，之后才允许旧helper重新定位。该增强尚未编译执行，不能把本轮成功追溯为新断言通过。
- 此次未认证全部9张图片、所有微博、长文完整性、原帖入口/返回、账号、真机或全可访问性。主题方向一致；照片没有整体反色。

所有命令已终态，Gradle在编译结束时交根做单元测试，ADB在session53165终态后交还。设备保留本轮准确0.3.10正式包，无活跃测试。下一步应从真实来源提供的大图结构与图片身份去重入手，再补无观察器滚动干扰的关闭保位与实际原帖旅程验收。
# 追加：原站真实点图与未移动返回验证（2026-10-02 14:19）

执行 `test-release.ps1 -KeepData -Mode sources -Source WEIBO -WeiboReaderJourney`，session **72085 exit 0**。准确正式包仍为 0.3.10，SHA256 `1D58A8DA92D8F9C54A1BA363DC3701BC41BE2A1DB0250AF4CDB30089683FFCF5`；生产候选的照片去重修复不在这个正式包内。没有清数据、登录输入、URL 猜改或 Cookie/HTML 导出。

证据目录：`artifacts/release-emulator-5556-20261002-141928-390/sources-1790921968383`。本轮全部 **8 张截图已逐张查看**。

1. 自动榜单 7.101 秒；到实际展开首卡 9.772 秒。首图观察第 1 点（44ms）未解码，第 2 点（698ms）真实解码为 360×615；这是观察窗口耗时，不等于整次下载耗时。
2. 实际点「查看大图」显示该图；关闭后的第一次只读 DOM 测量在任何移动 observer 之前，scrollY 差 0px、同图 top 差 0px，精确原来源/自有 revision/图身份一致。新增保位断言本轮实际通过。随后旧 observer 才将第一图滚回完整视口，两张返回截图不可混为同一位置。
3. 首卡没有真实「继续读取原帖」CTA，仍为 **CONTINUATION_UNCOVERED**，没有构造入口，也不宣称全文往返已验。
4. 通过真实菜单进入生产来源页，在 `.weibo-media-wraps img` 中找到与自有首图同 basename 的图片。实际触摸它而非 JS click，原站出现 1/9 图片查看器。
5. 查看器 1.5 秒观察点仍为 `https://wx4.sinaimg.cn/orj360/:image`、360×615；3 秒至 12 秒观察点出现同 basename 的 `https://wx4.sinaimg.cn/mw2000/:image`、**1200×2050**，CSS 412×704，实际可见。此地址来自原站自己的点击结果，不是猜 `/large/` 或变造 CDN URL。仅此样本证明原站有更清晰版本，不是通用路径变换契约。

独立视觉结论：自有正文/预览深色一致、图片可以显示、关闭不会改变位置；但 App 大图文字边缘明显柔糊，原站 1200px 图的字形和细节明显更清晰。0.3.10 在本样本未使用原站已有的高分辨率图，是明确质量缺口；不归因网络或 Referer，也不声称 9 张全部解码。

![App 当前低清大图](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-141928-390/sources-1790921968383/source-0-image-preview.png)

![关闭后尚未被 observer 移动的阅读位置](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-141928-390/sources-1790921968383/source-0-preview-closed-unmoved.png)

![原站实际点图后的高清查看器](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-141928-390/sources-1790921968383/source-0-original-image-viewer.png)

测试辅助同时修正 continuation pointer 的 JS 所有权/timeout 清理：超时后禁止迟到触控，清理只关闭自己开启的窗口。由于本轮 CTA 不存在，此超时分支仅编译检查，不能声称已实测。Gradle/ADB 已交还 root，无活动 session。

# 0.3.11 正式包高清预览复验（2026-10-02 14:24）

Session **9324 exit 0**，`test-release.ps1 -KeepData -Mode sources -Source WEIBO -WeiboReaderJourney`。正式包 SHA256 **44CFB58C6608065B58A8407FD603134CBCB434129C25E33AA5C8E45D3246381D**。证据：`artifacts/release-emulator-5556-20261002-142450-887/sources-1790922290879`；全部 **8 张新截图逐张检查**。

- 新增公开 WindowInspector 精确查找 `https://quiet-reader.invalid/image-preview` 弹窗 WebView，固定只读 DOM 检查前后 JS 均关闭，不引用 R8 混淆类/私有字段。
- 实际弹窗图 `mw2000/:image` 与 inline 同 basename，`complete=true`，自然尺寸 **1200×2050**，CSS 约 359.24×613.70；正文 inline 仍为 **360×615**。不是只凭「图片已显示」状态判成功。实际同图文字边缘比上一正式包明显清楚，符合原站取证。
- 实际关闭后、任何 observer 滚动之前，scrollY 与图片 top 偏差均 **0px**，文档/图片身份保持。此严格断言已执行通过。
- 源站同图再次实际点击，查看器仍显示同 basename 1200×2050；无需改写任何测试访问 URL。
- 榜单 6.953 秒，首卡实际展开 8.926 秒；首图第 2 观察点 630ms 已解码。这是单轮观测，不等于普遍提速或网络下载时长。
- 当前选中卡没有全文 CTA，全文打开/返回仍未覆盖；其他 8 张图片没有逐张解码检查。本轮并非全平台验收，也不把相同配文去重单元结果冒充实网全部卡片验证。

![0.3.11 真正解码的高清预览](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-142450-887/sources-1790922290879/source-0-image-preview.png)

![关闭后的原位置，读取前未重定位](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-142450-887/sources-1790922290879/source-0-preview-closed-unmoved.png)

结论：本样本高清预览修复实际有效，深色保持、无新增可见阻断。准确正式包留在专用模拟器；Gradle/ADB lease 已归还，无活动 session。测试源码与本文稳定。

