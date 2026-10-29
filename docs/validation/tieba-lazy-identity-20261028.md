# 贴吧无ID表情回复的备用身份（2026-10-28）

## 已复现问题

此前手机真实窗口中，短文本/纯表情回复因缺少可比较ID或唯一文本而未纳入关系验收。本轮检查生产StructuredSources发现备用身份仅包含root.text和图片src，而实际正文SourceParser.imageSource还接受data-src/data-original。添加跨窗口回归后9项中2项实际失败：不同懒加载表情被赋相同section ID；同一表情由占位src切换为真实src后ID发生变化。测试保留在TiebaReplyRelationsTest，原失败XML/日志位于 `artifacts/local-ai/tieba-lazy-identity-20261028/before-tests.*`。

首次主机调用的未加引号-D参数被PowerShell拆为Gradle任务，before.log为构建命令失败；它没有运行测试。误复制的旧六项XML已明确改名stale-prior-result.xml，不作为本轮结果。修正参数后真实before-tests.xml时间和九个测试名称已核对，不能把构建错误当产品失败。

## 修改

备用身份中的图片地址复用正文解析的同一imageSource规则（src→data-src→data-original，跳过不可用地址并规范化）。明确来源回复ID仍优先，不改变父楼层归属或热度排序，也不引入网络/模型/后台任务。SourceParser.imageSource由private改为包内可见，避免复制URL解析规则。

关系revision由2升至3：在线时旧版贴吧正文/阅读历史不能作为当前解析直接复用，离线仍可阅读原内容，不删除用户偏好/历史。新测试复核revision2的在线拒绝/离线保留，手机测试探针的硬编码2改为当前revision常量，之后必须对新候选实测，不能用新探针宣称旧手机包已满足revision3。

## 目前证据

修复后全量JVM682项，失败/错误/跳过均0；目标九项全通过，包括两个原失败、不同明确child ID的相同表情、跨窗口重复合并、父楼层、历史序列化及旧revision规则。after-build.log终态成功，after-tests.xml与jvm-summary.json已归档。Debug/Release lint终态成功，见lint.log。

这些为受控回归，不是新真实来源窗口验收。相同作者/相同文字或同一表情且缺少来源ID时，仍不能从窗口顺序推断真实唯一身份；本修复只解决不同可用图片被混同、同图懒加载变化产生重复的问题。完整真实楼层/分页/短重复身份及图片实际获取仍须续验。无ID备用身份仍可能受其他来源编辑影响，不当作永久来源ID。

本轮未启动模拟器或操作手机；普通安全候选仍4ce61dc6，未包含本修复。R8实际分支及普通保数据覆盖待验证，不推送/发布，整体目标保持未完成。


## 后续R8真实链路验证

新增ReleaseTiebaIdentityProbe，限ranchu模拟器隔离discoveryprobe包且检查nondebuggable。通过Android生命周期/视图挂载钩子设置受控WebView响应，执行真实压缩DynamicReader→StructuredSources→共同阅读页→底部自动TiebaReplies→正常pause历史保存，不反射生产类、不在测试计算section ID或调用私有方法。临时TIEBA-web配置按原有无键/值恢复，每轮URL采用唯一数字身份避免缓存遮盖解析。

原first/second/third三轮各2项/1失败、code0，均未取得初始正文；fourth同2/1/code0，源被释放且原生提示来源跳转到登录、其他问题或不支持页面，诊断JS超时。这几轮替换已开始导航的网页，不能证明表情合并的产品错误；也没有足够逐回调轨迹把全部原失败定性为同一个已证实根因。四份日志及各版驱动保留，没有放宽来源/帖子校验或修改生产实现使其通过。

最终e版在源WebView首次挂载、初次导航之前设置响应，保留原生来源生命周期。fifth.txt与repeat.txt两轮各13/0/code -1：首个lazy表情解析、两轮真实底部scroll触发自动续载、同父楼不同表情保留、旧表情成为src不重复、无文档横向溢出、pause历史revision3、两个不同child ID及父楼层关系。每个受控窗口增加一个明确floor ID驱动实际续载，不等于真实来源全部分页/无ID同内容身份验收，也不证明表情图片网络解码成功。

隔离App SHA256 `37907759639bce834c57c54d93a326184c515009e51cd439262a7d6837e562ab`，实际拉取installed.apk匹配。驱动e为r8/test-e.apk，散列ac74a6f0d0798d6f4961e14c03cfe2379d003a82a5df2e17e17843117a4f31d3。隔离AVD关闭、匹配进程0、ADB无emulator-5554；无截图/手机临时文件，合成记录仅在隔离AVD保留。

## 新普通候选3e66b9b5

原签名普通候选New-20261028-3e66b9b5.apk，SHA256 `3e66b9b5b956a481e8a29aea32f0c66d45a9d991b870a682d18029097920f4bf`，319466194字节。包app.quietreader/47/0.4.0-upgrade-probe，DEBUG=false/measurement=false；原证书SHA256 ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37。模型清单/五文件hash、六个64位ELF和ZIP对齐通过，全ZIP可识别明文凭据扫描无发现，不代替运行时网络审计。

专用AVD保留4ce61dc6→3e66b9b5覆盖，未重种fixture/清数据/卸载：before12/after12/models-ui19/repeat12，共55/0，各code -1。证明原保存数据、阅读/外观/会话设置、schema7迁移和合成Cookie保持及实际双模型运行释放；不是真实账号或旧revision正文缓存刷新实测。表情两轮13项证据属于隔离R8字节，不追称普通包该分支实际运行。

普通实际base.apk与构建hash一致，安全副本再次一致，current-candidate.json更新3e66b9b5，phoneInstalled=false/published=false。升级AVD关闭后即时ADB尚有offline条目（cleanup-immediate.json），后续复查进程0且ADB已无emulator-5562（cleanup.json），保留userdata。手机仍f0d4dd5a；本续验未操作手机、推送或发布。真实账号、完整贴吧、ARM资源/拔线能耗、个人推荐和独立去重等门槛继续开放。
