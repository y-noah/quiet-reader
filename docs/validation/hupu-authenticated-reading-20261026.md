# 虎扑来源已登录、正文或评论仍提示登录

用户确认：来源网页已登录，统一阅读页仍提示登录。本轮不使用手机，不推送或发布。

## 发现与修正

HupuDiscussion 原先仅接受 detail 和 thread 的 visibleRange 同时为 ALL_SEE。LOGIN_SEE 表示登录受众，但旧实现无条件返回 loginRequired，即使同一来源响应已提供登录用户和已展示正文也被拒绝。这是确定的代码缺陷；尚无用户真实受限帖样本，不能认定它解释全部实机现象。

公开来源脚本的帖子页在 detail.thread.tid 存在时渲染主帖及回复，用户状态来自 detail.user；SELF_SEE 另有隐私标记。公开页面 detailErrorInfo.code=200、匿名 detail.user.puid=0。参考脚本为 w1.hoopchina.com.cn/games/static/bbs-pc-web/_next/static/chunks/pages/%5Btid%5D-faf8941b181416b7.js，本地公开副本 artifacts/public-sources/hupu-thread.js。未读取账号存储或记录账号、Cookie 值。

新逻辑对 ALL_SEE 保持原读取行为；对 LOGIN_SEE 必须同时满足：

- 帖子 ID、URL 和回复页码匹配，帖子状态正常。
- 同一响应 detail.user.puid 为非零数字，detailErrorInfo.code 为 200。
- 第 1 页真实主帖区域的正文与状态正文一致；后续页至少有实际回复区域与来源回复一致。
- 受限回复仅收录实际回复区域已呈现的内容；纯 JSON、侧栏、hidden/aria-hidden/内联隐藏区域不能解锁内容。

未知、SELF_SEE、缺失范围继续拒绝；删除/审核/隐藏回复过滤保持。用户标识只用于当前来源响应的判断，不进入 Document、历史、缓存或日志。登录页与读取页沿用共享 CookieManager；无新增权限或后台服务。

## 验证

- 新增 6 个 JVM 回归覆盖登录正文/评论、后续页合并、只有账号元数据、匿名/退出、403、未知及私密范围、隐藏/无关/不匹配 DOM。
- 全量 592 JVM 测试，0 failures / 0 errors；Debug 构建、Debug Lint 通过。日志 artifacts/local-ai/hupu-login-20261026-build.txt。
- 实际模拟器来源窗口读取、返回阅读、缓存和原始推荐身份回归首轮 71/0，包含新增虎扑场景；日志 artifacts/local-ai/hupu-login-20261026-flow.txt。
- 最终增加虎扑主体/回复 ID、后续读取共享 WebView、Cookie 变化缓存失效断言，final-flow 75/0。正文匹配改为每页一次构建集合，避免每条回复反复扫描全页；最终源码重新完整592JVM/Debug及测试构建/Lint通过，模拟器 verified-flow 再次75/0，日志 hupu-login-20261026-verified-{build,flow}.txt。最后停止隔离应用；未操作手机。

受控响应不能证明真实账号跨域登录、真实私密帖或全部分页权限正常。下一轮用户方便连接手机时，复现其实际虎扑词条：来源已登录并可见完整正文及评论 → 返回统一阅读 → 继续评论 → 再读取。记录内容是否一致，不记录账号或 Cookie。手机仍是 ee26445e 候选，本轮代码尚未安装。

最新同份生产源码回归：608 JVM及Debug/Release Lint通过、隔离R8构建通过；最终登录读取75/0，包含虎扑来源登录后导入和会话变化保护。日志 artifacts/local-ai/tieba-login-notice-20261026-login-read-flow.txt。此补充不改变真实账号与手机未验收边界。
