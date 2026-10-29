# 贴吧无来源ID楼中楼的父楼层身份 — 2026-10-27

## 复现与改动

虚拟列表不同窗口中，同一作者在不同父楼层发相同楼中楼文字，且楼中楼没有data-id/spid。旧fallback只包含作者、页码、文字/图片和窗口内序次，不包含父楼层；两个窗口序次都从0开始，生成相同section.id。TiebaReplies.merge先按section.id过滤，导致第二父楼层的子回复丢失。

新增合成DOM回归先失败，before.xml为6项1失败（标识相同断言），before.log留存。StructuredSources现在先取DOM明确父层对应的section.id；只有没有真实楼中楼ID且父层已解析时，备用key加入lzl-parent与父层身份。来源明确ID不修改，不推测不存在的父层。不涉及热榜相似过滤、推荐分数、额外请求或后台任务。

关系缓存版本提升2；旧0/1版在线重新读取，离线继续保留可读，重新序列化不伪造新版本。当前合成R8阅读夹具用同一REVISION常量，不把旧夹具直接当新解析证据。无稳定父层/子层ID时仍只提供有界备用身份，无法保证同一父层中完全相同的无ID重复回复跨虚拟窗口的全局唯一性。

## 检查及边界

artifacts/local-ai/tieba-nested-identity-20261027/。三个定向解析/关系/虚拟列表类23/0；新回归确认不同父层不同标识、追加后各自关联正确、重放同窗口不重复、同父层重读标识稳定。旧缓存0/1在线不重用、离线保留与版本不洗白检查通过。全量JVM结果见jvm-summary.json，Android测试Java编译和Debug/Release lint均成功，regression.log保存。未新增模拟器/手机运行或真实账号证明。

普通待测39bffda4未包含本轮父层身份/版本修复和此前三处文案。下一步需要新版Android真实缓存与合并链路及普通包升级回归。手机未访问，没有推送/发布；贴吧真实逐楼层、账号受限评论、ARM同时内存、拔线能耗和本人推荐收益仍未完成。


## Android磁盘与历史回归补记

隔离Debug API36实际article-disk完成50/0/唯一code-1（此前40项加10项），见android-disk.txt、android-summary.json。新增验证生产Parser→merge不同窗口→真实ArticleDiskCache写/读→重放及ReadingHistory保存/恢复，父层身份、revision2、指纹均保持。revision1写入真实磁盘后在线拒绝、离线可读且仍revision1；历史在线剔除旧正文但位置/展开标记保留，底层旧JSON不伪造版本。夹具只用合成DOM，未访问贴吧网络或用户账号。

测试扩展复用已有唯一命名的ContextWrapper缓存/偏好隔离与finally清理，未添加后台任务或持久用户条目。android-build.log构建成功；本轮摘要记录构建APK hash，未额外拉取Debug安装包宣称同hash。停止隔离包/驱动并关闭AVD保留userdata；未操作手机。普通39bffda4未变，后续混淆与普通升级仍需纳入本源码，不把Debug缓存回归等同正式包升级/账号关系验收。


后续普通候选New-20261027-1a321e5d.apk已纳入此源码修复；schema7保数据及模型/UI55/0，安装hash匹配。详见ordinary-tieba-identity-candidate-20261027.md；尚无真实账号逐层或ARM证明。
