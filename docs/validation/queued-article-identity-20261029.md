# 异步正文缓存的贴吧楼内回复身份（2026-10-29）

## 问题与修复

缓存/能耗核对时发现，`ArticleDiskCache.enqueue` 给后台写盘复制 Section 时遗漏了 `sourceChildId`。直接 `write` 和 ReadingHistory 的编解码会保留该字段，原有直接写入测试因此不能覆盖这一真实入口。丢失字段不直接改变楼层缩进或文字，但会丢失 `TiebaReplyMedia.explicit` 所要求的来源身份，异步磁盘恢复后再次保存历史也会延续缺失。

最初生产修复增加 `cloned.sourceChildId=section.sourceChildId`，与已有父楼层、回复对象、时间和来源楼层字段一同冻结。不从哈希反推旧缓存缺失ID，不替用户猜测父子关系。

## 修复前后与诊断修正

新增 `queued-article-identity` 使用真实贴吧解析、受控阻塞的生产异步writer和真实文件恢复。源DOM同时含有明确child ID与无ID两类楼内回复；入队后改变调用方字段，确认异步快照与后来的对象变化隔离。直接磁盘写入作为对照，全字段JSON比较不依赖未包含child ID的DocumentFingerprint。

证据位于 `artifacts/local-ai/queued-article-identity-20261029/`。最初探针构建缺少Bundle导入、R8探针使用Android不支持的Files.readString，原构建失败日志保留；补齐导入/改用readAllBytes后继续，生产未为构建问题修改。

初版探针原实现9/4失败，补字段后9/1。剩余历史记录失败属于探针错误：直接disk.read返回的Document尚未带Repository.restoreArticle补回的缓存时间/作用域，不能直接当完整恢复结果保存历史。改用真实Repository恢复后，重新构建旧生产实现，`before-corrected.txt`仍9/4/raw0：异步身份、精确核对资格、完整JSON和恢复后历史四项均失败；直接写入、无ID不推断及清理均正常。所有首次失败保留，不混作最终通过。

## 回复身份阶段 Debug

同一修正后的队列探针连续两轮各9/0；完整正文磁盘/历史70/0、待更新预缓存13/0、刷新动画13/0，共114项，原始code均-1。该阶段构建完整899 JVM/127套0失败/错误/跳过、双lint成功；实际Debug App/driver hash与受测APK匹配。此时只有一个生产字段复制变更。

## 混淆回归暴露的相同记录重复替换

R8首次运行确认原生获取、历史、异步磁盘的明确child ID/父楼层及全部JSON字段一致，重开也没有第二次来源请求，但文件复用断言失败。首次断言同时限制mtime，需区分合法LRU触碰与真正替换；改为保留mtime观测、只要求inode和字节一致后，以同一生产App再次失败。`r8-no-rewrite-before.txt`实际inode 83058→83065，mtime相同，sameBytes=true，明确证明相同记录重复落盘。不是把一次合法LRU更新时间误判成写入。

现在在原有单worker/IO锁内比较完整已序列化记录（包括获取时间、全部正文及related），完全相同则复用原文件并触碰LRU；否则保留原配额、临时文件、sync和原子替换路径。最多比较单条2MiB，额外临时缓冲约16KiB，不在主线程读盘、不保留新常驻索引、不增加网络/模型/轮询或缓存额度。只减少相同记录的reserve/写入/fsync/rename；仍有序列化和有界读取开销，不能据此量化手机电量收益。

扩展Debug队列探针验证相同记录不替换、同获取时间但正文改变仍保存、正文相同但新获取时间仍提交。R8实际重开另检查来源请求数、文件复用、child ID和bodyAt不被续期。

最终包含两项生产修改的Debug同一扩展探针两轮各12/0，正文磁盘/历史70/0、待更新预读13/0、动画13/0，共120项，全部raw -1；899 JVM/127套全0失败/错误/跳过，双lint与构建成功。最终App/driver安装hash见 `debug-reuse-installed-hashes.json`，不把先前114项当作最终复用优化证据。

## 最终 R8 与普通升级

R8探针仅使用Android框架接口和受控HTTPS传输：走实际原生获取、解析、异步缓存及统一阅读，不调用/反射混淆后的生产类。正文长度足够超过首屏，避免测试未滚动时自行触发评论续读；不以错误/登录壳阻止续读。最终专项连续两轮各12/0，明确回复身份/父楼层、完整JSON、无ID不猜、实际统一阅读和历史恢复通过；两轮文件inode分别83058→83058、83187→83187，sameBytes=true，允许mtime作为LRU触碰更新，获取时间不续期，来源只请求一次。待更新正文预缓存10/0、有限首页刷新10/0，共44项，raw均-1。首次失败文件未覆盖。

普通原签名升级AVD先核对实际旧包d9cbac97，保留userdata基线12/0；覆盖后12/0、实际双模型19/0、再验12/0，共55项，raw均-1。本轮没有重种/续期Cookie。旧阅读位置/展开记录/字体外观/来源模式/合成登录Cookie及数据schema9保留；不冒称真实账号权限通过。最终Debug、R8、普通App/driver六个安装hash与各受测文件一致。

最终两项修改的Debug120 + R8 44 + 普通55 =219项，13运行，全部raw -1；不把前面的114项、失败诊断或重复开发夹具累加成独立产品质量证据。

## 普通候选与清理

`New-20261029-13b0d716.apk`，319498962字节，SHA256 `13b0d716ca680ddd35f719f022f2248a5000c7811aa86bf266b4360e7604070d`，app.quietreader /47/0.4.0-upgrade-probe，min26/target36。五个模型文件及manifest校验、六个64位ELF的16KiB LOAD、ZIP对齐和原证书 `ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37` 通过。81 ZIP成员已知明文凭据格式扫描0发现，不等价完整出站审计。

安全副本hash一致，仅staged更新；current-candidate文件hash未变，手机仍805b4ff9，phoneInstalled/published/phoneValidationComplete均false。两AVD临时驱动卸载、App停止、AVD退出，保留userdata；最终ADB无设备、CIM指定模拟器进程0，见cleanup.json。新增探针在本轮isolated emulator中运行，不进入普通安装包，没有操作手机。

真实受限账号/全分页、Find X7 Ultra一划/动画、ARM峰值回落、拔线耗电温升、自然长期缓存、独立去重和本人推荐收益等整体门槛继续开放。没有把host/simulator文件复用的改善称作手机电量结论；整体目标未完成，未发版。
