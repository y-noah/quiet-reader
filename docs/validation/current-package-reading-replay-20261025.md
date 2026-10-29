# 当前合并修复包连续浏览复核（2026-10-25）

## 包身份与范围

App SHA256：36B163E4D0B06E133738AEAB08FAD31D6456DEE398555671AD2B9E17B8C0FC33；驱动：6A84A034EB68BF4C228727700081DB50035E567D24D0B706F7159DFA152E3344。运行前核对实际安装base.apk，结束后核对实际驱动，均与race-fixed-release归档一致。没有重建、替换生产源码、操作手机或发版。

本轮弥补模型校验复用/接纳竞态修复后尚缺当前精确包100/6连续浏览的证据。API36 x86_64模拟器，DUAL/cold、60秒回放、普通在线主动预读、verifyPrefetchDiskReuse=true。cold指向量/精排缓存初始状态；正文为预置合成磁盘正文，不是100篇真实下载。列表位置通过真实ListView.setSelectionFromTop控制，不等于手指滚动帧率验收。模型首次完整文件校验并未强制清除已有记录。

## 终态与独立核验

current-online-dual-a.txt已终态9/0，实际命令exit=0。逐条operations.ndjson由主机summarize_operations重新核验：100不同事件、顺序/调度/时间线、六次实际卡片打开/展开/滚动/返回、每次无错误、唯一完整结束均通过，elapsedMs=62749。进入首篇前preparedEvidence=1；101实际向量输入/6精排对，显式计算窗口6。窗口数量属于100候选夹具，不与6候选核心的1窗口混称。

100个初始正文文件inode均保持（original=100/unchanged=100），普通预读和六篇阅读未重复原子替换它们。不能据此宣称未发生任何其他磁盘写入。App/驱动身份、原始日志、命令exit、操作NDJSON及严格current-online-dual-a-summary.json位于artifacts/local-ai/model-load-breakdown-20261025。

## 性能及开放边界

首篇Activity观察7460ms、正文DOM观察9939ms、稳定控件10097ms；之后正文观察4571、1528、1564、1595、1375ms。均为仪器观察点，不是屏幕帧呈现时刻。首篇仍明显等待，未关闭性能问题，也不将模拟器数据推导为Find X7 Ultra结果。没有全App资源/电量采样；wholeAppPeakVerified与energyComparisonVerified均false。

受控合成正文不能证明真实账号全文、权限、本人推荐收益或长期来源新鲜度。最终普通升级、真机1GB峰值/非充电耗电/发热、空间提示与完整辅助功能仍开放。终态收集后停止模拟器隔离flowprobe并确认无主进程；手机未操作，无提交、推送或版本变更。
