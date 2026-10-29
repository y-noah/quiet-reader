# 最新混淆包：文字阅读与新话题评价 / 2026-10-25

## 范围与结果

本轮新增 ReleaseReadingFlowProbe 的实际非 debuggable UI 检查，不改生产实现、R8 keep 规则、默认版本、品牌或签名；不提交、推送或发布。既有26项扩为35项，Android API36 emulator-5560 实际执行 **35 checks / 0 failures**。

新增验证：配对评价落盘包含推理前完整反馈账本指纹与候选成员来源记录；评价库版本为2；UI准备冻结已有身份；旧候选无法充当准备后的新内容；插入一个明确标记的合成后续V2EX候选后，实际控制器/双模型/评价UI只接纳该候选；第一次选择前已持久消费身份与文本hash；会话保留原协议cutoff及晚于cutoff的first_seen；不确定不算少看；重复启动不能再次消费同池；评价不改变反馈和当前卡片快照；偏好版本变化明确要求重新准备。

原路径仍通过：本地实际模型向量和精排缓存、待接收快照、显式更新、收藏、统一正文展开/滚动/返回、短于20秒不记弱阅读、少看后新顺序及评价历史。数据全部为隔离包合成供应/正文缓存/选择，不是真实新闻或本人兴趣效果证据。

## 精确包

- app.quietreader.flowprobe，默认版本仍46；签名隔离诊断包，无生产诊断入口或生产类反射。
- App APK：319341343 bytes，SHA256 `c0125511ce3e2bedaf3c5741d6bb5d38d9bb32483dc9f6f41e255ef5f2ba5473`。
- Test APK：1951050 bytes，SHA256 `c779ee990015ae30a0bb3d34513ee31a25fa1da557d72fc7bc25ed751878540c`。
- 模拟器pm path实际base.apk SHA与上述App一致；覆盖安装保留数据，无卸载/清数据。
- 6个arm64/x86_64 ELF的LOAD满足16KB二进制前提，模型307948507 bytes。不是ARM真机或实际16KB内核运行证明。
- 38个ZIP成员、395086094解压字节常见明文密钥格式扫描，无命中；不是全部机密或网络行为审计。
- 构建前原flow App/Test/package等暂存文件已保存在本地archive-flow-before-prospective-*目录。

## 证据

artifacts/local-ai/ 下：build-minified-prospective-20261025.txt、minified-prospective-flow-20261025.txt、minified-prospective-binary-20261025.json、minified-prospective-scan-20261025.json、minified-prospective-installed-20261025.txt、release-discovery-flow-package.json。普通调试恢复记录 restore-store-after-prospective-20261025.txt。

安全截图目录 quiet-reader-review 下：minified-prospective-complete-20261025.png、minified-prospective-history-20261025.png、minified-prospective-reading-20261025.png。三张均已实际查看：完成摘要及历史的按钮可见、文字无截断遮挡，正文展开后排版与滚动区域可用。只覆盖当前密度/字号/浅色页面。

## 未关闭

当前App含最新贴吧楼中楼解析修复，但本次缓存正文流程不调用该解析器；其生产解析证据仍以受控debug讨论72/0和JVM461/0为界，不能声称新增R8解析样本通过。真实平台认证、长期微博新鲜度、贴吧异步/虚拟列表、Find X7 Ultra全进程1GB与耗电/热、本人独立评价与最终普通发行包验收均未完成。holdout_qualified仍false，未得出推荐提升结论。旧R8报告不再用来代替当前精确包，本轮也不代替完整交付审计。
