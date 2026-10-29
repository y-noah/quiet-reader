# 普通虎扑进展候选（2026-10-28）

## 当前候选

New-next-trial/New-20261028-5982c350.apk，319482578 字节，SHA256 `5982c350457de644b5db67e1f68dd040152ade245ab89fc03b7b46fc9789d2dd`。包名 app.quietreader，版本 47 / 0.4.0-upgrade-probe，target36、compile36；DEBUG=false、RANKING_MEASUREMENT_ENABLED=false。原签名证书 SHA256 `ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37`。

包含虎扑回复来源创建时间与讨论进展接线。相关 Android/R8 功能验证见 hupu-discussion-progress-20261028.md；不是所有平台或真实推荐质量验收。

## 普通升级与模型

专用 quiet-reader-upgrade-api36 保留原 userdata：实际旧包 hash 为 c63fe1d7 完整 SHA256，与上一普通候选一致。先用已安装驱动验证旧状态 12/0，再 install -r 新普通包/驱动；升级后12/0、真实双模型19/0、重复保留状态12/0，共55项检查，均唯一 INSTRUMENTATION_CODE: -1。

未清数据、未重新种子、未续期或替换合成 Cookie。旧设置、阅读位置、展开信息、原收藏迁移与来源模式及合成 WebView Cookie 保持；合成 Cookie 不代表真实平台受限权限。双模型通过实际混淆服务、SentencePiece JNI、向量归一化、有限 logit 及释放退出检查；不是推荐质量或 ARM 峰值证明。

最终普通参数 JVM 820项/116套，0失败/错误/跳过；Debug/Release Lint 成功。五个模型文件/清单长度与 SHA256、六个64位ELF LOAD 16KiB对齐、ZIP对齐、原签名与已知明文凭据模式检查通过。实际模拟器 App/驱动 hash 和安全副本均与归档匹配；凭据模式扫描不等于完整隐私或任意密钥证明。

## 启动前置失败和修正

初次在模拟器启动过程中仅发出了 sys.boot_completed 查询，没有确认值为1就执行了测试。系统日志 08:07:08 明确 SecurityException: Package app.quietreader is not encryption aware；当时 App 未启动、无检查结果，系统保留了 PID0 的 active instrumentation。不是正常产品启动结果，不能计为成功或用等待超时下结论。

保存 baseline-start-diagnostic.txt 与 baseline-start-failure.json。确认上述具体拒绝原因后，force-stop 没有清掉系统挂起登记；关闭专用模拟器才结束宿主命令。该首轮宿主最终因没有 instrumentation 输出文件而失败。未清数据/改安全策略/将产品设置为 directBootAware。

新增脚本 scripts/wait-emulator-ready.ps1：只允许四个已知模拟器 serial，最多60秒逐次核对 device、sys.boot_completed=1、RUNNING_UNLOCKED 和凭据存储可用；无重启或数据重置。重新启动原 AVD、脚本确认就绪（emulator-ready.json）后同一旧包和已有数据12/0，才做升级。

另核对旧 reader-actions-r8 报告后发现图片重排遗留问题已有独立后续修复与两轮 R816/0，并非仍待复现；已在旧报告追加明确后续链接，不删除初轮失败。

## 状态和开放边界

候选指针已更新，phoneInstalled=false、published=false；lastPhoneCandidate 仍 c315361c，手机未访问或安装。本地候选不等于正式版本。模拟器关闭结果见 cleanup-final.json，保留 userdata。

真实平台受限登录内容/全长分页、其他平台讨论进展、无明确标记的新进展识别、用户推荐收益/独立去重质量、ARM全App峰值≤1GB、拔线耗电温升、长期在线缓存与完整出站审计仍开放。