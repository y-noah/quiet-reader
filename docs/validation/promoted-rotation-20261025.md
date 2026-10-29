# 已提升预读的在途配置重建交接 / 2026-10-25

## 复现

发现页已有ArticlePreloader原生/动态请求；点击该候选后ArticleHandoff把它交给阅读Activity。旧MainActivity配置重建只保留已读正文、PendingArticle或直接DynamicReader，onStop无条件pause预读器，onDestroy再close。因此尚未完成的提升请求被取消，新阅读页重新获取。

受控知乎HTML、实际发现页点击及连续两次横竖屏，promoted-rotation-baseline-api36.txt：16项9失败。失败包括提升器/提取器没有迁移、来源Context/视口不属于新页面、旧Activity仍持有旧提升器，最后正文和后续回答无法接上。不是只根据代码猜测。

## 实现

ArticlePreloader区分待交接的已点击前台请求与普通预读。仅当前来源、URL、候选版本和当前会话有效的已提升请求可保留。配置解绑清空旧Activity、前台回调、视频/空闲/准备观察者，停止策略Watch和队列推进；动态网页同步detach。Native Repository使用application Context，保留同一请求。原生完成或失败可暂存；在没有Activity时，磁盘恢复后的网络启动或原生失败后的动态回退延迟到接手后。

MainActivity配置重建把提升器转交给保留状态并清空旧字段；新页绘制加载界面后接手，重新绑定当前回调及来源视频隐藏更新，再继续交付。动态接手沿用DynamicReader的当前会话校验、缓冲结果及成功回调内的原网页续读转交。普通Home仍pause并取消，不把这次配置重建保留变成后台预读。重复接手完成过的缓冲结果不再调用接收者。正常板块返回后的预读策略继续保留。

## 证据及测试边界

artifacts/local-ai/：

| 报告 | 结果 |
|---|---|
| build-promoted-rotation-final.txt | 构建成功；JVM388项，失败/错误0 |
| promoted-rotation-handoff-api36.txt | 动态提升转屏初轮16/0 |
| promoted-native-rotation-native-edges-api36.txt | 原生提升首轮20/0 |
| promoted-native-rotation-fallback-api36.txt | 加入失败回退边缘22/0 |
| promoted-rotation-promoted-final-api36.txt | 最终动态提升/连续转屏/原网页续读16/0 |
| promoted-native-rotation-promoted-final-api36.txt | 最终原生提升/连续转屏/Home/回来重试/会话变化/缓冲/延迟回退/排队取消22/0 |
| promoted-background-promoted-final-api36.txt | 最终动态提升/连续转屏/实际Home/迟到拒绝/关闭15/0 |

动态检查使用慢响应拦截HTML，真实WebView、生产SourceSnapshot/SourceParser及AnswerStream；动态回退检查也使用拦截HTML。原生检查使用实际默认ArticlePreloader（无Loader替身），真实DiscoveryActivity点击→ArticleHandoff→MainActivity；生产HTTP和ThePaper解析，但HTTPS字节由协议处理器提供，可能发生系统DNS，不宣称真实公开源内容或真实账号验证。

连续转屏、Home及前台恢复使用实际生命周期；原生完成空档和失败空档则直接操作生产detachForeground/resumeForeground来精确控制时序。Cookie仅写入合成文章的独立路径。测试清理独立数据库/合成阅读历史并恢复来源设置和Cookie接受选项。清空对象字段/重绑Context的断言不等于已证明Chromium和所有系统对象的GC/峰值内存。

## 最终受影响回归

同一最终构建安装包顺序执行，artifacts/local-ai/下：

| 报告 | 结果 |
|---|---|
| dynamic-handoff-promoted-final-api36.txt | 10/0 |
| dynamic-rotation-promoted-final-api36.txt | 14/0 |
| prefetch-lifecycle-promoted-final-api36.txt | 10/0 |
| image-prefetch-promoted-final-api36.txt | 10/0 |
| image-prefetch-policy-promoted-final-api36.txt | 12/0 |
| login-read-flow-promoted-final-api36.txt | 61/0 |

按各诊断既有范围解释；低电量取消/恢复策略检查、配图预读不代表实机功耗或全来源图像命中率。git diff --check通过。

## 未完成项

正文在途配置重建的三条路径现分别有定向证据，不等于全阅读生命周期验收完成。图片请求转屏取消/去重、跨进程语义位置、更多错误/导航/辅助功能、真实账号、真实个人推荐留出、Find X7 Ultra整App峰值/发热/耗电、精确签名升级及回滚仍未完成。本文不授权或宣称已提交、推送、发布、改名或换Logo。
