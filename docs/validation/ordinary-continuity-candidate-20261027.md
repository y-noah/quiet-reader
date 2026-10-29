# 当前普通候选：停止动作与事件组连续性（2026-10-27）

待测文件 C:/Users/10197/Documents/Codex/New-next-trial/New-20261027-cc5a55d0.apk，319449810字节，SHA256 cc5a55d06e845cb5898fa1732e667b9c9ea678e2c7f8b688d4108dd37c9cf532。替代e94753dd，包含停止播出/终止合作严格主体准入及手动拆分不散开其他已接受事件组的修复。未装手机、上传、推送或发布。

## 构建和检查

证据 artifacts/local-ai/ordinary-continuity-20261027/。本轮原签名普通App重新R8构建终态成功，mapping、BuildConfig、包和驱动归档。app.quietreader，versionCode47/0.4.0-upgrade-probe，target36，DEBUG=false、RANKING_MEASUREMENT_ENABLED=false。

原签名证书SHA256 ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37 验签通过。五项模型文件精确清单匹配、六个64位ELF的16KiB LOAD前置检查通过。所有ZIP成员可识别明文凭据扫描无命中；无fixtures/loopback/p12测试资产。以上不证明任意秘密不存在、ARM实机资源或全部页大小兼容。

## 精确包模拟器覆盖验证

专用quiet-reader-upgrade-api36/emulator-5562保留e94753dd/schema7数据。升级前12项、仅-r覆盖新App和同签名驱动后12项、双模型/原生界面19项、推理后再检查12项，本轮55项0失败，各组INSTRUMENTATION_CODE:-1。未卸载或清数据。

旧收藏元数据、外观字体、来源会话模式、阅读位置与展开状态、持久合成WebView Cookie、schema7和旧收藏幂等迁移保持。实际512维embedding、reranker和SentencePiece JNI运行，模型服务释放退出。合成Cookie不代表真实账号登录已验证。

拉取实际安装base.apk与候选散列一致。安全目录current-candidate.json已指向此包。结束后App强停、模拟器关闭，进程检查0，userdata保留；未访问手机。

55项是普通包覆盖升级和模型主路径证据，不直接触发新增停止动作与拆分分支。相同生产源码的隔离测量R8包此前分支23、短标题23、阅读核心40共86项通过，见visible-group-continuity-20261027.md；不能混称普通包全分支运行通过。完整JVM659和Debug/Release lint沿用本轮此前生产代码验证，打包阶段无生产修改。

## 未完成

真实虎扑/知乎登录权限、贴吧楼层与楼中楼及切帖、Find X7 Ultra整App峰值RAM≤1GB、拔线耗电/温升、长期软淘汰与2GB以上正常预读、完整TalkBack/全页视觉、独立新事件质量及个人推荐效果仍需验证。开发样本已用于调参，不能称独立质量结果。手机按用户安排下班后连接，尚未更新。
