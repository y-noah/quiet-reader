# 贴吧楼中楼进展接线（2026-10-28）

后续普通候选已通过保留数据升级，见 [ordinary-tieba-progress-20261028.md](ordinary-tieba-progress-20261028.md)。下文“普通候选5982未变”描述本接线阶段，最新候选为后续记录中的9e06075e；手机仍未更新。

## 行为和边界

TiebaDiscussionProgress 接入现有 ArticleProgressBinding、ArticleProgressEvidence 和预筛选 supported 判断。仅已有前台获取/预读动作产生正文证据，显式刷新才使用证据影响排序；不新增模型调用、末页扫描、后台轮询或偏好反馈。来源时间获取沿用 tieba-child-times-20261028.md 的有界请求。

候选身份保持热搜入口的来源+URL hash；独立 thread 指纹包含实际帖子 URL 与话题解析记录的实际帖子会话指纹。换帖或换实际帖子账号不能沿用旧讨论连续性。不会把一个话题下另一个帖子的回复移给本帖。

只接受当前贴吧关系版本、第一页、一个明确主文、合法且唯一的节身份。楼中楼父楼必须已加载、为非嵌套节，并且 sourcePostId 与父节哈希精确对应；缺父楼、重复身份、第二页、登录/错误/权限提示、未知提示或续读链接拒绝。接受既有解析器的准确页面说明以及正常 loaded/end 状态。虚拟窗口允许参与，但不放宽以上主文和归属条件。

只有经 TiebaReplyTimes 验证过的楼中楼创建时间参与证明。普通顶层楼层保持未知时间，即使挂有不合适的时间版本也不能借用。新窗口的时间晚于原文 cachedAt 时拒绝，不把当前时间或新窗口取得时间写成整篇正文的取得时间。因此较晚续载的有效新回复可能暂时不进入进展判断，须待现有获取流程提供一致的新正文快照；不为提高召回伪造时间。

复用现有门槛：保留至少 80% 主文 hash；新子回复 ID 在旧基线不存在；创建时间晚于旧快照取得时间、整事件最后展示/有效阅读；至少 96 个新正文 hash，其中至少 64 个来自明确更新段落。旧分页、无可靠时间、普通无标记回复、编辑已知子 ID、复制旧正文和短句不提升。讨论不走跨 URL 新闻后续捷径。

限制保持 128 节/512 块/24000 字/4000 hash，历史 256 行/7 天/8MiB。UI 仅复制有界不可变文字，工作线程计算 hash。进展历史不保留原文、作者、Cookie 或帖子 URL。

## 持久化与账号边界

新贴吧格式 marked-tieba-child-v1，必须含 64 位十六进制 thread hash，顶层与 discussion 版本必须相同。缺失、类型错误、版本错配拒绝；既有 marked-new-reply-v1 的 V2EX/虎扑格式保持兼容。共享数据库表及限额不变。

ArticleProgressBinding 对话题解析文档额外冻结实际帖子 ArticleSession。hash 完成、入库事务和消费时重新检查候选与实际帖子两个 token，防止热搜路径 Cookie 未变、帖子路径 Cookie 已变时仍发布迟到结果。真实账号权限及全流程切换仍需真机验收；本轮只验证受控 CookieManager 行为。

## 验证记录

证据目录 artifacts/local-ai/tieba-discussion-progress-20261028/。首轮与最终构建的证据分开保留；最终结论见文末。

新增 TiebaProgressEvidenceTest 11 项，覆盖来源解析与时间附着、主文连续性、同话题换帖/换实际帖子会话、旧/无标记/复制回复、已知子 ID 编辑、顶层不借时间、父楼错误、权限与版本、第二页、晚窗口时间、账号与阅读时间、冻结输入和历史恢复、hash-only 格式与缺失版本字段。首轮 52 项定向中一项失败：测试直接 decode 正文而没恢复外层取得时间；改用实际 ReadingHistory.restore 外层 envelope，保留 first-history-test-failure.xml，未放宽产品规则。

首次全量 842 项/118 套、0 失败/错误/跳过，双 Lint 成功。Debug 首轮贴吧 20/0、重复 20/0、虎扑 16/0、V2EX 16/0、时间快照 12/0。SQLite 进展存储、真实 accepted exposure、显式 refreshSnapshot 排序、再次展示消费、取消不写和重开库均实跑；实际帖子路径 Cookie 改变而话题路径 scope 不变时，迟到 observation/consumption 无数据库写入，旧文档重新捕获也被拒绝。正文与账号 Cookie 均为合成受控数据，不是公网和真实账号证据。

首轮非调试 R8 两阶段贴吧 seed5/verify10、V2EX 回归 seed5/verify10，均零失败/raw -1。只用公共 Android/SQLite/缓存序列化格式，App 自行预读、自然曝光、计算新内容证明，实际圆点重推和再次曝光消费；宿主只在阶段间 force-stop 隔离包，不注入 hash、scope 或 verified_at。来源正文来自合成磁盘缓存，不证明 R8 公网解析。

首轮后追加实际帖子会话进入 thread 指纹的保护和对应断言；最终构建及运行结果在收尾段单列。普通候选5982、手机c315未变，未发布。真实受限登录/完整分页、无标记进展召回、个人推荐收益和独立去重、ARM 整App峰值、拔线耗电温升、长期缓存门槛继续开放。

## 最终构建收尾

最终完整 JVM 842 项/118 套、0 失败/错误/跳过，双 Lint 成功，见 final-jvm-summary.json、final-build-tests-lint.txt、final-TEST-TiebaProgressEvidenceTest.xml 及 final-lint-results-*.xml。最终 Android Debug 贴吧20/0、虎扑16/0、V2EX16/0、时间快照12/0，共64次检查；最终 R8 贴吧两阶段5/0+10/0，共15次检查；六份 final-* 运行日志均唯一 raw INSTRUMENTATION_CODE: -1。首轮重复检查不累加为独立场景数，也不代替最终构建结果。

final-app/test.apk、final-r8-app/test.apk 为最后归档包；四个实际安装包 SHA256 与归档匹配，见 final-installed.json。R8 测试仍基于合成磁盘正文，没有本轮 R8 公网 HTML 或真实账号证据。最终代码未做普通原签名保数据升级，因此不能更新普通候选指针或安装手机。

模拟器退出请求后首次立即观测仍有退出中的进程与 ADB 登记，保留 cleanup-request.json；继续核对同一实例的终态，结果见 cleanup-final.json，不因退出观察未完成重启或清数据。手机未操作、普通5982及手机c315不变，未发布。下一步是普通构建保数据升级、真实来源与账号及分页验证，并继续保留上述整体验收门槛。
