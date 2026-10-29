# Find X7 Ultra：正常推荐控制器与连续浏览资源 — 2026-10-28

本轮从短输入直接模型请求推进到实际正常Controller的100候选流程。仍是同生产源码的隔离R8 app.quietreader.flowprobe，测量模式开启；不是普通用户数据/真实兴趣收益或网络冷下载验收。普通手机App f0保持不变。用户整夜授权，无密码唤醒后测试。

## 范围与方法

主机 measure_device_probe.py 增加两个固定隔离phase：controller-prefetch映射energy-setup，controller-reading映射energy-replay/60秒。都固定DUAL、cold模型输出、activePrefetch、autonomous、verifyPrefetchDiskReuse与energy-replay-100-v2。只允许flowprobe目标，不在普通包注入数据。使用既有公开标题/开发者画像、100份合成磁盘正文；“cold”清的是隔离测试向量/分数，不指网络正文或后续轮模型文件冷安装。首次本轮隔离包安装前查询为空。

每次都由首页普通控制器自主完成，不调用手动继续整理；检查source/profile精确当前版本、personal快照、实际完成状态、102个唯一输入向量及42个rerank缓存。未接受pending前可见顺序保留；完成后模型进程退出，15秒/100ms离散检查无模型进程或模型缓存增长。普通预读必须在任何阅读前取得准备证据并保留100份原文件inode；第一轮准备40份，最终浏览轮准备100份。预读从已有磁盘正文恢复，不能称冷网络缓存成功。

PSS采样间隔参数0.5秒是查询结束后的等待，真实查询另耗时。每份以activity processes名册→meminfo→名册核对同PID/name/UID/startSeq及精确packageList，完整归属包括主进程、ranking和当时所属隔离WebView。未归属系统/GPU共享服务及采样间隙仍是盲区；顺序PSS也不是同一时刻瞬间总量，不能将各进程VmHWM相加。

## 原始轮次

| 前缀 | 检查/失败 | 主机观察秒 | 完整/不完整归属样本 | 最高完整PSS KiB |
|---|---:|---:|---:|---:|
| arm-controller-cold | 13/0 | 29.782 | 25/3 | 727427 (710.38 MiB) |
| arm-controller-reading | 11/1 | 32.218 | 28/2 | 755149 (737.45 MiB) |
| arm-controller-reading-navigation | 11/1 | 31.656 | 27/2 | 682155 (666.17 MiB) |
| arm-controller-reading-observation | 15/0 | 88.062 | 73/3 | 657131 (641.73 MiB) |

第一轮cold：13/0、raw code -1，正常自主完成约11.325秒；最高完整样本727427KiB，含main82283、ranking618754和WebView26390。终轮15/0、raw code -1，自主完成约9.040秒，随后100次ListView视口/6次实际卡片打开、真实触摸展开、正文滚动、Back回到保留锚点。浏览操作60.800秒，完整operations102行（identity+100operation+complete），通过既有summarize_operations逐项验证身份/顺序/时间/全部passed。视口通过setSelectionFromTop，不是手势流畅度或帧率证据。正文合成，不是真实站点图片、登录或长内容。

四轮最高完整样本755149KiB（737.45MiB）发生在失败浏览轮，不能删除失败轮挑较低结果。所有已采样值未超过1GB，但不能把它升级为全App所有实际场景/连续峰值已达标。普通包测量开关为false，本隔离开关true，亦不能当作普通包全量验收。

## 两轮失败及观察修正

最初reading 11/1/code0，在index0真实触摸展开后replayDom的evaluateJavascript未收到3秒callback。只保存一条失败operation，不能称浏览完成。下一轮仅增加导航诊断，仍11/1/code0：pageChanged=false/currentOwnRender=true，故“只在URL变化时重试”的条件没有解决问题。

最终只改测试观察：App自有render页没有callback记作无观察，在原调用方的有界15秒正文/5秒展开期限内重试；错误来源仍直接失败。通过依旧必须实际读到sentinel、展开控件aria-expanded=true和第3段正文，并实测可滚动及回到原列表位置，绝不将超时等同成功。最终记录3次无callback后完成全部100/6；这证明严格结果可被后续观察，不证明先前每次callback缺失的唯一原因。生产存在异步JavaScript作用域切换和render导航，但未追认其为已还原根因，也未改产品实现迎合测试。失败原日志和两份单operation保留。

一次本地主机编辑脚本使用默认GBK读取UTF8失败，未改文件；arm-controller-navigation-build仍构建旧驱动。修正明确UTF8后navigation-final-build成功。observation-build为最终测试Java成功构建。未将错误编辑后的构建称为含修复。

主机command构造/有界采样/停止及进程归属相关51项回归通过，见arm-controller-tools-regression.txt；不累计旧87项为本轮数量。

## 实际包、环境与清理

隔离生产App e1d4fe13c0ad41c7971bab41332b32e01658e4496b1ce07bec18a2679eb55009，最终驱动3e19b2e03771cca5be477f34976c684ca1e71ab5f0458679c11e135423b63008；最终手机实际sha256sum匹配，普通主App实际hash仍f0d4dd5a82ac7510a2f7652f06707bdae10821333c3d7918abb2e44e98f01167。初轮驱动实际hash单列，不把后续驱动当作初轮包。

ColorOS隔离App扫描提示1项敏感权限、驱动暂无风险，经系统正常继续安装；未关闭防护或授予额外权限。手机充电，各轮观察最高电池温度27.2/27.4/27.6/27.8°C、系统热状态0。不能据此证明拔线耗电或长时发热达标。

临时flowprobe.test及flowprobe均卸载Success，查询为空；卸载同时移除App专属测试正文/模型/operations文件，主机保留原证据。指定new-night-controller.xml精确删除；原stay-awake0恢复0，返回普通New后Dozing。主App/用户账号和画像未清除。arm-controller-summary.json核对四轮、完整操作、实际hash和清理证据。

## 尚未关闭

正常Controller的固定100候选ARM流程和合成连续浏览现在已有实际证据；真实网页大图/评论/账号、多来源在线采集与预读峰值、模型最长输入、长期压力与连续峰值仍需补齐。R06/R07不能整体通过。拔线重复对照能耗、长期在线缓存清理、真实个人>=5会话/100标签收益和独立未知事件去重质量亦保持开放。未发版、推送或修改品牌。
