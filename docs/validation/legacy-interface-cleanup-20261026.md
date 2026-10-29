# 旧界面代码清理（2026-10-26）

用户在晚间真机试用中看到来源菜单进入旧榜单页，明确要求安卓包清理冗余代码。此前五项新反馈已记录；本次先清理旧路径，不把五项反馈一并标为完成。

## 源码变化

- DiscoveryActivity来源菜单删除“浏览该平台榜单”及discovery_source跳转；登录、来源刷新、网页读取、状态与平台偏好仍可达。
- MainActivity只承担统一阅读，不再构建旧平台榜单。删除home/showBoard/drawBoard、排名卡片、底部平台栏、榜单位置保存和专属预测回调，删除收藏界面/保存入口及savedPage状态分支。空文章Intent关闭当前阅读Activity，正常返回前一个页面，不生成旧首页。
- 删除NavigationScroll、PlatformMarkView及只测试已移除底栏滞回的NavigationAndImagesTest。AdditionalSourcesTest不再断言旧五标签导航API，保留15来源可读检查；删除Models.navigationOrder别名。
- 话题聚合中的帖子入口原来复用榜单card，现为独立relatedLink，不带排名、热度和收藏长按。没有把聚合页任意选一帖伪装为正文，贴吧直达正文仍是待实施项。
- 保留统一正文、历史阅读进度、登录返回、图片/视频生命周期与ArticlePreloader交接；DynamicBoard是来源抓取公共能力，未按名称误删。
- frame的无用标题参数及未使用BUTTON色值移除。旧磁盘数据没有主动删除，不因代码清理清空用户登录/偏好。

## 证据与范围

1. `legacy-cleanup-20261026-build-fixed.txt`：隔离debug构建、测试包、lintDebug成功；94个JUnit报告、572测试、0失败/错误/跳过。较此前573减少的唯一测试覆盖被删除的底部栏行为，由当前导航实际测试补充。
2. `legacy-cleanup-20261026-final-build.txt`：隔离配置minifyReleaseWithR8及lintRelease成功。不是普通签名发行包安装证据，Lint通过不表示零警告。
3. 指定模拟器emulator-5560 / quiet-reader-api36；包app.quietreader.discoveryprobe和独立.test。未访问或替换手机包。
4. `legacy-cleanup-20261026-legacy-navigation-removal-final.txt`：19/0，实际设置→来源卡片→菜单，旧榜单入口消失，其余来源控制可达；逐级返回单信息流；空文章阅读入口退出。
5. `legacy-cleanup-20261026-reader-links.txt`：7/0，实际点正文包装链接进入另一缓存帖，再系统返回；原段落top前后380.0476379394531相同。
6. `legacy-cleanup-20261026-reader-rotation.txt`：15/0，横竖屏重建、段落与段内位置保持、展开状态、会话变化后拒绝旧内容、顶部标题与无横向溢出。当前截图已拉取并人工查看，位于C:/Users/10197/Documents/Codex/quiet-reader-review/New-cleanup-rotation-{portrait,landscape}-20261026.png。
7. `legacy-cleanup-20261026-pending-article-rotation-count-fixed.txt`：20/0，未完成原生请求跨多次旋转、后台取消、会话切换、脱离Activity后结果交接及排队磁盘查询取消。
8. `legacy-cleanup-20261026-promoted-native-rotation-count-fixed.txt`：25/0，预读提升、持有者转移、失败后动态回退、各正文写入1次及实际版本化磁盘可读，外加后台/会话隔离。

最终五组运行检查共86项，分别保留日志，非86个独立真实平台/账号用例。受控合成正文测试不能替代真机登录权限、推荐收益或真实能耗门槛。旧历史UI测试仍有依赖旧榜单的诊断分支，不以这些历史runner的编译成功宣称它们已运行通过；其有效场景需迁移至当前入口。

## 保留的失败证据

- 首次构建因长工具路径AAPT2启动失败；改用项目既有短共享工具链。之后编译发现被旧榜单共用的正文相关链接方法被删除，改为独立relatedLink后构建通过。
- 导航测试第一次在窗口动画期间读到null；改为按实际目标窗口/页面等待。第二次把来源状态硬编码为成功文案，失败来源实际应显示失败详情；改为同时接受正确的两种状态，没有修改生产状态文案或放宽菜单入口检查。最终19/0。
- 旧PendingArticleRotationProbe在Future.get后立即读ThreadPoolExecutor.getCompletedTaskCount，存在任务结果已交付但完成计数尚未增加的竞态。改为在单线程writer任务内部采样，两次标记之间仍严格要求1次正文保存；最终普通/预读均actual=1，动态回退actual=1。没有改变生产缓存策略来通过测试。

收尾隔离包已force-stop；本次无手机安装、数据清除、提交、推送或公开发布。手机仍为ee26445e候选。五项新反馈及完整交付审计保持开放。
