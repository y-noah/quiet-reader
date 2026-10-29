# 首页精简与品牌更新（2026-10-25，验证进行中）

用户最新决定覆盖旧需求：首页删除稍后读与分类、卡片序号和行内排序解释，顶栏集中刷新/更新/设置；名称改为New，Logo依据用户白色牛头表情重新绘制，不能嵌入模糊截图。尚未更新用户手机，其普通47仍为之前News版本。

DiscoveryActivity已删除大标题/状态块/兴趣横幅/分类条/底部导航和保存菜单，保留标题摘要来源时间。更新按钮固定48dp槽位，暂无更新时INVISIBLE以免挤动列表。兴趣选择、模型详情、阅读顺序及来源管理在设置中可达。卡片操作索引随移除稍后读同步调整，历史收藏/反馈数据不破坏删除，历史SAVED反馈仍可撤销。默认不再为不可见稍后读页查询列表。

DiscoveryController读取旧快照时剥离分类条件、重新生成保留来源条件的快照，并保持锚点；显示阶段同样不应用旧分类。内容/平台偏好及内部类型证据不删除。source筛选仍是设置中的可选操作，不恢复主页分类导航。

首轮debug构建/JVM469/0。实际API36专用emulator-5562：411dp/100%/浅色49/0，320dp/150%/深色49/0。每轮真实设置→兴趣选择（滚动/3个操作按钮）→来源管理→返回、48dp触控/文字布局检查通过。夹具故意保存all|SCIENCE旧过滤，实际新激活快照filter=all且60条候选均保留；首页无序号/排序说明/分类/大标题/稍后读。截图已人工查看，未见裁切；模拟器尺寸和font_scale已恢复原值。以上首轮品牌仍News，后续New/新图标需按新构建补证，不把旧截图当新图标证据。

ReleaseReadingFlowProbe正在迁移至无稍后读入口：核心正文缓存/证据恢复改走推荐卡片、独立核心夹具关闭预读（在线预读仍由专门回放验证）；不得去掉缓存inode/正文展开/返回/偏好更新断言。更新按钮通过实际contentDescription可达，模式检查使用真实快照身份并核对可见卡片，不依赖已删除的首页文字。新R8尚未构建运行，不能沿用旧37/0。

New牛头为原创矢量，参考形状而不复制截图；奶白脸/灰耳/浅色角/肉色鼻子，暗灰绿色底，提供API33+monochrome。SVG设计源docs/design/new-cow-icon.svg。名字资源及下载/视频提示中的News改为New；现正编译实际Android图标预览。无公开发版、推送或手机安装。

New/新牛头资源debug重新构建成功，JVM469/0；411dp100%浅色新包实际49/0，Android绘制new-cow-icon-preview.png已人工查看，使用自适应圆形mask无红底、无模糊图片，首页截图名称New。新的R8 flow包已成功构建并归档new-compact-release-20261025，核心回归启动中，结果待补。


新R8 App SHA256 884D10E017CC900A576F52FCF3F90DF4D9839BCDE26C66218DC426556740DD8D（319416490bytes），驱动B095D36EAB137C0881A479F3853894B0674089D7A57F54F01C3C7CEB665031E8。new-compact-core-20261025-a已启动reading-core，watcher26340/startTicks7685789；后续核对实际存活，当前未终态，不计通过。布局专用5562已正常emu kill，尺寸/字号此前已恢复。牛头PNG为Android实际AdaptiveIconDrawable渲染的432px预览；App本体使用108viewport矢量，未嵌入用户模糊截图。


新R8核心a COMPLETE3/1，Personal snapshot/rerank cache timeout，143804ms。失败及独立采样已收集；停止后合成SQLite/WAL已留存。实际meta source20/profile20，active768 cold、769 personal，6向量/0精排对、仅MORE sample一条，证明有初筛结果但本轮未完成精排，不宣称新包核心通过。新核心夹具关闭预读后，一次有界整理窗口不足以满足旧waitPersonal要求。驱动改为只在新有效快照提交后通过真实继续整理操作继续，最多4窗口、仍原60秒总体等待，生产15秒/分片2秒预算不变，未修改排序/模型/资源保护。新驱动已归档driver-b，同生产APK884D10E0…散列一致；new-compact-core-20261025-b运行中（watcher28872/startTicks7732051），不能计通过。

## 核心b终态（替代上段运行中状态）

复查同一watcher身份后，设备已落盘COMPLETE，37项/0失败，147322ms；独立watcher输出、20轮原始样本及资源汇总均已收集。生产App仍884D10E0…，驱动为17ACBE29F9B231D684098DA1BE1012B4B510976FF6122FE652F6FC0C268F82AF。两次CORE_EXPLICIT_WINDOWS均为2；实际向量与精排缓存、显式接纳个人快照、卡片无稍后读、正文展开/缓存文件不重写/语言证据恢复/滚动返回、内容反馈与真实平台来源记账、新反馈待接纳和新候选评价链均通过。合成样本排序变化不证明本人愿读收益。

20/20 named PSS样本有效，采样最大644607KiB；仅模拟器命名进程采样，wholeAppPeakVerified=false、energyComparisonVerified=false，不用其宣称真机全App峰值或耗电达标。证据目录artifacts/local-ai/new-compact-release-20261025，文件前缀new-compact-core-20261025-b。

主需求及两个增补文档已在顶部记录用户最新首页/品牌覆盖，防止原型旧分类、序号或稍后读约定被误恢复。DiscoveryUiProbe/RecommendationStatusProbe已迁移并编译；首轮discovery-ui-a在设置末尾“筛选来源”未滚动而失败13/1，保存原始失败，测试补真实ACTION_SCROLL_FORWARD后待复测。没有把失败改为跳过，也没有改变生产过滤/反馈断言。推荐状态与在线预读新包回放正在独立设备运行，未终态不计通过。

## 在线预读短回放终态

new-compact-online-20261025-a同一句柄已COMPLETE，7/0、163777ms；设备结果、独立watcher及原始操作/样本日志均已收集。严格host校验readingReplay.passed=true、operationCount=100，100个不同事件逐项显示、6次真实卡片打开/展开/滚动/锚点返回，三次连点只打开一次。DUAL实际101向量/6输入对，读取前preparedEvidence=1，证明普通在线预读在首个点击前恢复了缓存正文的版本证据。不是100篇冷网络下载证明，夹具正文仍为合成磁盘缓存。

回放操作区间62575ms，网络uncontrolled、activePrefetch=true；23/23 named样本有效、采样峰值570945KiB，非完整App真机峰值/能耗。首篇Activity/正文/稳定控件观测6864/8339/8592ms，仅主机instrumentation观察，不能作为首帧耗时或宣称打开即达；其余5次亦逐条记录，保留该慢首篇证据以供后续分析。此前手机index8失败在模拟器本轮未重现，不能据此关闭手机问题。

## 设置推荐状态迁移结果

RecommendationStatusProbe首轮a为8/1，实际model completion超时；该debug包未包含模型资产，隔离files/local-models只有未就绪目录。使用-BundleModels重新构建和安装后，b为12/0，验证取消/暂停不遗留运行状态、顶栏与列表位置不变、48dp设置入口、实际详情打开、准备结果待接纳、接纳后的真实模式、资源暂停保留已选个人快照、来源页返回按钮不被推荐回调覆盖。没有通过放宽完成条件解决该失败。

discovery-ui-b仍13/1，补滚动未解决原生TextView条目的ACTION_CLICK失败；驱动现对真实可见且与活动窗口相交的文字条目补触摸回退，并记录visibility/clickability/scrollability/bounds。c待终态，不能把测试动作修正本身算作生产问题修复或通过。遗留收藏通过直接种入旧格式数据验证清理保留，不恢复已移除的稍后读UI。

## 来源快速重建竞态：生产修复与定向回归

discovery-ui-c终态76/1：设置实际触摸已贯通，但选择知乎后重建时sourceFilter未保留，其余75项通过。filterSelection在主线程先更新已选来源，冻结快照提交/接纳异步执行；onStop递增feedGeneration后，旧任务回调不会接纳该快照，新控制器只读上一个active快照，因此会恢复旧来源。这是实际配置变更窗口中的状态丢失，不能仅等待更多时间让测试通过。

DiscoveryActivity在onSaveInstanceState保存已选sourceFilter，新DiscoveryController优先读取该恢复来源；没有恢复值时仍按持久快照加载。旧快照来源不同则重建；恢复值仍剥离旧分类，不复活分类导航。既有公开构造路径委托到新增参数，不改变其他调用方默认行为。此补丁覆盖Activity配置重建/系统提供的saved state，不声明无saved state的突然断电可保留尚未提交的操作。

discovery-ui-d刻意阻塞真实storage队列，在筛选提交未完成时选择知乎、保持第30条锚点并重建，释放队列后验证来源/锚点及无分类恢复；77/0。亦覆盖清理推荐缓存保留旧收藏及反馈、冻结排序、刷新待接纳、归组入口/拆分撤销、20篇串行预读、慢请求结束、热限制/迟到回调、读页接管、未认领超时、低空间门槛与兴趣选择重建。不是账号权限、真实网络吞吐或本人兴趣质量证明。

本轮debug App CDA8C58CE3BE207FDE34B17D5996474322CC74914A9583C2FA7DF2A568D8731D，驱动63F51E6CA3C28AFAD3E4611F37EA01572F33601F9B5F6B87B74D1F855346586D；构建与JVM469/0/0/0。原始失败a/b/c及通过d均保留于artifacts/local-ai/new-compact-ui-20261025。

## New窄屏大字号深色复核

首轮new-layout-dark-320-150为49/3；截图实际是尚未载入卡片的空列表，未记录CLIPPED HEIGHT/WIDTH。旧驱动以固定500ms启动延时和measured>5共同判断文字高度，过早检查了异步迁移与卡片数量，未证明存在文字裁切。改为最多10秒等待真实60条卡片就绪与active快照持久化再做原有几何/文字断言，不延长生产加载、不移除旧分类清理检查。

新布局b在API36、320dp、fontScale1.5、dark=true，49/0；迁移后filter=all且60条保留，触控48dp、文字宽高、兴趣对话框首尾/三个操作、来源管理与返回均通过。模拟器显示参数在finally恢复；实际截图已保存供人工检查。此为当前debug精确构建，不沿用旧News截图。

包含来源恢复修复的R8已本地编译并归档artifacts/local-ai/new-source-restore-release-20261025，App89C64F439843478C46E86A3DE6BF7769C429737AAE18D4001D0490F16506B875，驱动AB33BE074E2F7D476EF26697779991D90F4C8C3CCF15A43CE4051C5790D650FF。与此前884D10E0…不同，不能将此前核心/回放结果自动归给新包；其阅读核心回归待终态。无手机操作、提交、推送或公开发布。

## 来源恢复修复版R8核心终态

source-restore-core-20261025-a复查同一watcher5569/startTicks7863277，已COMPLETE37/0，192415ms。独立watcher输出、24轮原始资源样本、设备终态与严格汇总已收集。实际安装base.apk哈希与归档89C64F43…一致；初始整理3个窗口、反馈后2个窗口，在驱动既有上限内通过；不将模拟器冷模型耗时说成手机性能。

新精确包验证真实本地两模型缓存、冻结列表/显式更新、正文缓存文件复用/展开/语言证据/滚动返回、真实平台反馈归属及后续新候选评价链37项。functionalPassed=true；24/24 named样本有效，采样峰值497172KiB；wholeAppPeakVerified=false、energyComparisonVerified=false。此前在线100/6证据仍只属于884D10E0…，本轮没有伪称89C64F43…也跑过该工作负载。

New窄屏深色最终截图已人工查看：有真实60条合成卡片、无裁切；首轮错误截图确认为迁移前空状态，不能把等待修正解释成生产文字裁切修复。5560显示已核对恢复1080x2400/density420/font1.0；5562本轮临时启动的模拟器已正常emu kill，5560隔离flow应用在终态收集后已force-stop。用户手机未操作。个人兴趣效果、真实登录全文与全App真机峰值/耗电仍开放，未发版。
