# 前台动态正文在途转屏 / 2026-10-25

## 实际复现与范围

原实现 MainActivity.onRetainNonConfigurationInstance 只保留已解析正文及原生 PendingArticle；动态正文仍在加载时，新 Activity 重新获取，旧 Activity.onDestroy 销毁原网页。dynamic-rotation-baseline-api36.txt 为4项4失败（3个明确交接断言失败，随后正文等待超时），包括来源 Context/视口没有迁移。

此检查先以唯一合成知乎问题的缓存正文进入真实阅读页，再调用生产的显式来源刷新路径，在同一UI回合安装HTTPS HTML拦截，保证请求开始前完成拦截。不是直接把合成 Document 交给渲染器当作动态读取成功；慢响应释放后经过生产 WebView、SourceSnapshot、SourceParser、会话校验、统一正文和 AnswerStream。正文/作者均为合成，不证明真实账号或实时来源访问。

## 实现

DynamicReader 支持解绑旧 Activity/回调、移除旧视口并把 MutableContextWrapper 基上下文置为 application Context。配置重建保留同一个提取器，新 Activity 校验来源/URL/候选版本和当前 Cookie/epoch/namespace 后重新绑定。完成的正文或错误在无接收者间隙暂存，只交给新回调一次；交付前再次校验会话。成功回调期间仍可把确实已加载的知乎问题网页交给后续回答读取；未转交则最终释放。

MainActivity 在 reading==null 的配置重建场景交接动态提取器；普通 Home 则关闭、清空引用，迟到 HTML 不发布。返回前台遵守既有会话有效的历史/缓存恢复；失败页提供重试和来源登录入口。closed 提取器不再重新记录 Activity。Cookie 首次变化的一次有限重取、总超时预算、来源导航限制、视频网络过滤等机制保留。

## 证据

artifacts/local-ai/ 下：

- build-dynamic-rotation-final.txt：构建成功，JVM XML388项、失败0、错误0。
- dynamic-rotation-handoff-api36.txt：初轮修复6/0。
- dynamic-rotation-edges-api36.txt：扩展检查中9项1失败；前台恢复测试错误要求与上一 Document 对象相同，但正常 ReadingHistory 会解码新对象。修改为验证正文内容及未发起动态重载，不修改生产历史恢复。
- dynamic-rotation-edges-final-api36.txt：12/0（当时尚未加入Cookie变化缓冲检查）。
- dynamic-rotation-dynamic-rotation-final-api36.txt：最终14/0，覆盖实际连续横竖屏、同一个来源请求/Context/视口交接、统一正文和确切同源网页后续回答复用、实际Home取消/迟到拒绝、前台恢复、无Owner间隙缓冲并一次交付、Cookie变化后的匹配/交付两次检查。

交接空档和Cookie缓冲测试直接操作生产DynamicReader的detach/attach，不能描述为系统必然在该时间点完成转屏；前三项交接及Home/恢复是实际Activity生命周期。全文测试只检查归一化文本与资源所有权，不代替视觉、真实权限或真机性能验收。

## 最终受影响回归

最终构建同一安装包，顺序执行以下实际模拟器诊断，报告均位于 artifacts/local-ai/：

| 报告 | 结果 |
|---|---|
| dynamic-handoff-dynamic-rotation-final-api36.txt | 10/0 |
| pending-article-rotation-dynamic-rotation-final-api36.txt | 19/0 |
| reader-rotation-dynamic-rotation-final-api36.txt | 15/0 |
| login-read-flow-dynamic-rotation-final-api36.txt | 61/0 |
| discussion-flow-dynamic-rotation-final-api36.txt | 57/0 |

以上按各Probe原范围解释：受控登录/评论HTML不能证明真实账户内容可读；既有交接检查不能证明提升后的转屏已适配。git diff --check通过。未对该改动新增资源峰值、耗电或推荐效果结论。

## 剩余工作

被提升到阅读页的 ArticlePreloader 仍未保留在途配置重建；现有 dynamic-handoff 仅验证发现页→阅读页及后续回答，不等于已验证提升后转屏。图片请求取消/去重、跨进程语义位置、真实账号、个人推荐留出、Find X7 Ultra ARM资源/发热/耗电、精确签名升级与回滚等仍未完成。没有提交、推送、发布或改名改Logo。
