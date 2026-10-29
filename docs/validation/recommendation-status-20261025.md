# 推荐工作状态与稳定列表验证（2026-10-25）

本轮在既有文字平台热门候选和本地双模型流程中补充准确的工作状态，不改变来源选择或当前显示快照。

## 实现

新增 RecommendationState（IDLE/RUNNING/PAUSED/REPAIRING/READY/PARTIAL），控制器对取消、资源暂停、修复、提交成功及失败分别发布状态。无可用语义结果不能宣称“部分推荐已准备”。过期提交和存储错误分支清理运行状态。已有 recommendationStatus() 详情接口保留。

首页副文案显示当前工作状态，预留两行、最小48dp，点击进入原有推荐详情。来源/稍后读页面保留各自描述。“候选顺序/适合我”依实际接收快照显示，新结果只提示接收更新，不使当前卡片跳动。

## 证据

全部报告位于 artifacts/local-ai/。

- 基线 recommendation-status-baseline-api36.txt：9项/7失败。
- 首轮 recommendation-status-status-final-api36.txt：10项/1失败。详情对话框测试在无障碍树出现前取根节点；改为有界等待真实窗口，未削弱生产交互要求，保留失败记录。
- recommendation-status-status-verified-api36.txt 与 recommendation-status-status-final-verified-api36.txt：各10/0。
- 最终加强帧验证 recommendation-status-frame-final-api36.txt 和 recommendation-status-frame-repeat-api36.txt：各11/0。实际控制器、本地两模型、独立合成候选，覆盖取消/资源暂停、当前快照与标签不变、待接收结果、详情入口、来源副文案保护。额外等待两次 postOnAnimation，验证副文案高度和列表屏幕位置不变。
- recommendation-status-paused-frame-final.png 已人工查看，实际显示暂停文案。旧 recommendation-status-paused.png 在属性更新但画面未更新时截取，显示此前READY，不作为暂停可见证据。
- discovery-ui-status-final-api36.txt：77/0；automatic-recommendation-status-final-api36.txt：8/0；platform-preference-status-final-api36.txt：15/0。
- feedback-fallback-status-final-verified-api36.txt：8/0；recommendation-recovery-status-final-verified-api36.txt：11/0。
- 默认字号浅深布局各31/0；layout-light-status-320-150-api36.txt、layout-dark-status-320-150-api36.txt 各31/0。320dp/150%浅深截图已查看，未见此次布局溢出，设置在finally中恢复。截图并未覆盖每种工作状态的大字布局。
- JVM XML合计412项/0失败/0错误；git diff --check及新增三份状态Java文件空白/冲突标记检查通过。

## 边界

模拟器Android API36结果不代表Find X7 Ultra全进程内存或耗电验收；未完成全量TalkBack验证。本轮未独立故障注入过期快照提交及存储失败分支。合成候选用于交互验证，不证明个人推荐命中。真实账号阅读、个人反馈留出集、真机资源及精确签名升级仍未完成。未提交、推送、发布或修改品牌。

后续补证：过期快照提交和SQLite插入失败现已通过实际模型完成边界独立注入，并发现/修复待接收结果的版本竞争；见 `recommendation-pending-versions-20261025.md`。此补证不替代本节其余未完成项。
