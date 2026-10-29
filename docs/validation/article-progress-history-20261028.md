# 正文进展记录与显式刷新接线

状态：本地开发与回归中，未推送、发版、更新手机或公开候选指针。手机保持 c315361c/schema7。不能用合成用例代替真实推荐收益或耗电验收。

## 实现

- FeedStore schema9 增加 article_progress，保留 schema8 的曝光记录。记录按精确文章身份、账号会话指纹及缓存命名空间隔离，不存原始 Cookie。显式清理派生缓存时删除正文指纹，保留真实曝光事实。
- 正文到达主线程时只冻结有界文字引用和时间，后台计算散列、序列化、比较和 SQLite 写入。最多256条、7天、8MiB逻辑ASCII载荷；在动作/写入时清理，没有定时循环。这不是整个数据库文件的硬大小上限。
- 获取正文不代表看过。只有已提交展示快照接受的具体成员才可建立曝光基线；有效阅读使用实际读过的冻结正文，不能拿后来预读的新正文替代。快速退出不推进已读正文基线。
- 仅在同文章、同scope、可信来源更新时间晚于获取和整事件最后展示/阅读，且保留足够原文并有明确更新段落新增时提供进展时间。标题、热度、缓存时间变化不能提供证明。
- 显式刷新先检查来源、偏好、筛选和完整成员元数据，再在同事务内查预先算好的进展时间。最多128个有曝光事实的行查询，未知质量层屏障和同质量稳定排序保留。可选证据失败退回普通冷却，不中断刷新。
- 自动缓存只准备证据，不自动替换阅读列表。离开首页/后台使进展任务代次失效；账号scope在采集、计算、查询及最终发布时复核。没有改变偏好或模型缓存版本。

## 已取得的证据

目录：artifacts/local-ai/article-progress-history-20261028/。instrumentation 仅以唯一 INSTRUMENTATION_CODE: -1 为成功；shell exit0不代表测试成功。

- JVM 764项、112套，0失败/错误/跳过；增加冻结后原对象变动及时间回退测试。
- article-progress-history-chain.txt：33/0，真实SQLite合成8→9迁移、旧曝光和列表保留、基线建立、延迟写入、scope隔离、回滚、时钟异常、7天/256条/8MiB容量、重开数据库、真实会话stamp、取消/禁用来源拒绝，以及持久进展实际影响刷新后再曝光消耗证明。
- refresh-snapshot-final.txt：14/0，同兴趣层重排、陈旧证明拒绝、证据失败回退，以及原有版本/筛选/回滚检查。
- exposure-history-final.txt 18/0、article-progress-final.txt 12/0、recommendation-refresh-final.txt 6/0、default-chain.txt 190/0。后续最终修改的复验结果另见本目录日志与末尾补记。
- upgrade-before.txt 使用归档schema8普通驱动，12/0；upgrade-after.txt普通原签名保留userdata升级至9，12/0。模拟Cookie仅证明持久存储，不证明真实平台权限。最终源代码增加发布前scope复核后还需普通包再编译复验。

## 原失败与修正

- sqlite-round1.txt 22项1失败：容量夹具把所有新获取时间放在已展示旧条目之前，导致新行在展示前已被正常清理，后续空基线行使“必须少于22行”不成立。改为逐条递增的获取/展示时间后，实际双份满载散列压力用例通过。原失败保留。
- r8-refresh.txt 7项2失败：等待条件只检查全库存在任意曝光；保留的上轮曝光让本轮首条曝光尚未发生就提前检查。修正为本轮唯一成员key，并输出snapshot/first/expected/当前曝光计数，需复验后才算通过。
- 首次受限构建因共享依赖jar AccessDenied失败，后授权读取本地工具缓存构建成功。升级模拟器第一次boot检查未通过，未执行安装；启动完成后才检查旧包和升级。

## 尚未关闭

- 跨URL后续报道、讨论新增回复不在当前正文证明范围；不能称为通用的重要进展理解。
- 用户至少多轮实际阅读的收益、候选供给充足性、ARM整App≤1GB峰值、拔线耗电温升与长期缓存仍待验证。
- 真实虎扑/知乎受限账号正文和图片权限、完整贴吧分页及图片重载早期续页等原有门槛仍开放。
- 本轮测试均在本地模拟器，不访问手机；未更换 current-candidate.json。

## 最终复验与归档

最终源代码（包括发布前scope复核）完成764 JVM/112套、Debug/Release双Lint，0失败。terminal-summary.json逐项核对唯一成功终态：正文历史33/0、换批14/0、真实Controller7/0（新增刷新期间namespace变化后不激活）、可见区域7/0、动作缓存6/0、圆点13/0；旧曝光18/0、正文12/0、旧存储190/0亦通过。

混淆首页 r8-refresh-recheck.txt 与 r8-refresh-repeat.txt 连续两轮各7/0，保留上轮记录仍正确等待本轮唯一成员曝光；原 r8-refresh.txt 的失败不追记为通过。此次混淆专项使用V2EX合成列表验证自然曝光/换批，不是新闻正文进展在混淆UI上的端到端专项。

最终普通候选原位-r升级后 upgrade-final.txt 12/0、normal-models-final.txt 19/0、upgrade-repeat.txt 12/0；实际schema8→9迁移由之前upgrade-before/after各12/0验证。最终普通候选仅归档 normal/app.apk，SHA256 a3dc0df5bb69287239b5b54f9ce575fc4cf2356dcd7cf823616a2ac8a9d8d932，319466194字节。R8专项及驱动散列见 final-package-hashes.json。

cleanup.json确认本轮两个模拟器匹配进程0、userdata保留、phoneAccessed=false，current-candidate仍c315361c。没有推送/发布或把本地普通候选安装到手机。真实推荐收益、真机资源及上述功能范围外的门槛保持开放。
