# V2EX过滤后空列表回退（2026-10-27）

HotSourceCollector原先只在原生返回List本身为空时尝试V2EX网页回退；若List非空，但全部为非中文、其他来源、无效记录，经筛选后实际没有可用候选，则直接记失败，错过已有网页获取路径，继续保留旧内容。

将空判断移到候选筛选之后。仍仅V2EX可自动回退，原有单次、同刷新revision、串行WebView、离线/限流/Retry-After/取消/禁用保护不变。有一条可用中文候选就提交原生结果，不额外开网页；网页也全无效则结束，不循环、不更新成功时间。没有后台重试、轮询或新并发。

## 本轮证据

证据目录`artifacts/local-ai/filtered-board-fallback-20261027/`：完整JVM653项，失败/错误/跳过0，Debug/App测试包及Debug/Release lint构建终态成功。

API36隔离模拟器，实际main looper+串行SQLite+受控transport：V2EX全组22/0，覆盖原有限流/取消/同步回调/禁用及新增完全过滤、混合可用、离线过滤、网页仍全无效。新增检查原请求关闭、同revision、成功时间不伪更新、真实网页候选提交、无冗余网页、只增加一次失败。

初次22项中1失败，测试错误地要求历史失败总数=1，前一个离线过滤已累积一次失败。保留v2ex-fallback-first-failure.txt；修改为读取已有次数后断言只增加1，生产逻辑不变；最终22/0及INSTRUMENTATION_CODE:-1。

现有前台来源/正文供给回归冷7/0、暖8/0，均原始成功码。本轮成功组共37项，不累计首轮失败或重复运行。仅受控传输，不声称V2EX当前真实站点可用、手机VPN路由或真实登录通过。Debug App SHA256 a2b3b30eb59d0911aa29520ca7bbec1c997c6dea9e15cf1f6db2feb62c6bbb61；最终驱动另存test-final.apk。

当前普通待测候选仍2bf60a5d，不含此后续修复；尚需对应R8及普通包升级验证。未操作手机、推送或发版；最终关闭隔离App及AVD，见cleanup.json。其余真实账号/贴吧层级/ARM/能耗/质量门槛保持开放。
