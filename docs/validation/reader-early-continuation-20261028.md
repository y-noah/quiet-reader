# 保护期内真实前向手势的续读

状态：本地生产修复、Debug 回归完成；尚未做本修复的 R8/普通升级验证，未更新普通候选、未安装手机、未发布。手机仍为本轮使用结束时已经核对的 c315361c。

## 复现与边界

MainActivity 在正文渲染/位置恢复/字号变化后短暂禁止自动续读。期间前向滚动被 maybeLoadMore 拒绝；之后长页面的被动检查只接受短页，因此停在底部可能一直不续读。

新增 ReaderEarlyContinuationProbe：真实 MainActivity、缓存的 V2EX 格式正文、真实系统触摸、受控 DiscussionStream.Loader；不改保护截止时间、不调用私有 loadMore、不注入滚动脚本。WebView 先预热，模拟连续打开话题。只有确认自然保护期仍有至少 350ms、实际手势到达续读范围且保护期未结束时，才执行最终断言。

before-diagnostic.txt：保护期剩余552ms，手势后103ms，y=1363、距底3.75px、viewport=2051px，Loader调用0；超过保护期仍未调用，3项1失败/code0。确认的是通用阅读页手势丢失，不证明早晨手机贴吧脚本滚动失败同因。

冷启动 before.txt 前置条件不成立；before-warm.txt 测试断言在主线程抛出导致进程崩溃。之后只改测试异常捕获，将主线程异常返回测试线程报告。原失败保留。

## 改动

只接住真实前向手势：手指上移超过系统 touch slop、阅读 WebView 确实前向滚动、同一正文 revision、仍在最多1500ms保护期内。ACTION_UP 安排一次保护期结束后30ms的检查，复用完整 maybeLoadMore 守卫。

新手势、正文重新渲染、onPause 均取消；回调复核 WebView/revision/generation/阅读前台与窗口焦点，原来的登录、页尾、离线、加载中、位置恢复等限制继续生效。无循环定时器、无被动额度、无新的预读循环。

## 本地证据

目录 artifacts/local-ai/reader-early-continuation-20261028/：

- after.txt：初版修复3/0/code-1。
- reader-early-continuation.txt：最终代码真实早期手势3/0/code-1，手势后保护期剩256ms，最终只有1次Loader调用。
- reader-early-passive.txt：长页没有手势，2/0/code-1，Loader保持0。
- reader-early-background.txt：真实打开设置，onPause取消待检查，5/0/code-1，Loader保持0。
- regression-reader-prefetch.txt：21/0/code-1；regression-tieba-child-progress.txt：15/0/code-1。
- regression-reader-early-continuation.txt：前置条件失败，剩120ms，未作手势和续读结论。该轮与Lint并行，不能据此断定唯一原因是主机负载。
- repeat-final.txt：构建/Lint结束后重复3/0/code-1，自然保护690ms，手势后266ms。
- build-final.txt：701 JVM /107 suites，0失败/错误/跳过。
- lint.txt：lintDebug、lintRelease成功。

这些是受控正文/传输下的局部时序证据，不是完整真实贴吧分页、网络耗电或R8通过证明。下一步补本修复的R8与普通升级，再安排后续授权真机对照。不得将 c315 当作已包含此修复。

后续补验：图片字号真实重排、触摸与R8提取/两轮续载已有独立证据，并实际发现/修复onPause与onStop间迟到滚动仍启动续读；普通原签名候选c969保数据升级与双模型55/0。手机仍c315，真实账号/完整分页/ARM能耗未关闭。详见[图片重排、原失败与最终范围](reader-image-font-20261028.md)。
