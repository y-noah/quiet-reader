# 真机资源采集链准备（2026-10-25）

## 实现

scripts/models/measure_device_probe.py升级到schema2，纯解析拆分为device_probe_metrics.py。固定操作app.quietreader.flowprobe，不操作用户普通包。保留每轮原始meminfo、proc/stat、proc/status和低频电池/热状态为NDJSON，最终JSON记录以下数据：

- 每进程PID、名称、PSS、RSS、Java Heap PSS与Native Heap PSS；没有的字段为null，不拿分类PSS冒充堆已分配大小。
- /proc/stat的用户/系统CPU ticks、启动ticks与CLK_TCK，用PID+启动ticks区分生命周期；读取失败/进程退出不填0，已退出进程未观察到的工作无法补算。
- /proc/status的VmRSS与VmHWM，单位KiB；VmHWM从进程出生累计，可能早于本次运行，多个进程的HWM不同时，严禁相加当整个App峰值。
- 实际查询耗时、采样时间、进程集合增删；--interval是查询结束后的延时，不能声称是固定采样频率。支持0.5/1/2/5/10/30秒，默认2秒；内存专场与能耗专场应使用不同频率，采集本身开销仍需真机评估。
- 电池温度（0.1摄氏度转换）、电荷计数（uAh）、电压、供电和状态，默认15秒查询一次环境，另留前后端点；热状态未知不置0。
- 只有唯一、非空checks=N failures=0、命令正常且未中止才可算功能通过；无有效PSS数据即使功能通过也退出2。0项、重复总结、instrumentation崩溃/失败都不能假绿。

wholeAppPeakVerified与energyComparisonVerified明确保持false。端点电荷变化不等于App能耗；期间充电、其他App、亮度/网络均未控制。按包查询目前确实捕捉了WebView renderer，但不能靠名称猜测所有隔离进程归属，更不能证明采样间峰值。

## 中止边界

超时是有界ADB查询轮次之间检查，不是硬实时截止；--timeout默认900秒，不等于30分钟标准负载已经存在。中止只force-stop固定隔离包；若主机instrument命令仍未结束，杀死该主机客户端后再请求远端停止，避免启动/停止竞争。最终仅核查固定主进程和:ranking是否当时消失，离线返回未知，不声称所有隔离renderer或随后启动都已排除。原App从不force-stop。

## 实际验证

- resource-metrics-unit-20261025.txt：13/0，覆盖多进程和缺失字段、重复PID拒绝、含空格/括号的comm、CPU生命周期、权限拒绝、RSS单位、电荷计数/温度单位、充电不能冒充0耗电、0项/重复/崩溃总结拒绝、只停止隔离包以及未退出主机客户端的二次停止与离线未知。
- resources-v2-emulator-core-20261025-{instrumentation.txt,memory.json,samples.ndjson}：现有精确R8 App CCDECDAB…、最终驱动04E8ACF9…，实际核心35/0；50轮，48轮有完整按包PSS，2轮不完整保留未知。
- 60条进程记录覆盖主进程、:ranking和WebView sandbox；60条均有Java/native/RSS分类及CPU ticks，59条有proc/status；CLK_TCK=100。查询耗时中位1.3045秒，额外轮间延时1秒，不能当作1Hz数据。
- 该模拟器采样PSS最大414233KiB，仅为该轮观测下界，不是Find X7 Ultra数值或1GB认证。模拟器供电被识别，device电荷端点差0没有被换算成App零耗电；25摄氏度/热状态0也不是实际手机发热结论。
- resources-v2-emulator-timeout-20261025-memory.json：故意设置0.01秒超时，CLI退出1，aborted=workload_timeout、功能passed=false、0轮且PSS=null。实际遇到主机客户端未结束，触发第二次停止，两个停止命令均0、hostClientKilled=true、namedProcessesAbsentAtCheck=true。该失败试验仅证明中止路径，绝不加到功能通过数。

## 下一阶段未完成

采集链已试跑；固定100候选/同一页面序列的30分钟三模式（无模型/初筛/双模型）、冷暖缓存、交错重复及后台30分钟仍需要编排和试跑。不能把本次约两分钟合成六候选核心流程重命名成长会话压力测试。

真机需非充电、固定亮度/刷新率/网络/起始温度和电量，对采集开销及电荷计数可用性先做校验。现有ADB采集要求连接，未实现脱离连接的长实验，不能要求用户一直插USB后拿充电数据证明耗电。手机独立执行/稍后回收与受授权的无线方式需另行设计；现在无需重新连接手机。

空间紧张提示仍需实际再现时区分App/网页/系统来源，当前脚本不宣称定位或修复。个人推荐收益、真实账号权限、完整TalkBack与最终普通发行/升级等交付缺口同样保留。未修改App产品行为、品牌、版本，未提交、推送或发版。
