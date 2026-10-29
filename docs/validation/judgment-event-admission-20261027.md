# 新公开窗口及明确受贿判决准入 / 2026-10-27

## 来源复核及打分前冻结

生产 HotSourceCollector 在指定 API36 隔离模拟器上进行一次有界文字热门来源采集，completed=true、storageErrors=0、code -1。九个 READY：知乎30、微博50、虎扑35、财联社20、IT之家12、澎湃19、BBC中文10、百度50、贴吧30，共256条。V2EX为LOGIN_REQUIRED/0条，提示来源要求登录或限制访问，不判定为个人账号状态，也不据此删除该来源。无登录、验证码或手机操作。

证据 `artifacts/local-ai/text-source-audit-20261027/`。corpus.json SHA256 `64efd7b0dddab0102fce0e5bd3093d2b41879cc99e5b5ebc9dacc8ae39261894`，capturedAt1793095986496。与上一255条窗口按来源+精确标题比较，151条重合、105条未见于旧窗口或已打分夹具。该比较不是105个独立新事件。旧窗口中已有同版iOS报道，本轮多条标题仍涉及原有争议。

选定16对，6同事件、6不同事件、4不确定，全部原始候选字段、判断理由在实际INT8打分前固定。资产 `fixtures/observed-event-later-window-20261027.json` 与 labels-before-model.json 相同，冻结 SHA256 `8eac9ae8e2ed0f23b808495a44e94f23a059899f229b97031c20373b8d525cd8`。freeze-before-model.json / freeze-after-model.json / comparison.json 核对语料和夹具字节、标签及模型输入元数据。最初主机核对脚本漏写read_text UTF-8参数而报GBK解码错误，随后显式UTF-8成功；没有重标或重写夹具。

选样不是随机或专家审定，三对判决样本来自同一事件的三个来源，iOS及争议样本与旧事件相关。不是总体准确率、语义独立认证或本人兴趣标签。用来修规则后即为开发回归，不再声称独立质量成绩。

## 修改前真实模型结果

既有 EventFreshQualityProbe 仅新增测试入口 event-later-window-quality：真实512维embedding、双向INT8 reranker和生产分组。修改前同事件0/6、不同0/6、不确定0/4合并，planned=0；2项标签范围及拒绝误合并检查通过/code -1，不能说16/16质量通过。

三对判决原文明确姓名、4.36亿余元受贿金额及死缓结果，标题只省略职务或一审，双向较低分分别10.2736、9.2864、8.5888，均因缺少已知动作准入而未规划。另三对iOS更新/汇总、皇马吧声明短长标题、A股4900股下跌长短收评的较低分为5.3954、6.4632、4.8014，本轮不通过降低7.0阈值解决。

## 生产改动与保护

EventClaimEvidence版本升为claim-evidence-v8-corruption-judgment。仅识别完整断言句式：受贿金额（保留万/亿、余、元）+明确姓名（可带已识别职务）+可选一审/二审/再审/终审+被判死缓/死刑/无期徒刑。没有写死具体姓名、平台、金额或案例。

姓名、金额和单位、判决结果必须相同；双方都有职务或审级时必须字面相同。缺省职务/审级只是不矛盾，未补造事实。屏蔽匿名泛称、拟/计划/疑似/否认/未被、问题句及额外说明等不完整句式。双方有一方不满足结构则不能由这一分支准入。时间、全部数字、已有事实/极性检查仍执行；不同姓名、金额单位、结果、已知审级或职务不能被高模型分数越过。

得到CANDIDATE只允许既有有限模型复核，不直接合并。双向7.0、完整模型输入、24对和时间预算、代表来源原热度、冻结快照及显式拆分规则不变；原文和模型输入没有删数值或改写。特征仅在Prepared中每个候选解析一次；不以受贿开头的标题先用前缀跳过，正则预编译，没有增加模型、服务、后台轮询或云端接口。未以PC结果宣称ARM能耗已通过。

两项新JVM测试先2测1失败，原XML和日志保留；修复后全量662项0 failures/errors/skipped，Debug/测试包及Debug/Release Lint通过。覆盖省略角色/审级、两字姓名，以及姓名、金额单位、阶段、判决、角色冲突、匿名/不确定/问题/额外说明和“准入不等于合并”。

## 修改后及剩余漏项

固定同一标签和原始元数据复测：同事件3/6、不同0/6、不确定0/4，planned=3，2范围检查/code -1。三个新增合并均为同一判决事件的相关配对，收益事件族只有一个，不能写成解决三个独立事件。标签和元数据前后逐项相同。

模型结果为 model-result-before.json / model-result-after.json，比较为 comparison.json；另外三对保留在 remaining-misses.json。不放宽分数门槛，不将未合并改标为不同事件。其他既有残余漏项仍开放。

Debug修改后App SHA256 `5bcd9585f4836ace0f297b2bedb184dff6768a2e2a97c51e3a361d2236f085da`，驱动 `956217497f1bb4c84e76a16e4862198b017effbceccbbdb36ddc16bc3b944e80`；原窗口打分驱动 `805f6b58b09bc39dd49e8c8df3356d1fdea77dab65d3cfa272c7bede64977a8d`。对应明确install -r日志，不将后续R8拉取哈希混称Debug安装哈希证明。

## 真实公开贴吧边界

同一轮 tieba-live-replies 使用现有公开帖子流程5/0、code -1：实际取得帖11137152004、3个稳定回复身份和可转交的已加载来源。游客来源返回登录限制，自动续读停止并保留可读结果；这不能证明登录后的继续增长或真实楼中楼关系。日志 tieba-live-replies.txt，真实账号及手机门槛不关闭。

## R8完整操作及普通候选

隔离压缩构建新增仅测试judgment-events入口，复用原固定事件流程，将原播出对替换为冻结判决长/短标题，其余终止合作对和独立品牌保持。实际双模型从五个冻结条目准备两个组，用户接受更新后变三事件，保留五个来源身份及代表原热度；实际菜单拆开判决组后变四事件，另一已接受组保持，未凭空生成阅读兴趣。

证据 `artifacts/local-ai/judgment-r8-20261027/`：新判决分支24/0、旧播出句式24/0、阅读核心40/0，共88/0，各code -1。R8测量隔离App SHA256 `40a391044114793bf7aec530aa6ba1cff93b156795190678b05e2f1ea345b95a` 与实际拉取APK匹配，mapping/BuildConfig及构建终态归档。这些是开发操作回归，不是独立质量或ARM资源证据。

新普通候选 `C:/Users/10197/Documents/Codex/New-next-trial/New-20261027-c050a471.apk`，319449810 bytes，SHA256 `c050a4712b2b9e856d03b96d3f127d5abbd1164fe890b599d8ffad4d229fab9e`，app.quietreader /47 /0.4.0-upgrade-probe /target36，DEBUG=false、RANKING_MEASUREMENT_ENABLED=false。原签名证书SHA256 `ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37` 验签通过。

证据 `artifacts/local-ai/ordinary-judgment-20261027/`：模型及清单精确匹配、64位ELF16KiB LOAD检查、全ZIP成员可识别明文凭据扫描通过。production-markers.json确认DEX有v8且无旧v7标记，无测试fixtures或p12。专用quiet-reader-upgrade-api36/emulator-5562保留b78691eb/schema7数据，前12/后12/实际双模型与界面19/重复12共55/0，各code -1；-r覆盖，无卸载、清数据或重种旧状态。收藏原文、字体外观、来源会话、阅读位置及展开、合成持久Cookie保持，模型服务退出。实际安装APK拉取匹配，安全目录current-candidate.json更新。

普通55项证明升级和模型主路径，新增判决操作分支由同生产源码隔离R8验证，不混称普通包分支全覆盖。公开诊断导出已归档并按明确路径清理，测试AVD关闭、userdata保留，无手机安装、上传、推送或正式发布。

## 仍开放

V2EX当前网络访问限制、其他保守漏项及独立新事件质量、真实虎扑/知乎账号正文评论与贴吧层级、用户连续推荐收益、Find X7 Ultra整App RAM≤1GB、非充电耗电温升、真实长期磁盘和完整预读、完整TalkBack/全部页面视觉。未据分项测试关闭整体目标。
