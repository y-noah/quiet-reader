# 普通签名缓存替换候选 — 2026-10-27

当前待测包：安全目录New-next-trial/New-20261027-3d1434ae.apk，319449810 bytes，SHA256 3d1434ae9aef1d37b6566929a5c70bf0a39a7d12b4be6bb53f7f296161284423。current-candidate.json已更新；手机仍此前c050a471，本轮未操作手机、上传/推送或发布。

包含图片/正文缓存达到文件数上限时，替换旧条目不误占新增名额的修复。此前源码级修复前后及Debug668 JVM/Android正文40项证据见cache-replacement-quota-20261027.md。本轮没有额外生产改动，不把Debug专用满额测试说成本普通R8的直接满额运行证明。

## 普通包与保数据升级

目录artifacts/local-ai/ordinary-cache-replacement-20261027/。原签名证书SHA256 ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37验证成功；app.quietreader v47 /0.4.0-upgrade-probe /target36，DEBUG=false、RANKING_MEASUREMENT_ENABLED=false。模型manifest和每个文件匹配、64位ELF16KiB LOAD检查通过；全ZIP可识别明文凭据扫描无发现，无fixtures资产/jks/p12。mapping、BuildConfig、实际APK/驱动和检查JSON均留存。

专用quiet-reader-upgrade-api36/emulator-5562沿用原有schema7与合成旧版状态，不卸载、不清数据、不重种。成功基线before-schema7.txt12/0，原位替换后after.txt12/0，实际双模型/UI models-ui.txt19/0，再次repeat.txt12/0，共55/0，各为唯一INSTRUMENTATION_CODE:-1。原收藏JSON和载荷、字体/外观、来源会话模式、阅读位置/展开信息、既有合成持久Cookie均保持。模型实际512维向量归一化、有限精排logit/SentencePiece JNI通过，释放解绑后模型进程退出。

拉取模拟器实际安装base.apk与本轮app.apk哈希匹配，安全目录副本再次核对一致；verification-summary.json和package-verified.json保存结果。不是物理ARM峰值或实际登录账号权限证明。

## 失败记录与范围

首次before.txt在模拟器启动完成前发起，ActivityManager尚不可用；后来明确读取sys.boot_completed=1。before-ready.txt及更新驱动后的before-current-driver.txt各12项1失败，源于normal-upgrade-before模式硬编码schema6，本次保留数据已是schema7。开始误以为是旧驱动，核对当前源码后纠正；改用现有normal-upgrade-verify模式核对同一份schema7数据，未改变数据库或放宽断言，before-schema7.txt才是成功基线。两次失败不纳入55成功项。之前cbe的before.txt同样使用schema7校验，未把本次解释为6→7迁移。

本轮没有重做顶部栏手势/图片/所有来源账号完整验收，不能把历史不同包的检查当本包全部覆盖。未进入foreground-models隔离测试分支；正常包禁用测量开关。整App ARM峰值≤1GiB、拔线能耗温升、真实账号正文/评论、贴吧逐条父子关系、长期物理磁盘及个人推荐收益仍开放。

测试结束停止普通测试包/驱动，emu kill关闭专用AVD，保留userdata。cleanup.json最终命名模拟器进程0。未修改显示或用户手机设置。
