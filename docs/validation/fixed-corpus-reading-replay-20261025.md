# 固定100事件的实际阅读回放（2026-10-25）

## 本轮范围

在 `fixed-corpus-measurement-setup-20261025.md` 的相同100条公开标题、固定开发者测试画像和合成正文基础上，增加真实压缩App界面的连续操作。不是用户兴趣命中率、真实来源适配或真机能耗的结论。

`phase=energy-replay` 沿用普通控制器的隔离 NONE / EMBEDDING / DUAL 选择。准备结束后按固定快照顺序展示100个独立事件，在下标0、19、39、59、79、99打开正文。实际经过“查看更多话题”分页，逐条确认标题与ListView可见区域相交；正文通过真实卡片点击进入生产阅读器，实际触摸展开链接、验证展开后的段落、滚动并返回，验证列表首行和像素偏移保留。期间不接纳待更新快照，保证三模式打开相同事件。

列表定位使用 `ListView.setSelectionFromTop`，正文滚动使用 `WebView.scrollTo`，因此不是人工滑动、手势流畅度或帧时序验收。正文展开是注入真实触摸事件，没有用JavaScript调用click或修改展开状态。

## 观测与记录

测试只在App自有合成阅读页临时允许JavaScript，以 `evaluateJavascript` 读取DOM文本、aria-expanded和链接矩形，回调恢复原设置；不在来源网页执行脚本，不修改产品默认JavaScript策略。转换CSS坐标前等待几何稳定，实际点击后再次确认展开状态和超出摘要的段落。该方式不证明TalkBack/辅助功能可用，相关门槛仍需独立验证。

每次操作独立追加、flush、fsync到App专属 `device-run-ID-operations.ndjson`。包含固定语料SHA、模式、缓存条件、初始快照、计划和实际时间、事件身份、结果以及完整结束标记。未结束、100条不齐、重复事件、错序、时间轴重叠、模式或语料不符均拒绝作为通过。

脱机录制器与主机helper已接入 energy-setup / energy-replay；启动句柄、设备状态绑定阶段/模式/缓存/语料SHA/时长，并核验安装驱动内语料SHA。回放60..3000秒，默认1800秒，watchdog至少额外留240秒准备时间；通知停止和独立watcher日志沿用原有机制。采样与操作日志分别保留。整体内存和能耗verified字段仍固定false。

证据分析器 `test_detached_device_probe.py` 本轮9项通过，覆盖缺失记录、重复事件、错模式/语料/编号、不完整终态与错误时间轴等拒绝条件。

## 原始失败与通过证据

原始文件均在 `artifacts/local-ai`，脱机轮在 `reading-replay-20261025/`。

- reading-pilot-none-20261025-a / b / c：第二篇正文处辅助功能树缺少文字，均失败，保留日志和逐条记录。b失败截图实际已显示缓存正文、摘要和展开按钮，不能按此认定来源正文未加载。
- reading-pilot-none-20261025-d：绑定测试服务并请求触摸探索后曾推进到第60条，随后V2EX页再次缺少辅助功能文字；60条操作且无complete标记，分析器拒绝。没有把中途成功当完整通过。
- reading-replay-none-20261025-e：改用DOM观测后的首篇触摸未通过展开后置条件，4/1，脱机失败与独立watcher报告一致，证据保留；尚不能确定该次触摸失败的单一原因。
- reading-replay-none-20261025-f：完整100条、6篇正文、回放62204ms，6/0；独立分析 `readingReplay.passed=true`，13/13完整named采样，周期采样最高208917KiB仅模拟器下界。此轮驱动SHA3A6799ACADE8FC7D5E121F59D065D428DBDE3CC830A683D8FFA24B4DB5DE5947，已保存归档。并未证明所有时刻或WebView沙箱进程都纳入。

生产隔离App本轮仍为SHA103626FC98742191ED30F84CBE99507D8D2189ECBEEB34996AEF615F40E547D4，版本46/0.3.35；本轮只改测试与证据工具。加入稳定布局等待和明确触摸源后的驱动SHA E3C3FCAF2F441F06DC2A8F58EC200831158EA0A9C3E4E06CF6B91C0325CBFA23，后续模式结果另补。

最终驱动 `reading-replay-dual-20261025-g`：6/0，设备总时长98009ms，阅读回放62000ms，100操作/100独立事件/6篇打开，主机独立核验readingReplay.passed=true；16条原始采样中15条named完整，最高365437KiB仅模拟器周期采样下界。取证包含真实向量/精排缓存与双阶段执行检查，但不是所有精排完整完成或与阅读同步计算的证明。缺失的1条采样仍保留，不用0补齐。

同一最终驱动 `reading-replay-embed-20261025-g`：6/0，总时长75262ms，阅读回放61867ms，100操作/6篇；101向量/0精排、显式窗口1，独立核验通过；14/14完整named采样，最高212413KiB仍仅模拟器周期采样下界。整个实验并非严格同长度能耗比较，因为准备时间不一致，不能拿以上PSS或时长直接计算手机收益。

同一最终驱动 `reading-replay-none-20261025-g`：6/0，总时长70740ms，阅读回放61922ms，100操作/6篇，独立核验通过；13条采样中12条named完整，最高207188KiB仍只是模拟器下界。最终三模式均已有同驱动完整功能回放，不能表述为三模式能耗验收。结束后实际watcher/flowprobe进程均不存在，enabled_accessibility_services=null、accessibility_enabled=0；没有待运行测试。

复现短时回放（仅现有项目模拟器，先安装本文精确隔离App与驱动；不能用作手机能耗验收命令）：

```text
python scripts/models/detached_device_probe.py start --adb .tools/android-sdk/platform-tools/adb.exe --run UNIQUE-RUN-ID --phase energy-replay --stages DUAL --cache cold --replay-seconds 60 --timeout 360 --evidence artifacts/local-ai/reading-replay-20261025
python scripts/models/detached_device_probe.py status --adb .tools/android-sdk/platform-tools/adb.exe --run UNIQUE-RUN-ID --phase energy-replay --evidence artifacts/local-ai/reading-replay-20261025
python scripts/models/detached_device_probe.py collect --adb .tools/android-sdk/platform-tools/adb.exe --run UNIQUE-RUN-ID --phase energy-replay --evidence artifacts/local-ai/reading-replay-20261025
```

把占位编号替换成小写字母/数字/连字符、不超过40字符且未使用的编号；终态确认后再collect。超时或未知观察不得用同编号重启。

## 仍不满足完整资源协议的部分

当前是60秒短时演练，模型准备发生在回放之前。不能证明冷计算与阅读/主动缓存同时进行时的体验；没有完整30分钟三模式交错重复。`test_skip_fetch` 禁用了自动采集和主动预读，正文都是预先写入的合成缓存，不是预读命中率证据。

线上正文缓存正常联网时10分钟过期，现有准备链不能直接拉长到30分钟后声称同页面/同网络对照；长测还需受控网络/正文回放，不能暗改产品TTL绕过。固定刷新、登录页、媒体片段、网络字节/帧时序以及非充电手机能耗/热状态仍未贯通。真实登录、知乎补充/完整回答、微博刷新和私有图片、贴吧异步回复与用户真实独立评价保持原门槛。

未使用手机，未提交、推送或发版。模拟器临时辅助功能设置在c/d的finally恢复；f结束后独立读取enabled_accessibility_services=null、accessibility_enabled=0。后续DOM回放不需要额外绑定服务。
