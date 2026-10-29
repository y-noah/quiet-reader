# V2EX 真实文字讨论与分页验证 / 2026-10-24

## 范围与修改

本轮仅修改 ArticleLiveProbe 诊断与文档，没有改变生产来源、阅读器、品牌或发布版本。上一目标轮仅核对需求，属于无实现进展；本轮以真实来源证据推进 U03。

原诊断只走 native board，不能覆盖生产 V2EX 的 native→单次网页回退。诊断现允许同样的 V2EX 榜单失败回退，并单独报告是否使用。正式 HotSourceCollector 仍是热榜获取的权威路径。显式公开内容诊断新增严格同论坛帖子 URL 校验的 V2EX 入口。

显式目标原来假设存在正文图片；新增纯文字讨论不能继续套用图片必须加载断言。V2EX 改为检查实际回复 DOM 和无横向溢出，保留其他原图片诊断。文字 DOM 等待 interactive/complete，不以远端图片全部完成作为可读条件。两份失败报告保留，没有把诊断错误表述为生产适配修复。

新增真实触摸继续加载检查：取得按钮坐标并发送触摸，等待 MainActivity 当前文档与实际 DOM 追加回复；检查原可见段落 CSS top、末页按钮消失。再单独使用未注入 Loader 的 DiscussionStream 验证后续页获取、主帖及元数据身份保留。后者不是第二次 UI 点击。

## 证据

报告均位于 `artifacts/local-ai/`：

- `live-v2ex-production-text-api36.txt`：正常生产 HotSourceCollector READY，38 条热榜候选，无存储错误。先前 native-only 限制不能推断完整生产流程不可用。
- `live-v2ex-production-reading-api36.txt`：实际热榜首篇，62 条带来源 ID/作者的回复、主帖作者，正文缓存命中，统一阅读器没有重新加载来源网页。该篇没有下一页。
- 公开列表 `https://www.v2ex.com/recent?p=100` 提供 `https://www.v2ex.com/t/1248529`，103 回复。HTML 样本保存在 `artifacts/public-sources/`，仅公开网页，不导出账户或会话值。
- `live-v2ex-multipage-api36.txt`：原图片断言失败；`live-v2ex-multipage-fixed-api36.txt`：整页完成断言失败。均为诊断假设问题，保留原始结果。
- `live-v2ex-multipage-dom-api36.txt`：正文100回复→真实后续页103回复，主帖与身份保留。
- `live-v2ex-multipage-touch-api36.txt`：真实触摸100→103、实际DOM通过、末页按钮移除。初始截图在锚点恢复前捕获，不能单凭它判断位置保持。
- `live-v2ex-multipage-anchor-api36.txt`：使用真实合法首屏 URL `?p=1`，触摸后100→103，实际DOM无横向溢出；原可见段落 CSS top `6.470238208770752→6.470238208770752`。原主帖与身份保留。最终截图 `v2ex-live-anchor-page2-api36.png` 已目视检查，位于讨论区，无正文横向溢出。锚点就位等待最多8秒，此结果不证明没有瞬间视觉跳动。
- `live-v2ex-restored-discussion-api36.txt`：重开同 URL 实际阅读历史恢复第2页103回复，DOM一致、无多余续读按钮。明确标记本次没有新点击，不能把缓存恢复算成另一次真实触摸分页。独立正常 DiscussionStream 仍取得100→103。
- `discussion-flow-live-v2ex-api36.txt`、`discussion-flow-v2ex-final-api36.txt`：两轮受控网页/真实触摸回归均34/0，覆盖 V2EX/贴吧去重、失败重试、迟到结果、前后台、磁盘恢复、锚点、会话与命名空间失效；含知乎共享追加锚点。受控页面不替代真实来源鉴权证据。
- `build-v2ex-live-anchor.txt`：最终构建成功；JVM345，失败/错误/跳过0，diff检查通过。

## 未完成边界

仅一个真实公开多页讨论及当前热榜单篇；未证明登录后更多内容、账户切换、所有真实分页结构、来源长时间稳定或所有主题字号。实测环境为API36模拟器，不是真实 Find X7 Ultra。个人偏好标注/独立留出、真机内存功耗、精确签名包升级和其他文字来源完整阅读仍须继续。未提交、推送、发版或修改品牌，整体目标未完成。
