# 平台偏好及V2EX采集 / 2026-10-24

## 平台偏好实现

用户在本轮明确新增平台偏好。FeedStore schema从3升为4，仅给feedback增加source非空默认空列；新的READ/SAVED/内容MORE/LESS/兴趣选择保存实际candidate.source，旧反馈不猜补。现有撤销、过期、关闭阅读学习、取消稍后读和重置直接作用于同一事件，避免复制另一组难以撤回的计数。

PlatformPreference独立处理scope=source的MORE/LESS及有可信来源的sample事件。三个不同文章才形成弱来源倾向，同一candidate不同metadataHash/READ/SAVED/多次反馈不重复计数。显式来源选择优先，行为先验有界。SemanticOrder忽略source scope的主题输入，画像signature包含平台先验，算法版本更新为examples-rrf-platform-v4。原embedding缓存保留，精排缓存按新版本身份重用/重算。

冷启动排序应用一次平台调整；模型使用CandidateOrder.forModels原始基序，最后SemanticOrder.fuse在同一正/中/负内容分类内应用一次，明确内容负反馈不能被喜欢的平台越过。最新排序不受平台影响，事件合并只选择已有偏好来源，不累加热度。source-only可以生成platform模式快照，不启动模型；本轮没有新增模型、API或后台常驻任务。

DiscoveryActivity的来源菜单及内容卡片增加平台多看/少看；反馈记录区分平台偏好，继续遵守冻结列表/待更新快照，不因写偏好突然改变当前列表。

## 平台验证

API36模拟器emulator-5560，正常应用debug store runner。原始文件在 `artifacts/local-ai/`。

| 报告 | 检查/失败 | 范围 |
|---|---|---|
| platform-preference-api36.txt | 11/1 | 数据验证11项通过后原生菜单定位失败，原报告保留；不能称生产排序已失败，也不能证明全部根因 |
| platform-preference-touch-api36.txt | 15/0 | v3→v4实际helper升级、旧来源未知、收藏/反馈/候选/快照保留、三篇弱参考、READ/SAVED来源、关闭与撤销/重置；滚动定位+实际触摸来源菜单，真实控制器/快照排序及撤销恢复 |
| read-interest-platform-preference-api36.txt | 5/0 | 实际20.1秒前台阅读/滚动/退出，原语义与关闭学习回归 |
| read-interest-platform-provenance-final-api36.txt | 7/0 | 前项加实际READ事件source=当前打开来源，以及单次实际阅读不形成整体平台倾向 |
| feed-store-platform-preference-api36.txt | 148/0 | 全存储；v1/v2迁移夹具同步移除source列并真实升至v4，保留向量、收藏及旧反馈 |
| semantic-platform-preference-api36.txt | 13/0 | 真实双模型及暖缓存/温度/内存暂停，不是个人推荐效果验证 |
| v2ex-fallback-platform-preference-api36.txt | 17/0 | 新schema下采集器网页兜底完整回归 |

最后 `build-platform-preference-reader-final.txt`成功，仅在上一轮成功包上新增实际READ来源断言；生产代码没有再变化。JVM321，failures/errors/skipped均0；增加7个PlatformPreferenceTest覆盖弱证据门槛/身份去重、明确平台优先/撤销、未知来源/预读不计、限幅/内容档位、聚合入口、不改变最新/不双加分。diff --check通过。

15项验证使用受控事件/候选，不代表真实用户兴趣准确率；7项真实阅读使用合成正文和现有原生交互钩子，不是实际平台网络全文认证。真实分组来源点击归属、卡片偏好入口全部主题字号、更多真实来源长期偏好、最终minified包升级和FindX7Ultra功耗/峰值仍待验收。

## V2EX自动网页兜底

发现采集器原来V2EX原生失败即结束，单平台页面已有网页回退。HotSourceCollector现在允许V2EX原生失败/空列表后一次DynamicBoard，仍读 `https://www.v2ex.com/?tab=hot`。使用同一refresh revision，保持旧快照/成功时间，最终结果才提交/计一次失败。网页回退进入已有串行队列，不与B站/抖音榜单WebView并行。

429、未来Retry-After、离线不回退；browser失败不循环。queued fallback也参加取消，开始前refreshCurrent核验来源仍启用及revision有效，关闭/停用不会遗留REFRESHING或启动迟到浏览器。同步启动回调的返回handle也会立即关闭。未尝试自动处理验证码或登录。

`v2ex-auto-fallback-api36.txt`17/0是受控Transport+真实SQLite/主Looper调度证据；后在最终schema4包再跑17/0。新HotSourceLiveProbe支持单来源生产诊断，不为了验证一站加载全部来源。

真实当前结果：`live-v2ex-auto-fallback-api36.txt` READY、38条中文标题、38条有节点标签、ready=1/1、storageErrors=0。该报告未记录具体传输阶段，不能仅凭READY声称本次一定发生了原生403后网页成功。随后 `live-v2ex-reader-auto-fallback-api36.txt`通过原生榜单和正文取得当前公开主题，正文解析710ms，51sections/133blocks，50条有r_ID且有作者的回复，主帖作者存在，缓存命中，生产统一阅读readerUI=true/sourceReload=false。当前页没有下一页链接，不称全部历史回复或真实多页成功。

`v2ex-live-unified-auto-fallback-api36.png`来自该次App统一阅读截图，已目视检查标题、作者、正文、独立“讨论”标题和回复卡片；未见原站导航/侧栏、正文遮挡或横向溢出。本轮只取回该公开页面截图，没有导出账户Cookie或整份缓存。匿名公开样本成功不能替代登录账号权限、实际手机VPN或所有时段网络可用性。

整体目标继续进行中，未提交、推送、发布或修改名称/logo。
