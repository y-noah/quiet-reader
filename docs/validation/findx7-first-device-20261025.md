# Find X7 Ultra首次实际设备验证（2026-10-25）

## 设备与安全边界

用户主动连接并允许USB调试。先前仅MTP，随后ADB接口出现并完成RSA授权，未安装/替换Windows驱动。实际机型PHY110，Android16/API36、ColorOS版本字段V16.1.0、arm64-v8a，MemTotal11483108KiB，内存扩展不作为容量依据。getconf PAGE_SIZE=4096；这是真实ARM 4KB设备验证，不是16KB设备验证。USB充电，电量38%。

只安装独立包app.quietreader.flowprobe与app.quietreader.flowprobe.test，未覆盖或卸载用户app.quietreader。原版前后均versionCode46/versionName0.3.35。测试驱动首次安装-99失败，改用标准非增量、允许testOnly的安装方式成功；未绕过手机安全确认。

## 精确包

App SHA256 0f00be4ac8216712d8d67819b32704efdbcb4db62891ee046ffdc19fee608f60，319357727字节，non-debuggable/R8/内置双模型。实际安装base.apk散列一致。冷模型驱动FD5BA24B9D64C13FC8519C5B123BCC63FE84D8A0B0F7E344F3E62CFC4EA2AA12留档于findx7-cold-test-original-20261025.apk；后续core-only驱动元信息现归档于artifacts/local-ai/archive-before-settings-reader-20261025/release-discovery-flow-package.json（后续构建会更新工作目录同名文件）。生产App未因新增测试模式改变。

## 结果与适用范围

1. 冷模型22/0：验证首次无模型副本、真实UI准备校验、独立ranking进程、两条embedding归一化512维输出、两条reranker有限输出及SentencePiece JNI、批次身份/完成计数、释放后进程退出。模型文件准备2543ms；不是首次完整推荐耗时。
2. 核心阅读35/0：真实压缩生产模型/控制器/UI，合成中文候选和缓存正文；排序更新保持待确认、收藏、统一正文展开/滚动/返回、短读不记学习、真实来源少看、盲评价与准备后新池接纳/去重/偏好变化拒绝。截图已查看，普通长度作者展开可用。未做真实账号全文验证，也未对用户实际兴趣评价。
3. 核心模式明确不运行SAF导出，不创建公共下载测试文件；不声称已覆盖ColorOS系统保存选择器。

## 内存与电池证据

首次冷模型运行采样命令只选了精确主进程，3次均未捕捉ranking，163564KiB不是App整体峰值。该不足已保留原始报告，不用其数字证明资源门槛。

新增measure_device_probe.py使用dumpsys meminfo --package，核心流程0.5秒间隔，20次有效采样，实际出现主进程、:ranking及com.google.android.webview沙箱渲染进程。单次采样各关联进程PSS合计最高300606KiB，约293.6MiB；最高样本含主进程53725KiB与ranking246881KiB，渲染进程在其他时刻出现。

这属于该短合成负载的周期PSS观测，不是RSS、不是分配上限，也不证明采样之间峰值/所有隔离UID归属完整、真实百条内容长浏览或低内存负载。dumpsys本身有测量开销。未通过整个App峰值≤1GB的完整门槛。

USB充电/38%电量，电池温度基线30.9℃、结束30.7℃，不是SoC温度，也不能推断无发热或续航；本轮不报告耗电率。需另做断电受控长会话及可比基线。

## 清理与原版保护

本轮结束已经依用户要求卸载两个测试包，均返回Success。pm list packages app.quietreader.flowprobe无结果，两者/sdcard/Android/data目录均不存在。模型副本、测试库、私有缓存与截图随独立包卸载清理。仅在电脑留测试代码/日志/APK和明确的测试截图，未向手机公共目录导出数据。未清理用户原版数据、登录或系统调试设置。

日志在artifacts/local-ai/findx7-*20261025*，重点：baseline、cold-models-instrumentation/memory、reading-core-instrumentation/memory、installed-hash、after-core-battery、cleanup。

## 仍开放

手机长会话全进程峰值/热/耗电、实际认证内容、个人独立收益、ColorOS全部辅助功能/SAF、最终普通发行升级仍需验收。模拟器最大字号/极长作者的直接辅助功能点击仍有失败（reader-targets-light3-20261025.txt，9项2失败）；不能被本轮普通长度作者通过抵消。新48CSSpx独立目标与aria-expanded有布局/状态证据，但此边界尚未关闭。未提交、推送、发版、改名或换Logo。
