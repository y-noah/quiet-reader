# 图片请求登录状态与缓存隔离

## 生产变更

上轮现场缺口属实：MainActivity.imageResponse传null Cookie，静态图片Lru只按URL保存字节。正文已经登录不代表图片请求已经携带目标URL适用的Cookie；相同URL也可能跨账号复用旧字节。

新增ReaderImages，接入正文图片、大图及共用封面请求：

- Repository.imageBytes在每次HTTPS跳转上重新向CookieManager查询该目标URL适用的Cookie。正文Cookie不直接传给图片域名，同主机换路径也不重用原路径Cookie。普通正文请求原有重定向保护不变，原公开地址保护/5次循环/连接读取超时/8MiB单响应限制保留。
- 图片URL和Referer组成缓存身份；记录正文URL、首个图片URL、实际跳转URL的Cookie SHA-256指纹。匹配时复用；变化后删除旧项并重新读取。不保存原始Cookie到缓存、文件或诊断输出。
- 下载开始、各次目标选择及完成时核对状态。期间账号变化时丢弃迟到字节、不缓存为新账号结果，交给现有失败/重试界面处理。
- 绕过URLConnection缓存，防止本机HTTP缓存混入会话隔离。图片内存缓存预算仍沿用约2MiB，计入字节与保守元数据估算，最多128项，LRU淘汰；超过预算的图片可单次读取但不驻留该缓存。不是用户整个App的资源限额，也不能据此推算WebView解码峰值。
- 重试按图片URL删除各Referer缓存项；内存trim继续清理。正文和图片会话变动不更改兴趣画像或登录Cookie。

## 精确证据

`artifacts/local-ai/`：

| 报告 | 结论与范围 |
|---|---|
| build-reader-image-session-image-position.txt | 最终构建通过，JVM372、失败/错误0；其中ReaderImagesTest新增7项 |
| reader-image-session-final-api36.txt / reader-image-session-repeat-api36.txt | 各13/0；Android CookieManager、真实Repository请求头/重定向路径，HTTPS连接为受控实现 |
| reader-image-portrait-15-session-cache-api36.txt | 正文真实触摸、图库、系统Back/Home、位置13/0 |
| reader-image-landscape-15-session-cache-api36.txt | 同上横屏13/0 |
| login-read-flow-image-session-api36.txt | 现有登录读取/会话/导入回归61/0，合成来源页面，不冒称真实账户 |
| explicit-video-image-session-api36.txt | 共用图片封面兜底回归19/0，不增加视频推荐范围 |
| live-thepaper-image-session-position-api36.txt | 真实公开正文及配图；readable1/1、probeErrors0、native1767ms、cacheAccepted/cacheHit、统一入口、sourceReload=false、图片自然宽647、DOM无溢出 |
| reader-image-session-final-diff-warnings.txt | diff检查通过（警告单独保存） |

JVM7项覆盖：正文/图片账号变化与退出、跳转目标Cookie变化、取得中切换账号、同URL中途变更又变回、不同Referer/重试/清理、HTML与超预算不缓存、128项上限及最近使用保留。

Android13项实际使用CookieManager的域名/路径匹配与Repository的Cookie/Referer头、禁用URLConnection缓存、三跳请求及会话缓存变化。网络连接由进程内URLStreamHandler替换，不向测试IP发包；公共数字地址只为经过正常地址检查且避免DNS。合成Cookie使用随机路径，结束后逐一删除，恢复原acceptCookie状态；不读写真实账户登录，也不打印Cookie内容。此诊断不能证明TLS/服务器权限或真实CDN行为。

## 公开图片视觉检查的修正

两次早期报告naturalWidth>0，但截图图片区域仍空白，保留为`live-thepaper-image-session-api36.txt` / `...-visible-frame-api36.txt`，不将其写成已目视通过。另获取公开原图`artifacts/public-sources/thepaper-image-current.png`确认原图非空白。加强ArticleLiveProbe：给既有延迟历史恢复留出诊断时间，图片滚入视口，等待可见状态回调及绘制，再记录DOM/原生滚动与焦点。

最终`live-thepaper-image-session-position.png`已目视核对：真实图片内容与公开原图一致，在统一文字排版中显示；DOM top277.27/bottom505.125/viewport782，nativeScroll526、focus=true。图片内容本身是来源文章内的微博页面截图，不能误认为App打开了微博网页。早期截图为何空白没有被单独证明为某个生产缺陷，此次改动在诊断等待/定位，未为截图增加生产等待或替换图片内容。

## 仍未通过的整体门槛

真实知乎/微博等已登录账号图片、账户切换后的私有资源、第三方/分区Cookie或CDN特殊UA/鉴权、网络中断/真实慢图切换等仍需现场验证。公开澎湃不证明登录成功；受控Cookie测试不证明真实平台权限。未增加持久图片离线缓存。加载中旋转、完整无障碍、真实个人推荐留出、FindX7Ultra内存/功耗/发热、精确签名升级包继续未完成。

未提交、推送、发版或修改品牌。
