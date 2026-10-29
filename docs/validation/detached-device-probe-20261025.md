# 设备端脱离启动命令测试（2026-10-25）

本轮仅API36模拟器emulator-5560；未连接/安装手机，不等于OPPO拔线验收。

## 当前精确包

App SHA256 CCDECDAB78D9F93F35C6E63FB5C3D64EB91202C01371A392F77E9FA8BAF49C0F，319357727字节，与上轮相同。新驱动93254176DA8570B3E6C7DF57A96F5607366139F16F1DC47B9315FFDE5C971A13，1983818字节。离线R8构建成功，模拟器更新驱动安装成功。上轮04E8驱动和元信息保存在archive-before-detached-recorder-20261025。

## 实现边界

DetachedProbeRecorder仅测试APK使用，固定隔离包app.quietreader.flowprobe。App私有外部files目录保存AtomicFile状态和NDJSON原始采样；重复编号含AtomicFile恢复文件/已有采样拒绝覆盖。独立watchdog写TIMED_OUT后请求停止固定隔离包，并有自身进程退出兜底。初始化期间超时不继续分派业务阶段。

scripts/models/detached_device_probe.py提供start/status/collect。默认仅模拟器，真实设备需显式allow-phone；不配置无线ADB。nohup启动设备am instrument，标准流全部/dev/null，记录watcher PID/startTicks/cmdline，启动命令退出后按相同身份观察。未知结果不自动重启，采集不覆盖本地旧证据。测试结束后PID缺失是正常终态观察，已修正此前把其当查询失败而拒绝收集的问题。

## 证据

artifacts/local-ai/detached-pilot-20261025：

- detached-core-20261025-a：启动命令已退出，后续status核对watcherIdentityVerifiedLive=true；设备采样文件从4行推进至8行，无主机循环采样器。最终COMPLETE、35 checks/0 failures、96,665ms、15采样；所有15行JSON可解析并有meminfo。最终watcher不再存活。
- detached-timeout-20261025-a：timeout=1秒，1117ms记录TIMED_OUT，passed=false，1采样；收集时watcher不再存活，pidof固定主进程/:ranking均无返回。不能据此断言所有WebView renderer归属/退出。
- 原有资源解析单测13/0。

## 未完成

这是短合成阅读流程，不是30分钟100候选资源/耗电试验。未实现独立三模式能耗工作负载、手机侧手动取消UI、物理拔线与ColorOS后台保活验证。所有结果wholeAppPeakVerified=false、energyComparisonVerified=false。原始采样尚需纳入统一资源汇总，不能把15轮当作全进程峰值证明。未定位用户空间提示，未发版/提交/推送/改名。

## 通知栏手动停止与采样退出码修正

后续隔离App 06C6FAA1B614C79DFFF1AB8962B4DDB6D3062FCFE79D74A1EC4FD72A339F6854（319357771字节）仅在isolatedReleaseFlowProbe的release manifest增加POST_NOTIFICATIONS；普通main manifest无此权限。通知由测试驱动动态创建，无新增常驻组件。启动时通知权限/通道不可用则失败，不把无停止入口的运行当可用手机长测。

- detached-cancel-20261025-a：人为展开通知栏干扰真实菜单操作，COMPLETE失败1/1，记录保留，不当成功。
- detached-stop-control-20261025-a：使用独立control-idle阶段，已查看实际通知截图，点击通知正文后37,119ms写CANCELLED、passed=false、7轮采样；watcher与固定主进程/:ranking观察均退出。此阶段只验证取消，不验证业务或资源。
- detached-notice-core-20261025-a：未触碰通知栏时，完整阅读35/0、81,335ms、14轮原始采样，正常终态后无活动通知/模型进程。
- 对上述记录新增严格汇总后发现meminfo退出码全为null，故完整named样本计0，PSS峰值null，未虚报内存门槛。UiAutomation.executeShellCommand不会自动解释传入的重定向/分号。改用API31+ executeShellCommandRw("sh")并向stdin写脚本/退出标记后，detached-shell-20261025-b的8秒超时样本具有真实退出码，1/1完整named样本；仅空闲测试进程15948KiB，绝不是阅读/模型峰值。
- 最终驱动A48055733CE579565FE2B3F9598914A9479135D7C7A9C0C4BDBDC4E74D60327E（2762058字节），App hash仍06C6…；已构建安装模拟器。detached-final-core-20261025-b用于该驱动完整回归，结果单独补记。
- 主机明确UTF-8解码，修复Windows默认GBK读取中文状态异常；start增加ps实际watcher并发拒绝。已有30259运行时尝试concurrent-reject-20261025-a被拒绝，未创建新handle，不重启原测试。
- 新增严格汇总5/0：截断/失败/错误/未知退出码保持未知、缺行标完整性失败、重复summary/取消状态不得功能通过。原始NDJSON保留，资源报告不认证整个App RAM/耗电。

本轮仍仅模拟器；真实手机拔线、ColorOS通知可见性、长负载与后台节能仍未验收。此前“可拔线”通知是测试控制说明，不当作物理拔线证据。通知栏打开会打断自动UI流程，因此用户取消与正常业务测试分开报告。

### 最终驱动回归结果（未全部通过）

detached-final-core-20261025-b：App06C6…+驱动A480…运行103579ms，COMPLETE但35 checks/1 failure、passed=false；失败为“real recommendation details expose reported process CPU and loading work with scope limits”。其余34项通过，不能覆盖失败。此轮未操作通知栏。资源记录16/16完整、数量匹配，周期named PSS最高325243KiB（模拟器/包选择/下界），wholeAppPeakVerified和energyComparisonVerified均false。watcher终态不存活。

已在ReleaseReadingFlowProbe增加RESOURCE_DETAIL_OBSERVED行以捕捉下一轮实际工作量文案；该观测变更尚未编译，不能归入A480包结果。下一步定位计数/计时缺失、后台刷新/重入或检查时序，不先放宽断言。当前未就绪手机长测，不请求连接。

## 工作量详情失败根因（缓存复用与测试断言）

detached-detail-20261025-a保留实际文案：已缓存向量6/6、本轮精排3；已回报初筛0、精排3，推理CPU433ms/用时354ms，加载CPU6062ms/用时7614ms，且明确缓存不计为新推理/不代表整机耗电。35/1复现失败的直接原因是测试正则硬要求初筛[1-9]，不接受初筛缓存命中而精排仍有实际工作的正常路径。

已修正ReleaseReadingFlowProbe断言：初筛/精排为非负整数，至少一项>0；推理CPU>0、加载CPU完整、无计时未回报和耗电范围说明仍必须满足。不强迫为测试删除生产缓存或重复推理，生产推荐算法未更改。RESOURCE_DETAIL_OBSERVED继续保存。观察驱动及元信息已归档archive-detail-observation-20261025，先前A480驱动归档archive-before-resource-detail-20261025。

修正驱动670A822F951FE7A4A7E4E16B5193C73BF17F69058F61D8291DB05617B2D05042（2762058字节）；App06C6…不变。detached-detail-fixed-20261025-a用于完整回归，待终态补记。此前失败记录继续保留，不倒改成通过。

### 缓存断言已验证修正，另发现排序接纳未通过

detached-detail-fixed-20261025-a（App06C6…/驱动670A…）114875ms，35/1。工作量详情已PASS：初筛0、精排3，推理CPU468ms、加载CPU9996ms，计时完整；故原工作量失败已确认是缓存断言错误，无生产算法更改。

本轮另一项FAIL为explicit acceptance applies the new dislike-aware ordering through production UI；它同时要求active snapshot切换、首条不同、首条实际显示和适合我标签，需要进一步区分。未降低该断言。已新增DISLIKE_ACCEPT_OBSERVED，记录期望/实际snapshot、前后标题、首条可见性和模式标签；此观测改动未编译。需要检查受采样开销影响的部分计算/缓存续算、更新接纳时序及真实排序结果，暂不归因。

18/18有效named样本，周期PSS最高408046KiB，仅模拟器包选择下界；不证明手机峰值/耗电。新失败保留，整体未通过，不请求连接手机或发版。

## 排序接纳复核与可见状态等待

App06C6…/观测驱动F3FFEE7BD9A72D76815B756D6C8EC2EB00C9AD923A5F206322BED4610421AAE9：

- detached-dislike-20261025-a：100563ms，35/0，16/16完整named样本。expectedSnapshot=activeSnapshot=151；首条由手机续航合成话题变成明星红毯合成话题，shownFirst=true/personalLabel=true。
- detached-dislike-stress-20261025-a：采样延迟2秒（非2秒定时，需加查询耗时），75542ms，35/0，24/24完整named样本；expectedSnapshot=activeSnapshot=157，首条同样变化且实际可见。

这两轮没有复现670A的单次失败，不倒推该失败无效，也不能据此证明生产排序从无时序问题。源码确认accept先异步排队activateSnapshot，再通知feed并请求ListView布局；旧测试只轮询SQLite的active，随后立即读取可见性，缺少等待可见后置条件。现将同一个原5秒期限用于active==expected且首条可见且适合我标签可见；首条不同及全部原最终断言保留，不增加超时或修改生产算法。此改动排除可观察的测试同步缺口，不把它宣称为原失败已证实根因。

同步驱动571A90DF385E02A66EED9F2606127B13EE12150CE637AF460C471D77C31B354C（2762058字节），App06C6…不变；detached-visible-wait-20261025-a用于后续验证。F3FF…驱动及元信息归档archive-dislike-observation-20261025。

### 未保存终态的运行与诊断保护

detached-visible-wait-20261025-a / 驱动571A…：初始RUNNING后watcher、主进程和ranking均已不存在，状态文件仍RUNNING，15行raw；不能当通过或继续等待一个已不存在的handle。系统exit-info明确主进程8226为FORCE STOP，description=finished inst；无证据可将其归为生产App崩溃或原排序失败。原始状态/raw独立保存为-interrupted-state.json/-interrupted-samples.ndjson，exit-info及-interrupted-observation.json保留。未覆盖原设备状态、未按同编号重启。

发现旧nohup将instrumentation输出完全丢到/dev/null，不利于诊断结果写失败。启动器现将am instrument独立输出放到同一固定隔离App files目录的device-run-ID-watcher.txt；shell set -C拒绝覆盖，外层nohup仍不创建公共nohup.out。collect同步保留watcher输出。测试驱动onException对未捕获测试异常尝试保存失败结果；finish中采样清理异常计为失败后仍尝试写终态，避免清理异常跳过记录。以上为测试工具保护，非生产推荐逻辑变更。

当前detached-watcher-20261025-a用于新保护验证；未保存终态运行仍不算验收通过，原失败不删除。

### 独立输出保存成功与历史保留边界

detached-watcher-20261025-a（驱动5B47F397AAE5203ADCEC7CAF910D0C7C983794EC87D34B8188F3C2D367F2ED84）115540ms，35/2；AtomicFile终态和独立watcher.txt均保存相同检查报告。排序接纳PASS：expected=active=167、新首条实际可见、模式标签正确。未声称无终态运行的原始原因已复现/解决，只验证新增独立证据链能保存本轮结果。

两项FAIL均为prospectiveFlow预期sessions+1（重复未来池拒绝、偏好变化拒绝）。生产RecommendationTrialStore明确只保留最近30轮，而测试之前累积保留数据并要求总数永远增长。已改为记录开始时身份集合、实际创建后必须恰好新增一个身份、两次拒绝后身份集合精确不变。这样在淘汰历史时仍可核对没有偷偷新建评价，而非简单放宽总数。新增PROSPECTIVE_INITIAL_RETAINED_SESSIONS记录实际起始总数，待本轮验证30上限现场。

App06C6…未改；旧5B47…驱动归档archive-before-retention-assertion-20261025，detached-retention-20261025-a用于新断言完整回归。未发版，手机未连接。


### 保留边界最终验证

detached-retention-20261025-a：136113ms，35/0；PROSPECTIVE_INITIAL_RETAINED_SESSIONS=30，确认现场已触及历史保留上限。新评价恰好一个新身份，重复池和过期偏好拒绝均保持精确身份集合不变；排序接纳expected=active=174、新首条可见且模式标签正确。独立watcher输出与设备状态都保存最终35/0。

最终驱动SHA256 B655810DD0371555C7D706335DF73A2173E2CDF46FD36684A342B02B20530A26，App06C6…不变。有效named采样34/34，周期PSS最高401939KiB，只是模拟器下界。旧单次排序失败/无终态原因未被倒改；当前精确短流程已通过，停止继续重复同一短流程，转向长负载和真机边界。未连接手机、未提交/推送/发布。
