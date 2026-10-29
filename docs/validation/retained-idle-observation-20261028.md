# 推荐资源观测：驱动退出前的静置阶段

本轮只用保留数据的普通API36模拟器quiet-reader-upgrade-api36/emulator-5562，未访问手机。上一轮只复核已有证据，没有新产品修改；本轮补齐上一份内存诊断指出的观测流程缺口，不把测试结束后的无进程响应当正常空闲回落。

## 驱动修改

ReleaseRetainedRecommendationProbe新增可选foregroundIdleSeconds/backgroundIdleSeconds，默认0保持原流程，每阶段0..90秒；非法参数在启动Activity前拒绝。真实“继续整理”及原45秒观测后，先前台静置，再通过UiAutomation实际系统Home操作进入后台。阶段前后核对Activity存活及预期焦点，期间只让测试线程有界等待，不触碰UI、不绑定或保活模型、不注入或清理缓存。每阶段即时sendStatus记录设备wallTimeMs、elapsedRealtimeMs、PID，finishing明确标记测试退出界限。

偏好和真实worker覆盖断言移至可选阶段之后，缓存命中也能保留静置证据，但不会因此让覆盖不足通过。UI线程异常传回驱动线程记录，避免测试断言直接使目标主线程崩溃。instrumentation仍会影响进程优先级与存活，本轮不能证明普通后台LMK回收、自然进程寿命或拔线能耗。

## 实际执行

普通原签名R8 App和driver编译成功。App SHA256仍为`0d82822920f3622d21f7d98d1bc4864045d1e5bb07e2af26dcd28d40b6b139cc`，与手机候选相同；只有driver改变。模拟器原位安装，两次安装成功，未重置已有数据。

先启动110秒只读采样，确认首条完整327188KiB后触发真实入口，设置前台/后台各10秒。驱动最终11检查1失败：两个静置阶段、实际Home和偏好不变检查通过；worker0、vectors682→682、reranks78→78，故保留模型工作覆盖失败。active_snapshot211→219不证明模型重算，未清缓存强造工作。

完整采样78条、61完整、17未知；全窗口完整含ranking1条、最大414517KiB，但此条不在本次Controller观测阶段，不能用全窗口采集脚本exit0覆盖驱动失败。阶段关联只使用同一设备的meminfo Realtime与驱动elapsedRealtimeMs，不混用未校准的主机墙钟。此关联为meminfo时间点，并非三次读取整个区间都包含在阶段中。

| 阶段 | 实际时长ms | 样本/完整 | 完整含ranking | 首/末完整PSS KiB |
| --- | ---: | ---: | ---: | ---: |
| Controller观测 | 45135 | 31/31 | 0 | 125321/304176 |
| 前台静置 | 10012 | 7/7 | 0 | 302826/301067 |
| Home后台静置 | 10011 | 8/7 | 0 | 301070/154431 |

后台阶段目标仍存活时出现较低完整观测，区别于finish后的无进程响应；不能推出模型资源正常回落已验证、ARM峰值降低或满足长期预算。未知样本继续为未知；历史真机1.20GiB保留。

两个参数负例：foregroundIdleSeconds=91、backgroundIdleSeconds=invalid均在0项UI检查前返回IllegalArgumentException，原失败码保留。driver卸载成功、模拟器收到emu kill并退出；userdata保留。手机/安全候选指针未改，未发布。

原始日志、采样、阶段分析脚本与报告、driver归档在artifacts/local-ai/retained-idle-20261028；构建日志artifacts/local-ai/retained-idle-build-20261028.log。下一次有真机使用授权时，应先建立采样再触发自然推荐工作；命中缓存仍保留覆盖不足，不人为清理缓存。个人推荐收益、独立去重、真实受限登录、完整分页、ARM峰值持续及拔线能耗等整体门槛继续开放。
