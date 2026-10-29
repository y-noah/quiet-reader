# IT之家日榜与统一图文 / 2026-10-24

## 来源契约

新增ITHOME：公开入口https://www.ithome.com/，取页面div#rank内明确“日榜”页签对应的ul.order[id=d-1]，只保留本站已确认的数字文章路径。首页最新、周榜、月榜、软件下载、外站链接和重复URL不入池；缺少已确认区域时报失败，不回退到任意首页文章。原网页中下载区也存在id=d-1，必须限定榜单容器，不能全页取首个重复ID。

listKind=POPULAR，时间范围为来源日榜；保留来源内顺序，不编造阅读量或发布时间，不参与跨平台热度相加。榜单没有摘要/时间时留空。ITHOME加入文字热门默认供给和来源筛选，沿用独立平台偏好、单源请求/退避、共享正文预读、会话及磁盘边界，不给科技平台预设兴趣权重。

统一正文只读已观察到的#paragraph.post_content，标题来自h1，作者来自#author_baidu。正文真实图片位于data-original；其非空HTTPS地址优先于通用src占位图，修复仅在该来源正文区域生效。沿用共有图文/视频附件兜底；页面侧栏、推荐、未加载评论不冒充正文。没有新增ITHOME评论API或绕过账号限制。

## 数据升级

FeedStore schema4→5仅INSERT OR IGNORE新来源行，现有开关、收藏、反馈、快照和向量不重建。新数据库默认文字来源增加IT之家；已有用户可关闭，重开不恢复开关。v1/v2/v3原迁移断言更新为当前schema5，原有数据保留检查仍运行。

SourceExpansionProbe专门构造缺少ITHOME行的schema4数据库（包含原来源开关、收藏和反馈），真实SQLiteOpenHelper升至5；不是仅测新建库。仍非精确签名跨APK升级认证。

## 验证及保留的失败

报告位于artifacts/local-ai，正常debug应用/API36模拟器；公开原始页面位于artifacts/public-sources，未保存账户Cookie。

| 报告 | 结果 | 范围 |
|---|---|---|
| build-ithome-source.txt | JVM332中3失败 | 旧测试写死12来源/IT之家不支持；新适配4项单测已通过。更新旧支持集断言，原失败日志保留 |
| feed-store-ithome-source-api36.txt | 155/1 | 新升级夹具用了不产生SAVED兴趣的save，却要求同时保留SAVED/MORE两反馈；原数据开关/新行检查通过 |
| feed-store-ithome-source-verified-api36.txt | 155/0 | 夹具改用真实saveWithInterest后完整存储/采集/旧升级/新来源升级通过 |
| platform-ithome-source-api36.txt | 14/1 | 实际菜单/排序通过后，最后撤销等待超时；未确定是pending阶段还是顺序等待 |
| platform-ithome-source-repeat-api36.txt | 15/0 | 同生产包复核完成撤销与顺序恢复；不把一次重跑通过当成原超时已定位 |
| live-ithome-source-api36.txt | READY12，1/1，storageErrors0 | 实际Android生产榜单，非桌面抓取代替 |
| live-ithome-source-repeat-api36.txt | READY12，1/1，storageErrors0 | 第二次独立生产采集，有实际请求，不用旧缓存换时间 |
| live-ithome-reader-api36.txt | readable1/1，probeErrors0 | 当前日榜第一篇原生正文88ms，1节/5块，cacheAccepted/cacheHit均true，readerUI=true/sourceReload=false |
| live-ithome-image-api36.txt | readable1/1，probeErrors0 | 同一已取得的真实文章，从Discovery.open生产入口进入统一阅读，无原网页开启；actualBodyImageLoaded=true，解码宽1440，overflow=false，players=0 |

首次ithome-live-reader-api36.png截图在图片加载前截取，不能以占位证明图片成功；随后在实际图片解码后截取ithome-live-image-api36.png，已目视复核：标题作者、来源正文、真实图片、后续正文统一排版，无原站导航/侧栏或横向溢出。只覆盖默认主题/字号/屏宽；未证明全部字号/深色/无障碍。没有下载图片绕过显示限制，截图来自App实际阅读界面。

最终build-ithome-image-observation.txt成功，JVM332失败/错误/跳过0，diff检查通过；最后一次构建仅扩展诊断目标校验，生产代码未再变化。4项新单测覆盖榜单重复ID/日周分离、失败不回退、正文/作者/真实图片及内容路径所有权；ReaderLinks对本站静态资源子域不误识别为文章。

## 限制与下一步

公开单篇图文成功不等于真实登录、评论分页、每篇媒体或长期供给质量完成，也不证明“八个文字来源长期稳定”。来源平台偏好能学习该新source，但真实个人推荐效果仍需用户标签。另只读探查确认澎湃及掘金公开接口返回数据，尚未纳入App、未把它们计为可用来源。

当前无本轮活跃测试/构建，未提交、推送、发版或改品牌。原平台菜单撤销偶发超时保留待专门定位；个人质量、其他文字来源全文/讨论、真机资源功耗与精确包升级仍未验收，整体目标未完成。
