# 文字阅读中的知乎包装链接与返回位置

日期：2026-10-25。仅本地开发验证，未提交、推送或发版。

## 行为与边界

正文中的直接已适配链接继续进入统一阅读器。新增识别 HTTPS `link.zhihu.com/` 的单一 `target` 参数，解码一次后仅对已支持的正文地址进入阅读器。保留目标查询、片段和编码加号。重复参数、附加参数、外层片段、异常编码、双重编码、非 HTTPS、账号信息及不支持目标均保留原浏览器路径；不进行网络重定向探测。

实际返回测试发现原有 `onUserInteraction` 会在返回键释放时清除前一篇文章刚建立的语义位置恢复。改为新触摸按下、非返回键按下或滚轮事件中断恢复，行为学习仍由原 `onUserInteraction` 记录。没有保留未证实有效的布局等待改动或临时生产日志。

## 证据

- `reader-link-baseline-api36.txt`：3 项检查、1 项失败，点击知乎包装链接未进入统一阅读器。
- `reader-link-fixed-api36.txt`、`reader-link-geometry-api36.txt`：路由通过，返回位置失败，7/1。
- `reader-link-position-api36.txt`：诊断错误地将 LruCache 转为 Map，进程崩溃；不计通过，已修正诊断。
- `reader-link-position-corrected-api36.txt`：保存位置8270px正确，返回0px，7/1。
- `reader-link-script-coordination-api36.txt`：诊断改用生产脚本引用计数，排除脚本开关竞争后仍7/1。
- `reader-link-layout-api36.txt`：临时等待原生内容高度无效，7/1，该生产改动已撤回。
- `reader-link-trace-api36.txt`、`reader-link-trace-logcat.txt`：恢复回调未执行，与恢复提前取消一致；临时日志已移除。
- `reader-link-back-event-api36.txt`：最终实际点击包装链接进入缓存正文、显示实际标题/正文、一次历史入栈、系统返回恢复原文段落、无来源加载，7/0；返回前后链接均380.2083 CSS px，原生滚动恢复8270px。
- `reader-link-final-repeat-api36.txt`：相同安装包重复实际点击/返回7/0，段落位置一致。
- `reader-link-rotation-regression-api36.txt`：已有阅读旋转、会话变化后拒绝旧缓存和顶部标题位置回归15/0。
- JVM：416 项、0 失败，覆盖直接/包装目标、查询和片段保留、异常与不支持目标回退。

报告均位于 `artifacts/local-ai/`。模拟器为 API36、`emulator-5560`；两篇正文为合成缓存数据，经过实际 MainActivity/WebView 点击和系统返回，不是仅调用链接解析函数。

## 限制

不证明线上知乎包装链接的所有变体、真实账户权限、跨来源完整正文获取已通过。拒绝识别的包装链接仍可通过原浏览器路径访问。整个产品的用户推荐效果、真实 OPPO 手机内存/耗电和正式前驱版本升级验收仍未完成。
