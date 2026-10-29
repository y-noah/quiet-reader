# 推荐详情中的已回报模型开销（2026-10-25）

## 缺口与实现

M11要求本机诊断可查看模型计算开销。原RankingService已随ITEM发送elapsedMs、cpuNanos及processCpuMs，但LocalRecommendation没有保留或显示这些数据；加载仅有墙钟时间。用户无法从推荐详情判断一次整理是否实际进行计算、加载用了多少CPU。

本轮新增ModelWorkUsage，按实际请求ID汇总有效回报的向量/精排条数、推理进程CPU毫秒与墙钟毫秒，并分别汇总批次准备/加载的CPU及墙钟时间。进程CPU涵盖ONNX原生线程，不能用Java单线程cpuNanos代替。CPU与墙钟不同尺度，不相加成时间或换算电量。

RankingService的加载计时从本批次ModelFiles检查前开始，包含引擎创建、旧模型关闭、校验/加载及成功回报的修复尝试；增加DONE.loadCpuMs。原有请求deadline/模型顺序/生命周期不变。LocalRecommendation在既有模型revision、generation、profile、source、batch核对之后接收统计，只有有效ITEM计数；每批去重，意外ID与重复DONE不累计。缺失/负计时明确标记未回报，不伪装成已知0。累加溢出饱和，取消/回退前已回报工作保留在本轮统计中。

完成回调在释放recommendation引用之前读取诊断摘要，附到“本机推荐”详情。首页仍使用原有两行状态提示，没有把长数字放进卡片或额外增加入口。统计不持久化、不轮询、不访问网络，不增加模型调用。

## 表述边界

显示“已回报模型工作量”，并明确仅模型进程已回报部分，不含未完成任务、内容取得、界面或整机耗电。向量条数包含兴趣例子和候选输入，不是推荐卡片数；缓存命中不会产生新ITEM，因此不计为新推理。失败/取消中尚未回报的CPU不在汇总内，不能把该数字当作整个App或整个操作的总CPU。

此前的状态文案和向量缓存计数保留；本功能不替代Find X7 Ultra整App峰值RAM、充电/电量、热状态、后台活动和实际能耗验收，也不证明双模型推荐质量达标。

## 测试与当前精确包

所有报告位于artifacts/local-ai/。

- build-model-work-usage-20261025.txt及build-store-after-model-work-usage-20261025.txt成功。最终JVM XML423项，失败/错误/跳过均0；新增4项覆盖跨阶段CPU与墙钟独立累加、ID/DONE去重、缺失计时与空计算范围、溢出。构建可复用结果，不把全部项目称为重新执行。
- release-model-work-usage-20261025-api36.txt：20/0。非debuggable R8包中的真实双模型执行，正式推荐详情实际显示非零进程CPU、加载CPU和范围说明，没有未回报标记。并回归冻结/确认、保存、正文实际展开/滚动/返回、短读不学习、少看后新排序等。候选/正文为合成，不能外推线上来源或个人质量。
- model-work-usage-status-20261025-api36.txt：11/0。真实模型完成、暂停/继续、已接收模式、固定状态栏高度及列表位置、来源页文案回归通过。
- release-model-work-usage-alignment-20261025.json：6个64位库全部ELF LOAD段16KB对齐，0失败。
- release-model-work-usage-credentials-20261025.json：全部ZIP成员已知明文密钥格式0命中；不证明任意秘密不存在。
- 当前App319324959字节，SHA256 99ff72c4c848f91f00591296f283bf0d86bdc7b196c462ea35eb9355e96406ef。测试APK1877228字节，SHA256 66e2b7679287f8ded606cb672cff0d81b99d59e18c3661c130f1bd72af0fe013。
- release-model-work-usage-installed-match-20261025.json：实际flowprobe安装base.apk的SHA256与留档相符。

上轮flow包和测试包从实际安装位置读回，分别核对2239a191…和a7c3ec1b…旧完整哈希，与前报告一致，保存在release-flow-before-work-usage-20261025。普通包与升级测试包没有清数据。测试后用原签名-r恢复普通debug/store配置，见install-store-after-model-work-usage-20261025.txt。diff及新增Java空白/冲突标记检查通过。

未提交、推送、发布、改名或换Logo。本轮仅关闭“模型CPU已有回报但详情未展示”的实现缺口；真实账号、个人留出推荐质量、全来源/无障碍、正式版本升级/回滚、真机RAM/能耗等整体门槛保持未完成。
