# 虎扑真实受限样本定位准备与手机认证状态 — 2026-10-28

普通App仍f0d4dd5a，主App实际sha256sum已复核，本轮未清除或更改用户画像/账号数据，未发布。

## 本地完成

ReleasePhoneSourceProbe 新增phone-hupu-history测试入口，严格要求普通app.quietreader、PHY110/SDK36与phone=true。仅从reading-history的bbs.hupu.com数字帖子URL中选择最近最多4个不同主帖；不解析其他来源记录，不导出Cookie/账号标识或帖子正文。每个来源窗口最多25秒，只输出公开帖子ID、响应身份是否匹配、signedIn/loginAudience/permittedMain布尔及字数/回复数。LOGIN_SEE可见正文样本才能供后续完整来源→阅读对照；搜索结果本身不等于阅读权限验收。

hupu-history-build.log完整构建成功。普通App产物仍f0，诊断驱动D3540D26EA51F8B214634562CB2B3F7634836A289147A39419D42CA8C1EE6DF5。仅证明测试Java编译/打包；本轮诊断没有在手机执行，没有真实样本结果。

## 手机真实状态与操作边界

用户整夜授权并说无密码，此前标准唤醒可用；本轮标准wake/keyevent82后，系统UI实际显示“面部与指纹解锁”“输入密码”。标准wm dismiss-keyguard未解除，Back回到锁屏。没有尝试密码、仿造生物识别、关闭安全设置或推断用户凭据。

安装客户端是在构建终态确认之前启动的，可能拿到先前驱动；发现后没有运行它，更没有把旧驱动当成新检查成功。构建最终独立完成，但手机认证阻止继续安装，因此终止精确本轮adb install客户端。一次清理脚本对空Get-Content调用Trim报null，后续改用IsNullOrWhiteSpace复核，避免把空查询误判为已安装。

最终pm包查询临时app.quietreader.test为空、精确本轮host安装客户端数0，hupu-history-cleanup.json记录。未取得安装Success或驱动执行结果，不声称系统内部安装会话/临时安装存储均已审计。指定new-night-hupu-history.xml精确删除，stay-on原0恢复0，电源Dozing。锁屏期间没有再次尝试来源或其他App。

## 下一步

这只是本轮手机前台受认证阻止，不是整个项目已阻塞或已完成。用户自然解锁后可继续有界定位；若最近帖均公开，真实受限样本门槛继续开放，不能制造LOGIN_SEE或把公共响应当作受限验证。先前两篇虎扑及一篇知乎公开内容匹配仍仅各自范围有效。后续继续本地性能、来源及验收梳理，不打扰夜间用户。
