# 模型进程变化时的PSS缓存失效（2026-10-25）

## 已复现的问题

ProcessMemoryBudget在枚举进程之前直接复用五秒内的previous。首次推理前的采样可能只有主进程；模型服务在随后启动，下一批的预算检查仍拿主进程旧PSS。反向情况下，工作进程退出后也可能暂时沿用包含旧工作进程的总量。

原生API36模拟器上的MemoryProcessSetProbe没有反射统计字段或注入PSS值。先确认没有模型进程并采样，标准bindService启动真实:ranking，再用HELLO取得不同于主进程的PID，ActivityManager确认进程实际存在。五秒内再次采样，最后RELEASE/unbind等待工作进程退出。没有杀掉已有进程或清数据；没有加载模型。

memory-process-set-baseline-20261025-api36.txt：8项，1失败。185ms内模型进程已出现，采样仍为38835KiB且requested/measured仍为主进程一份，直接验证新增进程被旧采样漏掉。基线的退出检查碰巧回到原来的主进程旧值，不能将其单独解读为旧版已具备进程退出失效。

## 修复范围

现在每次预算边界先重新枚举同UID、同App包名前缀的进程，再比较PID集合。只有集合相同、列表可获得、时间未回退且采样未满五秒，才复用PSS。新进程、进程退出、PID替换、枚举失败、到期均重新获取；集合顺序不影响复用。

保留850MiB软暂停线、缺失/不完整PSS暂停规则、工作线程约束及无后台轮询。稳定进程集合仍复用PSS，不在滚动/每帧反复采样。新增进程枚举在原有推理批次边界发生，未增加常驻任务、模型调用或新的权限。

canReuse的两项JVM测试覆盖新增/退出/替换/顺序变化、未知列表、未采样、时钟回退、空集合及4999/5000ms边界。当前有效JVM XML425项，失败/错误/跳过均0。

## 实际验证

所有结果位于artifacts/local-ai/。

- build/install-memory-process-set-baseline-20261025.txt：只增加探针，生产代码保持原样，用于上述失败基线。
- build/install-memory-process-set-fixed-20261025.txt：修复后构建和原签名-r安装成功。
- memory-process-set-fixed-20261025-api36.txt：8/0。第二次采样在253ms内，PSS由38718KiB变为58937KiB，requested/measured至少2；集合不变时复用同一个Snapshot，释放退出后重新采到单主进程。该数字仅为空载服务测试，不代表已加载模型的推荐峰值。
- release-process-memory-flow-20261025-api36.txt：首次混淆回归9项、2失败。诊断菜单过渡时读取了旧窗口，随后关闭点击未可靠完成，测试还在对话框后操作底层Home；最终转储保留了实际非零CPU详情。不是内存采样专项失败，也不算通过。
- ReleaseReadingFlowProbe改用可点击节点的标准ACTION_CLICK（其余仍回退实际触摸），并显式等待模型工作量详情和关闭后的首页可见；没有修改生产UI/模型来绕过检查。原失败测试APK保存在release-process-memory-first-test.apk，SHA256 434ae96190758f85e6ac4c8358f870d43df6d64e04477552b516a22a69611e99。
- build-release-process-memory-driver-20261025.txt及release-process-memory-driver-20261025-api36.txt：20/0。真实非debuggable双模型、CPU详情、冻结/确认、保存、统一正文展开/滚动/返回及少看更新通过。供给/正文合成，非真实账号或推荐质量验收。只调整测试驱动的第二次构建，App APK哈希未改变。

## 精确包与恢复

- 当前App319324959字节，SHA256 d9eafd2fb34638d542e380116174b32a9e754559e6b16812f45675bc4b034159。
- 最终测试APK2557164字节，SHA256 7faa53966a033cb426850c628c237bbe19f66dd5d83e5b04ce98796b547bd704。
- release-process-memory-installed-match-20261025.json：实际flowprobe的base.apk SHA256与留档一致。
- release-process-memory-alignment-20261025.json：六个64位库LOAD段16KB对齐，0失败；不证明ARM设备实际运行。
- release-process-memory-credentials-20261025.json：所有ZIP成员的已知明文密钥格式0命中；不证明任意秘密缺失。
- 修改前的精确model-work-usage包、测试包和元数据保存在release-flow-before-process-memory-20261025，逐文件复制哈希一致。持久升级夹具未重置。
- build-store-after-process-memory-20261025.txt及install-store-after-process-memory-20261025.txt：恢复普通debug/store构建与原签名-r安装，无卸载/pm clear。diff和新增Java空白/冲突标记检查通过。

## 尚未证明的部分

同UID公开PSS仍是下界，独立UID的WebView renderer可能无法枚举。进程集合不变时五秒内的模型内存增长仍可能未反映；采样也不能阻止首次加载期间的瞬时峰值。本修复解决的是“已启动的新模型进程仍使用主进程旧值”的明确缺陷，不是整个App峰值不超过1GB的证明。

Find X7 Ultra ARM整App峰值、WebView归属、真实推荐/浏览负载、耗电/发热仍需设备级验证。个人留出推荐质量、真实知乎登录全文/账号切换、完整来源及正式升级等整体门槛保持未完成。未提交、推送、发布、改名或换Logo。
