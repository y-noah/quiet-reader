# 普通贴吧进展候选与保留数据升级（2026-10-28）

## 候选包

安全副本：New-next-trial/New-20261028-9e06075e.apk，319482578 字节，SHA256 `9e06075e6916348faf27d66550f8a373f4f9acc3051a5f4f7de825ebc4302397`。

包名 app.quietreader、版本47 / 0.4.0-upgrade-probe、target36/compile36，DEBUG=false、RANKING_MEASUREMENT_ENABLED=false。原签名证书 SHA256 `ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37`。保留 New 品牌，没有新增 UI 名称或 Logo 变化。

包含贴吧楼中楼绝对时间获取、帖子及父子身份校验、新回复进展接线、实际帖子账号变动时的迟到结果保护。实现与功能验证分别见 [tieba-child-times-20261028.md](tieba-child-times-20261028.md)、[tieba-discussion-progress-20261028.md](tieba-discussion-progress-20261028.md)。不是全部功能或真实推荐收益验收。

## 普通升级验证

启动保留 userdata 的 quiet-reader-upgrade-api36，确认 device、bootCompleted、用户解锁和凭据存储可用后才执行测试。实际旧包 SHA256 与上一普通5982c350候选一致。旧驱动 normal-upgrade-verify 12/0 后，install -r 新普通 App/驱动；升级后12/0、实际双模型19/0、重复保留状态12/0，共55项检查，均唯一 raw INSTRUMENTATION_CODE: -1。

没有清数据、重种测试状态、修改或续期合成 Cookie。旧设置、字号与外观、来源模式、阅读位置和展开记录、旧收藏迁移内容保持。两轮保留检查均读取原有状态；没有把重复检查称为独立场景。来源 Cookie 是合成 fixture，不代表知乎、虎扑真实受限登录权限通过。

双模型经实际混淆后的服务和 SentencePiece JNI 运行，验证向量归一化、有限精排 logit、批次完成及释放退出。不是 ARM 整 App 峰值、推荐效果或耗电证明。

## 构建与包检查

- 普通构建参数完整 JVM 842项/118套，0失败/错误/跳过；Debug/Release Lint成功。
- 五个模型文件与清单大小/SHA256一致；六个64位ELF的 LOAD 段16KiB对齐；ZIP对齐、原签名校验成功。
- 所有ZIP成员的已知明文凭据模式扫描无发现；只证明该扫描范围，不代替完整隐私或出站审计。
- 实际模拟器 App/驱动 SHA256 与归档一致；安全副本再次核对一致后更新 current-candidate.json。模型和升级检查均针对同一归档普通 App。

证据目录 artifacts/local-ai/ordinary-tieba-progress-20261028/：build.txt、tests-lint.txt、jvm-summary.json、两份Lint XML、原始instrumentation日志和android-summary.json、app/test.apk、package-audit.json、credential-scan.json、certificate.txt、zip-alignment.txt、manifest-badging.txt、BuildConfig.java、mapping.txt、installed.json、candidate.json。

## 收尾和剩余门槛

专用模拟器已关闭，最终 emulator/qemu进程与ADB目标均0，保留userdata，见cleanup-final.json。手机未访问、安装或修改；仍为c315361c。当前指针phoneInstalled=false、published=false，无发布或上传。

下一步验证真实来源与账号/完整分页、R8公网来源时间获取，以及更完整的推荐与去重质量。真实知乎/虎扑受限正文评论、无明确标记进展召回、个人推荐收益、ARM整App峰值≤1GB、拔线能耗温升、长期在线缓存和完整出站审计继续开放。新候选并不意味着这些门槛已经通过。
