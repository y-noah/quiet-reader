# 固定100候选测量准备链（2026-10-25）

这是长测准备组件，不是完整阅读回放或耗电验收。

## 固定语料与测试隔离

androidTest assets/energy-replay-100.json来自已保存公开标题语料text-ranking-capture-20261025.json的100个primary，原始语料捕获时间保留；不含用户评价。SHA256 f7060969b0d3d90b9552efa071b8f67ab9c0f992142a1e2d93ce7f83c7121683。正文为显式标注的合成统一阅读缓存，非真实来源全文。当前生产归组实际保留100个事件，但规范化模型输入只有99个；其中两事件可共享向量，不能要求为100行候选重复计算100向量。

新增BuildConfig.RANKING_MEASUREMENT_ENABLED仅isolatedReleaseFlowProbe为true，且运行时须包名app.quietreader.flowprobe才读取isolated-measurement/stages。普通构建为false，原构造器默认继续DUAL；没有生产设置入口。隔离R8中的NONE/EMBEDDING/DUAL通过普通DiscoveryController实际执行，不用反射生产类或将双模型结果拆成三份冒充能耗实验。

隔离R8可使用test_skip_fetch固定本轮来源输入。普通非debug构建不受该测试extra控制。准备阶段将100项及合成正文缓存写入固定隔离包，关闭弱阅读学习以保持对照画像，按cold清空或warm保留模型缓存；来源成功时间与retry预算固定，测试不会主动采集新网络内容。没有替换真实账号/浏览鉴权实现。

## 实现与范围

ReleaseReadingFlowProbe.runReplaySetup通过Android公开API操作实际压缩包，加载固定标题、写独立包SQLite和正文缓存，验证生产归组100事件，启动实际设置菜单的继续整理。使用一次明确profile版本变更确保不是复用同一完成代次的菜单空操作；反馈文本不变。若新快照只是部分结果，最多六个显式计算窗口/180秒，保持生产15秒窗口和所有缓存，不放宽计算时间预算。最终清除测试stage偏好，下一次默认回到DUAL。

种子写入已抽为persistSeed，原六候选核心流程继续调用相同函数。反馈source按实际种子来源写入，避免将公开语料中的非V2EX标题记为V2EX。

## 现场证据

普通build-probe -Offline -Target store更新源码后JVM466/0，生成debug BuildConfig测量开关false，未安装手机。当前隔离R8 App SHA103626FC98742191ED30F84CBE99507D8D2189ECBEEB34996AEF615F40E547D4，319357771字节。

- replay-setup-none-20261025.txt：4/0；100候选/100事件，向量0/精排0。
- replay-setup-embedding-20261025.txt：4/2；实际99向量/0精排，被错误的>=100断言拒绝，原始记录保留。
- replay-setup-dual-20261025.txt：4/2；首窗口99向量/0精排，旧准备器没有显式续算且断言>=100，两项均未通过；不能当双模型完整执行。
- replay-setup-embedding-fixed-20261025.txt：唯一输入99、显式窗口1，4/0；99向量/0精排。
- replay-setup-dual-fixed-20261025.txt用于修正后的真实双模型验证，终态另补。

## 未完成

当前只验证压缩包/三种计算路径/固定100候选和缓存准备，还没有30分钟固定滚动/阅读/返回/登录页/媒体回放、统一操作时间轴/帧时序或三模式非充电手机能量对照。准备阶段本身的耗时和资源不可当成30分钟阅读测量。warm参数已实现但本轮尚无warm现场证明。新energy-setup阶段尚未接入detached启动器与采样状态身份，不能据此请求手机无人看管长测。A08与真实权限/个人效果等门槛保持开放。

## 画像固定后的当前准备链验证

为三模式选择同一组显式开发者测试反馈，来自原公开语料回放的developer_profile；不是用户画像或推荐质量标签。测试反馈写入sample作用域，不伪造为某平台的用户偏好。energy-replay-100.json已加入该声明，当前SHA256 e6fc0bbb2c214aebd06cc447322dd854a8afdfcde40439cad721215d761c19c9；100事件的99唯一标题输入，加两条不同的开发者样本，共101唯一模型输入。此前f706…版本与当前画像不同，不混作同一能耗对照。

- replay-setup-dual-fixed-20261025.txt：旧画像六个显式窗口后99向量/0精排，4/2；保留失败，旧轮无具体状态文本，不推断其未精排原因。
- replay-setup-dual-profile-20261025.txt：新固定画像，显式窗口2、101向量/6精排，4/0，证实普通压缩controller已实际执行双模型。取证时详情仍为“正在按你的反馈整理”，因此只能证明两阶段执行，不能表述整轮所有精排已完成；测试结束会关闭controller。
- replay-setup-none-profile-20261025.txt：相同语料/画像，4/0，窗口1，基础顺序、0向量/0精排、上报工作量全0。
- replay-setup-embedding-profile-20261025.txt：相同语料/画像，4/0，窗口1、101向量/0精排，详情“已完成初筛”，实际初筛101。
- replay-setup-embedding-warm-20261025.txt：3/0，保留上述向量后窗口1、缓存101/101、本轮初筛0/精排0/上报加载计时0。尚未证明暖DUAL和实际完整重复阅读的能耗。

以上当前精确App仍103626FC…，最终驱动6DC87037C682A21B2A29D92EE2AEE92ABE2BD1558687F9E7AFFC270961AF4661，2033103字节。普通构建测量开关false与JVM466/0已另核。旧06C6…App和B655…驱动保留于archive-before-energy-setup-20261025；不能把旧包资源结果直接移到当前包。

fixed-corpus-regression-20261025-a用于当前包原六候选核心阅读回归，终态另补。固定100候选准备至此已贯通；下一步实际长时间操作回放/采样取消和设备能耗条件，不能用本轮准备检查数替代。


## 当前包原阅读流程回归

fixed-corpus-regression-20261025-a：当前App103626FC…及驱动6DC87037…，104635ms，35/0；15/15完整named周期采样，最高173622KiB仅模拟器下界。实际已安装base.apk SHA与103626FC…一致，见fixed-corpus-installed-app-hash-20261025.txt。源码新增阶段和persistSeed抽取后的原阅读/收藏/反馈/排序/评价流程均在本轮范围。

本报告cold仅表示清理向量/精排SQLite缓存，不意味着设备重启/操作系统页缓存/首次模型解包的真正冷启动。warm现场仅为初筛向量复用，不能扩展成整App暖启动能耗。test_skip_fetch同时禁用主动预读，当前准备测试因此不能覆盖模型和主动缓存共同工作的资源门槛；后续完整阅读回放必须单独接入该场景。未连接手机、未发版。

后续实际100事件分页展示、6篇正文真实触摸展开/滚动/返回及逐条持久记录见 `fixed-corpus-reading-replay-20261025.md`。energy阶段现已接入脱机身份与资源采样；NONE、DUAL短时回放已通过。仍未替代30分钟受控刷新/登录/媒体/主动缓存并行和真机能耗协议，本报告先前“仅准备链”的限制按该独立新报告补充，历史原始证据不改写。
