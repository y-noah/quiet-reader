# 正文/大图图片加载共享与取消 / 2026-10-25

## 问题和实现范围

此前MainActivity与ImagePreview都直接调用同步ReaderImages.load，未绑定阅读页生命周期、无在途合并。两个页面同时读同一图可能重复下载；原WebView销毁/Home后底层请求未必随之断开。此轮实现前未保存该问题的旧版本模拟器基线；不能把后来的成功检查描述为先有受控基线失败。

ReaderImages新增Scope和有界共享任务：身份含缓存namespace/epoch、图片URL、来源referer、二者Cookie指纹。同一有效身份共用一个底层获取；每个scope按调用次数计数，取消一个重复等待者不能误取消同scope其他调用。结果带取得时的完整Cookie作用域（包含所有redirect目标），每个接收者交付前再核对。缓存读取保留既有有效性检查。最多8个有效共享图任务，其他调用等待；没有新增Bitmap缓存或下载线程池，网络仍运行于WebView拦截线程。RequestQueue仅新增外部socket Ticket登记/释放/取消，单一后台取消执行器负责断开连接，原submit最新请求语义保留。异步取消可能与新连接短暂重叠，不能据任务数断言实机socket/内存峰值已测。

ReaderImageOwner只保存application上下文派生的ArticleSession、Item和Scope，不保存Activity/WebView/Bitmap。已读正文转屏转交同一所有者；旧Activity字段清空，配置重建继续已有图片任务。普通Home、离开文章、登录或销毁则释放；最后接收者离开后断开socket，拒绝迟到缓存。已结束图片的前台返回不重载正文，保留原WebView、页面URL和位置；仅有未结束调用被中断才恢复Scope和重载图文，单次读取几何锚点，在可见帧恢复段落/进度，不等待慢图或onPageFinished。无有效锚点回退原位置。

大图每页固定自己的加载Scope快照。关闭/前后切图/重试只释放该页订阅，正文仍在用的同一图继续下载；旧页面不能赶上新Scope后成为无用下载。重试和内存清理取消失效的在途任务；导入已经取得的同URL图片字节可取代旧网络任务。原磁盘缓存、重定向逐URL Cookie、预取1MiB/8MiB时间切片规则不变，推测预取仍由ImagePreloader自行取消，没有把所有推测和前台获取强行合并。

## 验证与纠错记录

artifacts/local-ai/：

- build-reader-image-ownership-position-final.txt：最终构建成功，JVM **397项，失败0、错误0**，比本轮前388增加9项。ReaderImageScopeTest覆盖两作用域共享/一个离开仍继续、最后接收者断开且迟到拒绝、同scope中断重复等待者、重定向Cookie变化全体拒绝、失败可重新取得、referer隔离、清缓存断开、9张图最多8项有效并发、导入缓存取代旧网络。
- reader-image-ownership-flow-api36.txt：首轮实际图片生命周期9/0。
- reader-image-ownership-gallery-api36.txt：扩展样本失败（第一请求等待超时）；原因是新增短图段实际自动展开、同时发起第二张图，诊断却假定请求数恒为1。改成真正折叠的合成回复，测试脚本保留原JavaScript设置，未修改生产折叠规则。
- reader-image-ownership-gallery-final-api36.txt：13/0，加入图库切换、取消后重看、关闭重试。
- reader-image-flow-image-ownership-final-api36.txt：原回归14项2失败，明确保留。暴露导入缓存被旧任务挡住和已完成图片的Home返回多余重载。修复生产的任务替代/中断检测，未放宽原像素/页面URL检查。
- reader-image-flow-ownership-regression-fix-api36.txt：原14项全部通过；reader-image-ownership-ownership-regression-fix-api36.txt：13/0。
- reader-image-ownership-image-position-final-api36.txt：最终14/0。实际连续两次转屏保持一个socket，实际大图加入/退出共用任务；Home最后接收者离开断开，迟到PNG不进内存/磁盘；前台只重试一次且**慢图未完成前**恢复同段约33%进度，继续复用已准备正文。图库下一张读取折叠区图像、上一张取消无用下载、再次进入重新取得、关闭显式重试拒绝迟到落盘。

图片为唯一受控URL的16×16合成PNG，经生产HTTP、ReaderImages和真实WebView解码；HTTPS字节由协议处理器控制，没有使用真实平台私有图。实际点击图注/按钮回归使用缓存的500×1800与700×1000 PNG。上述不证明真实账号权限、全来源图像覆盖、实机性能或耗电。

## 最终受影响回归与界面核对

同一最终安装包顺序执行，artifacts/local-ai/：

| 报告 | 结果 |
|---|---|
| reader-image-flow-image-position-final-api36.txt | 14/0，保留原WebView/页面URL/位置断言 |
| reader-image-session-image-position-final-api36.txt | 13/0 |
| image-preview-layout-image-position-final-api36.txt | 11/0 |
| reader-rotation-loading-image-position-final-api36.txt | 10/0 |
| image-prefetch-image-position-final-api36.txt | 10/0 |
| image-prefetch-policy-image-position-final-api36.txt | 12/0 |
| login-read-flow-image-position-final-api36.txt | 61/0 |

reader-image-ownership-final.png已拉取并目视核对：关闭图库后仍在正文中间段落，文本排版清晰，无残留遮罩或横向溢出；当前截图可视区没有图片，不拿它声称已视觉验证真实配图。合成PNG的加载/大小由实际DOM解码检查证明。git diff --check通过。

## 尚未完成

完整App峰值RAM/耗电/温度仍需Find X7 Ultra实测。进程死亡后的语义阅读位置、完整辅助功能、真实登录与长期新鲜度、个人推荐独立留出、精确签名升级及回滚等仍未整体验收。没有提交、推送、发布或改品牌。
