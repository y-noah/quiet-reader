# 缓存恢复完整性（2026-10-28）

## 实际发现与修复

检查长期缓存/淘汰路径时发现两个可复现问题，而不是将之前短轮换结果扩大为长期通过。

1. ImageDiskCache处理超过一小时的image-*.partial时，未检查delete是否成功。删除失败的文件仍存在却不计入used，允许继续写入超额。新增真实临时目录及File.delete拒绝注入，11项图片缓存测试中1失败（image-before.xml）；8192字节残留占满8192预算时仍准入新图。改为删除确实成功才释放账面字节，删除失败/尚未到期均按实际长度计量。修复后拒绝新图，真实移除临时文件后恢复准入。故障注入不代表实际遇到设备权限或闪存故障。

2. ArticleDiskCache.enqueue手工复制正文时漏传hupuBodyRevision，新Document默认当前1，导致旧版0异步落盘后变成1。原先直接write、历史和内存拒绝测试未覆盖这个副本路径。新增Android真实enqueue→文件出现→离线读回/在线读回，基线61项/2失败（hupu-before.txt/code0）：离线返回错误的新版本，在线错误认可。现显式复制版本值，旧正文离线仍可读、在线继续拒绝复用。本次不提高缓存配额，不增加计时器或后台工作。

## 已执行验证

证据在artifacts/local-ai/cache-recovery-integrity-20261028/。

- before-build.log：全量688项/1失败，明确为新增图片删除失败回归，保留原image-before.xml。
- after-build.log：全量688项/0失败/错误/跳过，jvm-summary.json；图片目标11/0，image-after.xml。
- 实际Android缓存回归hupu-after.txt/hupu-repeat.txt两轮各61/0/code -1，包含旧正文异步版本的两个新增断言。
- after-app.apk/after-test.apk归档，debug-packages.json摘要；模拟器实际base.apk为52f79056，与after-app一致。仅随机隔离ContextWrapper目录/偏好测试，清理前退休异步namespace，不导出真实账号。
- image-lint.log为图片修复后、虎扑字段修复前的双Lint；最终两修复源码的Debug/Release lint也已终态成功，final-lint.log。
- 隔离R8 ecaa7318实际运行受控HTTPS→虎扑解析→阅读图片解码→历史14/0/code -1，r8/media.txt；实际安装hash与r8/package.json App一致。这是当前媒体路径回归，不将其当R8删除失败注入或完整线上压力测试。
- 普通原签名81ab→7bc219bf覆盖前12、后12、实际双模型19、重复状态12，共55/0，各code -1。既有schema7、历史/偏好/合成持久Cookie保留，原userdata不清空或重置。普通安装hash与normal/package.json匹配；原证书ab59106c、16KB对齐、app.quietreader/47/target36核对成功。五项内置模型文件字节及SHA256一致，限定已知token模式扫描无匹配；不等于完整出站/隐私审计。
- 两个命名模拟器已关闭，cleanup.json进程0、ADB无5554/5562，userdata保留。最终只读进程核对第一次自动审批超时未执行；按工具允许重试一次成功，不是安全性拒绝或手机操作。

首次安装在模拟器尚在booting时被ADB拒绝，未启动instrumentation；后续强制检查boot_completed==1再安装成功。没有清空userdata或绕过安全校验。

## 边界和未完成项

这些验证证明两条具体故障路径修复，不等于完整DiscoveryController/ReaderPrefetch长时间真实在线预读、自然数日缓存或低电量耗电已通过。本地候选New-20261028-7bc219bf.apk已包含两修复，SHA256为7bc219bf369372bfd3d30ef585ea4d8d96216729f83140dc78bfcb077736453d，319466194字节；New-next-trial/current-candidate.json已更新，phoneInstalled/published均false。手机仍f0d4dd5a未操作，不发布/推送。真实虎扑/知乎受限来源、连续ARM整App峰值≤1GB、拔线耗电/温升及个人效果等门槛继续开放。
