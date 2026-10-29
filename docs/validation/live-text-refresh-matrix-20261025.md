# 九个默认文字来源两轮刷新与缓存回退 / 2026-10-25

## 本轮范围

新增测试侧LiveTextRefreshProbe及live-text-refresh诊断入口，使用实际HotSourceCollector生产传输、Android API36 emulator-5560和同一隔离数据库。顺序跑两轮真实九源采集；随后切换为明确注入的离线失败Transport，对相同实际采集缓存验证失败保留。没有关闭无线网络、操作验证码、登录或改用户来源设置，没有生产逻辑/品牌/默认版本变化，不提交推送发布。

两轮均READY 9/9，第二轮9/9确有新requested并完成committed。**58 checks / 0 failures**，storageErrors=0。第二轮每个成功时间均晚于本轮开始，所有仍在库的相同身份first_seen保持不变。来源中文标题/URL归属/身份去重与完成状态检查通过；受控失败9源全部保留既有内容指纹、数量及lastSuccessAt，不伪造成功时间，不遗留REFRESHING。

短间隔同标题不证明上游陈旧或长期新鲜，只能证明本次实际采集/入库发生；没有把视频条目数量当成默认推荐视频数量，最终CandidateOrder仍负责过滤。知乎热榜成功也不等于知乎全文权限通过。

| 来源 | 第一轮 / 第二轮候选 | 第二轮新请求 | 内容指纹变化 | 来源摘要 / 视频标记 |
|---|---:|---|---|---|
| ZHIHU | 30 / 30 | True | False | 24 / 0 |
| WEIBO | 48 / 48 | True | False | 0 / 0 |
| HUPU | 20 / 20 | True | False | 0 / 0 |
| CLS | 20 / 20 | True | False | 0 / 0 |
| V2EX | 2 / 2 | True | True | 0 / 0 |
| ITHOME | 12 / 12 | True | False | 0 / 0 |
| THEPAPER | 20 / 20 | True | False | 0 / 4 |
| BAIDU | 50 / 50 | True | False | 0 / 0 |
| TIEBA | 30 / 30 | True | False | 30 / 0 |

总候选第一轮232条、第二轮232条，来源内容类型未逐篇核实；其中澎湃4条明确视频标记保留于供应库，默认候选排序既有过滤仍负责排除，不能称232篇可读图文或独立事件。多数源没有摘要，没有凭热度/标题补写来源正文。

## V2EX仅2条的追查

旧记录曾有36条，本轮连续两轮均2条、其中1个驻留身份变化。为区分漏解析与来源短列表，追加一次生产单源诊断和一次普通WebView现场DOM观察。二者均给出相同2条主列表内容。DOM实际 #Tabs 唯一tab_current的href是/?tab=hot，#Main .cell.item .item_title 有2个主题，#Rightbar #TopicsHot 中另有10个item_hot_topic_title。后者是独立侧栏，既有选择器正确排除，不以侧栏凑大主榜。

V2EX来源契约仍为用户指定最热页，不改抓/api/topics/hot.json或另一榜；不因数量少循环抓取、强制浏览器重试或假装增加候选。既有ExpandedSourcesTest.v2exUsesOnlySelectedHotPageMainListInOrder已覆盖侧栏排除与主列表顺序，本轮没有再增加同义测试。

桌面Python直接匿名请求官方最热页遇HTTP403；Android普通WebView与生产原生通路本次均可获得页面。不同网络/客户端的可用性不互相代替，不声称已解决VPN或认证限制。

## 证据与边界

artifacts/local-ai/live-text-refresh-matrix-20261025.txt 与 .json 包含两轮时间/状态/指纹与逐条检查，live-v2ex-short-pool-20261025.txt为单源，live-v2ex-short-pool-browser-20261025.txt及局部HTML为浏览器观察，v2ex-short-pool-structure-20261025.json为可重复结构判定及捕获HTML SHA256，不含Cookie值。页面HTML只留本地诊断，不加入App资源或可发布夹具。

构建脚本成功，既有JVM461项结果复用（本轮未改生产源码）；新Android测试入口已编译、安装、实际执行。未重建R8，因为生产实现未变，不能把此debug现场矩阵冒称最终普通发行包现场通过。

A02有了默认九源的两次实际刷新证据，失败回退阶段明确是受控Transport；仍缺长时间微博新鲜度/权限、各来源真实鉴权与复杂内容结构、手机/VPN差异。个人推荐收益、真实账号全文、Find X7 Ultra全进程RAM/耗电/热与最终普通发行升级/完整视觉辅助功能均未完成。
