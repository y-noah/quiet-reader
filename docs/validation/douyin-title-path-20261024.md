# 抖音真实榜单标题路径 / 2026-10-24

上一轮有生产首屏提取修复及真实回归，属于进展。本轮完成独立浏览器配置对比，并从当前真实榜单返回的URL发现另一处生产路径缺陷。

## 现场配置对比

BrowserConfigurationProbe固定来源为抖音公开热榜，不操作登录/验证码，不导出HTML、Cookie或账户存储。独立控制网络图片阻断与桌面宽视口；全部配置都要求用户手势播放并拦截已有媒体文件扩展名请求。每种只创建一个WebView并在finally释放；名义30秒观察窗口不等于强制墙钟上限，UI/JS/解析耗时使本轮记录30.8–34.6秒。

| 报告（artifacts/local-ai） | 图片阻断 | 宽视口 | 条目 | hot链接 |
| --- | --- | --- | --- | --- |
| browser-config-normal-api36.txt | 否 | 否 | 50 | 51 |
| browser-config-images-api36.txt | 是 | 否 | 50 | 51 |
| browser-config-wide-api36.txt | 否 | 是 | 0 | 0 |
| browser-config-production-api36.txt | 是 | 是 | 50 | 51 |

四次主页面均无HTTP/网络错误，document.readyState为complete。普通视口innerWidth824，宽视口2560。宽视口单次无条目、完整生产配置却成功，因此不能把失败归因于宽视口，也不能断言图片阻断是问题；本轮没有改生产资源/视口/超时设置。观察配置使用定时采样，仍不等于最终生产DynamicBoard现场稳定性验收。

## 已确认并修复的生产缺陷

真实热榜返回的形式是 `/hot/2711309/百分号编码的中文标题`，而DouyinArticle原先仅接受`/hot/数字`，使来源自己的合法列表入口被正文解析和导航身份检查拒绝。

新增严格内容路径识别：hot允许数字ID后有一个标题段与可选尾斜线；video仍只允许数字ID。身份比较忽略展示标题别名，仍以精确topic/video数字ID核验；hot跳video仍要求唯一且匹配的sentenceId。正文owner.hotId/awemeId校验、权限/付费校验保持。未知域、非数字ID、多余嵌套路径和视频标题路径拒绝。保留原请求URL与缓存身份，不迁移候选/反馈键。

两个新增JVM测试覆盖正式榜单解析→保留真实形态入口→正文owner匹配的整条路径、纯编号/标题别名/转视频、错误编号及非法路径。DouyinReaderProbe也改成真实路径形态，使用合成受控页面经实际DynamicReader→快照→会话→缓存→统一渲染：douyin-title-path-contract-api36.txt为9/0，不把合成正文称为真实平台认证。

build-douyin-title-path.txt成功；JVM323，失败/错误/跳过均0；diff --check通过。

## 真实正文状态与后续

live-douyin-title-path-topic-api36.txt使用上述本轮实际观察到的完整中文编码链接，经正常生产正文路径读取，约25.5秒后超时，content=false/cacheHit=false/readable0/1。这证明路径兼容修复尚不足以让当前真实正文可用；没有正向正文/封面/评论样本，默认入口仍不迁移。probeErrors0不是来源通过。

下一步需增加正文取得阶段的有限结构诊断，区分未收到当前内容状态、来源权限限制与异步加载，避免继续猜测配置。其他统一阅读/真实账号、推荐质量、离线学习负例、FindX7Ultra资源耗电与最终升级验收仍未完成。最终安装正常debug store runner，本轮全部命令已完成；未提交、推送、发布或改品牌。