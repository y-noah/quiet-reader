# 当前R8模型运行到真实正文（2026-10-26）

结果目录：artifacts/local-ai/minified-live-model-reading-20261026，first.txt为实际运行，15项通过、0失败。只在emulator-5560/app.quietreader.discoveryprobe运行，不反射生产类、不增加keep规则、不绑定旁路模型服务。

## 实验

ReleaseReadingFlowProbe增加live-model-reading入口。固定100公开标题中一条替换成当前独立公开捕获的BBC繁体文章ckx2q587wvrqo/trad，原文24段。其余99条保留合成正文缓存及明确开发者兴趣。persistSeed明确跳过实际目标的正文与历史写入；目标选择前、准备后、卡片动作前均无正文文件，且没有目标阅读历史，不删除缓存伪造冷读。

本段关闭首页来源采集/推测预读以避免无关传输；通过正常设置菜单“继续整理”启动生产推荐。它不证明普通自动调度，该范围另见minified-autonomy-20261026.md。初始向量/精排缓存清空，实际SQLite中有2048字节向量、公开UI状态正在本机整理、ranking进程存在后，才由实际ListView卡片performClick导航。主线程还在点击边界再次核对正在整理，无人工注入ITEM回调或伪造进程。

## 观察

- 点击前实际2条向量、ranking PID3530；目标仍无磁盘正文。
- 正常卡片进入MainActivity，discovery_candidate URL匹配目标。模型退出首次观察130ms，之后正文检查时仍不存在。
- Activity resumed58ms、postResumed61ms、首次decor draw97ms；这些是生命周期/装饰层回调，不是正文呈现帧。
- DOM观察7235ms，24/24段落含尾段；新落盘时间不早于点击、精确URL和bbc-article原生节身份匹配。未使用合成目标正文或摘要冒充全文。
- 打开和加载阶段反馈总数保持开发者预置记录，没有READ反馈；可见active_snapshot保持，未接纳待应用新顺序。退出后的快速阅读负反馈不属于此“加载阶段不学习”断言。

原检查文字写“before network reading completes”，实际只证明模型先于本探针观察正文时退出，没有网络完成事件时间，后续已将文字收紧为打开阅读器时退出，不倒推底层下载先后。130ms是进程查询观察，不是Binder死亡/触摸时延，也不证明原约9.9秒场景的根因或优化效果。

本轮截图R8-model-reading.png已查看：捕获瞬间只有原生顶栏，正文尚未绘制。故本轮明确只有DOM/原生缓存与生命周期证据，不宣称实际正文画面通过；不能以非空DOM或decor draw冒充正文呈现。补充绘制时序与截图检查仍开放，之前minified-live-reader-20261026.md单独的真实阅读截图不替代本切换时刻的画面。

App SHA为db1efc8939e9eac7699d2e1a4fa569177ad1ccecf93d2cc1348880ea8edb8153，与实际安装base.apk一致；驱动d1b35dc6026b2142ff219c5ea59df1a9511e5adfd3fc15e5b47b1488762c3430。包清单/R8映射与完整日志归档。运行后全部会话结束、两个隔离包停止、ps匹配0。运行时未改生产逻辑；后续普通候选包的API注解修复是另外构建，不用本哈希冒充候选包完整运行验收。

## 仍开放

当前是单篇BBC/向量阶段、程序卡片动作的受控切换。不证明精排阶段、真正触摸/正文呈现帧、全平台权限或长会话/实际个人收益/ARM资源耗电、最终普通包升级交付。无手机、普通包安装、提交、推送或发版；总体目标仍未完成。

## 后续：另一篇真实冷文章的正文画面

body-screen.txt针对API级别声明补齐后的新隔离R8，16/0。新增独立官方热门链接样本release-live-bbc-more-20261026.json（原五篇没有删除或伪造未命中）；首次采集遇TLS EOF未生成资产，随后五篇均成功，逐篇结果见release-reader-more-capture-report.json。该样本集不同于原225段JVM集，不把新增采集算成已运行JVM回归。

目标cm4g1wr84j9xo/trad，36/36段含尾段；点击前3条实际向量，ranking PID8273，最后UI动作边界仍为正在整理。模型退出观察151ms、正文DOM2725ms，加载不学习与冻结快照不变再次通过。耗时来自不同文章/网络，不是性能提升证明。

新captureDrawnBody不调用invalidate/强制draw：在真实WebView屏幕矩形内排除原生栏和滚动条，通过截图像素差异拒绝纯空背景，最多等待5秒并保存真实截图。此轮DOM后337ms取得非空正文画面；已实际查看R8-model-reading-drawn.png，标题、署名及正文可见。配图当时仍是占位，故只关闭这个样本的正文画面缺口，不声称图片完成或用337ms当呈现帧基准。截图采样本身有测试开销。

新App SHA efd63a237963e00cf2461b1531b888265fdfedb1bb513fde04c537b3c3be133e、驱动3b371790deba1970e7a3357355aaaf1c97d0420cc47caf6d69dc1c741b725709，实际安装hash一致；package-body-screen.json及mapping-body-screen.txt单独保存，原首轮哈希/映射不覆盖。最终隔离进程0。两轮合计31项，但只有后轮16项包含正文画面检查。API注解与原签名普通候选的静态验收另见next-phone-trial-20261026.md；后者未安装，不能继承本轮隔离包的全部运行结论。
