# News 0.3.33 本地候选版验收记录

2026-10-11，versionCode 44。已完成多轮修复和测试，生成原签名安装包；未推送代码、未创建 GitHub Release。

## 本轮修复

- 总榜最多 150 条，去重后只取各来源统一榜位分的最高值，不叠加热度、不奖励重复出现。多来源入口保留。统一榜位指数不等同于跨平台实际浏览人数。
- V2EX 直接读取 https://www.v2ex.com/?tab=hot 的主列表，排除侧栏；使用手机现有网络/VPN。
- 百度搜索、贴吧话题与帖子、B站视频、抖音阅读分别适配手机宽度。保留“原网页 / 手机排版”和独立登录滑窗。
- 抖音卡住的实测原因：详情接口返回 HTTP 200 但正文为空；热榜页自动转入详情并重建内容后，原播放器无法初始化。仅改 CSS 无法解决。
- 新后备阅读区使用当前网页已经提供的公开视频与描述，独立于网站重建的节点。支持点击播放、全屏与返回、评论继续加载、展开回复。评论请求使用同源网页会话；没有导出 Cookie。
- 优先使用来源提供的 720p H.264 视频。点击播放前不下载视频；切回原网页时停止后备播放并取消评论请求。不会自动绕过私密、付费或登录要求。
- 修复登录返回时重新进入有完整数据的热榜入口，以及原网页切回手机排版的恢复。既有 1280 × 960 dp 双向登录滑窗保留。

## 最终通过的检查

| 检查 | 结果 |
| --- | --- |
| JVM 单元测试 | 202 项，0 失败 |
| 四平台布局、320dp/412dp、SPA、切换与受限内容回归 | 75 项，0 失败 |
| 抖音实网完整旅程 | 15 项，0 失败 |
| 12 来源合成会话共享、外链隔离与浏览行为 | 65 项，0 失败 |
| 登录双向滑窗、居中、触摸和重复进入 | 40 项，0 失败 |
| 总榜 150 条、迁移、导航等回归 | 339 项，0 失败 |
| 原签名压缩构建安装后 UI 回归 | 48 项，0 失败 |
| lintRelease | 0 错误，19 警告 |
| git diff --check | 无空白错误 |

抖音实网经历多轮检查：真实视频解码与播放进度增长；首批评论之后继续分页；楼中楼回复；真实触摸触发浏览器全屏并返回；登录返回路径重新加载阅读区，旧播放器释放、不自动播放。通过轮次观察到 720×960、readyState=4、播放时间 9.65 秒以上。测试明确排除隐藏的预创建播放器，缺少正文节点不算通过。

此前 9 项、11 项播放与评论专项分别通过；最终 15 项旅程补齐了全屏和登录返回。一次全屏触摸检查失败；改用固定可见的真实触摸测试按钮后，确认 document.fullscreenElement 为 VIDEO、Android 全屏回调生效，返回也通过。数次模拟器进程中断未计入成功；恢复环境后重新执行并保留通过日志。

原签名压缩包实装还验证了抖音后备阅读区、来源内部替换、切回原网页和恢复手机排版。此前已实网核对百度搜索、贴吧话题/帖子、B站真实播放；本轮四平台布局再次回归。

## 验证范围

只操作独立项目模拟器（API 35，WebView 124），未操作用户手机。测试会话共享使用合成 Cookie/localStorage；未使用用户的真实账号、会员内容。抖音后备区覆盖网页已提供数据的公开普通视频和文字评论；不把图集、直播、私密或付费内容算作已通过。评论发布、点赞等互动继续在原网页完成。

测试安装包可覆盖原签名应用。保留原网页入口供来源异常时使用。未创建 GitHub Release。

## 安装包

- 文件：News-0.3.33.apk
- 大小：189978 字节
- SHA-256：5b85e36e39359791911e1635d6db9b99bfbcd29f842790cebf8c105ecf34754e
- 签名证书 SHA-256：ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37

## 证据与复现

主要日志位于工程 artifacts：mobile-douyin-reader2.txt、mobile-douyin-reader-comments2.txt、mobile-douyin-fullscreen-final.txt、mobile-fixture-reader-final.txt、reader-expandedBrowsingUi-final.txt、reader-loginWindowUi-final.txt、reader-fiveSourceUi-final.txt、reader-release-ui-final.txt、reader-release-build.log。截图保存在 mobile-douyin-accepted 和 release-reader-accepted。

手机布局回归可用 scripts/test-current.ps1 -Suite mobileLayoutUi；实网专项使用 MobileLayoutInstrumentation 的 live=true、source=DOUYIN 参数。正式包回归使用 ReleaseInstrumentation。工具缓存与私钥不包含在源码包内。
