# 澎湃新闻文字热榜接入

## 来源与实现边界

公开入口为https://cache.thepaper.cn/contentapi/wwwIndex/rightSidebar，只读data.hotNews，不回退到editorHandpicked、morningEveningNews或其他栏目。当前公开响应20项，15个文章、4个视频、1个其他类型；解析保留19个受支持文章/视频元数据，未知类型跳过，视频标记在推荐排序前过滤。榜单名次为来源事实，不增加跨平台热度权重，不累加同事件来源数。公开响应保存于artifacts/public-sources/thepaper-sidebar.json，不含Cookie。

THEPAPER枚举、热门来源目录及默认文字供给接通，已有用户开关不重置。FeedStore schema5→6通过INSERT OR IGNORE添加澎湃行，schema4升级亦补IT之家；禁用后重开不重新启用。正文使用普通Repository/共享缓存/统一ReaderHtml，不新增浏览器采集或后台常驻任务。

正文仅支持www.thepaper.cn/newsDetail_forward_数字，原始公开页面包含__NEXT_DATA__。pageProps.contId及contentDetail.contId均与请求ID一致才采用contentDetail.content；不使用summary、侧栏、任意article兜底，不执行来源脚本。类型不符、已知付费标记、外链、正文缺失或身份不符显示来源不可用状态。标题、作者、图文由共用清理及渲染器处理；没有接入评论API、播放器或视频正文，不宣称付费/登录内容可读。

## 验证

报告位于artifacts/local-ai，API36模拟器，正常debug诊断包：

| 报告 | 结果 | 范围 |
|---|---|---|
| build-thepaper-source.txt / build-thepaper-source-final.txt | 构建成功，JVM337失败/错误/跳过0 | 5个新增单测覆盖热门范围/错误不回退/顺序与视频过滤/全文图片作者/身份和来源边界；最终补实际CandidateOrder排除视频及默认启用断言 |
| feed-store-thepaper-source-api36.txt | 158/0 | 完整存储、采集、旧升级；schema4/5缺少新增来源行时升级保留收藏、SAVED及MORE、原开关，禁用后重开不复活 |
| live-thepaper-source-api36.txt / live-thepaper-source-repeat-api36.txt | 均READY19，ready1/1，storageErrors0 | 两次独立Android生产请求；19为采集记录数，包括4条已标记视频，不冒充19篇文字推荐 |
| live-thepaper-reader-api36.txt | readable1/1，probeErrors0 | 当前热榜第一篇，原生获取1668ms、1节12块，cacheAccepted/cacheHit，readerUI=true/sourceReload=false |
| live-thepaper-image-api36.txt | readable1/1，probeErrors0 | 同一已取得文章，实际Discovery.open→common-reader，rawPageOpens0；actualBodyImageLoaded=true，imageWidth647，overflow=false，players0 |
| platform-thepaper-source-api36.txt | 15/0 | 新schema的旧反馈迁移及实际平台菜单/撤销排序回归；不是澎湃真实阅读行为标签质量认证 |

真实文章：https://www.thepaper.cn/newsDetail_forward_34324278。原始页面保存于artifacts/public-sources/thepaper-article.html。thepaper-live-reader-api36.png为最初截图，图片仍为占位；随后等待实际图片解码，thepaper-live-image-api36.png已目视检查：标题、作者、正文、实际配图采用共用排版，无原站导航/侧栏或横向溢出。配图自身是来源文章包含的网页截图，不是原站网页被当阅读器展示。默认字体/主题/宽度检查不扩大为全部无障碍验收。

最后构建仅补单测断言，生产代码与实际Android验证包相同；diff检查通过。未提交、推送、发版或改品牌。

## 仍待完成

公开单篇不证明真实登录复用、账号切换、全文覆盖率、评论分页或长期供给稳定。共用预读路径可接收该source，但本轮没有把缓存读写成功冒称澎湃预测预取已独立验收。九个默认文字来源也不等于九个长期可用来源。真实个人兴趣留出评估、其他文字来源全文/讨论、FindX7Ultra全进程峰值与耗电、精确release升级仍需完成。
