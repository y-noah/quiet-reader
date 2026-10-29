# 虎扑公开热帖状态3读取修复（2026-10-27）

## 实际发现与处理

对公开热榜topic-daily-hot重新采样，643330244.html正常HTTP200、detailErrorInfo.code=200、detail及thread均ALL_SEE，正文实际出现在主帖DOM中，但thread.status为3；对照643294354.html为0。原HupuDiscussion只接受0，使前者返回sourceUnavailable，不是Cookie未传的直接证据。未猜测状态3的产品含义，只处理已观察到的可读响应。

新增状态3路径要求成功响应、当前帖/页身份、该页JSON正文与正常主帖/回复区域实际渲染文本完全对应；回复也只取已渲染匹配的条目。其他未知状态仍拒绝；LOGIN_SEE仍要求同响应已登录用户、成功响应及渲染内容；SELF_SEE/未知受众保持关闭。未额外运行WebView或提高抓取并发。标准状态0路径保留。

资源hupu-public-status3-20261027.html是本轮公开响应的裁剪：保留主帖身份、状态和原正文，已确认原正文确实出现在当时返回的主帖DOM；移除无关用户/会话/推荐等字段，DOM外壳仅保留选择器。它不是整页截图或完整原始DOM；完整公开响应仅在本地artifacts留证。另有合成测试覆盖状态3受限/拒绝/未知状态、未渲染和错页及续页，不用公开样本代替登录权限验收。

## 验证

目录 `artifacts/local-ai/hupu-public-audit-20261027/`：

- JVM645项，失败/错误/跳过0，Debug/Release lint通过。
- Android API36正式Repository公开网络读取643330244.html：browser=false，4874ms取得主帖+66个真实回复，cacheAccepted/cacheHit均true。DiscussionStream实际下一页1174ms，回复增至80，主帖与身份保持。最终readable=1/1、probeErrors=0且成功结束码；无登录/验证码操作。不将这一次公开读取当作已认证账号验收。
- 受控讨论流程72/0、最终受控登录读取75/0，共147项独立检查，均最终成功码。登录组包含Hupu登录受众主帖和评论进统一阅读器、账号Cookie变化失效等场景，仍为合成账号。
- 首次登录组75项有1失败，为matching场景阅读反馈断言。原驱动固定等1200ms并直接调用onUserInteraction/scrollTo，没有确认WebView可滚动及恢复抑制结束，也没有验证滚动信号。改为等待真实可滚动布局/恢复结束、发送实际触摸、确认滚动和engagement.scrolled后计时；新驱动75/0。失败日志保留，不能断言首次失败唯一根因已被还原，也没有放宽生产阅读偏好规则。
- App hash及初始/最终驱动hash见hashes.json。最终驱动编译到完整终态后才安装。没有运行当前虎扑修复代码的R8运行回归；上一版R8结果不移用。

## 剩余门槛

真实用户虎扑/知乎账号下的正文和评论权限、贴吧实际楼层/楼中楼、个人推荐收益与去重独立样本仍需复核。该公开读取修复不意味着已证明全部登录同步问题解决。WebView网络释放、ARM峰值RAM及非充电耗电/温升也未完成。不操作手机、不推送、不发版，结束后关闭隔离App与AVD，状态见cleanup.json。
