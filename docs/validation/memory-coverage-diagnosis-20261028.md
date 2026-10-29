# 内存未知样本原因复算：不改变原判定

本轮仅在电脑分析既有记录并改进Python诊断，不访问手机、不启动模拟器、不改App生产代码或候选。最新手机/安全候选仍0d828229，测试驱动已卸载、手机已交还，未发布。

## 原始记录发现

多数未知不是解析器漏掉一个可直接补算的PSS。新包sync-memory的78个未知样本全部有meminfo成功命令的精确响应“No process found for: app.quietreader”。其中77个前后进程名单都未给出All known processes，另外1个前侧仅剩归属renderer且后侧不可用。这些响应只说明当次查询未找到目标进程，不能证明全部共享资源为0，更不是正常App前台/后台空闲回落。

第一轮Controller开始标记为21:06:21.560，结束21:07:08.044；采样首份设备TIME为21:06:35，首次no-process记录设备TIME为21:07:08，与测试结束相符。Windows和设备时钟未单独校准，不据此给出毫秒延迟因果结论；实际编排先启动Controller再启动采样，已使模型加载初期易漏。测试finish后目标进程消失的窗口，不能用来代替正常阅读中的资源恢复。

## 工具改进

device_probe_metrics.attributed_pss新增incompleteReasons：命令不可用、前后名单不可用、主进程缺失、进程身份变化、缺失/额外PSS成员、总量不可用/非正值、精确目标包的no-process响应。原complete和pssKiB计算不变，未知仍为None；读取失败、截断和其他包的no-process响应不能伪装成目标进程消失。

sample_retained_memory.summarize新增缺失原因计数，同一条可有多个原因，不能把原因数相加当样本数。新采样记录样本开始、结束相对时间与墙钟时间，便于下轮分清调度窗口。新增纯离线replay(Path)，从原三次读取重新分类；若complete或pssKiB与原记录不同立即拒绝，不悄悄改历史结果。离线报告显式deviceAccessed=false、normalIdleRecoveryVerified=false，不覆盖原observations或summary。

## 实际回放

对三组全部323条原始记录重算，逐条complete/pssKiB以及原聚合统计均完全一致，originalDecisionsChanged=0。

| 历史组 | 样本/完整/未知 | 精确no-process响应 | 完整含ranking | 最大完整PSS KiB |
| --- | --- | ---: | ---: | ---: |
| prior-controller | 64/46/18 | 16 | 1 | 1253930 |
| initial-refresh | 129/69/60 | 57 | 1 | 721706 |
| fixed-refresh | 130/52/78 | 78 | 0 | 906271 |

先前1.20GiB高值保持，不因较新采样模型覆盖不足而删除。first-refresh含ranking1条仅254870KiB且该轮手动Controller前置失败，也不升级为负载通过。另有身份变化/额外成员等未知原因见原始报告，仍未补成0。

Python全部101项通过，包含精确/其他包/失败/截断响应、进程变化、renderer缺失、离线不调用adb/不改原文件，以及拒绝旧未知被写成0。原文件SHA256记录在回放输出中。证据artifacts/local-ai/memory-coverage-diagnosis-20261028，原始输入和原历史summary均保留。首次多文件补丁因上下文不匹配被原子拒绝，核对文件未变后按实际内容重应用；没有因该工具编辑失败改验收口径。

## 下一轮测量要求

1. 先确认只读采样已输出第一条完整记录，再触发真实Controller操作，覆盖加载开始；两条命令不能按各自工具默认等待时间错开启动。
2. 给现有真实Controller测试增加有界的前台静置/真实Home后台观察阶段，在阶段结束前不finish instrumentation；明确其保活和优先级对正常后台回收的影响。当前驱动45秒后直接结束，不具备该证据，不能沿用作空闲验收。
3. 单独记录模型实际计算、完整主进程+renderer+ranking的重叠覆盖、采样间隔及观察缺口。无模型时应保留缓存命中事实，不能清缓存制造更好看的结论。
4. 按体验优先、1GB优化目标、短时1.2GB可接受的最新要求考察持续时间、恢复与交互；采样最大值仍不是绝对峰值。拔线耗电与温升另验，不用USB充电或桌面采样推断。

真实权限、完整分页、独立去重及个人推荐收益等原整体门槛未关闭。
