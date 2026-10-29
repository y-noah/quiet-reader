# 原生正文在途转屏交接 / 2026-10-25

## 问题和实现

原生正文仍在获取时，旧阅读 Activity 转屏会重发请求，旧请求被断开；按 Home 后旧正文仍可能迟到发布。受控合成 HTTPS 正文经真实 Repository、ThePaper 解析和实际 Activity 重建复现，基线 pending-article-rotation-baseline-api36.txt 为7项4失败。

PendingArticle 只保存 application Context、独立 Repository、会话和当前结果监听器；配置重建时解绑旧监听器并转移一个在途请求。结果在交接间隙完成可暂存，交给新页面一次。保留前校验来源/URL/候选版本、缓存 epoch/namespace、当前 Cookie 指纹；会话变化拒绝旧结果。按 Home 关闭请求，迟到结果不进页面/缓存，回来走正常重试。旧 Activity 不作为请求持有字段。原阅读页 Repository 在后台取消排队磁盘恢复/榜单请求，避免后台恢复回调再启动来源获取。保留原始 openingPosition，避免首次阅读转屏后错误强制恢复到零。

## 验证方法和纠错

唯一合成 ThePaper 路径的 HTTPS 响应由测试协议处理器截获并控制释放；经过生产 HTTP、解析及缓存流程，不是实际公开站点网络响应。Cookie 测试仅写入该合成路径，不代表真实账号验证。实际连续横竖屏、Home、应用内 REORDER_TO_FRONT 返回。完成交接间隙用生产 PendingArticle detach/attach 验证。

扩展测试中两处测试方法曾有误，保留报告以便审计：系统 shell 无法打开非导出的 MainActivity，已改同应用 Context 返回；ArticleDiskCache.IO_LOCK 仅保护写入，锁它不能延缓读取。最终改占满该 Repository 的三个工作线程，再发起阅读，确认磁盘恢复排队后实际 Home 取消，释放线程后确认无新请求。最后一项验证排队取消，不声称验证了正在读取文件中途取消。

## 本轮证据

artifacts/local-ai/ 下：

- build-pending-article-final.txt：构建成功；JVM XML合计388项，失败0、错误0。
- pending-article-rotation-native-handoff-final-api36.txt：19项0失败；连续两次转屏一个原生请求，新页面独占结果，Home断开/迟到拒绝，回来一个新请求，路径会话变化重取，交接间隙缓存结果只交付一次，排队磁盘恢复后台取消。
- reader-rotation-native-handoff-final-api36.txt：15项0失败。
- dynamic-handoff-native-handoff-final-api36.txt：10项0失败；这是既有发现页预取交接回归，不是动态来源获取期间转屏验收。

- login-read-flow-native-handoff-final-api36.txt：61项0失败。

## 边界

仅原生前台正文获取的配置重建交接完成本轮定向验证。DynamicReader 和被提升的 ArticlePreloader 在途转屏、图片网络请求转屏取消/去重、进程死亡位置恢复仍待继续。真实登录内容、用户个人推荐效果、Find X7 Ultra ARM整App峰值/发热耗电、精确签名升级与回滚仍未通过完整验收。本轮没有提交、推送、发布或改品牌。
