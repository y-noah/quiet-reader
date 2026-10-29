# 讨论进展的混淆全链路与普通候选

本轮完成 V2EX 讨论进展的隔离 R8 链路、普通包保留数据升级及模型回归。仅本地候选，没有手机操作、代码推送或发布。整体任务仍有真实账号、质量和资源门槛未完成。

## R8 验证方式

新增两个测试驱动阶段，均只允许专用隔离包，不引用或反射混淆生产类，也不注入预计算正文 hash、scope 或 verified_at。

1. seed：用唯一轮次的 30 条合成中文候选和完整结构的正文缓存启动实际非调试包。生产预读器读取主帖，生产 worker 自行生成讨论指纹；真实可见停留写入账号 scope、曝光和主帖基线（没有回复时间时 modifiedAt=0）。点击真实圆点使已见事件降频，确认它移到初始视口之外，且没有进展证明。
2. 宿主仅 force-stop 隔离包，丢弃静态内存缓存；保留其真实 SQLite、账号 scope、曝光和已冷却列表。verify 阶段替换本测试候选的合成磁盘正文：相同主帖加一条稳定编号、明确时间/更新标签及充分新增文字的回复。
3. 重新启动实际 R8 App。生产磁盘解码、预读观察、正文 hash 和历史事务自行生成证明，尚未接收时列表与旧基线保持不动；点击圆点经真实预筛/账号复核，恢复事件在同兴趣质量层的位置。再次自然曝光消费证明，下一次点击回到未见优先。兴趣版本/反馈不变，预读复用已有正文 inode。

这些缓存正文和候选均为合成；时间元数据由驱动按既有缓存协议写入。它证明生产 R8 的缓存解码至换批链路，不证明本轮经过公网 V2EX HTML 提取、真实回复价值或完整分页。公开时间结构及实际 HTML 解析的前置证据另见 discussion-reply-evidence-20261028.md / discussion-progress-20261028.md。

## 实测终态

证据目录 `artifacts/local-ai/discussion-r8-20261028/`。

- 两轮 seed 各 5/0、verify 各 10/0，各唯一 code -1。对应 `discussion-progress-{seed,verify}-{final,repeat}.txt`。真实普通包/隔离包/驱动的安装 SHA256 全部与本轮本地 APK 相同，见 installed-hashes.json。
- R8 原新闻刷新 7/0、跨 URL 后续报道 10/0，原行为保留通过。
- 最终普通参数下完整 JVM 807 项 / 115 套，失败/错误/跳过 0；Debug/Release lint 通过，见 final-tests-lint.txt / jvm-summary.json。生产算法未为本轮测试放宽或更改。
- 专用升级 AVD 保留实际旧普通包 `daad962b...` 及 userdata，不清数据、不重新种入 fixture。覆盖前 12/0、新原签名包覆盖后 12/0、实际两模型/SentencePiece JNI 与释放 19/0、再验 12/0，合计 55/0。schema9、原保存数据、阅读位置、外观/来源模式及持久合成 Cookie 保留；不是用户真实登录权限证明，也不是最大输入或 ARM 峰值证明。
- 最终 10 份 Android 日志合计 102 checks / 0 失败，逐份成功终态见 terminal-summary.json。重复轮次如实记录，不当作 102 个独立产品场景。

## 失败与纠正记录

- build-r8-first.txt：复用了受沙箱限制的旧 Gradle daemon，读取既有 ONNX JAR 被拒绝。用独立构建进程重跑成功，未修改生产代码以规避。
- discussion-progress-seed-first.txt：5 项中 1 失败，首次测试缓存遗漏解码原本必需的 images/links 数组，读取失败，无法建立正文基线。仅补齐测试序列化字段、重建驱动后两轮全链路通过。原驱动和 package-first.json 保留；没有让生产解析器接受不完整缓存，也没有注入假 baseline/proof。
- 首次 cleanup.json 记录进程已退出但一个 ADB 登记尚未注销；未重启设备，随后再次只读核对 cleanup-final.json，匹配进程和 ADB 登记均 0，userdata 保留。

## 普通候选与审计

`New-next-trial/New-20261028-c63fe1d7.apk`，319482578 字节，SHA256 `c63fe1d752bd8078e78d47a1805f342d975618be00fd819165b71afae7785d2a`。包名 app.quietreader、版本 47 / 0.4.0-upgrade-probe、target36、DEBUG=false、RANKING_MEASUREMENT_ENABLED=false。

原证书 SHA256 `ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37`；模型清单及 5 个文件长度/hash、6 个 64 位 ELF LOAD 的 16KiB 对齐、APK ZIP 对齐均通过。可识别明文凭据模式全成员扫描无发现；这不是任意凭据/隐私的完整证明。

normal/ 保存实际 APK、最终驱动、mapping、BuildConfig、模型/ELF审计、凭据扫描、证书和 ZIP 对齐结果。安全目录副本与实际模拟器安装包另核对 hash。current-candidate.json 指向本候选，phoneInstalled=false / published=false；lastPhoneCandidate 仍为 c315361c，未以新候选冒称手机已更新。

## 继续开放

真实知乎/虎扑正文及评论登录权限、贴吧完整分页和其他平台讨论时间、未标记自然语言的进展召回、本人推荐收益和独立去重质量、ARM 整 App 峰值≤1GB、拔线耗电/温升、长期多源在线缓存及完整可达性/出站审计仍未完成。保持按现有文档继续开发和验证，不能仅凭本轮 R8/升级通过宣布完整发版。
