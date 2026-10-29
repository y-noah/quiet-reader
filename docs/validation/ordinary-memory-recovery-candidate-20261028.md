# 普通签名内存恢复候选 / 2026-10-28

## 当前候选

`C:/Users/10197/Documents/Codex/New-next-trial/New-20261028-770c98c3.apk`，319449810 bytes，SHA256 `770c98c3358969e2d7a2978d7440b58b1122ea9128b6f6690d242d7857c88112`。app.quietreader / version47 / 0.4.0-upgrade-probe / target36，DEBUG=false、RANKING_MEASUREMENT_ENABLED=false。原证书SHA256 `ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37`。不是正式发布版本；手机仍f0d4dd5a，未安装本包。

生产含上一轮30秒最低内存恢复间隔、清预读额度/取消任务，以及本轮前台恢复时立即同步资源限制诊断的修复。没有新增恢复定时器、后台轮询、模型或服务。前轮debug证据和边界见 [内存通知回归](memory-trim-recovery-20261028.md)。

## R8暴露的问题及修复

新增仅测试ReleaseMemoryRecoveryProbe入口，限定API36 ranchu/sdk_gphone和已保留合成升级marker的普通App。使用框架Activity/无障碍树/界面点击，不反射生产类、不改写fixture、不操作手机。注入Activity RUNNING_LOW，非系统真实低内存事件。检查内存诊断、点击继续、页面重建后的状态，并观察模型进程快照。

初版普通压缩中间包dd8ef503：升级后12项状态通过；memory-recovery-a.txt为7项2失败/code 0。仅给诊断树增加最多2秒有界等待和失败时实际文字后，同一App字节b轮7项1失败/code 0：首个内存原因检查通过；重建后诊断仍实际显示“选择想多看或少看的话题后，在本机整理顺序”，不能解释当前限制。a轮没有保存该时刻实际树，不能把它的首个失败追认为确定的读取时序问题或产品根因。

重建问题为实际状态缺口：新Controller保留进程共享准入，但初始诊断未同步，来源加载等其他门槛可让整理入口提前返回，文案保持初始值。生产DiscoveryController.start在前台切换时读取现有RecommendationPolicy限制，若受限立即发布PAUSED原因；不启动额外任务，也不依赖模型先执行或新计时器。同样适用于当时省电/电量/温度等现有公开限制。

重新构建普通候选770c98c3，新的R8探针两轮各7/0/code -1。确认真实压缩Activity的trim原因、早期继续仍有内存说明、重建后的说明保持。模型进程检查是ActivityManager在指定时点的快照，没有连续采样；未记录列表为空的单独诊断，不能据此独立证明任意瞬间从未存在模型进程。准入实现及前轮debug真实等待/取消证据补充行为解释，不将这7项称为完整ARM资源或耗电验收。

## 保数据升级链与最终范围

专用quiet-reader-upgrade-api36 / emulator-5562，保留既有userdata和schema7；未卸载App、清数据或重新种fixture。实际链为06636775→dd8ef503→770c98c3，不能说仅做了直达最终包的一跳覆盖。

根目录before.txt为实际旧06636775的12/0；after.txt为中间dd8ef503的12/0；intermediate-before.txt为再次覆盖前dd8ef503的12/0，各code -1。最初只读包路径校验误排除了包名中的点而提前退出，尚未执行安装；核对实际路径后修正校验继续，没有重置或替换旧状态。

最终证据在 `artifacts/local-ai/ordinary-memory-recovery-20261028/final/`：

| 日志 | 结果 | 范围 |
|---|---|---|
| after.txt | 12/0 | 普通签名覆盖后：原保存条目/迁移标记/阅读位置和展开/外观/来源会话配置/持久合成WebView Cookie保留 |
| memory-recovery.txt | 7/0 | 新修复压缩包真实框架/UI内存恢复范围 |
| models-ui.txt | 19/0 | 压缩包本机模型修复、真实embedding与reranker/JNI执行，释放/解绑后worker退出 |
| repeat.txt | 12/0 | 模型路径后的同一原状态复核 |
| memory-recovery-repeat.txt | 7/0 | 曾失败的内存/重建场景再复核 |
| final-state.txt | 12/0 | 再次内存操作后原状态仍保持 |

最终六份共69项/0失败，各唯一code -1；不是69个独立事件/场景质量样本，亦不将历史范围累计成全部需求通过。最新测试驱动SHA256 `2c2707fcc51a0c7116d1a0a87dea04acef1e0424636906f6f03b6149630fd35b`。旧驱动和原始失败中间包仍归档，便于区分前后。

## 构建、安装和清理

build-debug-status-fix.log的debug/JVM构建成功，当前679项JVM结果0失败/错误/跳过；build-status-fix.log的普通压缩构建完整终态成功，final-lint.log的Debug/Release lint成功。归档精确BuildConfig、mapping、app/test和散列。5个模型文件及清单hash一致、6个64位ELF LOAD对齐检查通过、全ZIP可识别明文凭据扫描无发现；DEX包含新内存恢复标记，未打包assets/fixtures或p12。这不是完整运行时网络隐私审计。

最终安装base.apk实际拉取散列匹配770c98c3，安全目录副本单独复核同一散列，current-candidate.json已更新且phoneInstalled=false、published=false。原普通App设置/数据保留由上述真实升级fixture验证，合成Cookie不是用户真实账号认证。

本轮测试没有导出账号数据或截图。App/驱动force-stop后关闭专用AVD，cleanup.json确认匹配进程0、ADB无emulator-5562，userdata保留。未访问手机、修改手机设置、推送或发布。

真实受限虎扑/知乎权限、完整贴吧短表情/分页层级、ARM真实网络整App峰值≤1GB、拔线能耗/温升、长期在线缓存、本人推荐收益、独立未知去重、全部页面视觉/TalkBack和完整网络审计仍未验收。普通候选准备完成不等于整体开发已完成。
