# 阅读布局与停止滚动导航行为 — 2026-10-27

## 用户最新要求

顶部栏随向下浏览隐藏，停止后继续隐藏，只有向上浏览才显示。取代旧版“静止时显示”要求。初次进入和切到设置等页面仍提供可操作入口；不把系统状态栏隐藏，不增加定时器或后台工作。

FeedNavigationLayout.scrollState(IDLE)现在只清空方向累计，不再显示工具栏。feedPage相同页面通知不再重置显隐，避免首页内容更新时突然弹出。真正页面切换维持正常导航入口。列表尺寸和内容位置不随顶部栏改变。

## 导航验证

证据 artifacts/local-ai/navigation-no-idle-20261027/。增强后的手势测试实际抬手并等待ListView报告IDLE，再观察250ms，不用取消事件冒充正常停止。旧生产APK18项3失败/code0：停止隐藏、隐藏无障碍焦点、同首页更新不显示。修改后标准窗口18/0、320dp/150%字号18/0，均code-1；包括向上重新显示、刷新阈值/取消/横滑/加载拦截、中途下拉不刷新和固定列表高度。初版测试取消手势只检出了同页更新1失败，保留before.txt，不冒充已测到idle。最终证据为before-final.txt、after-final.txt、narrow-large.txt。

Debug构建通过；最终双Lint见lint-final.log。原先当前666项JVM在本轮表情改动后通过，导航类是Android视图逻辑，本项由实际手势回归验证，不将早先JVM结果说成导航专用测试。

## 阅读页左右系统栏

MainActivity和SettingsActivity现将左右systemWindowInset加到原18dp边距。SettingsInsetsProbe对真实两个Activity根视图注入非对称、对调、重复及归零侧边距，保留顶底边距和正文边距。旧APK24项12失败；修正后24/0，after-raw.txt有code-1。旧before-runtime.txt和初次after-runtime.txt使用非raw输出，仅用来留存失败/计数，不伪称含状态码。原阅读外观连接树检查43/0；非raw输出，限制如实保留。不是物理刘海设备或全部TalkBack证明。

证据 artifacts/local-ai/settings-insets-20261027/；前一轮模拟器传输失败记录保留，本轮恢复后完成对应检查。

## 纯表情段距

保留源段落、换行和阅读锚点，只对全部内容由有效行内表情跨度覆盖的段落使用emoji-line样式：段后距4px、行高1.55。普通文字/混合文字/有链接/无效跨度保留原段距。不合并原文行，不改缓存内容结构，不额外获取图片或模型推理。一次线性检查，没有新线程或计时器。

新增4项JVM验证段落及不同阅读锚点保持、文字段距不误判、单段多表情、无效/重叠/链接跨度降级。全量666项0failures/errors/skipped。Debug和Release Lint通过。实际统一阅读视图浅深色各32/0/code-1，含图片解码、文字放大、历史恢复、楼中楼、旧缓存边界及新增三表情段落几何检查。证据 artifacts/local-ai/emoji-line-spacing-20261027/，dark-compact.png目视检查；不能冒充真实来源换行结构的完整比对，也没有改变真实源段落顺序。

## 交付边界

三个源码修正均未更新手机；手机仍是已经安装核对的c050a471。新普通签名候选尚未构建和保数据升级验证，不改安全目录current-candidate指针。未操作手机、上传、推送或正式发布。模拟器显示恢复1080x2400/420dpi/fontScale1.0并结束隔离App；第一次误将emu作为Android shell命令，未执行关机，随后已使用host emu kill修正。最终命名进程状态记录cleanup.json。

原真实账号全面权限、逐条贴吧关系、ARM双模型全App峰值、拔线耗电及推荐质量验收仍开放。