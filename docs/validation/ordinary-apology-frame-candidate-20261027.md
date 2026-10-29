# 普通签名致歉句式候选 — 2026-10-27

当前待测包：安全目录New-next-trial/New-20261027-39bffda4.apk，319449810 bytes；SHA256 39bffda40abffc183c994db942136b81ef37b59840fdce5e088186027d5de152。current-candidate.json已更新。手机仍c050a471，未操作手机、推送或发布。

本普通包新增完整致歉句式准入，保留原有缓存满额替换修复。新句式只准入实际双向模型复核，不直接合并或降低7.0阈值；来源与热度不叠加、用户拆分规则不变。源码670 JVM及隔离R8正常Controller合并/更新/拆分等89项范围见apology-frame-admission-20261027.md，本轮不将那些成绩称为普通包直接执行同一场景。

## 本轮普通升级证据

artifacts/local-ai/ordinary-apology-frame-20261027/。原签名证书SHA256 ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37验证成功；app.quietreader47 /0.4.0-upgrade-probe /target36，DEBUG=false、RANKING_MEASUREMENT_ENABLED=false。模型manifest和逐文件摘要、64位ELF16KiB对齐检查成功，全ZIP可识别明文凭据扫描无发现，未含fixtures资产/jks/p12。mapping和BuildConfig留存。

沿用quiet-reader-upgrade-api36/emulator-5562既有schema7与合成升级数据，不卸载、不清数据、不重种。旧3d候选基线12/0，原位升级后12/0，实际模型/UI19/0，重复检查12/0，共55/0，各输出唯一INSTRUMENTATION_CODE:-1。原保存条目JSON及完整载荷、字体/外观、来源会话模式、阅读位置/展开信息和合成持久Cookie保持。实际512维归一化向量、有限精排logit及SentencePiece JNI通过，模型释放解绑后退出。

拉取实际安装base.apk，hash与构建包完全一致；安全目录副本再次核对一致。verification-summary.json、package-verified.json及原始四份instrumentation输出留存。首次签名命令因JAVA_HOME未设置未运行，signature-environment-failure.txt保留，配置既有JDK后完成校验，不是签名损坏。

退出普通包/驱动并关闭模拟器，保留userdata；cleanup.json复核命名模拟器进程0。本轮无真机、网络抓取或账号内容测试。

## 仍待验证

真实账号虎扑/知乎受限正文与评论、贴吧逐层父子关系及续页、完整App ARM含模型/WebView同时峰值≤1GiB、拔线耗电温升、长期在线预读/磁盘循环、本人推荐收益及更多独立事件质量仍开放。V2EX/BBC最近一次可达性限制未由本轮修复。普通新包尚未安装手机，不因升级55项通过而声称全部功能完成。
