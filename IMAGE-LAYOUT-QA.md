# 0.3.12 迟到图片与核心阅读独立审查

日期：2026-10-02。生产代码未修改。专用 quiet-reader-test / emulator-5556，保留数据；无账号操作、Cookie 导出、真实手机操作。

## 结论

已手势滚动的图下、中段和底部，迟到图片从 30 增至 640 CSS px 后，同一文字段落漂移分别为 -0.286、-0.286、+0.095 CSS px：本轮没有复现这三处的阅读锚点丢失，不需要据此添加生产滚动补偿。

短文刚展开后存在可见布局位移，不能把 39/0 理解为所有位置零跳动。原场景名 short-top/long-above 表示展开后未再手动滚动，并不等于严格 scrollY=0。实际 #part-0 导航已改变滚动位置；旧报告中的 top-of-page 措辞已在测试源码中纠正，历史报告原样保留。

## 执行与身份

- image-layout：session 46224 终态 exit 0，39 检查 / 0 失败，10 张新图已逐张查看。
- core：session 45830 终态 exit 0，53 检查 / 0 失败，14 张新图已逐张查看。
- 两轮诊断 APK SHA256：B90C162DE7D09E49D9593CC80CED293E2CC83EED297DD1E4AA306D6628977A86。
- 磁盘正式 0.3.12 / code 23 APK SHA256：8FF09200ABEDD8986DE9878BF61C88399F1E81EC20185884691DCB88486F8DA5。诊断包不是精确 R8 正式包。
- Gradle/ADB 已归还根代理，由根代理恢复并验正式包；本报告不冒称已自行完成该恢复。
- [慢图原始报告](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/experience-emulator-5556-20261002-150636/run-1790924797165/report.txt)
- [核心原始报告](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/experience-emulator-5556-20261002-150850/run-1790924931810/report.txt)

审查技能用于真实截图与逐步证据检查。Python 3 预检不可执行，手动确认无保存的 Product Design 上下文；没有安装环境或生成网页替身。

## 旅程与观察

1. **展开明确合成文章，拦住唯一图片的第一字节**：健康。真实 Main / ReaderHtml 加载；只对 fixture 图片 URL 返回受控 InputStream，其余请求和导航转发原 WebViewClient。图片未提前写缓存。真实 PNG 320×640，naturalWidth 由 0 变为 320，布局高度从 30 变为 640。
2. **用真实 pointer 手势读到指定位置，静止后放流**：图下/中段/底部健康；短文初展保留风险。滚动后静止至少 1.2 秒，截图后另静止 1 秒；后续观察不滚屏。JS 只在固定只读窗口开启，finally 清理，不加桥、不修改 DOM 尺寸。每例 openPosition=-1、pendingAnchor=-1，属于首次合成内容注入，不是缓存回访恢复。
3. **回归核心阅读**：53 项通过；搜索空态、菜单、键盘进入正文自动收起、长回答底部收起、字号调整、三完整行摘要、深色与缓存返回均有截图/断言支持。不是七平台实网或认证验收。

| 位置 | 同一段落 top 前→后（CSS px） | native scroll 前→后 | 结论 |
|---|---:|---:|---|
| 短文初展未再手动滚动 | 207.893→52.083 | 5→414 | 上方段落上移约156px，图下文字下移454px；不是严格零滚动测试 |
| 长文图片上方 | 52.083→52.083 | 414→414 | 上方文字保持，图片下方文字自然下移610px |
| 手势滚动到图下 | 125.952→125.667 | 1458→3060 | 文字位置保持，浏览器补偿 |
| 手势滚动到中段 | 62.357→62.071 | 6907→8509 | 文字位置保持，浏览器补偿 |
| 手势滚动到底部 | 9.762→9.857 | 14765→16366 | 文字位置保持，底部收起入口仍在 |

短文位移可能与页面增高后浏览器落实 fragment 目标有关，当前是推断，不是已隔离证明。图片上方段落仍可见、内容未丢，未据此证明不可恢复的阅读阻断。顶部/图片附近的内容扩张可能干扰阅读；不建议把“已滚动中段稳定”泛化成所有迟到图片无位移。没有覆盖缓存返回的 400ms restore 竞态、API26 或不同 WebView 内核。

## 慢图全部截图

### 短文初展

放流前：

![短文初展放流前](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/experience-emulator-5556-20261002-150636/run-1790924797165/80-short-top-before.png)

实际解码后：

![短文初展解码后](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/experience-emulator-5556-20261002-150636/run-1790924797165/81-short-top-after.png)

### 长文图片上方

放流前：

![长文图片上方放流前](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/experience-emulator-5556-20261002-150636/run-1790924797165/80-long-above-before.png)

实际解码后：

![长文图片上方解码后](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/experience-emulator-5556-20261002-150636/run-1790924797165/81-long-above-after.png)

### 长文图下

放流前：

![长文图下放流前](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/experience-emulator-5556-20261002-150636/run-1790924797165/80-long-below-before.png)

实际解码后：

![长文图下解码后](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/experience-emulator-5556-20261002-150636/run-1790924797165/81-long-below-after.png)

### 长文中段

放流前：

![长文中段放流前](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/experience-emulator-5556-20261002-150636/run-1790924797165/80-long-middle-before.png)

实际解码后：

![长文中段解码后](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/experience-emulator-5556-20261002-150636/run-1790924797165/81-long-middle-after.png)

### 长文底部

放流前：

![长文底部放流前](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/experience-emulator-5556-20261002-150636/run-1790924797165/80-long-bottom-before.png)

实际解码后：

![长文底部解码后](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/experience-emulator-5556-20261002-150636/run-1790924797165/81-long-bottom-after.png)

## 核心截图复核

14 张均已查看，未发现本轮新增的可见阻断。菜单文字完整；键盘仅在搜索图存在，进入正文后消失；25 字号折叠摘要为三完整行；深色、字号改变及缓存返回仍显示原段落附近。屏幕边缘滚动裁切是当前视口裁切，不是摘要自身半行裁切。

![键盘收起后进入阅读](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/experience-emulator-5556-20261002-150850/run-1790924931810/09e-reader-after-search-keyboard.png)

![大字号三行摘要](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/experience-emulator-5556-20261002-150850/run-1790924931810/05b-large-font-collapsed-preview.png)

![缓存返回原段落](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/experience-emulator-5556-20261002-150850/run-1790924931810/07-reopened-cache.png)

未进行屏幕阅读器等完整无障碍认证；截图证明配色和布局，不证明所有远程图片或真实账号可用。按用户要求收敛稳定版，不再新增探索性场景。
