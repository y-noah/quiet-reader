# 同版本正文类型纠正 / 2026-10-24

## 问题与实现

旧实现能从正文推断video并排除推荐，但同一标题/摘要后续取得图文时没有否定通道，需等待七天证据到期或元数据变化。现在较新的主帖图文可以撤销推断视频；旧正文不能把新判断倒退。来源明确提供的video=true且无推断时间仍具优先级。

BodyVideoEvidence.textTopic仅认可非回答的“正文/正文 · 作者/主帖/主帖 · 作者/问题补充”，累计至少200个文字字符。标题、notice、裸blocks、短摘要、评论/回答、搜索/帖子集合、视频简介不够；登录/来源受限/整篇视频拒绝，虎扑后续页拒绝。图文附带视频可保持图文身份。此门槛是保守来源结构约束，不是另一个AI分类器；图像为主的笔记等未知结构保持未知。

HotCandidate的既有videoEvidenceAt字段扩展为整篇类型取得时间，video=false也可保存，不改SQLite schema。FeedStore.recordTextTopic只接受当前active候选精确textHash、有效七天时间、严格晚于既有水位的输入。正/负证据均保留水位，重复/倒序/同时间不改变类型；同元数据刷新不续期，语言更新/JSON重开保留，元数据更新和证据到期刷新重新判断。更晚真实视频可再次改为video；类型变化才bump sources，不产生偏好反馈。

主阅读的ReadingLanguageRecorder及发现页预读的bodyPrepared复用这一通道，继续要求取得时URL/metadataHash、Cookie会话及namespace匹配。纠正后按原冻结列表/待更新快照机制更新发现页；当前画面不自行增删跳动。没有新增常驻任务、评论抓取、视频预取或模型调用。

## 证据

设备：项目API36模拟器emulator-5560，正常应用debug store runner；FindX7Ultra未连接。原始报告位于 `artifacts/local-ai/`。

| 报告 | 检查/失败 | 覆盖 |
|---|---|---|
| video-evidence-store-text-correction-api36.txt | 26/0 | 既有正向证据15项；正→负、倒序/同时间、过期/未来、hash、源video不可撤销、负水位保存/过期与偏好隔离 |
| video-evidence-flow-text-correction-api36.txt | 16/0 | 第一轮实际主阅读纠正、返回及列表快照；随后新增主动缓存纠正覆盖 |
| video-evidence-flow-text-correction-final-api36.txt | 18/0 | 真实MainActivity读取新正文纠正；旧取得时间及错误hash/Cookie/namespace拒绝；冻结列表不跳动；应用更新后恢复条目；实际ArticlePreloader/DiscoveryController纠正 |
| legacy-video-revalidation-text-correction-api36.txt | 19/0 | URL旧标记、重新取得、共享缓存、取消及失败节流 |
| article-version-flow-text-correction-api36.txt | 14/0 | 标题/摘要版本变化、旧缓存/迟到结果拒绝与预读交接 |
| body-language-flow-text-correction-api36.txt | 7/0 | 主动阅读/预读语言证据与返回快照 |
| feed-store-text-correction-api36.txt | 148/0 | 完整SQLite来源/推荐/用户数据回归 |

`build-text-type-correction-final.txt`构建成功。JVM314，failures/errors/skipped均0；本轮增加3个BodyVideoEvidenceTest，验证保守主帖范围、受限/集合/后续页、负水位JSON及候选资格。git diff --check通过。最后括号补丁仅显式表示已有&&/||优先级，未改变表达式语义。

## 未证明的内容

流程正文由SourceParser解析受控HTML，页面活动/缓存/预读/SQLite/返回是真实生产链路，没有真实网络平台访问。这不能证明各来源都已正确区分主帖与评论，也不能证明真实账号权限/模型推荐质量/真机资源。

已经从发现列表过滤的条目不会因本次改动全部启动后台重新抓取；有新正文取得（如显式重读或仍在冻结列表内的预读）才纠正，普通刷新仍可在元数据变化/到期时清理证据。该限制保持默认排除视频与资源预算，不能宣称自动遍历全部误判。真实平台样本及未来扩展适配仍须独立验证。

整体目标继续未完成：抖音/小红书默认统一迁移、真实账号全文与讨论、媒体归属/权限、实际离线旧快照学习负例、推荐效果、FindX7Ultra整体1GB及耗电、精确包跨APK升级等仍待补齐。本轮未提交、推送、发版、改名或更换logo。
