# 阅读外观操作可达性与当前候选（2026-10-26）

## 修复

SettingsActivity的可点击文字原先是普通TextView；现使用已有ActionTextView，以标准Button角色暴露点击操作。返回、五个外观选择器、恢复默认均明确focusable；恢复默认补最小48dp高度。静态文字仍为TextView。没有新增设置项、网络或模型行为。

## 当前R8验证

ReleaseAppearanceLayoutProbe只允许app.quietreader.discoveryprobe，通过连接的AccessibilityNodeInfo执行ACTION_CLICK，不反射生产类、不调用隐藏方法。emulator-5560，320dp/150%系统字号、浅深色各37/0，最终74项通过。五个实际选择器打开和取消、重置确认与取消、固定返回退出均通过；全部操作入口实际可滚动到达、Button角色/可聚焦且至少48dp。取消后真实窗口回到外观页，偏好保持；探针的临时模式修改也完整恢复。

首轮浅深各31/1，仅首屏返回节点未就绪；后续同按钮正常。驱动改为等待连接树中的实际节点后各31/0。截图人工复核发现取消动画尚在，故增加“取消后实际返回页面”断言、单独重置确认截图及400ms自然转场等待；最终各37/0。保留first-*与second-*日志，不把它们混成最终74项或性能证据。

已人工查看最终深色底部、深色重置确认及浅色底部截图：正文可读，重置操作可见，取消后无残留弹窗；滚动顶部的部分裁切属于正常滚动边界。不是完整TalkBack发声顺序、全页面或所有配置实际修改验收。本轮只检验打开/取消不误改。

证据：artifacts/local-ai/minified-appearance-layout-20261026，package-final.json、mapping-final.txt、installed-app-sha256.txt、summary.json。App SHA15bd72d2e74c023d7b1a7f470f710fe0cfc1a35f33abdf7051c7ccb8558088d7与实际安装一致。显示尺寸、密度、字号均恢复，两个隔离包停止，ps匹配0，全部命令会话结束。

## 普通候选更新

原签名普通候选已重建，当前使用C:/Users/10197/Documents/Codex/New-next-trial/New-20261026-8bf38039.apk；旧6941fe60候选保留作历史，不再作为下一轮首选。

新SHA8bf38039a921d20bc4cea8dde9768db0160005aece62b142386033644be6e2dd，319432842字节；app.quietreader，47/0.4.0-upgrade-probe，显示名New，非debuggable。签名v2通过、证书ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37与原普通包一致；仅INTERNET/ACCESS_NETWORK_STATE。LintRelease及94XML/567单元测试通过，0失败/错误/跳过；不宣称Lint无警告。六个64位ELF LOAD段16KB对齐通过，模型资产307948507字节；80ZIP成员常见明文密钥格式0命中，不代表任意秘密不存在或ARM16KB实机通过。当前生成目录已被后续隔离构建覆盖，不用它证明普通候选开关状态。

普通包证据：artifacts/local-ai/next-phone-trial-appearance-20261026。未安装普通候选、未操作手机、未提交/推送/发版。隔离包局部验收不自动成为普通候选精确运行/升级证据。真实账号全文、本人推荐收益、ARM全进程峰值和非充电长时能耗/发热等仍开放。
