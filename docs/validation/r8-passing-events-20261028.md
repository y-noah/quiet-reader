# 2026-10-28 称谓讣告规则R8完整流程

前轮生产claim-evidence-v12-passing-honorific已修复“刘延东逝世 / 刘延东同志逝世”前置拒绝，Debug真实双模型样本由2/8到3/8，其余漏点不变。本轮仅增加Android API测试分支PASSING，不反射生产类、不注入模型分数。

选取已冻结公开语料的精确两个完整候选，另保留既有独立品牌对与不同品牌单条，共5条。隔离非debuggable R8实际Controller→embedding→双向reranker→持久化分数/待接收→点击查看更新→3事件→长按手动拆分→4事件；逐成员身份保留、代表来自真实成员、热度不相加、不产生READ/QUICK_EXIT/MORE/LESS偏好、旧快照保持或正常有界淘汰均检查。

两轮各25/0、唯一raw -1，第二轮先force-stop旧进程。App与driver实际安装SHA256分别匹配归档，App59add7ed93f6c4ce5826bb3a2c890690f0cb54867ba08069b2658fe008391f55。全部日志及包在artifacts/local-ai/r8-passing-events-20261028。AVD关闭保留userdata。

这是固定开发样本的生产混淆流程回归，不是独立去重质量、全292候选预算、个人收益或ARM内存证据。未放宽双向7.0/完整输入/时间/冲突保护。后续原签名候选和真机见webview-teardown-20261028.md、phone-evening-20261028.md。
