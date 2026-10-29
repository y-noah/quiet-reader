# 当前普通候选：V2EX过滤后回退（2026-10-27）

本地文件`C:/Users/10197/Documents/Codex/New-next-trial/New-20261027-e94753dd.apk`，319449810字节，SHA256 `e94753dd50ca64f595127c7ec86407ccbd07dac68fae5096bc38b89ab4d00b84`。取代2bf60a5d作为下轮普通候选，包含其任命去重及后续V2EX原生结果全被筛除后的单次网页回退。未上传、推送、公开发布或安装手机。

## 打包及静态证据

证据目录`artifacts/local-ai/ordinary-filtered-fallback-20261027/`。实际重新执行compileReleaseJava/minifyReleaseWithR8/packageRelease，构建终态成功，保存mapping及BuildConfig；普通app.quietreader、47/0.4.0-upgrade-probe、target36、nondebuggable、RANKING_MEASUREMENT_ENABLED=false。

原签名SHA256 `ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37`验签通过，仅INTERNET/ACCESS_NETWORK_STATE。模型清单与五个模型文件精确匹配、六个64位ELF16KiB LOAD前置通过；不是ARM实机前置及峰值内存证明。常见明文凭据格式扫描无命中，不证明任意秘密缺失。正式包无fixtures、loopback或p12测试资产。

## 当前普通APK实际运行

quiet-reader-upgrade-api36 / emulator-5562保留上一轮2bf60a5d/schema7数据，只-r覆盖App和同签名驱动，无卸载、清数据或手机访问。替换前12/0、后12/0、双模型与原生UI19/0、推理后再12/0，共本轮55项终态成功，均INSTRUMENTATION_CODE:-1。旧收藏、外观字体、来源会话模式、阅读位置和展开状态、合成持久Cookie及schema7/旧收藏迁移幂等保持。

模型组实际运行512维embedding、reranker及SentencePiece JNI，服务释放后进程退出。设备base.apk拉取后与候选hash一致。保存待测APK后关闭App/AVD，指定模拟器进程0，保留userdata，见installed-hash.json和cleanup.json。

这些55项不直接触发受控V2EX过滤回退分支：该分支的22项和前台供给冷7/暖8项来自Debug受控运行，详见filtered-board-fallback-20261027.md，不能混算为当前普通APK分支运行通过。当前R8证据是实际编译和普通主路径运行。已有JVM653/0及Debug/Release lint沿用该生产代码最近一次完整构建；本轮未改生产代码。

## 未关闭的门槛

真实虎扑/知乎账号正文与评论权限、贴吧楼层/楼内回复及切帖、Find X7 Ultra整App峰值RAM≤1GB、非充电耗电温升、完整TalkBack及逐页视觉、独立事件质量与个人推荐效果。已知两对漏合并保持开放。合成Cookie不当作平台登录验证；模拟器不代替ARM手机。用户安排下班后连接，手机仍按既有718f6e1f证据。
