# 登录与旧正文交接：收尾代码核对

本轮是当前源码与已存在证据的核对，没有重新运行手机或登录测试，没有修改生产行为。候选仍为 bad82db2；不能作为真实知乎/虎扑账号权限验收。

## 已核实的当前调用链

1. `MainActivity.login` 先增加 generation，暂停预读，取消 Repository 请求、答案流、图片所属请求并关闭 DynamicReader，随后才打开 LoginActivity。先前匿名结果不能按旧 generation 回填当前阅读。
2. `MainActivity.onActivityResult` 对 request 100 无论 RESULT_OK 或 RESULT_CANCELED 均 flush 共享 CookieManager、暂停预读并通过 importSourceRead 使旧正文缓存失效。RESULT_CANCELED 包含网页登录后直接按返回的正常情形，不能视为登录失败。
3. 没有手动导入正文时，forceSourceRead 使 open 跳过历史正文、内存正文与磁盘恢复，直接读取当前来源。手动导入时先校验实际采样会话和缓存 namespace；不把旧 DOM 盖章成新账号。
4. Repository 原生读取使用目标 URL 的共享 CookieManager cookies；返回写缓存及主线程交付前均再次核对账号指纹与缓存 epoch。DynamicReader 使用相同 CookieManager、ArticleSession 和受限一次的会话重取；没有独立账号存储。
5. ReadingHistory.get、Repository.cachedArticle、ArticleDiskCache.read 和 ArticleSession.stamp 检查各自负责的会话、namespace、时间及帖子身份。Tieba 话题还校验实际子帖会话，不把其他帖子正文混回推荐入口。
6. SourceSession 对知乎直接走共享 WebView。虎扑先共享 Cookie 原生读取；若 Hupu 文档仍 loginRequired，则 nativeDocumentReady 返回 false，由现有动态读取兜底。HupuDiscussion 对 LOGIN_SEE 单独核对账号及来源实际渲染证据，不只凭该标志判断登录失败。

这些静态检查只排查“返回后必然复用旧匿名缓存”“另用 Cookie 容器”“把 LOGIN_SEE 一概当失败”等确定性错误；不证明真实网络响应、站点风控和账号内容权限。

## 已有运行证据的准确范围

- `artifacts/local-ai/login-extraction-worker-20261029/login-read-flow.txt`：75检查、0失败、raw -1。实际 LoginActivity、受控 DOM 和读取按钮、Activity result、阅读器渲染；来源响应和 Cookie 为合成 fixture。
- 同目录 `login-back-flow.txt`：25检查、0失败、raw -1。包含取消返回后重新读取及拒绝响应不能复活旧账号缓存。不是本轮新测试。
- `artifacts/local-ai/promoted-reader-budget-20261029/cookie-rotation.txt`：33检查、0失败、raw -1。最新关联受控会话变化回归；不代表真实账号登录成功。

## 下一步判定依据

仍按 closeout-readiness 的真机计划，选择当前知乎和虎扑条目，在来源页确认登录及同一答案/楼层可读，再对照统一阅读器的正文尾部、评论和重新读取。若没有受限样本，仅记录公开阅读通过及受限权限未验证，不能关闭登录验收。

本轮未找到上述交接链路中的新明确缺陷；不通过放宽会话隔离、删除来源拒绝标记或无限重试来假装修复。当前缺口需要真实账号响应证据，而非重复同一组合成测试。
