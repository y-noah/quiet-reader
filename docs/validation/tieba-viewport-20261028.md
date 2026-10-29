# 2026-10-28 贴吧正文采集桌面视口

## 可重复来源差异与对照

上一实际R8结果独立来源页14条、正文采集8条。进一步记录发现，桌面贴吧没有viewport meta；来源登录滑窗开启wide viewport，CSS980x735；默认正文采集WebView为CSS412x915。两者都能渲染公开内容，但源站虚拟列表初始化的窗口不同。

在同一未改产品代码的R8 App上，public-tieba-times.txt 默认15/0，采集8条；public-tieba-times-wide.txt 仅用测试生命周期hook给采集WebView设置useWideViewPort/overview，15/0，采集13条、4父楼、2父楼图片。没有注入源码、正文或时间；独立来源14条可逐一验证这13条。两次都有真实源站后续登录门槛，不声称匿名完整分页。

## 产品变更

SourceSession.configure仅在TIEBA桌面模式启用Android宽屏CSS视口与overview。使用已有MATCH_PARENT原生表面，不建立1280dp登录滑窗；显式手机模式不触发该分支。来源登录页本身随后继续采用原有显示设置。DynamicReader和新建TiebaReplies共享配置。

不新增请求函数、周期任务或模型；保留媒体手势、图片阻断、单采集并发、解析字节/回复上限及生命周期取消。更大的CSS窗口会让来源渲染更多公开DOM，不能据原生尺寸相同宣称RAM/能耗没有变化。

## 最终生产验证

production-tests-lint.txt：隔离正式诊断参数下847 JVM/118套，失败/错误/跳过0，双Lint成功。摘要和Lint XML归档。

production-public-tieba-times.txt：新实际R8 App、不装宽屏测试hook（wideSettingsExperiment=false），15/0/唯一raw -1。独立来源14条，实际采集13条，阅读18区块/13楼中楼，无横向溢出；CSS980x2175，原生像素1080x2400。全部13条采集ID都进入正常Home暂停后的新历史，同时与独立来源精确id/pid/time比对；四个父楼、两张父楼图片，登录门槛保持。来源还有1条不在本次采集窗口，不计为分页成功。

production-tieba-child-progress.txt：受控R8自动续载15/0/raw -1，追加三条表情子回复而父楼保持1个，窗口重叠/历史不重复。不代替公网完整分页。

production-installed.json核对实际App/driver与production-app/test.apk相同。原始A/B包、最终包、mapping、四份Android原始日志与构建/测试日志位于artifacts/local-ai/tieba-viewport-20261028。模拟器关闭保userdata，终态见cleanup-final.json。

普通原签名候选/升级另见ordinary-tieba-viewport-20261028.md。手机未操作、未发布。真实登录权限与完整分页、个人推荐/独立去重质量、ARM整App峰值≤1GB、拔线耗电温升、长期缓存和出站审计仍开放。
