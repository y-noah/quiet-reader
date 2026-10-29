# 待接收推荐结果的版本检查与提交故障恢复

日期：2026-10-25。API36项目模拟器；隔离测试数据库、合成候选/偏好，实际本地模型、控制器、SQLite事务与首页交互。不代表用户个人兴趣效果或真机资源验收。未提交、推送、发版或更改品牌。

## 问题与修复

推荐写入时原有 `FeedStore.commitRanking` 会拒绝过期来源/画像版本；但用户浏览当前冻结列表期间，新偏好或来源可能在待接收结果生成之后到达。旧 `DiscoveryController.applyPending` 只检查控制器 generation，没有重新核对快照内的来源/画像版本。因此点击“查看更新”可能把旧偏好顺序当成新结果显示。

现读取待接收快照时，先结算当前有效画像，再核对来源与画像版本。过期或已丢失的快照不调用 `accept`，不修改当前卡片、排序标签和位置；清理过期待接收标记，通过原有 `prepare(false)` 重新准备。下一份有效结果仍需用户接收，不自动替换正在看的内容。没有改变模型、排序公式、平台偏好权重、来源开关或数据库格式。

丢弃过期结果本身使用IDLE说明，避免按时间模式（无需再运行模型）永久保留PAUSED提示；正在修复模型时保留原修复状态。普通存储/计算失败仍按原规则报告暂停。按时间模式的过期结果重建和最终标签/副文案已单独验证；模型修复并发不因此宣称新增独立故障注入完成。

此检查和既有 generation 校验共同工作；不声称跨多个独立进程的任意数据库写入与UI绘制具有全局原子性。用户点击时读取到的候选/画像版本、同控制器后续操作的generation校验，是本轮直接验证的边界。

## 故障诊断方法

新增 `RecommendationCommitProbe`，`recommendation-commit` 模式：

1. 在隔离数据库创建四个合成文字候选和一条明确内容偏好，启动真实 DiscoveryActivity 自动本地推荐。
2. 仅在实际模型完整语义结果回调边界，诊断包装监听器按同一串行存储队列安排故障，随后调用原生产完成回调；不伪造模型结果或直接调用控制器提交成功分支。
3. 存储故障：对测试库 `snapshots` 的插入安装SQLite `RAISE(ABORT)` trigger，让真实提交抛错。检查事务无残缺快照、当前首页不换/不改标签、退出整理状态，候选、反馈及已计算向量保留；移除trigger并通过原入口重试/接收。
4. 提交版本竞争：在模型完成后、提交前先记录新偏好，验证真实 `commitRanking` 拒绝旧画像并可恢复。
5. 待接收画像竞争：新结果已准备后再次记录偏好，然后点击实际“查看更新”按钮，旧结果不得上屏，自动准备当前画像结果。
6. 待接收来源竞争：再实际提交一轮来源快照，增加第五个候选；接受旧结果应被拒绝，当前来源/画像结果算完后继续保持待接收，最终显式接收的卡片必须包含新增候选。

诊断使用反射包装测试中的LocalRecommendation监听器，不增加生产测试钩子，也未修改用户数据库/偏好。测试异常与Activity结束后隔离库被删除。

## 原始证据

全部报告位于 `artifacts/local-ai/`。

| 报告 | 检查/失败 | 意义 |
| --- | --- | --- |
| `recommendation-commit-api36.txt` | 16/2 | 存储异常与提交前版本竞争本身通过；两项失败来自诊断把现有排序按钮标签误写成“为你排序”，已改为实际“适合我”，未改生产文案 |
| `recommendation-pending-baseline-api36.txt` | 16/1 | 修正诊断标签后，直接发现待接收画像过期仍上屏；这是本轮生产缺陷基线 |
| `recommendation-pending-fixed-api36.txt` | 20/0 | 接收时版本检查生效，覆盖存储故障、提交竞争、待接收画像与来源竞争及恢复 |
| `pending-version-recommendation-commit-final-api36.txt` | 21/0 | 加强最终卡片确实包含新来源候选，完整链路通过 |
| `pending-version-chronological-api36.txt` | 23/0 | 最终源码补按时间模式：过期结果重建不运行模型、不换当前卡片；接受当前结果后仍显示最新及正确副文案，无残留暂停提示 |

构建 `build-recommendation-pending-final.txt` 及最终 `build-pending-version-chronological.txt` 均成功，JVM416项、0失败/错误。

受影响回归报告：`pending-version-recommendation-status-final-api36.txt` 11/0（首页工作状态及布局保留）、`pending-version-discovery-supply-final-api36.txt` 16/0（分批来源供给/失败恢复）和 `pending-version-platform-preference-final-api36.txt` 24/0（平台反馈/筛选/详情/撤销）。本轮变更Java文件空白与冲突标记检查无问题。最后按时间状态调整另复查状态模式，见 `pending-version-status-chronological-api36.txt`。

## 限制与剩余验收

本轮SQLite插入异常不是实际整盘写满、设备断电或全部数据库I/O异常；合成反馈不是用户标签。真实多来源内容质量、登录权限、个人留出推荐效果、Find X7 Ultra内存/耗电与正常正式包版本递增升级/回滚仍未完成。已有R8包报告不自动覆盖本次新源码。
