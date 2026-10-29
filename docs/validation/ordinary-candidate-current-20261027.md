# 当前普通签名候选与保数据替换（2026-10-27）

## 精确候选

本地文件 `C:/Users/10197/Documents/Codex/New-next-trial/New-20261027-e4dae6c6.apk`，319449810字节，SHA256 `e4dae6c63358efd68b4ec58277cf2d63483d8237c6e346c976e1360ac0bb0f08`。普通applicationId app.quietreader、versionCode47/versionName0.4.0-upgrade-probe、target36，非debuggable，RANKING_MEASUREMENT_ENABLED=false。这是本地测试候选，不是发布版本号递增，也没有上传或装手机。

包含38ce208b以后已完成的阅读页动作/方向预缓存、知乎来源后台释放、请求线程空闲退出、虎扑状态3读取及事件规划CPU优化；对应专项证据独立保留。当前正式源码646项JVM及Debug/Release lint通过，来自event-plan-cpu-20261027完整构建；本轮另构建普通签名R8包和同签名测试驱动。保存当前mapping和BuildConfig，普通compileReleaseJava/minifyReleaseWithR8/packageRelease均实际执行，不以旧产物代替当前包。

## 静态与打包

原证书SHA256 `ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37`，验签通过；仅INTERNET/ACCESS_NETWORK_STATE权限。两份模型清单与5个模型文件精确校验、六个64位ELF的16KiB LOAD前置通过；这不是ARM16KiB实机验证。常见明文密钥格式扫描无命中，不证明任意秘密缺失。正式包没有loopback测试证书或fixtures测试资源。

首轮apksigner因未设置JAVA_HOME而未执行、首轮模型检查因提供了不存在的清单路径而未执行，均保留原错误日志；补齐工具环境及使用正式源码清单后独立检查成功。没有用工具未运行的失败状态冒充有效验签/模型检查。

## 专用模拟器保留数据

明确quiet-reader-upgrade-api36 / emulator-5562 / ranchu / API36，保留userdata，未访问日常AVD或手机，未卸载/清数据。替换前已安装38ce208b（精确拉取散列一致），现有schema7；替换前12/0。随后仅-r安装当前普通包和同签名驱动：替换后12/0、模型/原生UI19/0、推理后数据12/0，共55项本轮检查全部最终成功码。

检查旧收藏JSON、字体/外观、来源会话配置、阅读位置/展开标记及合成Cookie继续保留，schema7与旧收藏导入幂等；模型组实际运行512维embedding、reranker与SentencePiece JNI，独立服务释放后退出。此次是47/schema7→47/schema7同版本候选替换，不重做46→47或schema6→7迁移。合成Cookie原始生命周期及先前续期见reply-cache-normal-upgrade-20261027.md，不能称本轮验证真实账号登录。

设备上实际base.apk拉取后SHA256与候选完全一致。最后停止app.quietreader与:ranking，正常结束专用AVD并核对相关进程0，保留升级userdata。全套原始数据在 `artifacts/local-ai/ordinary-candidate-20261027/`。

## 仍未完成

普通包55项只覆盖上述主路径，不是当前精确包的全部UI/来源/后台/缓存/故障范围。当前R8包含虎扑与事件规划改动，但此组不直接覆盖这些分支，不把旧Debug专项算到本APK运行次数。真实虎扑/知乎账号、贴吧真实楼层/切帖、WebView真实联网取消、去重三对已知漏项/新独立样本、个人兴趣收益、完整TalkBack、Find X7 Ultra整App峰值≤1GB与非充电耗电/温升保持开放。手机当前仍按既有718f6e1f证据，不受本轮影响。
