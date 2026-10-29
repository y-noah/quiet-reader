# 知乎连续回答预算与来源释放

日期：2026-10-25。当前生产改动的本地及 Android 16/API36 受控验证；不是最终发行或真实账号全文验收。

## 修复范围

AnswerStream 原本每次续读有请求时限，但跨批累积的回答没有总量保护。新增事务式 mergeWithinBudget：先在副本合并，累计回答与已过滤身份超过 600，或超过既有单篇正文 2MiB 估算/JSON序列化预算时，拒绝整批，保留之前的回答、段落与获取身份。恰好达到600时接收并停止继续追加。单篇限制不等于恢复已取消的 App 总ROM上限。

DOM与API合并都经过同一保护；拒绝的API批次不会推进nextApi。达到限制返回一次结果并关闭来源WebView、停止解析器和延迟回调。ReaderHtml显示限制说明与单个来源入口；MainActivity直接调用loadMore也不会创建新的AnswerStream。AnswerStream.copy补齐loginRequired与sourceUnavailable状态，避免副本漏掉来源状态。

## 验证

新增 AnswerBudgetTest 5项，覆盖正常追加/长文替换、原获取metadata与状态、跨600整批拒绝、恰好600及过滤身份计数、正文内存预算、控制字符造成的仅序列化超限、状态JSON往返、单一来源按钮。全量JVM447/0，读取app/build/test-results/testDebugUnitTest/TEST-*.xml汇总；新增类5/0。

实际DiscussionFlowProbe增加6项：600回答缓存进入MainActivity后的DOM及直接loadMore保护；599回答+真实WebView快照2条新增回答时整批拒绝；一次回调、closed、解析器shutdown、无API请求/原metadata不变及关闭后重复续读无回调。沿用V2EX/贴吧/虎扑续读、位置、缓存、失败/后台取消等原检查。最终67/0：artifacts/local-ai/zhihu-answer-budget-final-20261025-072207.txt。

首轮67/3与诊断轮67/3保留在zhihu-answer-budget-flow-20261025-071935.txt、zhihu-answer-budget-diagnostic-20261025-072053.txt。原因是测试loadDataWithBaseURL(historyUrl=null)的WebView.getUrl为about:blank，正式同问题保护正确拒绝，未进入预算合并。测试改用shouldInterceptRequest提供受控HTML并loadUrl真实问题URL，未改生产URL保护。最终确认limit/599、一次回调、来源销毁、解析器关闭。

实际存储回归158/0：artifacts/local-ai/zhihu-answer-budget-storage-20261025-072301.txt。普通debug/store诊断构建与原签名覆盖安装成功；未清数据、未卸载。

## 未证明事项

API分支已接入相同合并函数且顺序在推进游标之前，但本轮未单独构造API超量响应集成测试；DOM集成测试只证明拒绝前无API请求。2MiB预算扫描与副本仍在现有主线程合并路径，本轮未量化边界耗时，不推导省电或整App峰值。真实登录知乎、完整补充/回答权限与账号切换、Find X7 Ultra峰值RAM/功耗、个人推荐效果及最新精确R8发行验证仍待完成。旧R8证据不能证明当前代码。未提交、推送、发版或修改品牌。

## 后续：真实WebView接口分支

新增AnswerApiBudgetProbe，以shouldInterceptRequest为同源XHR提供受控JSON，保留正式AnswerStream的more→DOM三次检查→requestApi→pollApi→ZhihuAnswers.parse→预算合并完整路径，未反射调用合并或直接注入回调。每轮API提供2条新回答及有效的下一页游标，分别从599、598、1条旧回答开始。三轮各6项共18/0，日志artifacts/local-ai/zhihu-answer-api-budget-20261025-072451.txt。

检查生产XHR确实只请求1页；599+2整批拒绝且保留599，598+2接收600并停止，1+2正常追加且保持源可继续使用；只有正常可继续批次推进nextApi，两个限制场景不推进；各一次回调、旧Section对象和metadata/缓存获取时间/会话保持，限制时WebView关闭且parser shutdown。此证据关闭上文“未单独构造API超量集成”缺口，仍不证明真实账号权限或真机功耗。最新生产代码未因这次测试改变。
