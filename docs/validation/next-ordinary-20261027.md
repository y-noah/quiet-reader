# 2026-10-27 普通签名候选身份与边界

本地候选 `C:/Users/10197/Documents/Codex/New-next-trial/New-20261027-42b01fd1.apk`，319449810 字节，SHA256 `42b01fd1f4aa44e62657a198c89989da15f87a43fd101fdb3339891c75903a7b`。app.quietreader，47 / 0.4.0-upgrade-probe。没有安装、推送或公开发布，手机仍为 718f6e1f。

普通构建使用 normalUpgradeProbe、bundleLocalModels；非隔离测试包，DEBUG=false、RANKING_MEASUREMENT_ENABLED=false。620 JVM 项零失败错误跳过，Release Lint、R8 构建通过。签名证书 SHA256 `ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37` 与既有普通签名一致。仅 INTERNET / ACCESS_NETWORK_STATE，没有 debuggable 标记。独立签名命令首次因 JAVA_HOME 未配置无法启动，配置现有 JDK 后验证通过，保留启动错误日志。

包含首页滚动导航/下拉刷新、虎扑原生优先及受限占位回退、V2EX 图片语法清理、数量/任命去重准入修正。不包含诊断性的标题归一化实验。

`scripts/models/check_apk.py --model-manifest app/src/main/assets/local-ai-manifest.json` 新增精确检查：APK 主模型清单及模型目录副本与源码逐字节相同，5 个权重/分词文件逐一核对大小及 SHA256，一致；没有未登记模型文件。六个 64 位 ELF 库满足所查 16KB LOAD 段对齐前提，不代表 ARM 16KB 真机运行完成。常见明文凭据扫描 81 个 ZIP 成员零命中，不是任意秘密不存在的证明。

证据目录 `artifacts/local-ai/next-ordinary-20261027/`：candidate.json、unit-summary.json、build.txt、signature.txt、signature-environment.txt、badging.txt、apk-check.json、credential-scan.json、model-integrity.json。新增检查首次把合法目录清单副本报为未知成员；核对 prepare_models.py 的生成行为后，改为要求副本与源码精确一致，最终通过。

## 后续源码变更与开放验收

用户随后提供评论截图，发现回复对象拆成大段正文、楼层与楼内回复扁平化；相关源码正在修正，因此此候选不能用于证明评论层级修复。下一候选必须重新构建和核对身份。

本候选尚无精确普通包运行/保数据升级证据；真实虎扑账号、知乎完整补充及登录阅读、贴吧实际换帖、完整 TalkBack、整体 ARM 峰值 RAM、非充电长期能耗/发热、本人推荐收益仍开放。不能用单元/静态打包通过替代这些验收。
