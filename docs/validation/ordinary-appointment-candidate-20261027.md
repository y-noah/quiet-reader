# 含任命归组的普通签名候选（2026-10-27）

最新本地候选 `C:/Users/10197/Documents/Codex/New-next-trial/New-20261027-2bf60a5d.apk`，319449810字节，SHA256 `2bf60a5dda9467929d5b6aeb26b6f2b58c4aebd6a4fdb0516e2b2f64dc308190`。取代e4dae6c6作为下次本地真机候选；不是公开发布，未装手机。

普通包app.quietreader，47/0.4.0-upgrade-probe，target36，nondebuggable，RANKING_MEASUREMENT_ENABLED=false。新增明确任命家族去重，具体边界及JVM653/0、隔离Debug42/0、隔离R8选定75/0证据见appointment-evidence-20261027.md；这些独立运行不能当作此普通APK全部路径验收。

## 精确打包与升级证据

证据在`artifacts/local-ai/ordinary-appointment-20261027/`，构建日志在`artifacts/local-ai/appointment-evidence-20261027/ordinary-build.txt`。终态BUILD SUCCESSFUL，当前源码实际compileReleaseJava/minifyReleaseWithR8/packageRelease；保存mapping含AppointmentEventEvidence解析与规划映射，以及measurement=false BuildConfig。

原签名证书SHA256 `ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37`，验签通过；仅INTERNET/ACCESS_NETWORK_STATE。两份模型清单、五个模型文件精确校验、六个64位ELF16KiB LOAD前置通过；不是ARM16KiB真机验证。常见明文密钥格式扫描无命中，不证明任意秘密缺失。正式包无测试fixtures、p12或loopback资产。

专用quiet-reader-upgrade-api36 / emulator-5562保留既有e4dae6c6/schema7数据，未卸载、清数据或访问手机。首次误用normal-upgrade-before阶段，其预期schema6，与实际7冲突，12项中1失败，已保存before-raw.txt；未改代码或降低断言。改用现有normal-upgrade-verify检查实际schema7，替换前12/0；随后仅-r覆盖当前普通包及同签名驱动，替换后12/0、双模型/原生界面19/0、推理后再12/0。本轮成功组55项均有INSTRUMENTATION_CODE:-1；不计首次失败组为通过。

旧收藏JSON、字体/外观、来源会话配置、阅读位置/展开标记及合成持久Cookie保持；schema7、两条旧收藏迁移幂等。是47/schema7→47/schema7同版本候选替换，不冒充重验46→47/schema6→7。真实512维embedding、reranker及SentencePiece JNI运行，服务释放后退出。合成Cookie不是实际平台登录证明。

从模拟器拉取已安装base.apk，hash与候选完全一致。候选复制到安全待测目录后正常关闭App及AVD，核对指定模拟器进程0、保留userdata，见installed-hash.json及cleanup.json。

## 仍未验收

真实虎扑/知乎账号权限与读取、贴吧楼层/楼内回复/切帖、Find X7 Ultra整App峰值RAM≤1GB、非充电耗电温升、独立事件质量与个人推荐收益、完整TalkBack均保持开放。已知公开回归仍有卢拉及水上漂两对漏合并；不能凭已参与开发的5/7宣布整体去重质量。隔离包新菜单11项不当作此普通包同分支运行证据。用户安排下班后连接，当前未操作手机、未推送/发版；手机仍按既有718f6e1f证据。
