# 候选存储与多来源采集检查

目标手机：OPPO Find X7 Ultra / 12GB物理RAM / Android16 / ColorOS16。
本次执行环境是项目Android16 x86_64模拟器 `quiet-reader-api36`，不是上述手机。

> 最新进展：生产刷新已经为B站/抖音启用串行普通WebView，初次真实结果9/9 READY、418条；后续两轮均8/9，抖音网页已加载但未取得列表，稳定性未通过。见本文末尾更新；早期“仅显式重试”及7/9记录是历史证据，不再代表当前采集实现。单次9/9不代表长期稳定或完整阅读验收。

## 初轮代码边界（历史）

- 多来源各自持有Repository/RequestQueue，普通请求最多3个并行。浏览器读取只在显式重试时启动1个，当前没有后台自动刷新或自动浏览器补抓。
- serial storage executor由页面owner管理；先在main调用collector.close，再排入store.close，再shutdown。晚到main回调不能重新投递存储工作。
- 取消、关闭来源、替换请求均使旧revision无法提交。重复success/failure只接受第一次；失败保留成功内容与成功时间。
- Retry-After支持秒数和HTTP日期。失败次数与等待期限持久化；局部指数退避最多30分钟，但服务端更长等待优先。没有为了等待结束而启动定时器；下次前台操作检查期限。离线不阻止网络恢复后的手动请求。
- 微博仍以官方hot_band为主；主入口429或明确Retry-After时不继续探测备用入口。其余原有备用逻辑保留。
- SQLite v1→v2显式增加来源退避字段和兴趣时钟元数据，不清空候选、收藏、反馈或阅读会话。
- 临时兴趣到期推进profile版本，拒绝到期前启动、到期后完成的排序；读画像和提交排序时惰性检查，无后台轮询。时钟回拨不恢复已经到期的兴趣。
- 向量读取拒绝并淘汰非有限或非单位向量，避免损坏缓存污染排序。
- 候选文本来自解析器的纯文本，不再二次当HTML解析而丢失 `<video>`、`<branch>` 等真实标题字符。规范化版本升到hot-title-v2，旧向量不会误命中。

## 确定性证据

| 范围 | 结果 | 证据 |
|---|---|---|
| JVM解析/阅读/新候选/退避/贴吧接口回归 | 最终191项，34 suites，0失败/错误 | `app/build/test-results/testDebugUnitTest` |
| API36存储与collector首轮 | 46项，0失败 | `artifacts/local-ai/discovery-store-collector-api36.txt` |
| API36补充升级/到期检查 | 49项，0失败 | `artifacts/local-ai/discovery-store-collector-api36-round2.txt` |
| API36贴吧与Item元数据变更后回归 | 49项，0失败 | `artifacts/local-ai/discovery-store-collector-api36-round3.txt` |

受控transport测试使用真实main looper、serial executor和SQLite，但不把合成网页/回调当作真实平台适配。覆盖排队中取消、数据库提交前取消、网络晚到回调、重复通知、禁用来源、浏览器限流、关闭页面和executor后无回调复活。

## 实际平台请求

2026-10-23，项目模拟器按当前会话配置真实请求；未进行任何账号登录。没有使用旧榜缓存伪装本次成功。

| 来源 | native热门入口结果 | 候选数 |
|---|---|---|
| 知乎 | READY | 30 |
| 微博 | READY | 50 |
| 虎扑 | READY | 50 |
| 财联社 | READY | 20 |
| V2EX | READY，仍为?tab=hot | 38 |
| 百度 | READY | 50 |
| 贴吧 | UNAVAILABLE，未返回可解析热门列表 | 0 |
| B站 | UNAVAILABLE | 0 |
| 抖音 | UNAVAILABLE | 0 |

证据：`artifacts/local-ai/live-native-hot-sources-api36.txt`，6/9、238条、storageErrors=0。这不是正文、中文全文、真实账号登录复用或全部平台已适配的验收。

后续桌面无账号只读响应样本位于 `artifacts/source-retrieval-20261023`：贴吧HTTP200但不是已有选择器能解析的榜单；B站HTTP200、JSON code=-352；抖音HTTP200、脚本启动页面。它们不能算“成功取到0条的空榜”，也不能把抓取时间当成功时间。正常浏览器流程另测，不逆向/绕过验证。

### 贴吧入口修复与浏览器复查

正常WebView首轮：贴吧首页为推荐流，未找到旧热议DOM；B站100条、抖音50条可正常解析（`live-browser-hot-sources-api36.txt`）。因此不把贴吧主页普通帖子当热议榜加入。

实际验证官方 `https://tieba.baidu.com/hottopic/browse/topicList` 返回errno=0、`data.bang_topic.topic_list` 的30条热议。新增TiebaHotBoard，明确拒绝`sug_topic`/`user_his_topic`、错误状态、错主机、错topic_id和普通帖子URL。保留返回的顺序、讨论量文本、真实topic_desc和create_time；不从抓取时间伪造话题创建时间。JSON中的`&amp;`只做实体解码，不破坏percent编码。

- `Models.Item`增加可选来源摘要/时间，旧构造与旧缓存JSON仍兼容。新collector/HotCandidate桥接保留真实字段；其他无证据来源仍为空。
- 贴吧native endpoint切换到官方热门JSON；普通来源页保留主页，DynamicBoard的贴吧后备则读取官方JSON的body文本。
- 第二轮native实际结果：7/9。知乎30、微博50、虎扑35、财联社20、V2EX38、百度50、贴吧30，共253条；B站/抖音的直接HTTP仍未取到列表，不隐藏该限制。见 `live-native-hot-sources-api36-round2.txt`。
- 第二轮正常WebView实际结果：贴吧30、B站100、抖音50，probeErrors=0；见 `live-browser-hot-sources-api36-round2.txt`。没有账号登录或验证码操作。该probe验证普通浏览器与解析器，不替代新首页的真实重试入口验收。
- native与browser取到内容发生在不同时间，不能把计数机械相加当成一个已提交的候选池；生产collector目前仍只在显式重试时建立单个浏览器。

本轮安装包证据 `artifacts/local-ai/discovery-source-package.json`：签名诊断APK 325,504,327字节，SHA256 `902ed29e98006fed47799ee6c4c44d5c3ec0301b0ac2cd5f354fd6c1ef2702b2`；测试APK SHA256 `0e2355e55a8b9834c94dd619fbfeada7cace6cbf00e8b6b80bdb1cf6bdcc952e`。模型文件/清单未变，64位ELF 16KB对齐检查通过；仍不是发布版或ARM实测。

## 未完成与后续工作

1. 在来源UI接入已验证的显式浏览器后备，展示真实访问限制/成功时间；真实登录和移动版正文仍需逐平台验证。
2. 新发现UI、来源管理界面、完整偏好闭环及推荐调度尚未接入；M02/M03/M09未整体验收。
3. 标题的汉字筛选不等于中文全文认证，除已保留的贴吧字段外，摘要/时间仍须逐来源核实补齐；不捏造这些信息。
4. 保守事件归组和多来源入口；不能靠相似标题强行合并，更不能给来源数加分。
5. 缓存容量、存储故障恢复、进程被杀后的状态恢复、模型资源修复与清理。
6. 实际Find X7 Ultra上的整App峰值内存、耗电、热保护与个人兴趣效果。P0真机门槛保持未关闭。

没有发布、没有推送、没有改品牌或添加云AI服务。

### 后续当前样本复查

当前原生热门采集仍7/9成功（live-native-hot-sources-current-api36.txt）。新增真实正文诊断live-article-cache-api36.txt：微博/财联社/V2EX各一条正文与内存缓存命中，首次耗时分别15955/214/418ms；知乎返回登录或访问限制；虎扑样本定向复查为视频过滤（live-article-hupu-api36.txt）。小样本只证明这些请求结果，不证明全平台正文完整性、账号同步或推荐命中率。进展与剩余门槛见development-progress.md最新记录，前面的“未完成”历史条目不能代替当前实现审计。

## 生产刷新接入普通网页来源（2026-10-23 后续）

HotSourceCollector生产Transport为B站/抖音选择DynamicBoard，其他七源仍走Repository；并发总槽位最多3，动态WebView最多1，另一个动态来源等待时原生来源仍可占用空槽。前台owner停止即取消，来源禁用/退避继续在开始前检查；浏览器受RecommendationPolicy初始限制和资源监听。用户不必另点“通过网页读取”才让两源进入候选池。

DynamicBoard的快照解析改到单worker，原HTML最多400万字符，空列表继续有界重试，关闭后不交付旧结果；主框架HTTP401/403/429向collector传递状态及Retry-After，保留成功缓存。禁止图片加载，媒体需用户手势并阻断常见音视频分片URL。不是无图登录页：此surface仅用于读榜，显式登录页不改动。B站/抖音原解析器本就会对空结果抛错重试，自动采集缺口主要是生产入口没有接通；通用空列表检查是额外健壮性修复。

browser-collection-store-api36.txt为115/0，新增自动网页串行与原生空槽并行测试。live-production-browser-sources-api36.txt使用生产Transport/Collector、隔离FeedStore及当前设备会话：知乎30、微博49、虎扑50、财联社20、V2EX39、百度50、贴吧30、B站100、抖音50，9/9 READY共418条，storageErrors=0。没有账号/验证码操作，没有将两次独立抓取结果拼成一个成功快照。

只证明本轮真实热门列表采集。未证明长时间稳定、不同会话条件、正文中文100%、登录后的完整内容、手机端交互和真机能耗。范围门槛要求稳定可用，不能仅凭单轮9/9关闭该门槛。诊断mode live-sources仍是纯native对照；新增live-production-sources验证实际选择路径。

## 后续生产采集：抖音不稳定，不能以初轮9/9作为完成结论

live-production-sources-round2-api36.txt与round3均completed=true、ready=8/9、storageErrors=0。第二轮338条（虎扑20），第三轮368条（虎扑50）；知乎30、微博50、财联社20、V2EX38、百度50、贴吧30、B站100，抖音0。每轮独立空测试库，不用历史榜单填充成功状态；仍沿用模拟器现有WebView会话，没有登录/验证码动作。

DynamicBoard失败提示拆分TLS连接失败、主框架加载错误、未完成加载超时、已加载但提取未成功。第三轮抖音明确为“来源网页已加载，但尚未读到热门列表”。不据此断言必需登录，也尚未区分平台返回挑战/结构变化/异步加载不足，后续需要网页结构诊断；没有调整重试频率、超时上限或绕过访问限制。HTTP401/403/429分类与Retry-After仍保留。

其余8源两轮READY不是正文完整、账号同步或内容时效的全范围验收。V2EX38项有节点标签，其余多数仍OTHER，分类能力缺口仍存在。

## 抖音对照与统一页面截止时间

新增live-browser的source参数可仅诊断单一平台。douyin-browser-comparison-api36.txt普通WebView同解析器取得50条、HTML854514字符，无登录/验证码动作。这说明至少此次页面结构可解析，不能断言所有失败都是解析规则错误。

DynamicBoard同时使用12次尝试与30秒owner截止；前者可在DOMContent仍填充时提前终止。保留原30秒总上限、1500ms检查间隔和取消，移除12次的额外提前终止。live-production-deadline-api36.txt随后生产路径9/9 READY，共418条，抖音50；B站成功后约25.6秒才记录抖音成功，处于原总上限之内。普通WebView对照可能也改变同一会话的站点存储，尚未做隔离会话A/B，因此不把一次恢复称作根因完全解决或稳定性通过。主框架限制和Retry-After不变，不自动登录或解验证码。
