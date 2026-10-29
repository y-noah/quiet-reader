# 虎扑可见媒体核对与旧正文刷新（2026-10-28）

## 可复现缺口

核对此前真机登录公开帖成功与受限帖未验收记录时，发现HupuDiscussion的rendered核对只使用Jsoup纯文字。新增受控测试后，HupuDiscussionTest共27项/4失败（before.xml/log）：纯图片主帖被拒绝；纯图片回复在需要DOM佐证的public status3或已登录LOGIN_SEE路径被丢弃；只有文字一致却JSON含来源未显示的附图仍被认可；隐藏或不同来源图也不能阻止这种文字误认可。

这些是源码受控复现，不能把之前真机70来源回复与67阅读回复的差额归因为本缺陷，也不能追称此前虎扑所有打不开都已定位。未读取真实账号或新受限帖子。

## 修改边界

内容佐证改为纯文字加按顺序的规范化可用图片URL，以JSON数组编码保持边界。复用正文SourceParser.imageSource的src/data-src/data-original优先级与URL规范化，按实际请求页面作基准，不猜测缩略图/签名参数等价。来源DOM排除广告/推荐/隐藏祖先，并在克隆上移除hidden、aria-hidden及常见内联隐藏后代；不操作远端DOM或尝试绕过登录。

原帖ID、URL、页码、成功响应、账号非零和ALL_SEE/LOGIN_SEE规则不变；SELF_SEE等未知范围仍拒绝。没有放宽身份/权限准入。严格图片核对也可能使加载尚未完成、地址确实不同的来源保持不可用，不能用文字相同兜底认可JSON附图。Jsoup无法解析全部样式表/运行时可见性，当前仍是已知结构及可见标记核对，不是完整浏览器权限验证。

新增hupuBodyRevision=1，随Document复制、指纹、历史编码解码保留。旧记录缺字段解为0：内存、磁盘及阅读历史在线不再直接复用；离线保留旧内容及旧版本，阅读位置/展开信息保留，不把旧内容重新标成当前已解析。未改数据库schema或删除账号/偏好。原R8合成正文种子补当前版本字段，避免未来合成回放误用旧版夹具；该字段仅声明测试夹具版本，不代表真实来源权限通过。

## 已取得证据

- 修复后的目标28项通过（包括旧版本回归），全量JVM687项，失败/错误/跳过均0；after.xml/jvm-summary.json和构建日志归档。已有捕获的public status3 HTML回归仍通过；不是新实时来源样本。
- Debug/Release lint终态成功，lint.log。
- API36隔离debug实际磁盘/历史/内存两轮各59项/0失败/code -1，其中新增虎扑版本七项。新正文真实落盘/还原保持指纹；旧版在线拒绝、离线保留图片与版本0；在线历史保留位置199及展开节；原始历史版本不被改写；实际Repository内存入口拒绝旧版。测试使用随机ContextWrapper隔离缓存/偏好，最后退休异步写入的namespace并清理。
- debug-package.json归档真实执行的App/驱动，installed-debug-hash.json实际base.apk一致。
- 后续ReleaseHupuMediaProbe仅用框架API与隔离模拟器，受控HTTPS响应经过实际R8 Repository→SourceParser→图片读取/解码→普通退出历史。公开status3及合成LOGIN_SEE两路径各保留纯图主帖/回复，JSON单有附图、不同DOM图、隐藏DOM图三类回复均排除；保存的版本1与源帖/回复ID正确，未认可附图未落历史。两轮各14/0/code -1，r8/media-fixed-driver.txt及media-repeat.txt；安装实际base.apk的C8FF32E1哈希与归档包一致。没有生产类反射或模拟器外执行。
- 首次r8/media.txt为4项1失败：Android JSONArray字符串转义斜线与浏览器JSON.stringify编码不同，原测试错误地比较编码字符串；只改为解析数组逐项比较后通过，保留原失败日志。未为此改变产品代码。
- 普通原签名候选81ab481c完整构建终态成功，normal/归档。原证书ab59106c、16KB对齐、包app.quietreader/47/target36、五项模型文件长度及SHA256与清单一致，已知token模式扫描无匹配；后者仅限定格式/条目扫描，不是完整隐私审计。
- 原保留userdata升级模拟器3e→81ab：升级前12、覆盖后12、真实双模型19、重复数据12，共55项/0失败，各code -1。schema7、原收藏、外观/来源偏好、阅读位置、合成持久Cookie保留；实际安装hash与普通归档81ab一致。初次在系统尚未完成启动时启动instrumentation，AMS留下PID0未启动记录；归档upgrade-start诊断，force-stop未返回后明确重启模拟器并记录原会话INSTRUMENTATION_ABORTED终态，待boot_completed=1后才重跑成功。无wipe/reseed，不能把被中止的首次当通过。

## 状态和后续

隔离模拟器和升级模拟器force-stop测试包后关闭，cleanup-r8-upgrade.json确认匹配进程0且ADB无emulator-5554/5562，保留userdata。无手机操作、认证重试、截图/账号导出或手机设置改变。无推送/发版。

本地安全候选更新为New-20261028-81ab481c.apk，完整SHA256为81ab481cc2ea9963150cf4450325f239917964ecd5d2e88c06444cf6c15d3637，319466194字节，含本次虎扑修改；New-next-trial/current-candidate.json已指向该包，phoneInstalled/published均false，手机仍f0d4dd5a。压缩包来源→阅读媒体分支及普通原签名保数据升级已获限定运行证据；真实LOGIN_SEE受限主帖/评论和跨账号权限还须手机自然解锁后验证。知乎真实登录验收、完整贴吧分页、ARM全场景≤1GB、拔线能耗、长期缓存和个人推荐/独立去重质量等整体门槛未完成。
