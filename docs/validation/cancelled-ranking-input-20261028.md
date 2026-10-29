# 取消后的排队推荐输入（2026-10-28）

## 实际缺陷与修复

DiscoveryController.beginRecommendation先把画像/候选读取和CandidateOrder.forModels交给串行storage，原来只在主线程回调时检查generation/epoch。用户滑动取消或stop后，已排队的准备仍会读取画像、推进profile_time并构建模型输入，只是不发布过期结果。这不等于退后台继续推理，但存在可避免的数据读取和CPU工作。

将recommendationEpoch与feedGeneration设为volatile，使storage线程能观察主线程取消；在排队任务真正开始时拒绝失效请求，在读取候选后、构建画像与模型行之前再次复核。null结果由原主线程守卫丢弃。没有取消必要的反馈持久化、账号状态或滚动位置保存；没有新增线程、轮询、网络或改变模型/推荐预算。已进入的单次数据库读取、排序本身没有变成可瞬时中断，不能承诺后台零CPU。

## 复现与回归

新增仅AndroidTest的CancelledRankingInputProbe，严格限制debuggable app.quietreader.discoveryprobe/API36 sdk_gphone。唯一临时数据库、合成单候选、test_skip_fetch，无个人数据。真实Controller/SQLite，测试用反射定位私有准备入口并临时解除interaction标志；这是内部排队取消测试，不冒充用户手势或真机行为。以受控latch占住串行storage，在真正开始准备前分别保持当前、调用stop、调用interacting(true)取消。profile_time只读观测真实profile读取，当前正对照必须推进；取消场景必须不推进，反馈仍0。

同一driver修复前9检查/2失败、唯一raw0：current推进，stop及superseded也推进，确证缺陷。修复后两轮各9检查/0失败、唯一raw-1：current推进，stop与superseded均与before精确相等。没有删除首轮失败记录。

保留数据的既有隔离模拟器另跑实际模型recommendation-status：12/0、raw-1，实际模型运行→取消→诊断继续→完成待接收→显式接受→内存暂停链通过。不是个人推荐质量、模型峰值或手机测试。

修复后全量JVM 849项/118套0失败、错误、跳过；lintDebug/lintRelease通过。原始build、before、after、repeat、recommendation-status与JVM汇总在artifacts/local-ai/cancelled-ranking-input-20261028。初次native路径配置错误的构建失败保留，指定现有pinned原生源码后成功；未下载新源码/依赖。源代码修复尚未进入普通签名候选，R8普通升级与真机仍需后续验证；手机本轮先测现有8a5，禁止将此debug修复结果移作其证据。
