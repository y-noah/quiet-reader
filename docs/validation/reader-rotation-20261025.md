# 统一阅读转屏位置与会话恢复 / 2026-10-25

## 实际复现

原 MainActivity.onRetainNonConfigurationInstance 保存展开集合、正文对象和 WebView 的原生像素 scrollY，新 Activity 原样恢复。实际竖屏宽376 CSS px、横屏宽879 CSS px的重排中，在第10段约35%位置转屏，旧实现把8278.48 CSS px距离直接还原，横屏落到第17段约43%。展开的回复和正文对象仍在，问题是像素距离不代表同一内容位置。

受控模拟器报告 `artifacts/local-ai/reader-rotation-baseline-api36.txt`：8项1失败。不是仅靠静态代码推测的缺陷。

继续加会话测试：仅为合成文章的唯一URL路径写入测试Cookie，准备当前会话新正文，再实际转屏。旧保留实例分支直接render旧Document，绕过正文缓存现有的Cookie校验。`reader-rotation-session-baseline-api36.txt` 11项1失败，失败为仍展示旧会话对象。没有进行真实账号登录或输出Cookie值。

## 实现

- ReadingViewport 保存段落/图块稳定id、块内进度比例、必要的上方间距。解析限制id/数值范围，计算异常回退原位置并由既有ReadingPosition夹紧。
- 在自有、已转义且有CSP的统一阅读页，滚动停止150ms后只读一次几何位置；页面可见提交/完成时也安排一次。只存坐标与id，不读正文、不轮询、不新增网络或JS桥；延续现有readerScript临时脚本与500ms回调保护。
- 保留实例仅携带当前render版本且与实际scrollY匹配的最近坐标；样本过旧或尚未取得则使用原像素回退。文章顶部保留顶部，不用正文锚点引入长标题裁切或反复转屏位移。
- 新 Activity 在可见帧按新段落高度恢复比例，onPageCommitVisible即可进行，不要求慢图片结束或整页onPageFinished。主动操作取消待恢复位置，字体调整/重新渲染使旧采样失效，后台撤销待采样任务。
- 复用保留正文前，校验当前Cookie会话、阅读缓存namespace及既有候选版本策略；失配交给正常open/cache/disk/来源流程，不能为保留滚动而展示旧会话正文。正常匹配正文与已展开回复直接保留。

目前此改动服务配置重建的保留实例；进程死亡/跨进程历史仍使用原历史位置，不把它写成已实现跨进程语义锚点。

## 最终证据

`build-reader-rotation-edge-final.txt` 构建及JVM **388，失败/错误0**，新增3项涵盖宽度/缩放重排比例、间距及无效几何回退、非法/非有限/超界锚点拒绝。

报告位于 artifacts/local-ai/：

| 报告 | 结果 |
|---|---|
| reader-rotation-anchor-api36.txt | 中间修复8/0，正常字体实际双向转屏保持约35%，不换正文/展开集合 |
| reader-rotation-final-api36.txt | 中间版11/0，包含Cookie切换后的当前正文选择 |
| reader-rotation-loading-api36.txt | 10/0，阻塞真实生产图片资源流，整页尚未完成时，横竖屏仍能恢复同一段及进度 |
| reader-rotation-15-edge-final-api36.txt | 最终版系统字体1.5，15/0，长标题、段内重排、回复展开、Cookie变更、顶部双向转屏不累积偏移 |
| reader-rotation-loading-15-edge-final-api36.txt | 最终版系统字体1.5，10/0，图片一直阻塞时的实际双向转屏及早期定位 |
| discussion-flow-rotation-final-api36.txt | 讨论连续追加、失败和过滤页位置回归57/0 |
| reader-image-flow-rotation-final-api36.txt | 图片/图注/图库、关闭/Back/Home位置回归14/0 |
| slow-image-reading-rotation-final-api36.txt | 慢图片期间真实可见文字、20秒和主动滚动的阅读学习7/0 |
| login-read-flow-rotation-final-api36.txt | 原登录与阅读流程61/0 |

最终长标题普通模式：portrait段内0.34981377→landscape0.34983216→portrait0.34981377，稳定id相同；纵向距离由约8305.14变为4761.52后回到8305.14，不再强行保持像素。图片阻塞模式：0.34959095→0.34943430→0.34959095，稳定id相同，横屏检查时document.readyState仍未complete。

reader-rotation-15-edge-final-landscape.png与reader-rotation-loading-15-edge-final-portrait.png已目视检查：实际统一正文在正确排版的阅读视口可见，未横向溢出；段落身份及精确进度以DOM测量为准，不凭重复合成文字截图猜测。正常模式为实际指针展开长回复；图片模式使用被截获的公开数值地址HTTPS原生连接，阻塞输入流，没有外部图片网络数据。finally释放阻塞和清合成路径Cookie，恢复Cookie接受设置及对应历史；脚本finally恢复系统字体，不改全局方向锁定。

复用脚本 `scripts/test-reader-rotation.ps1` 可选择字体与WhileImageLoads，验证项目AVD、保存报告/截图、恢复字体。没有新增测试专用生产网络钩子。

## 未完成的边界

这是受控正文和受控图片流的Activity转屏验收，不是真实登录账号、所有来源DOM、后台进程被杀恢复或图片请求在旋转时去重/取消的认证。真实来源正文尚在获取时的请求交接、跨进程语义位置、完整TalkBack、个人推荐留出、Find X7 Ultra资源/耗电/热量及精确升级包仍需继续。未提交、推送、发版、改名或换Logo。
