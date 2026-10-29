# 抖音/小红书统一阅读现场复核 / 2026-10-24

本轮没有修改生产默认入口；修复了 ArticleLiveProbe 的测试路径：抖音/B站榜单改用生产发现采集所用的 DynamicBoard，报告明确记录 browser/native/explicit-target。新增安全限定的抖音公开 /hot/或/video/目标诊断，复用原先显式公开笔记模式，不获取任意域名或账户接口。

## 现场证据

- live-xhs-unified-recheck-api36.txt：DynamicBoard 没有可用小红书列表，readable=0/1；公开发现页 HTTP200 也无当前签名笔记入口，不能证明正文可用。
- live-douyin-list-unified-recheck-api36.txt：普通观察 WebView 曾取得50条抖音热榜，htmlChars=830238。它没有使用生产 DynamicBoard 的全部资源/尺寸配置，且只证明当时列表成功。
- live-douyin-article-unified-recheck-api36.txt：旧诊断走native榜单，无可用列表。不能据此归因正文。
- live-douyin-browser-article-unified-recheck-api36.txt：修正后明确boardTransport=browser，生产DynamicBoard在期限内没有读到条目，readable=0/1。单次普通观察成功不能证明生产路径稳定，也不能据两次差异直接认定原因是图片阻断或视口。
- 从前次榜单诊断保存的确切公开HTML只提取三个/hot/数字路径，没有导出整个页面、Cookie或其他缓存。对其中/hot/2711331运行live-douyin-public-topic-recheck-api36.txt：explicit-target，浏览器正文读取25319ms后超时，content=false、cacheHit=false、readable=0/1。报告仅包含变化Cookie名称，没有值。这个单话题失败不代表所有抖音话题均失败，也不证明登录能修复。

probeErrors=0只表示诊断没有异常，不表示平台通过。没有正向正文、封面或评论样本，不将抖音/小红书切换默认统一入口，不把摘要或页面外壳当作正文。

## 构建与后续

build-live-douyin-public-target.txt成功；当前JVM报告321 tests/0 failures/0 errors。git diff --check通过（存在既有换行格式提示）。最终安装正常debug store runner，全部本轮命令已结束，未发布、推送或改品牌。

下一步需隔离普通观察页与生产DynamicBoard的配置差异，取得当前真实正文与权限样本；同时统一入口仍须有可用失败/登录回读路径，不能用单纯原网页当作统一阅读完成。平台偏好专项证据仍以platform-preferences-and-v2ex-20261024.md为准。本轮不增加推荐质量/真机资源/账号读取的完成声明。