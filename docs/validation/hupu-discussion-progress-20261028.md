# 虎扑讨论进展接线及 R8 验证（2026-10-28）

## 实现

在已完成的虎扑回复来源时间基础上，将 HUPU 加入 DiscussionProgressEvidence、ArticleProgressEvidence 与 ArticleProgressBinding。来源显式传入冻结输入，候选身份继续使用来源名和 canonical URL 的 hash；已有 V2EX 身份及持久化格式不变。ArticleProgressPreparation 通过同一 supported 判定纳入虎扑，不新增数据库或后台工作。

虎扑必须是当前正文版本、第一页 URL 且含同线程主帖。接受解析器既有的精确 PAGE_NOTICE；登录、来源错误、任意其他提示仍拒绝。主帖 ID 必须匹配 URL 线程；回复必须是稳定 hupu-reply-ID，不接受未知节、答案或嵌套子回复。合并分页若来源回复时间晚于正文 cachedAt，拒绝产生证据；本轮不自动抓取末页。

既有保守规则不变：主帖至少保留 80% 原有 hash；新回复 ID 在基线中不存在，来源创建时间晚于基线获取及整事件最后曝光/有效阅读；至少 96 个新增正文 hash、64 个明确更新段落 hash。旧分页、编辑已知 ID、重复正文、相对或缺失时间不重推。讨论不能借用跨 URL 新闻后续证明。128 节、512 块、24000 字、4000 hash 与现有 256 行/7 天/8MiB 历史限制不变。工作线程计算 hash，不新增模型调用、网络请求或循环任务。

## 验证

- 最终完整 JVM 820 项、116 套，0 失败/错误/跳过；新增 HupuProgressEvidenceTest 8 项，含来源解析、hash-only 往返、旧分页/复制/已知 ID 编辑拒绝、权限和版本、账号与事件阅读、不同线程和冻结快照。
- Debug/Release Lint 成功；隔离 Debug App 与测试驱动构建成功。
- Android16 隔离模拟器虎扑两轮各 16/0：真实 SourceParser、ArticleSession、CookieManager、ArticleProgressBinding、SQLite、显式 refreshSnapshot；账号切换失效、取消不写、重开库保持、再次展示消费均验证。
- Android 回归：V2EX 16/0、来源元数据 9/0、新闻进展 12/0、历史与接线 35/0。共六份 Debug 日志 104 项检查、0 失败，均唯一 raw INSTRUMENTATION_CODE: -1。重复检查不视为独立场景数。
- 隔离非调试 R8 包：新增参数化虎扑阶段，只用公共 Android/SQLite/序列化缓存格式；不反射生产类或注入正文 hash、scope、verified_at。真实预读和自然首页曝光建立主帖基线，圆点降频；宿主只 force-stop 本隔离包，独立阶段替换合成正文，由生产缓存解码/observer/hash/storage 产生新回复证明；圆点重推、自然曝光消费、下一次未见优先。虎扑两轮 seed 各 5/0、verify 各 10/0；V2EX 回归 seed 5/0、verify 10/0，共 45 项检查，均唯一 raw -1。
- 实际安装 Debug 与 R8 App/驱动 SHA256 均与归档包匹配。

证据目录：`artifacts/local-ai/hupu-discussion-progress-20261028/`，包含 JVM/Lint、APK/hash、Debug/R8 原始日志。一次主机 ADB 查询未设 ANDROID_USER_HOME 导致无法建立 /.android，设置项目工具路径后查询成功；不是产品故障，没有清除 AVD 数据。

## 边界和下一步

本轮 R8 验证从序列化合成正文开始，不证明 R8 公网 HTML 解析、真实受限登录内容可用或推荐价值。Android Debug 验证真实解析器，但正文和账号 Cookie 是受控测试值。未验证自然语言无标记的新进展识别；贴吧/知乎讨论进展仍未接入。真机账号权限、全长分页、真实个人收益、ARM 整体峰值/拔线能耗和长期缓存门槛保持开放。

普通候选仍 c63fe1d7，手机仍 c315361c；本轮未生成新的普通升级候选、访问手机或发布。模拟器退出证据另存 cleanup-final.json，userdata 保留。