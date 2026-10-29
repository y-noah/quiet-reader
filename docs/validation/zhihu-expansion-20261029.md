# 知乎展开完成判定修复

## 缺陷与修改

SourceSnapshot 原来点击展开后仅等待六秒；六秒后即使按钮仍显示“显示全部”，也允许稳定摘要通过。getClientRects 还会把 visibility:hidden 的按钮视为可点击。

改为仅点击可见展开控件一次；点击后仍可见且没有明确“收起”状态时保持 pending。按钮移除、隐藏或变为收起后才允许采集。因此“加载中”文字不会提前放行，立即展开也无需固定等待六秒。未新增线程、轮询或延长任何调用方预算。AnswerStream 原有20秒总时限保持；DynamicReader 和 LoginActivity 仍按各自现有预算结束。

一个展开控件持续卡住时，本轮整页快照会暂缓至现有预算结束；不将未完成摘要伪装为完整正文。此为保守策略，不保证任意来源 DOM 变化均兼容。

## 证据

目录：artifacts/local-ai/zhihu-expansion-20261029/。

- 新增 ZhihuExpansionProbe，通过真实 WebView 定时器、SourceSnapshot、DynamicReader 和生产解析器验证受控 HTML；无真实账号或来源内容。
- 原生产代码17项3失败：9.5秒慢展开提前返回摘要、卡住时误交付摘要、点击隐藏按钮。
- 修复后17/0，raw -1：慢展开约11.7秒读取完整补充，卡住约15.7秒按原有尝试上限结束，隐藏按钮零点击且约1.3秒读取已有完整内容。
- 共用流程回归：LoginReadFlow 77/0、DiscussionFlow 72/0；均 raw -1。
- 全量 JVM 907项、127套件，失败/错误/跳过均0。
- 普通原签名 R8 包保数据覆盖：前后各12/0，raw -1；保留设置、历史及合成 Cookie。本轮 Android 成功检查190项，失败基线独立保留。
- 原证书 SHA256 ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37，签名和 ZIP 16KiB 对齐验证通过。
- 相比174a候选只有 classes.dex 改变；模型、原生库、manifest、资源未变。此轮没有重测全模型或手机资源。

候选 New-0.4.0-20261029-251ea4f5.apk，SHA256 251ea4f536e9cbf3cbb11ded0f8cde02f9ea92407501bbe9bb1bd438527ce9b7，319498946字节，版本48/0.4.0。手机/current仍805b4ff9，未推送、发布或安装手机。专项时序在 Debug 隔离包验证，普通 R8 验证覆盖升级，不混称相同时序已在R8或手机通过。

构建时沙箱无法解密已有系统签名凭据，原命令经许可在本地正常权限重跑成功；未输出签名密码。

真实知乎/虎扑账号权限、用户手机刷新感受及 ARM 峰值/后台能耗仍待真机，不作为已关闭问题。

## 最终安装包补充审计

最新Debug/Release lint通过；final-apk-audit.json验证每个随包模型hash与大小及64位ELF对齐。final-badging.txt核对48/0.4.0、New、target36、DiscoveryActivity及仅两项网络权限。final-component-audit.json和二进制清单记录未发现具名应用测试类/测试Instrumentation/测试权限；已核对sourceSets隔离，不将混淆后名字检索单独当作不存在测试代码的证明。全APK可识别凭据格式扫描无命中，范围见final-credential-scan.json。安全目录候选再次验hash一致。

SDK apkanalyzer两次只读尝试因AccessDenied失败，未将其计为通过；改用已成功的aapt2二进制清单和直接解析DEX定义获取同类证据，未放宽权限。无新生产改动，无手机访问。交付摘要已写入安全目录New-251ea4f5-validation.md。
