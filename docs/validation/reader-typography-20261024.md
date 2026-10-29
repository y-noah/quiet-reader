# 统一文字阅读器：窄屏大字号换行 / 2026-10-24

## 复现与生产修复

上一轮全视频回复页修复为进展。本轮U02检查发现作者等`.meta`辅助文字没有正文已有的长字符串换行规则。320dp、系统fontScale1.5、App最大阅读字号28下，180字符连续英文作者将元素scrollWidth撑到1929/2065，整页scrollWidth2078，WebView布局viewport被拉到1136；两项排版断言失败。不能以正文p已换行推断全页不会溢出。

ReaderHtml给作者/元信息、操作按钮、阅读提示、尾部和讨论标题增加overflow-wrap:anywhere，并让section-nav可换行；原有figcaption规则保留。没有隐藏/裁剪作者或缩小字号。新ReaderTypographyProbe通过真实MainActivity/缓存/统一WebView，在当前配置检查长标题、主作者、回复作者、长链接、提示和长作者折叠按钮的元素边界、整页宽度、最大字号、主题色和操作高度；它使用明确合成极端文字，不用于证明来源质量。暂设字号仅当前Activity反射值，不改用户持久字号；主题和模拟器size/density/font_scale均恢复。

## 证据

报告在`artifacts/local-ai/`：

- `reader-typography-baseline-wrapper.txt`保留最初5项2失败的完整报告。修复首轮`reader-typography-first-fixed-dark-api36.txt`5/0、宽度284/284。
- 加长作者折叠操作后，诊断未核对readerPageUrl会读到旧重排页面，产生null测量；保留`reader-typography-render-race-api36.txt`。诊断改等当前精确URL/已完成/文字字号，未以延长固定sleep掩盖。此失败属于诊断同步，不作为生产缺陷。
- 最终`reader-layout-dark-320-150-api36.txt`、`reader-layout-light-320-150-api36.txt`各5/0，均App28、实际computed27.93，viewport/scroll均284、元素overflow空。续读操作52.92 CSS px，长作者折叠操作415.06 CSS px，保留全部信息，极端作者会占多行。
- `reader-layout-dark-412-100-api36.txt`5/0，App28、viewport/scroll376。该轮生产CSS相同，后来诊断仅增加分区截图。
- `layout-reader-320-150-{dark,light}.png`及`-author.png`、`-action.png`保存真实截图，已目视核对深浅色和作者/按钮多行，无横向裁切。极端160/180字符是压力样本，不宣称自然新闻作者长度如此。
- `build-reader-typography-verified.txt`最终构建与JVM365失败/错误/跳过0。
- `discussion-flow-reader-wrap-api36.txt`最终讨论交互57/0，包含三文字论坛/知乎锚点与全视频页后继续文字；diff检查通过。
- `live-hupu-reader-320-150-api36.txt`：临时设置320dp和系统1.5，真实公开虎扑帖通过生产卡片进入统一阅读器，65→84→101两次实际点击、回复DOM无横向溢出，锚点-112.828125→-112.66146087646484、-220.078125→-220.078125。没有修改该真实样本的App持久字号，不把它声称App最大28验证；App28由上述合成压力样本覆盖。操作结束恢复原显示配置。

## 范围

本轮实际覆盖竖屏320/412dp、深浅色、系统1/1.5与App28极端文字；没有完成所有字体/行距/配色组合、横屏、图片失败重试/大图和真机无障碍验收。真实样本两页追加结果不证明没有瞬间跳动。个人兴趣标注与独立留出、认证全文、Find X7 Ultra整体RAM/功耗和精确包升级仍未完成。没有提交、推送、发版、改名或换logo，整体目标继续。
