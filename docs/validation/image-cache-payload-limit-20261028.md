# 图片缓存命中遵守预读额度（2026-10-28）

## 发现与修复

资源检查发现ReaderImages只对新网络返回执行单次payload limit；已有RAM或磁盘命中直接返回，ImagePreloader原有1MiB上限可能被正常阅读留下的大图绕过。新增两项回归，未改产品时ImagePrefetchTest为8项/2失败，分别是RAM/disk超限命中被接受，原始before.log、before.xml保留。

ReaderImages现在对有效RAM命中检查当前请求limit；ImageDiskCache增加有额度的read重载，在读完并校验记录头后、分配metadata及payload之前拒绝超限。独立PayloadLimitException由磁盘读取透传，不按损坏文件删除，不进入网络补抓、不更新LRU时间。普通读取仍允许原8MiB上限；这次没有缩小既有预读范围、图片额度或普通阅读能力。磁盘记录已经超出格式硬上限/头不合法时仍走损坏处理。限制拒绝本身不证明被跳过记录的会话/完整性有效，后续正常读取继续执行全部原校验。

## 验证与范围

证据目录artifacts/local-ai/image-cache-payload-limit-20261028/。

- after-build.log、android-build.log终态成功；690JVM/106套，失败/错误/跳过0，jvm-summary.json及after.xml。新增两项核对原缓存保留、无额外下载、磁盘mtime不变、正常离线恢复。不是只检查错误文案。
- API36隔离debug：limit1.txt、limit2.txt各13项/0失败/code -1。Android Bitmap生成1430631字节PNG（640×640），超过实际IMAGE_LIMIT，先普通读取缓存，再检查RAM和disk预读拒绝；完整文件仍在且未续期，正常阅读离线恢复和BitmapFactory实际解码成功，无重复受控传输。使用真实图片/缓存IO，传输为受控Fetcher；不是公网图片或峰值RAM测量。
- prefetch.txt 10/0，policy.txt 12/0，各code -1：正常控制器正文先行、图片跨话题、磁盘复用、实际统一阅读解码及不重复下载、移动视口/低电量/HOME取消和迟到结果拒绝。受控HTTPS/正文，不证明真实权限或真实耗电。
- lint.log：Debug/Release双Lint终态成功。
- r8/media.txt 14/0/code -1：框架驱动的真实压缩包受控虎扑图片提取/原生读取/阅读图片解码/历史保存。r8/controller.txt 13/0/code -1：100公开标题+合成缓存正文，正常控制器自主执行真实双模型、40正文语言证据、100缓存文件inode未改、模型退出和15秒安静观察。R8这些是常规路径回归，未专门在R8注入大图额度拒绝，不冒充端到端精确分支覆盖。
- debug/R8/普通APK与驱动均归档；r8/installed-hash.txt与r8/package.json匹配。debug APK为本轮修复后构建，packages.json归档。

## 普通候选覆盖升级及原失败

保留的quiet-reader-upgrade-api36中原普通候选a5cd0ddb，schema7，没有清空或重新灌入旧数据。upgrade-before.txt 12/0；原签名覆盖新8aa074ca后upgrade-after.txt 12/0。首次普通模型检查被模拟器“System UI isn't responding”的Wait/Close app弹窗挡住，models-systemui-anr.txt为4/1/code0，原models.txt也保留。upgrade-anr-ui.xml保存实际弹窗；dumpsys activity lastanr报告无本次boot ANR记录，不能仅凭弹窗确定底层原因或归咎产品。

在同一模拟器按实际UI的Wait一次后，不重建数据、不修改App，models-retry.txt 19/0/code -1，双模型实际推理和工作进程退出成功；upgrade-repeat.txt 12/0。成功状态检查合计55项，另有上述首次失败，不把失败隐藏进总通过数。合成偏好、原阅读进度/展开、schema7、旧迁移数据和合成持久Cookie保留；不是用户真实受限登录验证。

normal/package-audit.json核对五项模型长度/SHA256及限定已知凭据模式，无匹配；不等于完整出站或秘密审计。normal/signature.txt原证书ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37，align.txt验证16KiB对齐；app.quietreader/47/target36，普通非debuggable、measurement=false构建路径。normal/installed-hash.txt与归档普通包、New-next-trial安全副本完全一致。

当前本地候选New-20261028-8aa074ca.apk，319466194字节，SHA256 8aa074caba3eafdae3978758b19e9d08291a50076d13d338a30bd3e5f6f83829。current-candidate.json指向该包，phoneInstalled/published仍false，手机仍f0d4dd5a。模拟器正常退出、终态清理见cleanup.json；本轮未操作手机或系统认证，未推送/发版。

## 开放门槛

这次关闭的是图片预读资源额度绕过这一具体缺陷。真实受限虎扑/知乎权限、完整贴吧分页、ARM连续整App≤1GB峰值、拔线能耗/温升、多源小时/天级缓存积累与淘汰、用户本人推荐收益和独立语义去重、全部页面/无障碍及全运行时出站审计仍未完成。不能从单项额度限制推断整机峰值、发热或省电幅度。
