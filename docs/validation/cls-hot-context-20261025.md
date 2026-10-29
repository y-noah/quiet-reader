# 财联社热榜摘要进入推荐候选（2026-10-25）

内容对象沿用文字流平台要求：文章、问答、帖子与讨论；不扩充视频主导来源。

## 修改与边界

SourceParser 的 CLS 分支保留来源 `brief`，仅接受字符串，按现有 HotCandidate.clean 规则清理控制字符、折叠空白并限制600个Unicode码点。纯文本不按HTML解析，保留比较符号、标签字面量与表情。缺失、空值、对象、数组、数字和布尔值不生成摘要。继续使用既有视频标记、来源顺序、链接与去重规则。

既有 Item JSON 持久化与 HotCandidate 将摘要传至模型输入；摘要变化通过 textHash 使文本缓存区分。未增加正文请求、模型或外部服务；热榜摘要不等于完整正文。未修改发布时间推断。

## 验证

- ClsHotContextTest 新增3项：纯文本/存储往返/模型输入及hash变化，非法类型，Unicode与长度/控制字符边界。
- 全部 JVM 测试：464 项，失败0、错误0。
- API36现有模拟器使用生产 HotSourceCollector 单源真实请求：READY，20行，18行带非空摘要，storageErrors=0，completed=true。该次数量来自实时响应，不能假定每行都有摘要。
- 日志：artifacts/local-ai/cls-context-build-20261025.txt、cls-context-install-20261025.txt、cls-context-live-20261025.txt。

这验证摘要接入，不证明个人推荐效果或真实登录权限。未推送、未发版、未修改版本/品牌。先前R8包测试对应旧生产源码，不能作为本修改的精确二进制证明；最终交付仍需重建并验证。其余个人效果、真机整体内存/耗电及来源登录验收保持未完成。
