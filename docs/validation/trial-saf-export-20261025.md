# App 内匿名评价导出 / 2026-10-25

## 用户可用变化

偏好 → 本轮推荐评价 → 选择操作 → 导出评价记录。保存前说明内容和匿名记录仍属个人活动数据；用户选择系统文件位置，App不主动上传、不申请全盘权限、不唤醒模型或改变兴趣反馈。包含已完成和未完成会话。

新增TrialObservationSnapshot：工作线程从评价库只读一致事务复制sessions/labels的元数据白名单，生成schema1 SQLite快照。逐会话验证既有生产结构、标签时间/选择/身份及30会话/1800标签/单会话2Mi字符预算，拒绝孤立标签；只保留候选身份和observedAt。标题、URL、摘要、正文、Cookie、学习反馈原文及未知嵌套扩展字段均剔除，不打开兴趣数据库或账户库。保留原会话三种顺序、模型/策略、学习指纹及已冻结准备协议，未复制活跃evaluation_protocol表。

新增TrialExportUi：ACTION_CREATE_DOCUMENT + content URI，先生成快照再显示选择器，IO不在主线程。取消/失败删除私有暂存，关闭流失败也不显示成功；外部目标可能残留不完整文件，提示重试。保存/恢复页面状态只记录受限临时文件名，临时文件丢失仍保留已知请求以走明确失败提示，不能静默忽略。超过24小时的遗留暂存下次导出清理。不承诺任意进程死亡后的写入能自动恢复。外部保存可能使用用户选择的云文件提供者，App本身不连接上传服务。

DiscoveryActivity转交保存结果及状态；RecommendationTrialUi新增菜单并持有导出生命周期。无生产反射、额外keep规则或测试绕过入口。默认版本46/0.3.35、名称图标/签名保持，不提交推送发布。

## 实际验证

- JVM：461 tests，0 failures/errors/skipped（本轮生产改动后执行；后续恢复构建复用通过结果）。
- RecommendationTrialProbe：38/0，含原有评价、旋转/前后台/取消/偏好边界，新增说明取消、系统选择器取消清理和不改变兴趣/快照。记录在trial-saf-export-initial-20261025.txt；同一日志前一段快照测试有1项误报，下述已修正，不把该日志全部称为通过。
- TrialObservationSnapshotProbe：11/0，实际Android SQLite；含完整/未完成、未知私密字段剔除、原记录/准备/反馈不变、原目标拒绝覆盖、孤立/错误标签/空记录失败清理。首次断言将规则名hot-title-v2中的title误当标题字段，改为检测实际JSON键，并保留私密合成内容断言。最终记录trial-saf-export-system-20261025.txt。
- TrialExportSafProbe：最终11/0，实际系统DocumentsUI选Downloads保存、真实content URI回调和实际落盘SQLite读取；不确定选择保留；暂存删除、Bundle保存恢复、不可用提供者、恢复后暂存丢失及状态释放均通过。最后两项是注入Bundle/结果的定向边界，不冒充真实系统杀进程。记录trial-saf-export-final-20261025.txt。
- 初版导出完整R8流程：39/0，App SHA256 be6c1acfd3f5020f8fd94b5eb3d0ab23c312d16fc600b04a5588cfc3340efba7，含实际模型、推荐阅读及真实SAF导出；minified-saf-flow-20261025.txt。
- 随后仅修正恢复时不应丢弃缺失暂存的请求。最终精确R8 App SHA256 **b6c12b996d4ccf2d7972f4fa66c74b60ef315f444486d1f40b64cac64364f767**，319357727 bytes；Test SHA256 c6e58c389825d72247d649fda2852c4ce647245feef87b2d88f77b4619ce07f3，1963338 bytes。覆盖安装无卸载/清数据，实际base.apk hash一致。定向reading-export **5/0**：非debuggable、实际系统选择器、匿名落盘、暂存/学习/快照、开发兼容副本。minified-saf-final-20261025.txt，不把旧39/0直接表述为最终精确包完整流程。
- 最终APK6个64位ELF LOAD满足16KB二进制前提；模型307948507 bytes；38个ZIP成员395093894解压字节常见明文密钥格式扫描无命中。分别见minified-saf-final-binary-20261025.json与minified-saf-final-scan-20261025.json；不是16KB ARM真机、全部机密或完整网络审计。
- 系统保存截图及返回截图已查看：quiet-reader-review/trial-export-system-picker-20261025.png、trial-export-after-save-20261025.png、minified-export-system-picker-20261025.png；按钮可见、无遮挡，只覆盖模拟器当前浅色/字号。系统选择器属系统UI，不能据此声明全App视觉验收。

## 实际格式到开发侧评估链

生产快照测试副本synthetic-observation-sanitized-93edc78c-91b2-4ba8-a2da-c035ea67775a.db有2会话（含准备后的配对与未完成单列表），SHA b6db58586165691c72814c032a2f3e845b42ed92e671bcbbb99d323487d3f87d。既有只读export_trial_observations输出与人工核对template通过，原副本hash不变；严格评估器直接拒绝观察包。saf-export-compatibility-20261025.json记录这些边界。

真实debug SAF文件synthetic-evaluation-06ca7afd-4a6f-4452-8685-557657f26319.db：1会话、不确定1，SHA 0c3e43f05285b8271ac7a159662494d67c4458b463e7830d18563cd20c86a510，实际格式观察导出通过。

最终R8真实SAF文件synthetic-minified-observations-bcf90711-207e-4d66-908e-c1ce7b9344b9.db：8个保留合成会话、38个合成选择；既有只读工具成功读取，逻辑SHA 380c2ab1b8e6ebd00b421d92e5edd29a7a1faeb852e99036d9e5d34668db4f4b，和恢复修正前导出一致。记录restore-store-after-saf-20261025.txt、minified-saf-final-observations-20261025.json。均holdout_qualified=false；所有选择由开发测试生成，不是用户标签。普通debug/store构建及安装恢复记录同前日志。

## 未完成的整体目标

App内主动匿名导出已实现，不等于App内完整人工事件核对工具或推荐效果验收。仍缺真实本人独立5会话/100事件、完整历史/跨平台事件核验、个人收益，真实认证知乎/微博/贴吧权限，Find X7 Ultra含WebView全进程1GB与功耗/热，以及最终普通发行升级、完整无障碍/视觉。真实平台解析器不由缓存正文或导出测试证明。继续按delivery-gap-audit-20261025.md推进，整体目标未完成。
