# 2026-10-28 贴吧图片父楼与八条真实楼中楼

## 问题与来源证据

接续 tieba-rendered-identities-20261028.md 中只有四条时间的问题。live-floor-structure.txt / live-image-structure.txt 对同一公开帖子核实：两个父楼各四条楼中楼，所有八条精确根 Vue2 组件都有有效 id/time/pid。154074798665 父楼的 pb-rich-text 为空；两张图片位于其直接兄弟 image-card-wrapper，使用 data-src 与 data GIF 占位。旧选择规则只补 pb-image-list，整层被 appendRegion 当作空正文丢弃，其四条子回复因没有父层身份不能附时间。不是来源缺时间，也不是网络请求失败。

## 修复与回归

StructuredSources 在已定位的 comment-content 下，仅补入 pb-rich-text 的直接兄弟 image-card-wrapper，沿用既有图片地址与 data 占位回退解析。不抓父容器的任意后代，避免将楼中楼、广告图片并入父层；不捏造空父层或时间、不增加请求/轮询。延续本批尚未进入普通候选的关系版本4。

新增两个 JVM 回归以图片父层、独立楼中楼表情、下一父层和旁路广告结构复现；修复前两项真实失败保留于 image-floor-before-fix.txt / image-floor-before-fix-test.xml。修复后验证两张父层图片、子层精确父 ID、子层时间与历史、父层时间0、data-src 到 src 过渡及重复续载不重复。

## 最终验证

- image-floor-final-build.txt：847项/118套 JVM，失败/错误/跳过全0；Debug/Release Lint成功。摘要与专项测试和Lint XML归档。
- image-floor-live-final.txt：生产 Debug WebView/解析/历史，真实匿名公开页，readable=1/1、probeErrors=0、唯一 raw -1。两个父楼、八条子回复全部父子匹配、父层两张图片；8/8 来源子回复时间且历史精确8，顶层时间0。moreStatus=login，restrictedProgressRejected=true；不是登录后全量分页验收。
- image-floor-tieba-child-times.txt 18/0；image-floor-tieba-virtual-replies.txt 13/0；image-floor-tieba-discussion-progress.txt 20/0；均唯一 raw -1。这51检查是受控传输/虚拟列表/进展门槛验证。
- image-floor-installed.json：最终模拟器实际 App/driver 与归档SHA256相同。测试结束 force-stop 两个隔离包，模拟器关闭且保留userdata，终态见 image-floor-cleanup.json。

本轮未操作手机，未发布。普通候选9e06075e、手机c315361c均不含本批渲染身份和图片父楼修复，保持不变。下一步新增实现的R8真实网页/展示与保数据升级；真实登录分页、推荐收益、独立去重质量、ARM峰值/拔线能耗/长期缓存门槛继续开放。

后续正式构建与普通升级已补验：见 [R8实际公网页面](r8-public-tieba-times-20261028.md) 与 [普通原签名候选](ordinary-tieba-rendered-20261028.md)。实际采集窗口8条全部保留，普通候选aefb479b已保存本地、未装手机或发布。
