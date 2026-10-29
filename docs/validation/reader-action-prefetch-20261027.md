# 阅读页操作缓存与性能边界 / 2026-10-27

## 实现

- DiscoveryController 从当时显示的冻结列表准备当前事件后32个、前8个位置的候选，只取其他事件的代表来源。合并事件的备用来源仍按同一位置查找，不把同事件的其他来源当成新话题重复抓取。
- Intent 仅传候选元数据，最多40条、UTF-8最多128KiB，不传正文或模型状态。没有有效上下文、诊断禁用自动缓存或离开原话题时不授权预读；不另查5000条候选、不运行模型、不更改可见列表顺序。
- ReaderPrefetch 复用 MainActivity 已有的 ArticlePreloader，最多一个推测性正文请求，单次 offer 最多40个候选。正文未就绪、滑动未停稳、当前评论需续页、加载评论或播放视频时不开始；评论/视频优先时取消推测请求。
- 进入前台、真实触摸结束及滚轮动作续期20秒机会窗口；绘制、滚动位置、来源/缓存/续页回调只检查或延后，不续期。650ms静稳等待复用加载器原有350ms启动延迟。超时、失焦、后台、内存压力取消推测任务；没有后台定时器、保活或模型唤醒。
- 显式打开正在预读的候选时保留同一加载器，走原 promote 路径。机会窗口清理只取消自身推测任务，不取消已经接管的显式阅读或旋转保留的正文。
- 阅读页的普通预读器也启用候选元数据校验。正文仍经既有账号、命名空间、帖子身份和版本校验；旧标题的同URL缓存不能被当成新候选已就绪。
- 空间判定仅 worker 查询文件系统剩余空间，15秒复用；worker无任务5秒退出。缓存证据沿用可丢弃的有界writer，不触发推荐；已提交过的证据键最多保留80项，避免反复绘制导致重复提交。
- 设置返回移除旧预测上下文再重建阅读页，防止继续按已修改的平台/偏好配置抓取。返回列表后由当前列表重新提供上下文。
- 来源网页另选文章导入时也更新当前缓存锚点，不能继续使用先前文章的预测计划。
- 资源审查另外发现 MainActivity.onStop 原先未释放 AnswerStream。现与论坛续页一致，关闭闲置/正在工作的知乎来源WebView、解析器及Handler任务；正在续读则标记中断并保留正文，返回后暂停续读等待用户操作，不自动重开来源。
- 不增加图片预取、视频下载或评论爬取。整App内存、真实网络取消与非充电能耗仍需按整体门槛验收。

## 测试与原始记录

所有本轮运行在专用 emulator-5560 / Android 16 上进行，没有枚举或操作手机。记录目录为 `artifacts/local-ai/reader-prefetch-20261027/`。

- `ReaderPrefetchPlanTest`：冻结顺序、32/8边界、合并事件的备用来源定位、元数据变化、URL归属、视频排除、往返与超长载荷拒绝。
- `ReaderPrefetchProbe`：通过实际 MainActivity 的触摸、绘制、续页、HOME、返回、打开、设置返回路径；仅正文和评论传输为受控合成样本。检查来源、时间与缓存身份，不宣称实际平台权限。
- 最终同一生产代码的JVM为633/0/0/0，Debug与Release lint均通过（`final-background-source-build.txt`）。严格逐日志解析的选定模拟器检查为291/0，其中Debug251、R8阅读核心40；见 `verified-summary.json`，不包含默认误跑157或非终态/失败重试。

| 最终检查 | 通过/失败 | 原始日志 |
| --- | --- | --- |
| 知乎空闲/活动续读后台释放 | 10/0 | `final-answer-stream-background.txt` |
| 阅读页动作缓存、元数据、接管、导入、设置 | 21/0 | `final-reader-prefetch.txt` |
| 磁盘缓存文件与原始时间复用 | 17/0 | `final-prefetch-disk-reuse.txt` |
| 已接管正文的旋转与生命周期 | 25/0 | `final-promoted-native-rotation.txt` |
| 阅读页旋转恢复 | 15/0 | `final-reader-rotation.txt` |
| 讨论追加、预算、身份与楼层回归 | 72/0 | `final-discussion-flow.txt` |
| host GPU视频功能，无诊断截图 | 16/0 | `final-inline-video-no-capture.txt` |
| 受控登录/读取/交接与会话拒绝 | 75/0 | `final-login-read-flow.txt` |
| R8真实模型与阅读核心 | 40/0 | `final-minified-reading-core.txt` |

Debug App SHA256为`502D1D9202307E0E0481CA1E5C052E0FB5178F3F811EBE837DAEC25AAD5E1505`，R8隔离测量App为`C972B09C41A07D9E7E0EFFADE9B2E8F5FDCB2C4EA7587F0D6652E9955F16299B`。分别从安装位置拉回base.apk，严格匹配归档包。前六组使用`final-debug-test.apk`，后两组使用仅修正视频操作/增加无截图模式的`video-debug-test.apk`，R8使用`final-release-test.apk`；完整大小/hash见`verified-summary.json`。两个包基于本轮同一份生产源码，R8包额外包含离线模型及隔离测量入口，不是普通手机交付包。

R8包检查5份模型逐文件大小/hash、模型manifest hash及6个64位ELF的16KiB LOAD前置均通过；可识别明文凭据格式扫描无发现，不能证明任意秘密均不存在。R8核心实际计算6条向量/3条精排、经UI显式接收后阅读/展开/滚动/返回和弱反馈等40项通过，不是个人推荐质量或整机资源验收。没有SAF公开导出文件。

结束后隔离App/模型PID为空，正常退出同一AVD并用进程表确认匹配进程0；`final-cleanup.json`记录未访问/安装手机、未推送/发布。普通手机测试包仍未更新。

保留测试错误：

1. 首次误传 `restartPhase`，驱动读取 `mode`，实际跑了默认157/0；保存在 `default-suite-wrong-mode-argument.txt`，不计阅读专项通过。
2. 第一轮专项6/1，合成回复仅填sections却没有通用blocks，被生产合并器正确拒绝；修正样本同时填两者，生产合并规则不改。原记录 `reader-prefetch-invalid-comment-fixture.txt`。
3. 第二轮专项12/1，测试从shell打开未导出的MainActivity而未恢复旧Activity；改为应用内部 `NEW_TASK | REORDER_TO_FRONT`，保留未导出的生产安全边界。原记录 `reader-prefetch-invalid-external-return.txt`。
4. 前述驱动修正后19/0，归档 `reader-prefetch-before-reader-version-gate.txt`。随后发现普通阅读页原预读器未启用元数据重验证，现已开启，并增加旧标题同URL缓存的断言，需以最终回归为准。
5. 旧讨论回归72/1仍要求屏幕上显示“回复 · 读者乙”，与此前已实施的紧凑作者展示不符。现同时断言语义标签保留原作者/回复分类、可见作者为“读者乙”、正文仍在讨论区，不恢复冗余前缀。原结果保留在 `before-import-context-fix/final-discussion-flow.txt`。
6. `AnswerStreamBackgroundProbe` 首版data-url样本8/3，未能让“正在续读”的前置条件成立，不能据此判后台中断逻辑通过或失败。改用准确问题URL加全请求受控拦截并新增两次URL前置断言，10/0；生产代码未为样本放宽来源校验。原记录 `answer-background-data-url-fixture.txt`。

`AnswerStreamBackgroundProbe` 的10项检查覆盖实际HOME释放闲置/活动来源、解析器关闭、视图脱离、迟到回调不替换正文、返回保持暂停且不自动开来源。它验证受控DOM的生命周期，不能替代真实平台网络取消/账号权限验证。

### 视频回归环境记录

- SwiftShader模式下，两次带截图运行在前三个状态输出后整个AVD进程消失，指定serial也不存在，没有测试终态；原记录为 `inline-video-emulator-exit-no-terminal.txt` 和 `inline-video-second-emulator-exit-no-terminal.txt`，不能按前三项算整组通过。一次恢复后实际解码/播放已发生，但原全屏坐标落到App窗体外，被Android16输入注入规则拒绝（`inline-video-offscreen-control.txt`）。现驱动先将真实播放器控制区滚到可见区域并断言位置，不放宽全屏打开断言。
- 增加不执行诊断截图的模式，其播放/全屏/切换/失败/结束/后台功能断言保持相同。SwiftShader下它仍在开始解码阶段丢失整个AVD（`inline-video-no-capture-emulator-exit.txt`），因此不能确认是截图导致；精确native退出原因未查明。
- 每次重启均先确认指定AVD没有进程、serial不存在，保留原userdata，没有重复启动。最后改用同一AVD的host图形模式作定位实验，日志确认实际使用AMD Radeon 780M / OpenGL ES Translator，不能把图形模式改变当成App代码修复。
- 一次在驱动构建尚未结束时过早安装了旧manifest的测试包，入口未注册（`inline-video-premature-driver-install.txt`）；之后修正为等待原构建句柄终态。非正常退出后的另一次启动也没有注册的驱动（`host-boot-driver-not-registered.txt`），已重新安装并核对。均非App功能断言通过记录。
- 最终视频结果以带终态的对应日志为准；无诊断截图的运行不证明截图/视觉QA或真实平台视频传输兼容性。

## 未完成验收

- 真机Find X7 Ultra整App（含WebView与模型）峰值RAM≤1GB、非充电长时耗电/温升。
- 真实来源请求在取消后网络/TCP及WebView的释放，不以受控Loader.cancel计数代替。
- 真实虎扑/知乎权限、贴吧楼中楼与账号场景及个人推荐效果，仍按原需求开放。
- 本文基线尚未实现方向自适应；后续方向优先与fling近似已另行实现并验证，见 `directional-prefetch-20261027.md`。仍是冻结列表附近的有限预读，不声称全部操作或任意距离均已缓存。

本轮不推送、不公开发布、不更改品牌，不安装手机。
