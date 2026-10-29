# BBC中文接入与候选筛选（2026-10-26）

## 选择证据与未选来源

新授权允许加入符合条件的海外中文平台。原始只读研究在artifacts/source-research/overseas-chinese-20261026：BBC中文、中央社、文学城、联合新闻网和联合早报页面HTTP200；RFI403；德国之声TLS连接超时。访问首页成功不等于正文可免费读或适合接入。

进一步核验BBC官方简体热门页HTTP200、mostRead=10、官网前五篇简体文章HTTP200且均有公开完整文章content块。成熟中文媒体与现成热门信号足以作为首个候选，不虚构中文具体活跃用户数。联合早报存在订阅内容及首页热门区异步加载，不本轮加入；中央社/联合新闻网/文学城仍是未完成正文与信号核验的候选，不宣称不符合或已接入。RFI/DW本轮访问失败也不是所有设备永久不可用的断言。

正式入口 https://www.bbc.com/zhongwen/popular/read/simp 。遵循现有官方名次回退与本地兴趣排序，无地区权重、阅读数混加或总榜恢复；默认文字热门来源9→10，旧导航五平台不扩成多栏首页。

## 实现

BbcChineseSource解析官方mostRead和本篇文章的Next数据，核对服务/版本/文章路径/类型/URN。主文、段落、链接、图片和图注进入SourceParser统一排版；不把摘要/整页/侧栏拼成正文。已知推广块忽略、未知正文结构失败关闭。简体榜+繁体中文站内文章可读，不增加外文翻译。

Models/UrlPolicy/ReaderLinks/HotSourceCatalog接入，FeedStore显式6→7仅新增来源行；旧库源开关与反馈保存。SourceExpansionProbe增加实际v6缺少BBC行的迁移，v4/v5也保留，关闭来源重开仍关闭。

文字内不透明视频保留封面与紧凑来源入口。ReaderMedia限定BBC实际图片CDN；ReaderHtml行内受限媒体现在也显示合法封面，无播放地址时无video元素/解码器。未获取视频文件或调用播放器API。

## 可复验样本

app/src/test/resources/bbc-chinese-20261026保存官方Next数据的裁剪派生：保留身份/内容，移除与测试无关的数据，没有改写段落。capture-manifest.json记录原HTML和派生SHA及五篇实际正文的全部text段落。繁体样本额外保存，同ID对应繁体文章；原始HTML留研究目录。

五篇简体文章正文逐段包括尾部核对通过（侧栏摘要不混入），一篇原页面无作者署名，测试不要求伪造作者。受控边界覆盖错误域名/英文路径/错误URN/服务/语言/页面类型/空正文/未知正文结构，默认来源启用、官方名次、平台屏蔽及偏好、站内路径边界、受限视频结构。

## 运行证据与边界

日志在artifacts/local-ai/bbc-chinese-source-20261026/，精确汇总summary.json。

- JVM最终94份XML、566测试、0失败/错误/跳过；BbcChineseSourceTest10个用例包含五篇实际简体文章、一篇繁体、未知结构和平台控制。最初旧平台数量14断言与无作者误要求导致失败，已按真实15来源及作者实际存在性纠正，失败构建日志保留；没有改写来源署名或放松正文完整段落检查。
- live-collector.txt：正式HotSourceCollector/Repository链READY、10条、10/10官方发布时间、storageErrors=0，无脚本采集。
- live-prefetch.txt：10/0，实际官方榜单→之前未缓存正文→自动预读4438ms→排空写队列仅1次写→清共享内存/新Repository磁盘恢复→实际统一阅读，32块正文，878ms文字就绪，时效/inode保持，预读反馈0。
- live-images.txt：7/0，10个真实正文已准备，取得此前未缓存17050字节配图；随后关闭模拟器Wi-Fi/数据，新ReaderImages能从盘取同字节，统一阅读中实际解码800像素图片，767ms文字就绪、无横向溢出、players=0。原网络设置已恢复；截图已查看。此次发生在增加繁体支持之前，不能作为最终精确二进制的完整网络复验。
- current-storage.txt：164/0，含1/2/3及4/5/6迁移与当前版本7、旧源开关/收藏/反馈/会话保存；没有清用户数据。
- current-trad-reader.txt：真实繁体文章native获取2503ms、59块、cacheHit、进入实际阅读器，无来源重载，图片解码800、overflow=false/players=0；截图已收集。
- current-video-text-reader.txt首轮正文53块已读取，但图片检查失败：驱动只查旧.media-poster，不能识别行内.media-cover。保留原失败；只修改驱动定位为两种明确视频封面，不退化为任意正文图命中。
- current-video-text-reader-final.txt及final-video-text-reader.txt：正式卡片进入共同阅读器，真实文字/视频封面解码成功、overflow=false、players=0，检查包含行内封面；后者针对最终未知结构保护源码。截图已查看，未初始化视频播放。

本轮最终仅隔离emulator-5560/discoveryprobe调试包；非用户手机或普通发行包。原始采集、模拟器耗时及截图证明本轮样本范围，不证明长期全站稳定、独立个人推荐收益、ARM整App峰值/耗电/发热或最终R8。新来源不增加模型数量、服务器或Key，也没有提高资源预算。无提交、推送、手机升级或发版。

终态：全部命令会话结束；最终App/驱动hash见summary。隔离App/驱动停止后ps匹配0；git diff --check退出0。正文首次预读和配图离线证据与最终结构保护包分开标注，不以早期包代替所有精确最终路径验收。
