# 正文图片真实触摸与后台释放

范围：合成中文正文、两种尺寸PNG、生产正文缓存和图片字节缓存、实际MainActivity/WebView/ImagePreview。指针事件进入正文链接及原生按钮，不以DOM click或直接调用zoom代替触摸。

## 覆盖与缺陷

正文包含三个图片位置（两个不同URL，一个重复）。实际触摸图片进入第一张，验证尺寸500×1800、图库去重为2项；实际下一张/上一张切换700×1000和第一张；实际关闭保留同一个正文WebView、URL及非零scrollY。第二张“查看大图”文字链接实际触摸进入对应图片，系统Back关闭后保持该文字位置。

新增真实Home检查复现：阅读Activity已wasStopped，但隐藏的预览仍持有current WebView。`reader-image-portrait-15-lifecycle-baseline-api36.txt` 为12项、2失败：资源保留一次失败，以及恢复后等Activity窗口焦点超时。后一超时不能单独证明Activity无法恢复，旧对话框也可能持有焦点；不把诊断超时包装为额外生产缺陷。

MainActivity.onStop现在dismiss并清空imagePreview。保留正文、历史位置和正文缓存；回前台回到原文，需要看大图时再次点击。此次仅证明隐藏预览WebView释放，不证明已经在执行的同步HTTP请求被立刻中止或进程峰值内存降低了多少。

## 验证证据

`artifacts/local-ai/`：

- `build-reader-image-lifecycle-fixed.txt` / `install-reader-image-lifecycle-fixed.txt`：构建、安装成功；JVM365，失败/错误0。
- `reader-image-portrait-15-lifecycle-fixed-api36.txt`：13项0失败。
- `reader-image-landscape-15-lifecycle-fixed-api36.txt`：13项0失败。
- `reader-image-portrait-10-lifecycle-normal-api36.txt`：13项0失败。
- 修复前独立基础路径 `reader-image-{portrait,landscape}-15-first-api36.txt` 与 `reader-image-portrait-10-normal-api36.txt` 各9/0，保留诊断演进证据，不混称最终13项。
- `discussion-flow-reader-image-lifecycle-api36.txt`：57项0失败。
- `slow-image-reader-image-lifecycle-api36.txt`：7项0失败，包括实际20秒文字阅读、滚动与待加载图片不阻断READ。
- diff检查通过；显示配置使用现有脚本finally恢复。

脚本新增 `-Mode reader-image-flow`，与原布局模式分开报告，避免复制原布局截图冒充新触摸截图。此诊断未生成截图，结论来自实际指针/生命周期与DOM、缓存身份和坐标校验；前一轮布局截图仍只证明前一轮视觉检查范围。

## 边界与后续

图片字节预置在当时的真实Lru缓存中；验证生产imageResponse消费缓存与实际解码，不证明新下载图片、Cookie鉴权、Referer防盗链、下载失败重试或完整离线图片缓存。前一轮独立ImagePreview失败重试验证仍有效，但不是本轮正文鉴权链路。当时MainActivity.imageResponse未传Cookie给Repository.bytes；后续已接URL作用域Cookie与ReaderImages会话缓存，受控头/跳转和真实公开配图证据见`reader-image-sessions-20261024.md`，仍不能把缓存样本当成真实账号认证通过。

加载中旋转、TalkBack、真实捏合、来源登录图片、个人推荐独立留出、FindX7Ultra资源/功耗、精确签名包升级仍未完成。未提交、推送、发版或改名/logo。
