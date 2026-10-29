# 普通签名财富纪录去重候选 — 2026-10-28

候选 New-next-trial/New-20261028-055b1f1c.apk，319449810 bytes，SHA256 055b1f1c2455030dc53f6dacb1feb9ba6cd6c6b31e764cc1479a7371b2114597。仅在专用模拟器验证，未安装手机、推送或发布；手机仍为 New-20261027-f0d4dd5a.apk。

本包包含 claim-evidence-v10-wealth-record-amount：完整同主体、同范围财富纪录句式允许一方省略金额后进入既有双向模型核对，两方金额、单位或币种冲突仍拒绝。没有直接合并或热度相加。源码674项JVM及双Lint、固定开发样本和隔离R8分项范围见 [源码去重验证](wealth-record-event-admission-20261028.md)，不能解释为独立总体去重质量。

普通签名构建终态成功后保存 app.apk、test.apk、BuildConfig.java、mapping.txt 和 package.json 至 artifacts/local-ai/ordinary-wealth-record-20261028/。模型清单及每个模型文件大小/hash一致、64位ELF LOAD对齐检查无失败，可识别明文凭据扫描无发现。原签名证书SHA256 ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37。包名app.quietreader、版本47、target36，DEBUG=false、RANKING_MEASUREMENT_ENABLED=false。

专用 quiet-reader-upgrade-api36 模拟器 emulator-5562 保留原有schema7状态，不清数据、不重新种入fixture。升级前实际安装hash为f0d4dd5a；before/after/models-ui/repeat分别12、12、19、12项，均0失败、唯一终态INSTRUMENTATION_CODE -1，共55项。覆盖升级持久状态、设置、合成WebView会话、旧保存数据及阅读状态、实际双模型/JNI和释放。models-ui使用short输入，不计作最大输入测试。拉取升级后的实际base.apk，hash与本轮候选完全一致。安全目录副本另核对同一hash。

cleanup.json记录模拟器已关闭、匹配进程0、adb中emulator-5562已消失，userdata保留。本轮没有操作手机或新增手机测试驱动。

这些结果不替代真实虎扑/知乎受限正文评论权限、贴吧全部分页及短表情身份、正常真实网络高负载ARM连续内存峰值、拔线耗电温升、长期缓存、本人推荐收益及独立未见去重质量。整体验收仍开放，未发版。
