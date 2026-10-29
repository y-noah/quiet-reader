# 文字操作控件的无障碍语义 / 2026-10-25

## 问题与修复

DiscoveryActivity和MainActivity的文字操作控件复用TextView，虽有名称、点击监听和既有尺寸，未提供Button角色。新增ActionTextView，通过标准getAccessibilityClassName对可点击文字返回android.widget.Button；不可点击文字仍使用TextView语义。两页面的文字构造函数复用该类型，按钮工厂明确可聚焦。发现页状态文字沿用已有随页面切换的clickable/focusable控制，静态来源/稍后读说明不获得按钮角色。既有阅读页图标使用ImageButton，不修改。无新增网络、定时器、模型调用或资源预算。

## 证据

- scripts/models/build-probe.ps1 -Offline -Target store：编译成功；当前JVM XML汇总425 tests / 0 failures / 0 errors / 0 skipped。
- 在现有Android16/API36模拟器emulator-5560，保留原签名覆盖安装debug/store诊断包，没有卸载/清数据。
- DiscoveryLayoutProbe在实际View创建AccessibilityNodeInfo，核对来源、稍后读、发现的Button角色及focusable/clickable，再调用标准ACTION_CLICK；验证实际来源页标题、列表重置、返回发现页。未直接调用performClick来代替新增导航检查。
- 320dp、系统字号150%，浅色42/0、深色42/0。包括原有实际兴趣弹窗、文本边界、48dp导航目标、长卡片菜单可达性。日志：artifacts/local-ai/accessibility-layout-{light,dark}-20261025-api36.txt。
- 同配置统一阅读实际WebView，阅读字号28，5/0：长标题/作者/链接/操作换行、整页无横向溢出、主题和操作高度。日志：artifacts/local-ai/accessibility-reader-layout-20261025-api36.txt。
- 人工查看浅色首页截图，未见标题、导航文字重叠；合成卡片和极端大字号不代表真实内容质量。脚本finally恢复模拟器尺寸、密度、系统字号。

## 边界

这是标准节点角色、导航动作和布局回归的局部证据，不是完整TalkBack验收。尚未逐项验证实际朗读顺序、卡片分组、WebView正文/评论朗读、所有菜单/阅读按钮的辅助功能动作；本轮阅读页测试只证明布局。没有以此关闭A09整体门槛。真机内存/耗电、真实登录下知乎正文、个人长期推荐效果等仍未完成。未提交、推送、发版、改名或更换logo；此前R8/普通升级APK不是本轮新源码的构建产物。
