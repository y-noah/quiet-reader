# 贴吧楼中楼时间获取与隔离验证（2026-10-28）

后续接线见 [tieba-discussion-progress-20261028.md](tieba-discussion-progress-20261028.md)。本文记录时间获取阶段，当时尚未启用进展判断；当前状态以该后续记录及其最终验证段为准。

## 实现范围

TiebaReplyTimes 已接入 SourceSnapshot、DynamicReader、TiebaReplies 与 ReadingHistory。仅为实际解析出的楼中楼附来源创建时间，不开启贴吧进展重推。公开字段依据见 tieba-reply-time-audit-20261028.md。

前台已有来源快照动作发现稳定子回复 ID 时，同源请求 totalComment，沿用该 WebView 的 Cookie。请求最长 1000ms；同一来源页面五分钟内只尝试一次；缺少子回复、隐藏页面、可见登录门槛不发起。失败不重试、不阻塞已有正文。visibilitychange/pagehide 中止请求，finally 清除定时器与监听器。最多消费 512KiB 响应流、扫描 256 组/2048 行、保存 128 条身份时间三元组；流消费上限不等于浏览器实际网络传输字节上限。

JavaScript 验证 thread_id/post_id/comment_id、唯一性及 Unix 秒整数；Java 再验证实际来源帖子与页码、父楼 sourcePostId、父子哈希和已解析关系。错误形状、重复子 ID、非法时间原子拒绝；窗口外或缺父楼不附时间。顶层楼层和主帖永远不借子回复时间。只导入身份/时间，不导入 user_list、作者或接口正文。

第二次重叠虚拟窗口可补全旧子回复的缺失时间，复制 Section 后替换，避免 AnswerStream 的浅复制反向修改已显示文档及进展基线。登录或错误快照不能补时间。

## 自动验证

证据目录：artifacts/local-ai/tieba-child-times-20261028/。

- 初始三条公开样本的测试 HTML 把两个真实父楼误放为一个，产品正确拒绝第三条，引发两个断言失败；仅修正测试父子结构，原失败报告 first-fixture-parent-failures.xml 保留。
- 完成实现时 829 项/117 套 JVM 测试，错误、失败、跳过均为零；Debug/Release Lint 成功。日志 final-build-tests-lint.txt、jvm-summary.json 和两份 Lint XML 保留。
- 最终 Debug Android 同一快照专测两轮各 12/0，虚拟续载 13/0、子回复进展回归 15/0、虎扑 16/0、V2EX 16/0、元数据 9/0，共 93 次检查。每个记录仅一个 INSTRUMENTATION_CODE: -1。重复轮用于稳定性验证，不称 93 个独立场景。
- WebView 专测使用真实前台 Chromium、快照脚本、解析和历史缓存；HTTP 内容由测试 WebViewClient 控制，超时/超量/页面离开采用受控 Promise/ReadableStream。证明立即返回 HTML、请求复用、超时中止、超量流取消、登录门槛拒绝、无稳定 ID 不请求；不是公网或真实登录账号验收。pagehide 为合成事件，不声称已证明真实 Activity 后台或能耗。
- 补充两项话题解析回归：缓存按实际帖子保存子回复时间；同一个热搜换了帖子后不接受旧帖子时间。明确断言贴吧仍不具备进展重推资格。定向 11 项及随后全量 831 项/117 套均零失败、错误、跳过。见 topic-history-tests.txt、topic-history-full-tests.txt、topic-history-jvm-summary.json、topic-history-test.xml。这次仅增测试，无需重建 Android APK。
- app.apk/test.apk 与安装包哈希核对见 installed.json；模拟器关闭、归属进程与 ADB 目标均为零、保留 userdata，见 cleanup-final.json。本阶段未操作手机。

## 剩余接线与验收

1. 不能简单给 DiscussionProgressEvidence.supported 增加 TIEBA：现规则拒绝 nestedReply、virtualDiscussion、topic，且要求创建时间不晚于正文 cachedAt。须设计真实帖子主文基线、稳定父子身份和逐次获取时间，不能用当前时间抬高旧缓存的取得时间。
2. 热搜话题身份与所选帖子身份必须同时约束进展。换帖、换账号、旧窗口及迟到结果不能生成该候选的新进展；父楼无创建时间保持未知。
3. 复用既有主文连续性、新回复创建晚于实际曝光/旧取得时间、足够新文字及明确更新标记规则；普通回复计数变化、旧页、编辑已知子回复及复制内容不能触发重推。
4. 接线后须做生产刷新/再次曝光消费、R8、保留数据升级；再验证真实来源登录和分页。此实现尚无新增 R8、普通升级或真机证据。

普通候选保持 New-20261028-5982c350.apk，手机仍 c315361c。两者均不包含本阶段新增的贴吧时间获取；未推送、未发布。真实账号、推荐收益与独立去重、整 App ARM 内存、拔线能耗、长期缓存门槛继续开放。
