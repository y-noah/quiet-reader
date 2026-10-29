# BBC Flourish图表导致整篇拒绝（2026-10-28）

## 诊断与证据边界

上一轮三源会话41候选/40匹配正文，BBC9/10，但没有记录未准备条目的URL和失败原因。本轮新增仅用于隔离模拟器的BbcCacheDiagnosisProbe，读取原boards缓存10条，先按产品正常在线规则恢复正文，再实际读取缺失条目的公开HTML及原生解析结果。没有重写榜单、清空磁盘或使用登录账号。

诊断时原正文已超过十分钟在线复用期限，inventory.json显示10条均未恢复，而不是证明原9篇缓存突然丢失；ArticleDiskCache.read在线过十分钟返回null、七天内文件保留。当前公开响应中9篇可原生解析，1篇status200/article却sourceUnavailable：`https://www.bbc.com/zhongwen/articles/c680zjdvk7kgo/simp`，“卢拉军团”的WhatsApp聊天群如何扭转巴西选情。原始HTML及10条逐项状态均归档在artifacts/local-ai/bbc-cache-gap-20261028/。不能倒推上轮唯一未准备项已被逐项日志确证就是此URL。

该文章正文顶层存在oEmbed，实际结构为Flourish rich / aresFlourish，source为`https://public.flourish.studio/visualisation/30709667`，附带iframe和resize脚本。原BBC适配器遇到未知正文块会拒绝整篇，因此有可复现的适配缺陷，不是当前响应要求登录。未知正文拒绝的保护继续保留，不能简单忽略oEmbed来换取表面成功。

## 修改

BbcChineseSource只增加观察到的Flourish合同：rich/aresFlourish/Flourish名称、https且无认证/端口/查询/片段的public.flourish.studio、严格visualisation数字ID。正文原位置生成“查看互动图表 · Flourish”链接，前后全文照常统一排版。不会把嵌入HTML、iframe地址、resize脚本搬入阅读器；未知提供方或不合法来源仍拒绝解析。此实现是可见图表入口，不是内嵌交互图表或静态图表截图。

## 验证

- 捕获文章和实际33段正文进入测试夹具，仅test资源，不进入普通App资产。before-build.log记录全量693项中2失败：实际文章全文/图表未读、恶意opaque标记测试因整篇不可读失败；新增未知来源保护原已通过。首次失败构建抛错后未导出before.xml，不伪造该文件。
- after-build.log及after.xml：BBC16项0失败；693JVM/106套总失败/错误/跳过0，jvm-summary.json。实际文章每个原text/subheadline段落均保留，来源链接和脚本边界检查通过。live-build.log成功编译后续真实链路驱动。
- live.txt：实际当前BBC第7项，起初没有匹配正文，正常DiscoveryController主动缓存→磁盘→清RAM/新Repository恢复→真实统一阅读页面，11项/0失败/code -1。自动准备4143ms，缓存阅读ready562ms，仅这台模拟器单次值；一份原生获取仅一次正文写任务，文件inode/获取时间保持，图表链接出现在真实阅读Document中，无来源WebView重读/登录页面和兴趣事件。
- r8/chart1.txt、chart2.txt：实际非debuggable压缩包，经原生HTTP读取捕获HTML而非替代解析器，框架UI/DOM和JSON历史检查，无生产类反射。两轮各8/0/code -1；33个来源段落全部在实际WebView内可见，图表入口和阅读历史保留；DOM无iframe/resize脚本，受控原生图表提供方请求计数0。R8传输为控制夹具，图片传输特意不可用；不宣称R8现场BBC公网/配图完成、图表真实可达或全运行时出站审计。
- lint.log Debug/Release终态成功。lint命令中sentencepieceSource参数有路径笔误，Lint未执行/验证原生构建；后续普通构建脚本使用正确路径，实际JNI和两模型另有下项成功证据。不用该静态检查证明原生路径正确。

## 普通候选与清理

保留升级AVD旧8aa074ca/schema7数据，不清数据：upgrade-before12/0，覆盖5830ab3c后upgrade-after12/0，普通双模型实际推理normal/models19/0，upgrade-repeat12/0，共55/0，各code -1。偏好、原阅读位置/展开、schema7迁移记录和合成持久Cookie保留，不作用户真实权限证明。

normal/installed-hash.txt、normal/package.json及本地安全副本一致；原证书ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37、16KiB对齐、五项模型长度/散列、限定已知凭据模式检查通过，见normal下证据。config.txt为app.quietreader、DEBUG=false、measurement=false。debug/R8/普通APK及驱动均归档；诊断/test与live-test不同，packages.json分别记录。R8实际安装hash另存。

当前本地候选New-20261028-5830ab3c.apk，319466194字节，SHA256 5830ab3c58d55efcf9a66d8a6f4d3f1369bf9350a479f96cd1557e7d75c3f5c7。current-candidate.json已更新，phoneInstalled/published仍false；手机仍f0d4dd5a。本轮未操作手机、系统认证或账号，没有推送/发布。公开诊断导出JSON/HTML已从模拟器删除；两AVD正常退出，进程终态见cleanup.json，保留用户数据。

## 未完成

修复并实测了这一篇/这一种oEmbed的原生读取和缓存链路，不能升级为BBC所有内容或所有图表提供方均已适配。真实受限虎扑/知乎、完整贴吧分页、长期多源缓存淘汰、ARM连续整App峰值、拔线耗电/温升、个人推荐收益/独立去重以及全页面和出站审计仍开放。
