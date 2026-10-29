# 2026-10-26 正文缓存保存责任与真实网络预读

## 问题证据

此前真实澎湃预读检查 `artifacts/local-ai/live-prefetch-current-20261026/thepaper-first.txt` 为9项/1失败：首次文件出现后，在清内存/阅读期间inode变化。该证据不足以确定是恢复缓存重新保存还是初次重复保存。

本轮增强LivePrefetchProbe：在单候选首页启动前排空ArticleDiskCache写队列，预读准备完成后再排空并减去测试自己的屏障任务，记录实际写任务数及首次/稳定inode。未修生产代码基线 `baseline-live.txt` 为10项/1失败：一次普通原生获取产生2个写任务，inode83128→83129；排空队列之后再清内存、创建新Repository恢复和打开真实阅读器，文件保持。因此原失败实际来自初次获取的第二次保存，不能归咎于磁盘恢复。

## 修复边界

- Repository.article已经检查会话/epoch、保存正文后才调用成功回调。ArticlePreloader普通请求成功使用complete(...,false)，不再重复保存。标题为空的后备值在Repository首次保存前补全。
- PendingArticle使用articleForReading，同样已经保存；MainActivity接收后使用renderCached。
- 前台接管的ArticlePreloader在交付前已经完成原生或动态缓存；其renderSource使用updateCache=false，同时保留真实源WebView向AnswerStream的移交。
- MainActivity自身新建DynamicReader成功仍通过默认renderSource保存；动态预读、注入Loader的新获取仍保留complete默认保存。没有给Repository.cacheArticle增加通用“同对象跳过”规则，后续回答/评论更新、导入正文、历史恢复等既有更新路径不被禁止。
- 不降低会话、命名空间、元数据版本、有效期、取消及晚回调保护，不增加前台同步磁盘写入。

## 当前验证

日志与严格终态汇总在 `artifacts/local-ai/native-cache-ownership-20261026/summary.json`。均为emulator-5560独立discoveryprobe调试包，不是手机或普通发行包。

| 检查 | 项数/失败 | 证据范围 |
|---|---:|---|
| 澎湃真实热榜→预读→清内存→新Repository磁盘恢复→实际统一阅读器 | 10/0 | 一次写入、179块正文、缓存时间与inode保留 |
| 普通原生请求跨两次实际转屏 | 20/0 | 一次HTTP/一次写入；HOME取消、返回重试、会话变化及无Owner间隙 |
| 原生预读转前台跨两次实际转屏 | 25/0 | 一次写入；Owner移交、晚响应、动态回退仍一次落盘且磁盘可读 |
| 实际磁盘恢复与新获取 | 17/0 | 三份已有正文不重写；重建Owner缓存命中；注入新获取仍保存 |
| 实际控制器预读生命周期 | 16/0 | 视口变化、返回复用、旧回调拒绝、HOME及自动恢复 |
| IT之家真实热榜→预读→磁盘恢复→统一阅读 | 10/0 | 11块正文、一次写入及inode保留 |
| 虎扑真实热榜→预读→磁盘恢复→统一阅读 | 10/0 | 61节/88块主帖评论、一次写入及inode保留 |
| V2EX真实榜单 | 0/1 | 获取榜单连接超时，本轮未通过，不归类为缓存回归成功 |

成功运行累计108个检查；原生请求与动态回退传输部分使用明确标注的受控合成正文，不冒充来源全文权限验证。真实公开正文使用默认生产获取/预读流程，无注入Loader；打开卡片调用实际open方法，阅读就绪需正文已可见及engagement.ready，不仅是Activity创建。

三次真实成功样本预读耗时澎湃4624ms、IT之家2704ms、虎扑3248ms；缓存阅读可见分别615/521/688ms。来源与正文长度不同，仅为本轮观察，不是提速对照；不包含全部图片完成。

JVM XML93份/556测试/0失败错误跳过；构建成功。构建/安装包SHA见summary。原始失败日志保留，未以重跑覆盖。

## 仍开放

V2EX当前模拟器网络访问、真实账号与受限全文、ARM整App峰值内存/能耗/发热、独立个人推荐收益、精确R8自主运行及真实网络与模型并发阅读仍不能据本轮宣称通过。未操作手机、升级普通包、提交、推送或发版。海外中文来源仅为用户讨论，没有新增接入。

终态：全部命令会话结束；隔离App/驱动force-stop后ps匹配0进程；git diff --check退出0。
