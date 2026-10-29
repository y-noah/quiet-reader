# 缓存写入目录统计合并 — 2026-10-28

## 发现和修改

主动预读已使用轻量可用空间查询，但正文持久化仍在每次成功写入前prune、reserve及写入后prune。按旧源码完整路径计，prune各枚举partial/json两个列表并排序json，reserve另枚举并排序，因此最多5次目录枚举、3次排序，不是最初口头按阶段估算的3次枚举/2次排序。

ArticleDiskCache现将过期peer、超过1小时的abandoned partial清理与容量/名额预留合并为一个目录清单。统计后已有空间和条目名额则直接允许；需淘汰时才按原LRU/名称顺序排序。临时副本字节仍计入预算，已有目标不占新增文件名额，旧目标保留至rename；成功发布后已由预留保证字节/1000文件预算，不再重复prune。清理失败的文件继续计入使用量，近期partial不会被清理。没有修改缓存格式、Cookie指纹、namespace、在线10分钟/离线7天读取判断、写入队列或后台策略。

ImageDiskCache亦仅在容量或文件数不足时执行原排序/淘汰。除此之外保留原扫描、有效性/会话检查、临时文件占用、空间拒绝及原子发布。

上述是代码路径和元数据调用范围的减少，不代表物理磁盘读写次数或手机耗电百分比。满额时仍需枚举和排序，没有无限索引增长或新增后台维护任务。96MiB正文/128MiB图片是原派生缓存内部预算，没有恢复App总磁盘2GB硬上限。

## JVM及Android直接缓存路径

证据 artifacts/local-ai/cache-write-inventory-20261028/。完整679项JVM/0失败错误跳过，Debug/Release Lint及debug App/驱动终态构建成功。

图片新增测试用真实64条记录及仅计数的File包装观察单次新增：目录枚举1次，既有记录lastModified共64次，即每条一次，无排序比较器引起的重复元数据读取。它统计Java方法调用，不把OS缓存后的查询说成物理IO。另一测试连续20次3文件限额压力，读取保留项、写新项，验证LRU保留、旧项淘汰、数量及字节上限。ImageDiskCacheTest共10项通过。

API36隔离debug真实文件/Controller测试，原始终态均code -1：

| 报告 | 检查/失败 | 范围 |
| --- | --- | --- |
| article-disk.txt | 52/0 | 原正文恢复/会话/预算/损坏/namespace/贴吧身份，新增过期peer清理及1000条满额连续12次替换；未误删无关条目 |
| prefetch-reuse.txt | 17/0 | 真实预读接管/转屏/已写磁盘复用，新正文仍可写入读取 |
| image-cache-clear.txt | 7/0 | 清派生缓存后保留账号Cookie和阅读/反馈状态，旧namespace不复活 |
| image-prefetch-policy.txt | 12/0 | 资源约束、HOME中止阻塞连接、统一阅读实际解码及不重复下载；传输受控 |

没有将这些合成正文和受控传输称为真实平台账号、长期在线缓存或耗电验证。debug App a5c8d38074525d996883f9fb200047317b955828271c56a654183e21798956ba，驱动301c4f8b9f73141131ef0d74e6fc247c3414e22ae86ef0f81e44d4f13dd6579c。

## R8普通控制器复用

完成构建后安装隔离R8，energy-setup/DUAL/cold/activePrefetch/autonomous/verifyPrefetchDiskReuse，固定energy-replay-100-v2语料。r8-controller-reuse.txt为13/0/code -1；真实控制器自主完成本轮，102向量/42精排缓存，40份正文语言证据，100个原正文文件inode保持。该轮21.751秒是本机这一运行的观察耗时，没有同轮未改基线，不能宣称全面提速。cold仅指测试输出缓存设置，非整机/文件系统冷缓存。

fixture文件由驱动预先写入；此R8轮验证正常控制器不会无故重写已缓存正文，不是R8满额新写入证明。新写入/替换的直接证据来自上述debug Android真实ArticleDiskCache和JVM图片测试。R8 App SHA256 0602575288c57eae53bf13b52f256626e777879cd79f64006016a1c8bab0d8b1、驱动ce848d605904e7848ac03e37fdeaad54372af9406d40e08771f72998e7f7fdc3，与模拟器实际安装hash相同。

## 清理与范围

专用模拟器App/驱动force-stop后关闭，cleanup.json确认匹配进程0、adb中emulator-5554消失，userdata保留。一次启动请求自动审批超时未执行，重试前重新确认无活跃AVD进程，随后正常启动；没有重复启动或由超时推断测试通过。

手机没有操作、安装或设置改动。普通签名候选验证另记；源码验证不当作手机已更新。真实在线长期缓存/取消、ARM全App连续峰值和最大输入、拔线耗电温升、受限登录权限、完整楼层关系、本人推荐和独立未知去重质量仍开放。未推送或发布。
