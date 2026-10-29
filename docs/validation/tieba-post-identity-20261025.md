# 贴吧混合页面结构的跨页回复身份

日期：2026-10-25。修复受控页面能复现的重复追加；不等同于贴吧虚拟列表/楼中楼完整适配或真实账号验收。

## 问题及实现

StructuredSources分别用DOM节点id（例如post_content_2）和data-field.content.post_id（例如2）计算Section身份。同一来源楼层在两页使用不同结构时会产生两个hash，DiscussionPages只能按Section.id去重，重复楼层因此再次追加。

数字data-field帖子ID现统一为历史DOM拼写post_content_<ID>再hash，既有DOM身份保持不变。文字、作者及楼层位置不作为来源ID的替代：不同作者、相同回复内容仍分别保留。没有可靠来源身份的旧回退策略未扩大为精确去重保证。

兼容已有数字ID缓存：新解析Section保留受限数字sourcePostId，经过ReadingHistory JSON可选字段持久化；旧JSON无字段仍可读。新Section若身份与canonical hash吻合，可在同帖合并时识别旧raw数字hash，保留已显示旧Section及其段落ID而跳过重复新Section。不会改写旧段落、主帖或已取得时间。字段参与非空内容指纹及缓存空间估算；未修改SQLite表版本。

## 复现与单元测试

- 新用例首次运行437项/1失败，精确失败为同一帖子ID两种表示的Section.id不相等。摘录保存artifacts/local-ai/tieba-post-id-baseline-20261025.txt。
- 修复及旧缓存补充用例后438项/0失败。检查两页结构混用只追加新作者的楼层；将原回复构造成旧数字hash、经实际JSON编码/解码，再与已持久化新页合并，保留旧回复ID且不重复，并保留另一作者相同文字的新回复。

## Android证据

DiscussionFlowProbe的贴吧第二页将重叠回复10改成data-field身份，第三页将回复11也切换；保持不同来源的实际WebView、触摸、共享缓存、分页失败/重试、后台取消和段落位置验证。初次修复后59/0；持久化字段接通后的同流程59/0。日志分别为tieba-post-id-flow-20261025-070106.txt、tieba-post-id-legacy-flow-20261025-070320.txt。

进一步将初始贴吧已读回复替换为旧数字缓存身份，以实际读页验证旧身份保留和新来源ID追加；最终60/0，日志tieba-post-id-final-20261025-070435.txt。共享存储回归158/0，日志tieba-post-id-storage-20261025-070505.txt。没有清除用户登录或来源数据；正常原签名debug/store包仍在日常模拟器上。

## 边界

输入为受控旧式DOM/data-field页面结构，未把实际线上访问成功冒充为证据。新版贴吧虚拟列表获取、楼中楼续读和真实登录仍待验证。真实知乎、个人推荐效果与Find X7 Ultra资源等门槛保持开放。此前R8发布诊断通过的包早于本次生产修改，本轮不能沿用旧hash声称最新版发布包也已通过。未提交、推送、发版或改品牌。
