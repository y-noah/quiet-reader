# 操作缓存预算：本地验证

对应需求：`../requirements/action-cache-performance-20261027.md`。

本轮实现首页动作 20 秒机会窗口、自动回调不续期、后台清额度与取消、正文候选 32 前向 + 8 回看且保持串行。窗口只影响推测预读，显式阅读仍走现有接管路径。图片预算与模型批次不扩大。

执行 `testDebugUnitTest lintDebug --offline --no-daemon --max-workers=4`。首次沙箱构建因已有 ONNX api.jar AccessDenied 失败，日志保留；在获准的本地非沙箱环境执行相同离线检查成功，44 秒。JVM 628 项，失败/错误/跳过均 0；其中新机会窗口策略 3 项。lintDebug 通过。

日志：`artifacts/local-ai/action-prefetch-20261027-build.txt`、`action-prefetch-20261027-build-elevated.txt`。

本轮未执行 Android 集成回放、真机 RAM/温升/耗电、网络截止时刻验证；不能据此宣称完整能耗验收通过。未安装手机、未生成新的普通交付包、未推送或发布。后续须在同一候选验证实际到期取消、滚动触发与显式阅读接管，再做非充电真机资源验证。

## 后续模拟器运行

以下补充覆盖上面的“未执行 Android 集成”状态，但不覆盖真机资源未验收状态。

隔离 debug 使用70个合成文字候选，实际 Controller、Activity、SQLite、Repository、Handler，替换正文 Loader；远窗口45与32前向/8回看范围错开，仍验证取消/快速返回/迟到结果拒绝。实际 HOME、重建、前台返回保留。新增21.5秒反复同位置 viewport 回调，额度到期后实际 pending Loader 被取消、迟到正文不入缓存，新滚动恢复。

- `lifecycle-a.txt`：11项通过后，重建自动获取在8秒内未出现，1错误。无调度状态日志，原因未证实；当时另有本机R8构建进行，不能直接归因主机负载，也不能删除失败。
- `lifecycle-diagnostic.txt`：补充超时状态输出后20/0，未放宽8秒等待、20秒额度或生产逻辑。
- `prefetch-lifecycle-repeat.txt`：同一生产实现再次20/0。
- `discovery-prefetch-supply-repeat.txt`：7/0；`discovery-prefetch-supply-warm-repeat.txt`：8/0。真实Controller调度配受控来源/正文，部分来源未完成时准备正文、浏览器回退抢占、恢复与退出取消通过。
- `prefetch-disk-reuse-repeat.txt`：17/0，磁盘文件复用/接管与新正文写入通过。

后四项合计52/0，诊断重跑另20/0。不是52次真实平台下载或实际网络取消证明。debug APK/驱动及散列保存在 `artifacts/local-ai/action-prefetch-20261027/`。首轮失败保留。

混淆基线DAECBBC688A8E475DCAF559B307A2BB4EC2D08BA06B8146424DA1E2D865B89D9，驱动8997ACF3297F5101A702984930561553320F14D3FA32F344F03BFA5ED416B128。首次安装命令发现5560不在线而停止；随后进程检查确认该AVD为0进程，才保留数据重启原AVD，sys.boot_completed=1后开始双模型冷缓存回放。未枚举或操作手机。

## 证据更新合并与显式续算

基线混淆包冷12/0（33,626ms）、暖+实际HOME15/0（自动整理15,095ms），100份正文文件inode保持。暖缓存16篇正文证据导致大量中性快照重建；旧逻辑每篇类型/语言变化都prepare(false)。现逐篇校验/保存，500ms合并一次prepare，后台/销毁取消待重建，显式推荐/评价开始不受旧合并回调打断。

另发现续算间接依赖预读/到期回调；有限缓存窗口不足以保证任意后续分片都获调度。LocalRecommendation现仅在正常预算耗尽或事件复核预算未完成且本片成功持久化了新向量/分数时授权续算，Controller明确安排下一片。完成、缓存命中、失败、资源限制及无进展不授权此续算。单片15秒与自动启动间隔15秒不扩大，后台取消不变。

最终生产代码debug APK：200AF6BBCECC3FB11FED196C53518C60F4FD8C636A1C02E8F388E0513DE99C7A；当时回归驱动61F711978C4D89D2E0F7CFDE34E98D7B1993AF0714DFE935A3E5759E91C4F4EE。生命周期20、冷/暖供给7/8、磁盘17、不可用模型反馈9、实际三执行阶段15、故障恢复11，合计87/0。JVM628/0/0/0、Debug/Release Lint通过。后续仅测试驱动改动不自动取代这些驱动身份。

最终隔离混淆APK：7C1AF42AE033AA60F04591547A5B95A3AE9BBB284189DFD7C4ABEAB7B5E5E162，319461349字节；初始驱动AC74351C65515D0080EA8C692DB62DFBCFA39FE33D8AF3EDEF760AEA8ECE4EF6。

- `final-release-cold.txt`：12/0，20,298ms自动完成，无手动续算，102向量/42精排缓存，100份正文inode保持，完成后15秒未观察到模型进程或模型缓存增长。
- `final-release-warm-background.txt`：15/0，18,745ms自动完成，实际工作量0初筛/0精排/0推理CPU/0模型加载；16份正文证据。首个观察快照到完成的编号增量从基线17降为3，与合并重建相符；非严格速度/能耗实验，暖启动本次反而更慢，不能宣称全面提速。
- 对应`final-release-warm-background.ndjson`：真实HOME/onStop，10秒宽限后60秒/31个每两秒采样点均无模型进程，返回仍保持列表。不是锁屏、唤醒追踪、CPU零占用或整机能耗证明。
- `final-apk-check.json`：与源码相同模型清单、5份模型文件大小/SHA、6个64位ELF的16KB LOAD前置检查通过。`final-credential-check.json`识别格式扫描无发现，不等同于任意秘密绝对不存在。
- `final-release-reading-core.txt`：5项通过后1错误；驱动关闭诊断弹窗后停在高级页，却等待首页设置入口。已在测试侧补显式returnToFeed，未改产品导航、未移除断言，重跑待完成。此失败不能记作阅读核心通过。

## 受控分片边界（测试时钟，不是自然运行性能）

新增recommendation-continuation：仍用真实Controller/模型/SQLite/100个公开标题+合成正文，等实际新向量落库后，将该片deadline设为已到期，连续两次；必须观察第三片在缓存额度过期后自动开始，并最终完成及45秒空闲不重启。没有显式推荐操作、没有增加模型输入，但修改了测试时钟，因此不称完全自然自主运行证明。

首轮`final-controlled-continuation.txt`6项检查/4失败：第一片观测4个新向量后设到期，排队结果最终保存48个；第二片测试错误地沿用4作为基线，立即把前片48个当作本片进展，将其过早终止。该片实际0新初筛/精排/CPU，生产正确拒绝续算，第三片未启动。已将进展基线改为每片首次观测时记录；不放宽生产“必须有新结果”门槛。原失败保留，修正测试重跑待完成。

修正每片基线后，`final-controlled-continuation-corrected.txt`11项检查/1失败：三片实际模型按预期启动，第三片缓存额度为0，自动完成54,396ms，45秒无新模型，当前全列表冻结；严格缓存inode只保持99/100。没有放宽该断言。检查发现两个旧回放的合成贴吧话题正文均未填topicResolution，而现有ArticleSession/ArticleVersion明确拒绝此类缓存，预读会走重新读取；这也使之前的“全有效正文缓存”假设不成立。失败未记录具体变更URL，因此不把这一结构缺失当成唯一已证实原因。

测试现补齐仅用于合成正文的帖子身份、选择列表及对应会话（明确合成帖URL，不是官方话题解析证据）。debug启动前逐份用实际ArticleDiskCache.read、ArticleVersion.matches验证100份正文均有效；保留100份inode断言后重跑。ReleaseReadingFlowProbe也补相同结构。生产的缓存拒绝规则、会话校验和平台解析均未修改。

上述修正前的冷/暖时延及快照增量只能作为诊断记录，不能称严格全缓存、无网络回退的性能证据；100份文件未变并不排除网络请求。以修正样本的后续回放为准。待运行的新证据尚不能追溯覆盖旧报告。

修正样本后，`final-controlled-continuation-valid-cache.txt`12/0：启动前100份实际磁盘/会话/元数据/话题身份校验通过；真实预读准备32份正文证据；分别在本片确有新向量落库、总数达到5/51时模拟两次到期，三片启动，第三片缓存额度为0，自动完成38,708ms；随后45秒无新模型启动，100/100正文inode保持、当前100事件顺序保持。该38.7秒含人为预算到期及片间等待，绝非自然速度指标。生产代码仍为200AF6…/7C1AF4…，仅测试驱动和合成缓存结构调整。

源码审查补记：主manifest只有网络及网络状态权限，私有绑定ranking进程；未检出主源码中WakeLock、KEEP_SCREEN_ON、AlarmManager、WorkManager、startForeground或开机接收。此为设计证据，不替代实际后台CPU/唤醒与真机能耗测量。

## 混淆驱动再次核对

补topicResolution后的首轮release驱动3937AC3DF50F6CF4894FB40CC54921DC79D875C9393DA7647165E23EB70A42CF仍有旧的种子会话序列化：把贴吧写成baidu-account-v1，实际生产已是tieba-account-v2并排除明确访客字段。修正前仍会缓存不命中，因而这轮冷12/0、暖15/0不能作为全缓存窗口通过。保留日志`corrected-release-cold.txt`/`corrected-release-warm-background.txt`。

这轮从模拟器拉回的base.apk实际散列严格等于7C1AF4…；`corrected-release-reading-core.txt`40/0，覆盖实际模型排序/保留旧列表/显式接纳、统一阅读展开/滚动/返回、正文证据、短阅读不当喜欢、反馈归属、评价与真正未来样本。该阅读核心使用新URL的6条V2EX合成候选，不经过贴吧会话分支，证据有效；未执行SAF导出。

下一驱动只同步测试会话序列化，并在冷/暖终态增加preparedEvidence>=32的硬门槛，避免“只有一条命中”继续掩盖窗口缓存不完整。生产缓存身份没有放宽、没有增加白名单、没有关闭安全校验。冷/暖新门槛重跑进行中。

## 本轮终态与边界

会话同步驱动E76F32110193D785912BDE02FEF52C2005C5AD7545731C88C579274452E69208；debug有效缓存驱动C8FD46989388A5ED1935DA4C9AB52A77C9F7C4917A22622ADEE40294B804ACFC。均测试同一最终生产代码；实际混淆base.apk已拉回并严格匹配7C1AF4…。

- `valid-session-release-cold.txt`：13/0，自动完成10,427ms，102向量/42精排缓存，点击前完整32条窗口准备、100份文件身份保持，当前列表不自动重排，完成后15秒无观察到模型或模型缓存增长。
- `valid-session-release-warm-background.txt`：16/0，自动完成4,320ms；完整32条窗口准备、100份文件身份保持，实际HOME/保留列表返回通过。模型工作量细节及状态见原始记录，不能由PC时间推算手机电量。
- 对应NDJSON重新逐行解析：31个实际后台采样quiet=true且rankingPids为空。10秒宽限后60秒观察范围不扩大成锁屏、持续CPU、无线电或电量证明。
- 严格解析选定11份当前代码结果，合计168检查/0失败，清单及冷/暖时间与窗口计数写入`verified-summary.json`。非把旧失败删除后拼总数：所有失败及其修正范围仍在本报告和原始目录中。
- 隔离App和驱动force-stop后未观察到主/模型PID；四个明确NDJSON复制后删除，原AVD正常退出且匹配进程0，userdata保留。见`final-cleanup.json`。未访问手机、未新增手机测试驱动、未发布。

仍需完成：阅读页更多动作机会覆盖及真实网络/WebView到期取消的额外证据，首次8秒重建超时的根因、Find X7 Ultra非充电耗电/温升与整个App（含渲染进程）峰值RAM、普通交付包重建/原位升级及其他平台/推荐质量验收。现有普通候选38ce208b…与手机718f6e1f…均未加入这轮调度变更。本轮完成不等于全部需求或整个目标完成。
