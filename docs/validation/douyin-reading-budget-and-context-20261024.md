# 抖音动态读取预算与上下文身份 / 2026-10-24

上一轮真实标题路径修复及配置对比属于进展。本轮取得真实正文结构，并定位到过早截止与无关页面上下文变化触发重读两个问题。本文不宣称抖音默认入口/全来源统一阅读完成。

## 真实正文结构

`douyin-body-observation-api36.txt`直接观察本轮榜单的/hot/2711309/编码标题链接：来源跳转到/video/7700163938993868072，sentenceId归属校验通过；30.3秒时正式解析器取得视频说明与20条当前页评论（21 sections），无loginRequired/sourceUnavailable。页面11个Flight脚本，mainHTTP/network错误0。没有转写视频、获取播放流、绕过权限或导出HTML/Cookie值。

普通观察成功不等于生产读取链路成功。DynamicReader原先22秒总期限与14次检查，可能更早由检查次数结束。`dynamic-reader-budget-baseline-api36.txt`真实WebView合成26秒后才填入正文，旧实现18.2秒失败，6检查/4失败；夹具/报告均保留。

## 有限主动等待与重试

仅explicitReading且DOUYIN使用40秒截止时间/28次检查；其他来源和后台预读维持22秒/14次。页面检查间隔不变，仍只有一个读取WebView；默认推荐及预读继续排除视频。来源会话变化至多重新取得一次，且继承绝对截止时间，不重复续期；已过截止时间不创建重试。

`dynamic-reader-budget-fixed-api36.txt`8/0：实际26秒后填入的受控正文29.3秒完成、会话/元数据身份保留、视频为说明兜底、单次完成、后台短预算与立即取消。该报告在最终只修正截止时间扣除构造耗时前运行；`cookie-rotation-shared-deadline-api36.txt`最终16/0验证三种实际会话重试/再变更/取消，均继承原截止时间。主Looper繁忙时墙钟回调可晚于配置时间，真实报告有约43秒结束，不能承诺任何设备精确40秒返回。

ArticleLiveProbe的显式正文诊断改用explicitReading=true，与MainActivity主动阅读一致，不把原先后台提取目的当作生产主动读取验收。补充attempt/replacement阶段和Cookie名称变化采样，仅诊断模式使用，finally停止采样。

## 缓存身份精确例外

扩预算后仍失败。`live-douyin-acquisition-state-api36.txt`记录原读取9次检查后重读5次至截止；仅看开始/结束Cookie差异为空会漏掉中间变化。`live-douyin-session-observation-api36.txt`记录期间只有IsDouyinActive变化，触发了相同重读。随后仅忽略该活动标志仍在重启后的报告中观测到stream_recommend_feed_params变化（`live-douyin-activity-cookie-recovered-api36.txt`）。

ArticleCookies现在只在合法抖音数字话题/视频内容路径中，排除精确名字IsDouyinActive和stream_recommend_feed_params。这两项是活动/推荐页上下文，不用来区分固定内容的账号身份。完整Cookie仍照常发给来源；__ac_nonce/__ac_signature/strategyABtestKey/ttwid、全部账号和未知字段仍保留，不按名字模糊丢弃。前缀douyin-content-v2使旧身份缓存自然失效；不改账号登录或收藏。榜单、登录、无效路径及其他域没有这个例外。

JVM新增边界覆盖：活动/推荐上下文变化身份不变，sessionid/sessionid_ss/passport_csrf_token/unknown_auth/unknown_field与风险字段变化仍不同，其他域/路径继续保留活动Cookie证据。

受控`douyin-content-context-api36.txt`12/0：两个上下文同时变化，真实DynamicReader无重试；正式解析、会话、缓存及统一渲染成功；随后未知账号字段变化使旧ArticleSession拒绝盖章且真实缓存不可读。仅合成子域/数据，不替代真实登录账号切换认证。

## 生产链路与缓存的不同结论

`live-douyin-content-context-api36.txt`：本轮实际完整话题链接17.9秒取得21sections/21blocks，content=true、loginRequired=false、unavailable=false、retry=none，说明预算/上下文修复已经让真实正文提取链路成功。但cacheHit=false，结束时ttwid也发生变化；该字段仍保留在身份中，没有为通过测试放松它。不能将正文成功写成缓存已成功或默认迁移已验收。

测试期间原API36模拟器进程两次消失；一次源报告为空，另一次安装前设备不存在。没有发现明确退出原因，不归因于App或机器内存。空报告不能计为通过。重启复用原AVD，未清数据；安装脚本改为设备离线/命令失败时明确停止，替代对空getprop调用Trim的异常。PowerShell AST解析0错误。

最新构建`build-douyin-cache-observation.txt`成功，JVM325（失败/错误/跳过0）。最终缓存/封面复核及当前模拟器状态见本文后续记录。全目标继续进行中，未提交、推送、发布或改品牌。
## 最后受控复核

`live-douyin-cache-controlled-api36.txt`在同一启动/安装/测试宿主进程内正常结束，但43.2秒未取得正文，attempts11/retry=none；没有发生重复续期。期间来源重新设置nonce/referer/signature/ttwid等字段，本轮没有将它们从身份中排除。cacheAccepted=false/cacheHit=false。此结果与前述一次正文成功并存，证明现场仍不稳定；不是缓存或封面通过。

`live-douyin-cache-observation-api36.txt`对应的一次命令在安装前设备消失，没有有效来源报告，不能算失败正文样本或成功测试。没有为真实缓存/封面补齐正向证据，所以DOUYIN默认统一入口保持未迁移。下一步应处理真实稳定性及缓存/入口/登录回读验收，不能继续扩大Cookie例外来追求绿灯。当前其余未完成项（全来源、真实账号/讨论/媒体、离线旧快照学习负例、长期兴趣质量、FindX7Ultra峰值/耗电、最终包升级）仍有效。