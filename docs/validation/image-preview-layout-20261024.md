# 大图预览横屏空间与失败重试验证

范围：统一文字阅读器的图片预览，受控图片响应与实际 Activity/Dialog/WebView。不是来源认证、推荐质量或整体验收。

## 复现与修复

横屏 1920×960、density480、系统字体1.5：旧固定图片高度为528px，WebView从y663延伸至1191，可见窗口底888，且与底部按钮重叠。基线 `image-layout-landscape-baseline-api36.txt` 为8项2失败，其中关闭资源一项是诊断在异步dismiss回调前取样，已通过等待主线程空闲修正，不归因于生产资源泄漏。

`ImagePreview` 改为先测量控件、图片使用剩余空间的权重布局；横屏使用可用全窗口，较紧凑的标题与提示，成功后不重复展示缩放提示。失败保持明确提示及重试；每次重试/换图仍创建新WebView并保留旧回调身份保护。纵向长图仍可滚动、双指缩放。

中间失败报告保留：只有权重时高度0；只有全窗口时29px且图片状态取样过早；紧凑布局初版114px，仍未达诊断要求的48dp。没有把“未出屏”当成可用。最终失败态横屏162px（54dp），成功态241px；竖屏768px。

## 证据

`artifacts/local-ai/`：

- `build-image-layout-nonzero-return.txt`：最终构建成功；JVM365，失败/错误0。
- `image-layout-landscape-15-nonzero-return-api36.txt`：最终11项0失败。
- `image-layout-portrait-15-nonzero-return-api36.txt`：最终11项0失败。
- `image-layout-landscape-15-reader-return-repeat-api36.txt`：前版诊断10项0失败，同一生产实现，重复横屏通过。
- `image-layout-landscape-10-final-api36.txt`：普通字号8项0失败，同一生产实现。
- `image-layout-{landscape,portrait}-15-reader-return.png`：已目视检查，等待WebView可见帧后截图；图片为纯色长PNG合成图。
- `discussion-flow-image-layout-api36.txt`：讨论续读57项0失败。

诊断先通过生产缓存打开合成文字文章，确认实际滚动到非零位置，再打开受控加载器的生产ImagePreview；第一请求故意失败，重试返回可解码PNG，检查仅两次加载、一次当前URL失效、视口/按钮范围、长图可滚动和缩放配置、关闭后WebView释放、底层阅读器实例/URL/scrollY不变。最后等待实际可见帧，避免截图旧尺寸。此诊断直接调用预览，不证明正文图片链接的触摸命中或真实来源图片鉴权。

复用脚本 `scripts/test-image-preview-layout.ps1` 限定项目AVD，finally恢复原分辨率、density、系统字体；回归后确认1080×2400/density420/font1.0。未清除登录或兴趣数据，未提交、推送、发布或修改品牌。

## 未完成边界

需要后续覆盖正文实际图片链接触摸、真实登录图片、加载途中旋转、极大字号/小窗口以及完整无障碍；此次未重跑旧gallery真实滑动及手势测试，不能据缩放配置宣称已验证实际捏合。受控非零阅读位置保持不代表所有真实来源图片/字体重排均无跳动。个人推荐真实反馈与独立留出、认证全文、FindX7Ultra整进程内存/耗电/热量、精确签名包升级等整体门槛仍未通过。
