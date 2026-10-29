# 冷画像事件去重：首页接纳及版本变化（2026-10-26）

新增 EventControllerProbe，通过真实 DiscoveryActivity / DiscoveryController、普通 LocalRecommendation、RankingService、SQLite 与首页更新圆点完成端到端验证。仅独立 emulator-5560 / app.quietreader.discoveryprobe；不改生产类权限、不注入模型输出、不直接伪造pending或替换列表。

## 数据及检查

重放已保存的12对24条真实候选，未添加兴趣画像。该数据已参与规则开发，按回归集处理，不声称独立质量留出。三种场景均从独立随机库开始，关闭来源抓取，仍运行真实模型；候选抓取时间/事件元数据沿夹具，不伪造发布时间。

1. current：原始24行可见，Controller完成去重后23行留在pending；原列表/活动快照保持。实际点击圆点后显示23行，mode仍cold，没有虚构偏好。
2. profile-change：实际23行结果准备后，在存储线程新增SEPARATE反馈。点击圆点拒绝旧偏好版本，不移动可见卡片；重新整理后再次实际点击，显示24行，尊重拆分。
3. source-change：准备后在知乎来源加入一条明确标注合成的独立测试候选。点击圆点拒绝旧来源版本；重新整理并接纳24行（原24+合成1-事件合并1）。这条合成候选只验证版本和数量，不作为来源覆盖或推荐收益证据。

每种场景还检查SQLite活动快照的来源/偏好版本与当前一致，旧24行快照载荷冻结。没有手机安装、普通包升级、提交、推送、默认版本或公开发版。

## 证据及边界

build19-controller.txt 构建成功，严格最终JVM XML91份544/0/0/0。controller-first.txt、controller-second.txt 两轮均14/0；既有pending-acceptance-race在更新输入覆盖包回归15/0（pending-race-coverage.txt），覆盖更新点击读库期间新pending取代旧pending及当前/偏好/来源版本场景。事件输入覆盖/普通链路证据见 event-input-coverage-20261026.md，当前APK与测试APK哈希见 coverage-controller-summary.json。

本项关闭的是该实际回归窗口的冷画像Controller pending接纳、偏好拆分及来源版本变动检查。它不证明全部语义等价质量、触摸流畅度、真实个人推荐收益、全部账号全文、ARM整App峰值/发热/耗电或旧混淆包并发9.9秒问题已关闭。

所有日志位于 artifacts/local-ai/event-verification-plan-20261026/；每个测试随机库在结束后删除，独立应用结束后停止。

