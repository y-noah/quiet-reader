# 2026-10-28 R8 真实公开贴吧时间与图片楼层

## 验证范围

实际非调试、混淆 App，隔离包名 app.quietreader.discoveryprobe，仅 emulator-5560。公开帖子11139792603：独立来源网页 -> 正式包实际采集WebView -> 正式阅读页 -> 正常Home暂停保存的历史。没有生产类反射、结果注入、合成正文或写入时间/身份。每次使用唯一查询参数排除旧历史；bodyAt必须晚于本轮打开时刻。测试只导出数量与判断，不导出源正文、账号或Cookie值。

新测试 ReleasePublicTiebaTimesProbe 仅依靠Android View/WebView API、公开根Vue2元数据与正常SharedPreferences历史。source-session来源模式在隔离环境临时设置，结束恢复；手机没有操作。

## 结果与比较边界

public-acquisition.txt：15检查/0失败、唯一raw -1。独立来源14条；实际采集WebView8条；正式阅读11区块/8楼中楼，无横向溢出。实际采集8条全部进入正常历史，同时与独立来源的真实id/pid/time逐一核对，两个父层关系、两张父层图片与8条精确来源创建时间一致；父层没有借用子层时间。登录续载门槛保持。

独立来源与实际采集窗口差6条，不能用不同虚拟窗口的总数直接判断解析丢失。最终测试不仅核对阅读页已有行，也捕获正式包实际采集窗口，要求其中所有观察到的子回复全部进入历史，无未匹配行。此证据覆盖本次窗口，不能证明后续完整分页、真实受限账号或所有帖子的适配。

public-round1.txt 保留首轮失败：错误地要求阅读页数量达到独立来源14条，未观察正式采集窗口。最终补上同一正式App的采集窗口观测后，证实该窗口8条且全数保留。App SHA256始终相同，未改产品来迁就测试。initial-driver-compile-failed.txt保留测试驱动Java字符串转义失败，修正后通过。

## 包与状态

App SHA256 0875bb51c796f3ac67b796bc9e1b8eb66f402c628ae363e64e1848af8862277d；实际安装App/最终acquisition-test.apk哈希核对见installed.json。BuildConfig/mapping及原始日志归档artifacts/local-ai/r8-public-tieba-times-20261028/。本App是隔离诊断构建，不是普通安装候选；后者另有原包名、原签名、关闭测量及升级检查。

模拟器关闭保留userdata，终态见cleanup-final.json。手机未操作、未发布。后续普通候选见ordinary-tieba-rendered-20261028.md；真实登录/分页、推荐收益与独立去重质量、ARM峰值/拔线能耗/长期缓存仍开放。

后续正文采集视口改进：见 [桌面CSS视口对照与生产验证](tieba-viewport-20261028.md)。采集窗口由8条增为13条，全部实际窗口回复仍精确保存；独立来源14条中1条未进入该窗口，未声称完整分页通过。当前普通候选见ordinary-tieba-viewport-20261028.md。
