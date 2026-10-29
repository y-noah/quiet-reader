# 2026-10-28 保留数据的真实推荐请求与内存观测准备

## 为什么补充

此前手机直接服务诊断曾观测1094411KiB，修复后虽有614.11MiB观测，但完整样本含ranking进程为0，不能关闭1GB资源门槛。代码复核：正常DiscoveryController.beginRecommendation在loading/preloader.busy时不启动，canPrefetch在recommendation非空时不开始正文缓存；直接绑定服务诊断绕过这部分生产调度。原超预算记录继续保留，不追认为不存在。

## 新的原生Controller测试

新增仅androidTest的ReleaseRetainedRecommendationProbe，使用普通app.quietreader、非debuggable R8与已有userdata；从真实设置→高级与诊断→本机推荐状态→继续整理按钮发起，不反射Controller、不直接绑定服务、不种候选/偏好/模型分数、不清缓存或Cookie。仅读本AppSQLite计数/版本与App自己可见的model PID。45秒有界观察不计作产品后台轮询。FLAG_KEEP_SCREEN_ON只在测试Activity期间临时使用，退出恢复。

边界明确分两类：phone=true仅Find X7 Ultra/Android16；不传phone只允许普通API36 sdk_gphone模拟器。没有放宽已有foreground-models的隔离包守卫。手机本轮未访问，新手机路径仅编译，尚未运行。

普通保留userdata模拟器两轮各6/0、唯一raw -1：
- 首轮观察2个worker PID；vectors298→563，reranks28→78，active_snapshot174→174。
- 次轮保留前轮缓存，观察1个worker PID；vectors563→578，reranks78→78，active_snapshot181→181。

两轮feedback条数/profile版本均未变化。不同轮开始前快照174→181不能冒充轮内自动替换证明；这里只证明每轮原生请求观察区间内的记录。需要在后续真实手机再次核对。45秒中观测过worker不是45秒持续推理，也不是独立个人推荐收益。

源代码与生产APK没有变化，原签名生产hash仍8a5ca3f199879587d3a3cdb05edfcd553224a44f04e1d02b2694da73b8a2d883；仅测试driver改变。构建成功18秒，生产/driver实际安装hash与归档一致。没有重建新候选指针或修改手机包。归档artifacts/local-ai/retained-controller-20261028。

## 可复用采样工具与实际不足

新增scripts/models/sample_retained_memory.py：只读固定普通包app.quietreader，指定serial，不安装/启动/停止App，不与隔离fixture工具混用。时长限制1～300秒，间隔1/2/5秒；输出目录必须不存在，避免拼入旧样本。每次按membership→PSS→membership验证稳定归属；逐行保存原始输出、错误与未知，KeyboardInterrupt保留已完成数据及终态。输出overall完整、含ranking完整、两个最大PSS分别统计，wholeAppPeakVerified永远false。

次轮真实请求同期70秒采样55次、完整34、含ranking完整0，最大404278KiB仅是不含模型的已知观测；含模型最大值null。一个原始窗口读取前与PSS有ranking，但读取后该worker已退出，按规则拒绝合计。不将其当作0或预算通过，不人为延长worker/清缓存强造覆盖。本轮仍未解决真实高峰覆盖问题。

--require-model开关使“有一般完整观测但没有含模型完整观测”返回非0，防止后续脚本将采集成功误当资源通过；即使通过覆盖也不是预算验收。实际已结束工作后的2秒负对照两样本全未知，返回1；这一负对照本身不能区分无一般覆盖与无模型覆盖，前述区别由单元测试单独核对。

5个新汇总/覆盖门槛测试、12个现有进程归属测试、23个现有指标测试均通过。未修改生产代码，未无意义重复848产品测试；新增Java测试driver已实际运行两轮。

下一轮手机可用时，在用户手动/正常原生请求期间使用--require-model与此测试共同观察，不清数据制造负载。如模型全缓存/短暂退出而没有完整观测，继续记未覆盖；实际Controller、直接服务诊断、长期日用和拔线能耗必须分别报告。当前普通候选和手机仍8a5，资源、受限权限、完整分页、独立去重/个人收益、长期缓存及出站审计继续开放。

## 后续命令（从项目根目录，手机须另行确认可用）

先启动只读采样，指定真实serial与全新输出目录：

```powershell
.tools/recommendation-python/Scripts/python.exe scripts/models/sample_retained_memory.py --adb .tools/android-sdk/platform-tools/adb.exe --serial 43062fa0 --output artifacts/local-ai/next-phone-controller-memory --seconds 90 --interval 1 --require-model
```

另一路安装与当前候选签名匹配的临时driver后，使用普通包入口（不得用fixture或isolated参数替代）：

```powershell
.tools/android-sdk/platform-tools/adb.exe -s 43062fa0 shell am instrument -w -r -e phase retained-recommendation -e phone true app.quietreader.test/app.quietreader.ReleaseDiscoveryInstrumentation
```

必须检查Android checks/failures与唯一raw-1、采样completeWithRanking、原始进程归属及安装hash；adb退出0不能代替这些证据。结束卸载临时driver，不清App数据。此次AVD已关闭，userdata保留。
