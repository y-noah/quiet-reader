# 来源浏览页主动读取 / 2026-10-24

## 实施范围

发现页打开尚未默认统一阅读的抖音、小红书时，仍进入来源页，但提供“读取当前页”和“返回发现”。用户主动读取后，经实际Activity结果回到App统一图文/讨论/视频兜底布局。仅本次精确URL获得统一阅读许可，防止MainActivity再次把同一内容退回来源网页；没有将这两个平台的自动适配标记为完成。

返回快照需通过来源、URL所属域、会话、缓存namespace、正文可展示状态及十分钟新鲜度核验。原URL且元数据身份一致时保留发现候选；在来源页导航到另一篇时仅打开实际内容，不向原候选归因。登录要求/来源不可用留在来源页提示，取消不打开阅读器，结果只消费一次。

普通主动导入使用Repository.importBrowserSnapshot，不清空其他平台已预取的正文。原MainActivity显式登录返回仍使用importSourceRead并保持原失效语义，两者范围不同。来源账号变化仍由各URL会话身份隔离；不能把缓存保留理解为允许跨账号复用正文。

ArticleDiskCache和Repository允许只有可信视频兜底元数据、没有正文段落的结果缓存和恢复；不下载视频媒体，登录/来源不可用/过滤守卫保留。

## 已取得证据

报告均位于artifacts/local-ai，正常debug应用、API36模拟器。页面是隔离合成域名上的受控内容，不使用真实平台账号。

| 报告 | 检查/失败 | 证据与范围 |
|---|---|---|
| source-browse-import-api36.txt | 3/1 | 首轮发现MainActivity非统一来源守卫导致返回来源页循环，保留失败报告 |
| source-browse-import-scoped-api36.txt | 49/0 | 精确URL主动许可修复后的真实Activity/WebView/结果/缓存链路 |
| source-browse-import-touch-api36.txt | 55/0 | 对可见原生按钮使用真实屏幕坐标触摸 |
| source-import-preserve-prefetch-api36.txt | 60/0 | 最终普通导入保留无关预取；匹配内容、导航另一篇、视频元数据磁盘恢复、受限页面和取消 |
| source-import-recreation-api36.txt | 73/0 | 新增真实后台DiscoveryActivity.recreate；旧实例销毁、新实例从系统保存状态恢复候选身份和元数据，来源Activity实际结果抵达新实例并进入统一阅读，其余场景回归 |
| login-read-flow-source-browse-import-api36.txt | 61/0 | 原登录读取、会话竞态、磁盘恢复及20秒阅读学习回归 |
| article-disk-source-browse-import-api36.txt | 37/0 | 原磁盘缓存回归 |

61/0和37/0运行在新增普通导入专用方法之前；之后仅Discovery调用专用方法，原Main登录导入路径未变。最终build-source-import-preserve-prefetch.txt构建成功；重新汇总单元测试XML为325项，失败/错误/跳过均0。

source-import-toolbar-api36.png与source-import-reader-api36.png已目视检查：四个工具栏入口均可见，标签完整；统一阅读标题、作者、提示与正文无横向溢出。该结论仅覆盖本次默认主题/字号/屏宽，不能代替全部无障碍及深浅色验收。

## 仍需验证

build-source-import-recreation.txt构建成功。73/0是实际Activity重建和结果投递，不是进程死亡恢复。没有因此改变默认来源路由。

缺失/错误来源返回、进程死亡、全部字号主题与辅助功能仍需进一步验证。真实抖音/小红书账号、图片封面与自动正文稳定性仍未通过；本功能是用户主动导入路径，不能替代U05要求的默认统一入口。用户随后澄清核心为文字流平台的个性化热门内容，抖音/小红书完整适配转为后续，不再以这些来源适配为当前推荐主线前置。整体目标未完成，未提交、推送、发布或修改品牌。
