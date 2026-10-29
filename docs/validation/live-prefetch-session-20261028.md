# 真实网络连续预读与内存淘汰回访（2026-10-28）

## 新发现，不把旧短测当长期通过

LivePrefetchSessionProbe从实际IT之家首页解析当前日榜，不替换网络、ArticlePreloader.Loader、Repository、解析器或缓存。仅启用该来源，写入随机test-ui数据库，通过真实DiscoveryController的滚动状态/视口API触发十个六秒窗口，包含前后反向和第3项之后的位置。此驱动调用控制器回调，不是手指事件/视觉滚动性能证明。每个动作前用产品suspendRecommendation暂停模型，限定本次为正文预读测试。

首次冷正文会话session.txt：12个候选起初无内存/磁盘正文，10篇在首窗口落盘，随后12篇均完成实际获取和文件/指纹核对；来源实际刷新提交新版本并应用。清空RAM正文后同一预读器回到已缓存候选，15秒仍未恢复，8项检查后1失败/code0。没有为了制造冷态清除磁盘、改URL或塞入正文。

原因是ArticlePreloader把成功完成也留在attempted中；下一窗口在磁盘恢复之前先按5分钟重试保护跳过。原PrefetchDiskReuseProbe只测内存命中/新实例磁盘恢复，未覆盖同一实例RAM淘汰回访。

新增确定性复现：同一实例先恢复三篇真实文件、再次命中RAM，再清RAM并重新offer。首驱动断言在缺失正文时直接调用非空ArticleVersion.matches导致测试进程崩溃，原revisit-before.txt和revisit-before-crash.txt保留；修正测试防空值后，不改产品再跑，revisit-nullguard-before.txt为19项/2失败/code0，明确是回访未恢复及RAM未重新填充。

## 修复和保护边界

只在成功取得可读正文且不是filteredVideo/loginRequired/sourceUnavailable时移除对应attempted标记。回到已完成正文可重新走内存/磁盘恢复；网络失败、权限外壳和纯视频结果保留原5分钟限频。没有增加定时任务、后台循环或放宽网络/来源校验。缓存的实际获取时间不因复用续期。

## 修复后的实际证据

证据目录artifacts/local-ai/live-prefetch-session-20261028/。

- 全量688JVM，失败/错误/跳过均0；after-build.log和jvm-summary.json。当前源码Debug/Release双Lint终态成功，lint.log。
- revisit-after.txt：19/0/code -1，同一预读器RAM淘汰后立即恢复三篇匹配正文；接管、转屏缓冲和新获取写盘仍正常。
- video-cooldown-after.txt：22/0/code -1；新增纯视频成功仍保留限频，已有网络失败不重复请求及主帖视频标记保护通过。
- session-warm-after.txt：12/0/code -1。明确允许复用刚才实取的磁盘正文，不冒充第二次冷获取；当前日榜12篇均提前恢复/准备，十窗口与真实来源刷新完成。再次清RAM后同一预读器回访取得完全相同正文指纹、获取时间及文件inode。实际HOME后5秒内被动布局回调不恢复预读/动作额度，正文文件清单与完成写任务数不增长，整个过程不产生兴趣反馈。会话69.210秒是这台模拟器单次范围，不是耗电或长期天级证明。
- debug实际安装ea01ca9f与after-app.apk一致，after-packages.json/debug-installed-hash.txt；驱动及原始App均归档。
- 当前R8普通控制器固定100公开标题/合成缓存正文，真实双模型自主完成、零手动继续，40份正文语言证据，100文件inode保持，工作进程退出后15秒无观察到模型缓存增长：r8/controller.txt为13/0/code -1。cold只指输出缓存设置；本轮不是R8真实网络或同实例回访专门验收。R8实际安装4c7fe8e与r8/package.json一致。
- 普通原签名7bc→a5cd保数据升级：前12/后12/实际双模型19/复核12，共55/0，各code -1。schema7、偏好、历史位置/展开、合成持久Cookie保留；真实安装hash与normal/package.json一致。原证书ab59106c、16KB对齐、app.quietreader/47/target36、五项模型长度/SHA256及限定已知凭据模式扫描通过。后者不等于完整隐私/出站审计。

## 当前候选与未完成门槛

本地New-next-trial/current-candidate.json指向New-20261028-a5cd0ddb.apk，SHA256 a5cd0ddb4e751a1007f9b5d7b79c00a7024b849f7a60a60abd922c7cb455e195，319466194字节。phoneInstalled/published均false，手机仍f0d4dd5a。两个命名AVD已退出，cleanup.json进程0/ADB无5554和5562，保留userdata。本轮没有操作手机、系统认证、账号或显示设置，没有推送/发版。

这是单一公开来源、约一分钟有动作会话加5秒后台观察；不关闭多来源数小时/数日在线积累与淘汰/取消门槛。真实受限虎扑/知乎、完整贴吧分页、ARM连续全App峰值≤1GB、拔线能耗/温升、本人推荐收益和独立去重质量仍待验收。没有用合成开发兴趣或热榜阅读次数替代用户标签。
