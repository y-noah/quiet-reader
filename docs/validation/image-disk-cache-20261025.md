# 已获取图片落盘、重启断网恢复与清理

## 生产行为

此前正文已落盘，图片只在小容量内存中，退出进程后会重新请求。本轮增加ImageDiskCache，只保存已经取得的受支持图片；没有增加批量图片预测，也不下载视频。没有取得、过期或已淘汰的图片仍可能需要网络，不承诺文章的全部配图已离线可用。

ReaderImages在WebView资源工作线程先查会话内存，再查磁盘，再下载。身份包含正文缓存namespace、图片URL和Referer；保存正文、首个图片和实际跳转URL的Cookie指纹，读取重新校验，不保存原始Cookie。账号或缓存状态在取得/写盘期间变化时拒绝迟到结果。重试在工作线程删除该URL的各Referer磁盘副本；trim只释放内存，之后可以重新从磁盘恢复。

记录含版本头、身份hash、取得时间、长度与CRC32，临时文件fsync后原子替换。单图最多8MiB、元数据64KiB，损坏/截断/错身份/未来时间或取得超过7天拒绝并删除；访问时间只用于LRU，不延长取得有效期。目录128MiB、最多1000项，写入预留临时副本空间并淘汰旧项，低于64MiB加本次写入所需空间时不写缓存。仅处理自己的文件，不递归删除。这是内部派生缓存预算，不是整个App的2GiB限制。

入口改为“清理阅读缓存”，明确正文及图片副本；namespace先失效，再由控制器工作线程清正文、图片及历史正文，保留阅读进度/展开、稍后读、反馈和Cookie。占用统计归入“正文及图片缓存”。初次图片取得后在资源工作线程同步写盘，不在UI线程扫描；不能据此承诺真机写入耗时或整个App内存峰值。

## 证据

报告位于artifacts/local-ai/：

| 文件 | 范围 |
|---|---|
| build-image-disk-public-reader-route.txt | 最终构建成功，JVM379、失败/错误0；新增磁盘6项和统计归类1项 |
| image-offline-restart-verified-api36.txt / image-offline-restart-final-repeat-api36.txt | 两轮准备2/0、还原6/0；合成正文/500×1000 PNG/合成Cookie，实际force-stop确认无PID、关闭两种网络；新进程无内存图片仍解码，清内存后实际点图可看大图 |
| image-cache-clear-api36.txt | 实际控制器清理7/0；图片文件物理删除、namespace变化、进度/展开、收藏/反馈和Cookie保留、已清图片不能离线复活 |
| reader-image-portrait-15-disk-cache-api36.txt | 正文/图库/Back/Home实际触摸13/0 |
| reader-image-session-disk-cache-api36.txt | Android CookieManager及受控HTTP头/跳转13/0 |
| discovery-ui-image-disk-cache-api36.txt | 发现页与存储统计回归77/0 |
| public-image-offline-disk-reader-route-api36.txt | 真实公开澎湃文章在线及重启断网后均readable1/1、probeErrors0，图片647px解码、无溢出、统一入口无来源重载，离线为disk-restore |

磁盘JVM用例包含新实例恢复、按取得时间过期、CRC/截断、LRU/低空间/未知文件保留、写入中失效不发布、离线与账号切换、重试及namespace清理。原有ReaderImages7项继续通过。以上不是个人推荐质量或真机资源验收。

## 真实公开图与诊断修正

首轮public-image-offline-verified-api36.txt在线通过、离线失败：原ArticleLiveProbe用于强制来源获取，离线仍调用网络article接口。失败报告保留。随后诊断增加offlineAtRequest，并在离线调用现有生产Repository.restoreArticle，再经真实发现入口进入MainActivity；没有为测试改变生产正文加载规则。

最终实际停止进程且确认无PID、禁用WiFi/移动数据，新进程offlineAtRequest=true，正文disk-restore95ms（仅该阶段，非整页/真机性能）、sourceReload=false。public-image-offline-disk-reader-route.png已目视检查：真实来源配图在统一正文排版可见，与公开原图一致。图像自身是文章包含的网页截图，不是打开来源网页。脚本finally恢复原网络设置，最终确认1/1。合成Cookie设置于独立.invalid域并清除，不触碰真实账号Cookie。

## 未完成

缓存已取得图片不等于所有预测正文的图片提前齐备；图像预测覆盖、真实私有图/特殊CDN鉴权、全来源离线完整度、加载中旋转、完整无障碍、个人推荐独立留出、FindX7Ultra资源/耗电/热量及精确签名升级包仍未通过。未提交、推送、发版或修改品牌。
