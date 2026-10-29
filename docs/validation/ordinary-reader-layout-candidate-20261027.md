# 普通签名阅读布局候选 — 2026-10-27

## 当前候选与手机状态

安全目录 New-next-trial 当前文件 New-20261027-cbe07180.apk，319449810 bytes，SHA256 cbe071801cbd78b98a5e8825c9f6b545e7a2c103cc2ecbc16d2e5f29cd8cea7a。原包名 app.quietreader，v47 /0.4.0-upgrade-probe /target36。手机仍为此前真实安装核对过的c050a471；本候选未安装手机，未上传、推送或正式发布。current-candidate.json已区分新候选和lastPhoneCandidate。

包含停止滚动后顶部栏保持隐藏、相同首页通知保持显隐、纯表情段距、正文和阅读设置左右系统栏边距。实现及Debug分项证据见reader-layout-idle-navigation-20261027.md；本轮没有新增生产代码。

## 普通APK与保数据升级

证据 artifacts/local-ai/ordinary-reader-layout-20261027/。原签名证书SHA256 ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37 验签通过。DEBUG=false、RANKING_MEASUREMENT_ENABLED=false。模型清单及每个模型文件精确匹配、64位ELF16KiB LOAD检查通过；全ZIP成员可识别明文凭据扫描0发现。DEX保留纯表情样式及v8判决准入标记，无测试fixtures或p12。归档mapping与BuildConfig。初次签名检查漏设JAVA_HOME，工具未启动；失败日志保留signature-env-failure.txt，设定本地JDK后实际验签通过，不算APK签名失败。

专用quiet-reader-upgrade-api36/emulator-5562保持原数据启动，不卸载、不清数据、不重新种植旧状态。替换前12/0、原位-r替换后12/0、实际双模型及UI19/0、重复保数据检查12/0，共55/0，均INSTRUMENTATION_CODE:-1。schema7、原收藏JSON和载荷、字体/外观、会话模式、阅读位置及展开标记、既有合成持久Cookie保持。实际embedding512维归一化及reranker/SentencePiece JNI完成，模型进程释放后退出。不是物理ARM/真实账号或个人推荐收益证据。

拉取实际安装base.apk哈希等于候选，复制至安全目录再次核对哈希一致。报告verification-summary.json列出本轮每组计数，不把历史测试加成本轮总数。

## 实际压缩包顶部栏操作

在同一普通APK的真实缓存首页，通过adb实际滑动并抬手，然后uiautomator空闲等待返回后截图：feed-before有设置节点[907,128][1033,254]；向下浏览停止后feed-down-idle没有设置节点且截图无顶部栏；向上浏览feed-up设置节点恢复原边界，截图显示两入口。toolbar-ui-observations.json与toolbar-comparison.png留存并目视核对。未用反射、替代布局或生产测试入口。这里是一次实际普通包场景观察，未额外虚构自动断言计数、精确idle毫秒或覆盖全部机型。之前Debug真实ListView等待IDLE的标准/小屏各18项仍是独立分项证据。

## 清理和开放项

仅操作专用模拟器。两个明确临时截图/XML路径删除，App停止，AVD正常关闭，保留userdata；命名进程最终状态见cleanup.json。没有手机操作。

本候选是下一轮测试包，不代表整体目标完成。仍需：手机新版交互、全部真实账号正文/评论权限、逐条贴吧身份与父子关系、双模型ARM全App峰值≤1GiB、拔线耗电温升、长期实际磁盘软淘汰、完整TalkBack/页面视觉、独立事件去重质量和个人连续推荐收益。晚间手机分项证据保留在phone-evening-20261027.md，不能因新候选出现而抹去，也不能把旧手机观察当作本APK真机验收。