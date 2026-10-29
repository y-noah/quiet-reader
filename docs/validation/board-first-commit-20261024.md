# 榜单首屏提取 / 2026-10-24

上轮改变诊断并取得真实来源反例，属于进展。本轮对比发现 DynamicBoard 只在 onPageFinished 后开始 inspect；普通观察诊断则独立每三秒采样。资源加载未结束时，可用榜单可能已经出现在DOM中。此差异不是抖音现场失败的全部根因证明。

## 生产修改

DynamicBoard同时响应onPageCommitVisible，在首个可见提交时启动原有检查循环；onPageFinished仍可兜底。tries在inspect同步增加，两种事件不会创建两个循环。保持原30秒总期限、1.5秒空列表轮询、串行解析、会话/缓存策略、网络图片/视频限制和close取消保护，没有增加请求或绕过登录。

## 复现与回归

BoardCommitProbe使用真实Android WebView和生产DynamicBoard。只替换传输为合成HTML，生命周期回调转交原WebViewClient；异步脚本响应以InputStream闸门保持未结束，不访问真实来源。主页面包含可被正式抖音列表适配器识别的一个合成条目。

- board-commit-baseline-api36.txt：旧实现6项检查中1失败；真实commit已发生，但完成回调仍在等待无关脚本。释放脚本后正式解析成功，排除了无效夹具。
- board-commit-fixed-api36.txt：相同场景6/0，脚本未结束、page-finished未发生前已经交付正确榜单，后续无第二次回调。
- board-commit-hydration-api36.txt：7/0。首屏空壳，3秒后脚本填入榜单；没有把空壳当成功，原检查循环在无关资源仍挂起时取得正确结果。
- v2ex-fallback-board-commit-api36.txt：17/0，回退队列/取消/停用/失败计数回归。这是受控Transport，不扩大为所有真实WebView取消场景认证。
- live-douyin-board-commit-api36.txt：真实生产网页榜单路径仍未读到条目，readable=0/1。修复确实解决受控已复现的等待缺陷，不能称当前抖音现场已修好。

build-board-commit-hydration.txt成功。单元报告321、失败/错误均0。未提交、推送、发布、改品牌。最终正常debug store runner，无活跃测试命令。全来源统一入口、真实账号读取/媒体/讨论、长期推荐质量、真机峰值/功耗等门槛仍未完成。下一步仍需隔离真实来源的视口/资源配置差异与访问状态。