# 顶栏 Logo 与短卡片留白修复

用户要求：左上角 New 文案换为已有牛头 Logo，消除“武契奇承认败选”等短标题上方明显空白。

DiscoveryActivity 首页和设置根页复用 ic_launcher_foreground 牛头矢量，56×48dp，保留 New 无障碍名称；真正子页仍显示页面标题。卡片关闭基线对齐并顶部对齐，最小高度80→64dp，保留菜单48dp触摸区域及底部对齐。仅改变展示布局。

验证仅使用 emulator-5560 的独立 app.quietreader.discoveryprobe，无手机安装或发版：

- 默认1080×2400、420dpi、字体1.0、深色：81项，0失败。
- 960×2160、480dpi（320dp）、字体1.5、浅色：81项，0失败。
- 两轮包含长卡片复用为无简介短卡片、标题起点等于顶部内边距、无额外高度、简介隐藏、Logo可见及无障碍名、设置往返。
- 两轮首页截图已目视核对；截图使用合成知乎布局数据，不能作为真实新闻抓取证据。
- 当前JVM XML汇总526测试，0失败、0错误、0跳过。包含其他模块测试，不代表全部产品验收。

日志：artifacts/local-ai/logo-compact-cards-20261026/layout-dark-default.txt 与 layout-light-narrow-large.txt。

截图：C:/Users/10197/Documents/Codex/quiet-reader-review/New-logo-compact-default-20261026.png 及 New-logo-compact-narrow-large-20261026.png。

测试后恢复模拟器屏幕、密度和字体，并停止独立测试应用。手机现有包尚不包含本次修改；全项目未完成。
