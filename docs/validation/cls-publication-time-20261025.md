# 财联社来源发布时间保留（2026-10-25）

## 来源事实

公开热榜ctime此前被丢弃。核对两个真实条目：2514147字段1792806728，对应UTC 2026-10-24 01:52:08，来源页面显示北京时间2026-10-24 09:52；2514219字段1792833086，对应UTC 09:11:26，页面显示北京时间17:11。该样本支持ctime为来源发布时间Unix秒；不把抓取时间当发布时间，不推断其余平台字段。

原始公开页面及结构化核对保留在artifacts/local-ai/cls-date-source-inspection-20261025.json与cls-date-source-2514147-20261025.html、cls-date-source-2514219-20261025.html，不包含账号会话，不进入App资源。

## 实现

SourceParser的CLS分支把有效ctime转换为Item.publishedAt毫秒。只接受解析后的Integer/Long，正数且不超公历9999年末Unix秒253402300799；缺失、null、字符串、对象、数组、布尔值、非整数、零/负数、毫秒误传或越界留空。范围验证使乘1000不会溢出。不按设备时区重复转换epoch。

增加内部带publishedAt的add重载，原有调用继续默认null；来源名次、链接、视频过滤、摘要与去重保持。既有Item JSON、HotCandidate及候选存储保留时间，最新排序使用它，无日期仍后置。候选顺序继续按来源名次，不因本修复改成时间排序。

## 验证

- 新增2项单元测试：秒到毫秒、Item JSON往返、readerItem保留、最新排序/未知日期后置、抓取时间不影响新旧、候选顺序不变；非法类型、单位和范围边界。
- JVM合计466项，失败0、错误0；cls-publication-build-20261025.txt。
- API36实际生产采集→SQLite候选读取：READY20行，sourcePublicationDates=20/20，摘要18/20，completed=true，storageErrors=0；头两个值与上述来源核对一致。见cls-publication-live-20261025.txt。

不证明所有平台时间完整或个人推荐效果，不提供真实登录/真机资源凭证。未提交/推送/发布，未改版本、名称或Logo。上轮R8 d7ad8f93…早于本次时间修复，其39/0不能当作此源码的精确二进制回归；最终发行仍需当前源码重建与对应验证。
