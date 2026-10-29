# 普通签名产品参数去重候选 — 2026-10-28

候选 New-next-trial/New-20261028-9d05b7c4.apk，319449810 bytes，SHA256 9d05b7c4d93cb9ce184f87497721d96c0f4494ecb2c0ff47abe7e27d953515d8。仅专用模拟器验证，未安装手机、推送或发布；手机仍New-20261027-f0d4dd5a.apk。

本包增加完整产品发布参数省略的模型准入，仍保留数字/事实冲突保护、双向模型阈值和来源热度不叠加；范围和原开发漏项见 [源码与隔离R8证据](release-specifications-event-admission-20261028.md)。不将开发回归同5/8视为独立总体准确率。

普通构建终态成功后才保存app.apk、test.apk、package.json、BuildConfig.java和mapping.txt。证据目录 artifacts/local-ai/ordinary-release-specifications-20261028/。模型清单及5个模型文件hash/大小一致，6个64位ELF LOAD对齐前置检查无失败；可识别明文凭据扫描无发现。签名证书SHA256 ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37。包名app.quietreader、版本47、target36，DEBUG=false、RANKING_MEASUREMENT_ENABLED=false。

quiet-reader-upgrade-api36 / emulator-5562保留原schema7数据，不清数据或重新种fixture。升级前实际旧包hash055b1f1c；before/after/models-ui/repeat各12、12、19、12项，共55项/0失败，每份唯一INSTRUMENTATION_CODE -1。覆盖设置、合成持久WebView会话、保存条目/阅读状态、迁移完成标记及实际短输入双模型/JNI和释放。不是最长输入、真实账号或真实网络数据验收。

拉取升级后的实际base.apk与候选hash相同；安全目录副本另核对同一hash。cleanup.json确认App/驱动force-stop、模拟器关闭、匹配进程0、adb中emulator-5562消失，userdata保留。本轮没有手机操作。

真实受限账号全文/评论、贴吧全部分页与短回复身份、ARM正常真实网络连续峰值/最长输入、拔线能耗温升、长期在线缓存及本人推荐收益和独立未见事件质量仍开放。整体验收未完成，未发版。
