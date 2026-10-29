# 阅读页实际视频息屏补验 — 2026-10-28

本轮仅扩展 InlineVideoProbe，不改生产代码、普通候选或手机。预读Loader在息屏时取消不等同于已经播放的视频和阅读图片所有权释放，因此另查真实MainActivity/行内WebView/解码器路径。

## 实际路径

专用API36模拟器 quiet-reader-api36 / emulator-5554，host GPU。明确不使用此前解码时退出的SwiftShader环境。本次不截图；不能计作视觉验收。

沿用测试包内CC0 flower.mp4、受控ReaderVideoRequests范围请求、两段视频夹文字的缓存正文；视频素材不在生产包中，未声称真实平台传输成功。先验证真实播放时间推进及readyState，再用系统SLEEP(223)息屏。通过条件要求PowerManager非interactive、MainActivity readingResumed=false/wasStopped=true、视频请求所有者和监控均为空、ReaderImageOwner.paused=true，并且实际transport close计数增长。随后1.5秒记录请求数不增。

WAKEUP(224)及无密码模拟器dismiss-keyguard后，必须真正回到前台、有窗口焦点、同一readerWeb实例；每个行内视频paused且没有src，正文尾段仍存在。显式再次点击后要求真实播放时间推进且readyState>=2。随后仍执行实际HOME释放transport/monitor和清除所有视频源的原检查。

原全屏控件、Back保留正文、真实暂停、切换附件只保留单一播放者、失败收起、显式重试及自然结束检查保留。没有用“发出系统命令成功”代替实际生命周期结果。

## 结果与身份

证据目录 artifacts/local-ai/reader-sleep-media-20261028/。inline-video.txt 与 inline-video-repeat.txt 各21项/0失败、唯一INSTRUMENTATION_CODE -1；每轮包含原16项和新增5项。build.log完整终态成功，之后才复制和安装APK。

- 隔离debug App SHA256 3d982aa996cae43e982e2682eb5363283fbebba1b81baf3659d1ab3c830f0047，89189591 bytes。
- 驱动 SHA256 1834b96834ecaa0c51f11f7780cd40a84f4314749e7ae6044b59192135a4d8e2，4116264 bytes。
- package.json与installed-hash.txt保留构建及模拟器实际安装包散列，两者一致。

测试结束force-stop隔离App和驱动，关闭模拟器；cleanup.json确认匹配进程0、adb中emulator-5554消失、userdata保留。没有手机操作、安装、解锁或设置修改。普通候选仍New-20261028-055b1f1c.apk，手机仍f0d4dd5a，未发版或推送。

## 证据边界

验证真实解码状态与受控transport释放、阅读图片作用域关闭；没有进行真实远端图片传输或验证其TCP取消时延。1.5秒请求计数不变不是长期CPU、温度或耗电证据。未证明WebView内全部计时器停止、真实平台视频兼容、ARM峰值或ColorOS策略。全屏窗口内再次息屏、系统来电/多窗口等也不在本轮范围。整体验收的真实受限权限、长负载峰值、拔线耗电、长期缓存及本人推荐收益门槛保持开放。
