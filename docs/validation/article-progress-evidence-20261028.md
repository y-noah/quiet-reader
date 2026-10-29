# 来源更新时间与正文进展证据基础

状态：已有解析、缓存字段、保守正文对比与独立验证。尚未接入曝光/阅读基线持久化、实际进展回传和冷却绕过。不能称为“重要进展重新推荐”已完成或可用。无手机操作、普通候选替换、推送或发布。数据库仍schema8；current-candidate仍c315361c/schema7。上一阶段普通本地449ca853也没有重新生成/晋升。

## 本轮生产变化

1. ArticleUpdateEvidence只为BBC/澎湃/IT之家/财联社/爱范儿/少数派的正文考虑来源更新时间，不处理讨论平台。通用meta须有完全一致的canonical；JSON-LD仅考虑身份完全一致的Article/NewsArticle/ReportageNewsArticle。拒绝未来/无时区/冲突时间、异常或多根JSON；解析前限制深度12、单段200000字符、最多8段脚本，解析后图遍历64节点/8层。发布时间、标题、页面普通time标签不能替代更新时间。此为支持的合同，不代表每个平台都实际提供这些字段。
2. BBC直接利用已捕获__NEXT_DATA__中经过现有文章id/语言/页面合同校验的metadata，再要求canonical一致、数值lastPublished不早于firstPublished且不晚于当前时间，并与atiAnalytics.timeUpdated一致。已在真实捕获bbc-article-0样本验证；不是声称观察到了同一文章的两次真实重大更新。字段冲突只扣留证据，不破坏可读正文。
3. Document增加带版本的sourceModifiedAt证据；ReadingHistory编码/解码和ArticleDiskCache异步快照保存。旧缓存没有证据不补造，未来版本/时间类型错误/未来时间不采信。新增字段纳入RAM/序列化估算。它不参与展示指纹，单纯时间变化不应触发正文重绘。
4. ArticleProgressEvidence仅考虑同一文章身份与同一scope的两次完整中文新闻正文，保留hash而非正文/标题/作者/Cookie。排除分页、相关帖、评论/楼内回复、虚拟讨论、视频主题、登录/不可用/部分内容提示。最多16个正文节、256块、24000 UTF-16字符、12000个8字符指纹；超限不推断。
5. 字符窗口按内容hash采样，避免插入一个字导致后面所有样本错位。标点/空白规范化、重复文本去重。只认明确的简繁中文更新/最新进展/补充报道/后续报道标签或独立标题。初始工程阈值：原正文指纹至少80%仍在、当前至少50%来自原正文，至少96个新增指纹且64个来自标注更新段。更新时间须确实晚于旧获取时间和上次展示/阅读；没有正的真实展示时间不放行。这些阈值未经用户收益校准，不是事实真伪或重要性的语义分类器，也不能代替跨文章/新回复的判断。

## 验证与原失败

证据目录 artifacts/local-ai/article-progress-20261028/。

- 最终762 JVM /112套，0失败/错误/跳过。新增ArticleUpdateEvidenceTest 13项、ArticleProgressEvidenceTest 16项，BBC既有套件新增2项。涵盖真实捕获BBC时间字段、来源/身份/时区、深层/多根JSON、旧缓存、正文新增正例、单字插入及少量替换/标点/顺序/重复/仅标题时间/无更新标签等反例、相同scope、分页/评论、体积上界和hash-only往返。
- Debug/Release完整Lint通过。此前一次Lint传入了拼错但该任务未使用的native源路径而成功，最终以正确源路径重新执行，采用最终lint.txt；不把错误参数作为环境证据。
- final-article-progress.txt及final-repeat-article-progress.txt两轮各12/0、唯一code -1：真实Android统一解析、公开BBC捕获样本及冲突反例、hash/JSON比较、真正ArticleDiskCache.enqueue异步保存后立刻改变原对象，磁盘仍保留入队时证据；旧缓存不制造证据；只删除本探针自己生成的单一缓存文件。
- final-recommendation-refresh.txt 6/0/code -1，既有真实首页换批、已读降频、连点合并、后台取消仍通过。早期本轮exposure-history.txt 18/0/code -1，不追称为最终字节的全存储覆盖。
- first-build.txt原756项1失败：旧缓存测试夹具缺少decode原本必需的sections数组，补齐夹具而未放松decode。bbc-fixture-type-first.txt原761项1失败：测试构造字符串类型时间时对数值调用严格JSONObject.getString，改用Long.toString(getLong)；不是生产正文故障。原失败均保留。
- 中间article-progress.txt/repeat-article-progress.txt各10/0，不含后来新增的BBC两项；最终使用上述12项文件。

## 后续接线要求

- 在主线程只捕获有界不可变输入；hash/JSON/SQLite工作放到已有串行worker，不能在滚动热路径计算所有正文。队列有界，不新增定时抓取、后台扫描或模型轮询。
- 建立scope隔离、时间及字节/条数有界的已获取正文/真实展示基线。只有实际曝光或有效阅读才能推进基线，抓取不算看见。有效阅读必须记录当时真实显示的正文，不能被较新的预读副本替代。
- 来源获取、缓存namespace、账号会话、元数据/选帖身份在输入捕获和写入前均复核。过时任务、账户切换、时间倒退、错误写入不得重推或污染另一scope。
- 同一已接受事件仅从原成员身份获取证据；重要进展时间必须晚于整个事件的真实最近曝光/已读事实，仍不能跨兴趣质量层。未知证据仍保留冷却。自动获取只能准备证据，不能改变正在阅读的列表。
- 新URL的后续报道和新的有价值讨论仍需独立的已接受事件关系/内容证据；当前严格同文章模块不能冒充已覆盖。未识别更新标签的实质变更也尚无通用语义验证。上述接口完成后再验证进展真的进入显式换批，随后补R8/普通升级和真人/资源门槛。

上述进展判定尚未接入FeedExposureHistory，当前生产Recent.verifiedProgressAt仍为0。仍待完整进展持久化与接线、实际本人收益、真实账号/完整分页、当前普通候选真机资源/拔线能耗、长期缓存等整体验收。

terminal-summary.json核对最终三个探针各唯一code -1。package.json记录最终Debug与驱动散列，jvm-summary.json保存762/112/0失败。cleanup.json确认本轮模拟器匹配进程0、userdata保留、current-candidate仍c315361c、没有手机操作或发布。重要进展冷却绕过仍未启用。
