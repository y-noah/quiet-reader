# 阅读卡片基础验证

日期2026-10-23。目标机仍为Find X7 Ultra / 12GB / Android16 / ColorOS16；本次是Android16项目模拟器，不是真机结果。

## 当前实现

DiscoveryActivity是launcher，DiscoveryController管理单个串行数据worker、HotSourceCollector和当前冻结快照。页面使用原生ListView复用卡片。读取快照/迁移旧收藏/保存反馈/排序在worker；用户当前列表在新数据准备时保持不动。来源故障细节放来源菜单，不占首页大段文字。

MainActivity通过内部discovery_item/discovery_source参数复用原来阅读与平台榜单；原生条目直接返回发现列表。B站/抖音/贴吧/百度等webReading来源复用LoginActivity browse分支，不经过会重新打开网页的空中间reader。CookieManager/SourceSession未重建或清空。

当前顺序是明确说明的中性候选顺序或来源发布时间顺序，尚无BGE模型分数参与。多看/少看已保存为显式样本，不能据按钮存在宣称模型已学习；看够了的当前内容版本抑制、保存与撤销已经执行。

## 证据

| 范围 | 证据 | 结果与边界 |
|---|---|---|
| JVM | app/build/test-results/testDebugUnitTest | 194项、35 suites、0失败/错误；包括候选顺序、真实日期/未知日期、当前版本抑制 |
| SQLite与collector | artifacts/local-ai/discovery-store-ui-foundation-api36.txt | 53项通过；增加可见快照防淘汰、最多3份快照、旧收藏只迁入一次与移除不复活 |
| 原生UI首轮 | artifacts/local-ai/discovery-ui-api36-round1.txt | 15项通过；100条合成候选、60位置恢复、复用行、cached reader/system BACK、保存、反馈/撤销、重建、九来源 |
| 连续排序 | artifacts/local-ai/discovery-ui-api36-round2.txt | 16项通过；最后一次排序选择有效，已知日期优于未知日期 |
| 冻结更新 | artifacts/local-ai/discovery-ui-api36-round3.txt | 18项通过；更新先pending、点击查看更新后才替换可见快照 |
| 生命周期补查 | artifacts/local-ai/discovery-ui-api36-round4.txt | 18项通过；返回前台刷新来源状态/版本后的完整卡片回归 |
| 视觉检查 | artifacts/local-ai/discovery-cards-api36.png | 已查看实际原生截图，合成标题明确标记测试样本；默认尺寸/浅色 |
| 真实首页 | artifacts/local-ai/discovery-live-home.xml 与 discovery-live-home.png | 实际启动新Activity，显示V2EX/财联社/微博/百度/贴吧等真实平台标题与获取时间；已查看截图，不是写死的演示文章 |

UI测试使用调试包允许的严格`test-ui-*.db`隔离库、test_skip_fetch，以及本机缓存合成正文。测试数据不写入生产来源表；release忽略测试库/跳过抓取参数。先关闭collector/数据库worker后删除测试库。真实首页验证另行使用正常默认库/网络路径。

UI测试不是完整阅读回归：它验证一个受控原生缓存正文桥接，不代表每个平台的真实登录、续读、视频、网页窗口和账户状态都已验证。旧平台仪器测试直接启动内部MainActivity，不能再把它们称为新launcher完整验收。

已安装包的`cmd package resolve-activity`实际解析到`app.quietreader/.DiscoveryActivity`。最终签名诊断包325,524,807字节、SHA256 `5ae987177dd5af110b2c93ad6616effeef3a07028c39214f57b0a0e69a7dc56d`；详情`artifacts/local-ai/discovery-cards-package.json`，64位ELF LOAD段16KB对齐检查通过。模型清单/资源未改；这个hash对应卡片基础版本，不是早期模型数值探针的APK，也不是正式发布包。

## 尚未达到的门槛

- 双模型推理/兴趣桶/精排接入，以及真实正负样本的个人排序增益。
- 跨平台保守事件归组、多来源入口、细分类目与筛选；目前只去重精确source+URL身份。
- 来源中文全文资格；单凭汉字标题不能通过该门槛。
- 模型准备进度、修复、旧资源清理和全App资源调度；不会通过首屏按钮存在来冒充完成。
- 320/360/412dp、字体1.0/1.3/1.5、深色和TalkBack矩阵；默认尺寸截图只证明这一场景。
- 进程被杀恢复、低磁盘/数据库损坏、前后台切换压力、网页来源实际返回、升级/正式release验证。
- Find X7 Ultra上的整App峰值、发热、耗电与个人兴趣留出集。

本轮不关闭P0/P1/P2/M09整体门槛，不发布，不推送，不修改品牌。
