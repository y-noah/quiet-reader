# 息屏预读生命周期补验 — 2026-10-28

本轮仅扩展 PrefetchLifecycleProbe，没有修改生产代码、普通候选或手机。依据用户要求，主动缓存应由操作触发，后台及息屏不应持续投机加载。旧HOME和空闲额度验证不等同实际息屏证据。

## 方法与结果

API36专用模拟器 quiet-reader-api36 / emulator-5554，隔离debug App app.quietreader.discoveryprobe。测试沿用随机独立数据库、70条明确合成的文字候选、真实Activity/Controller/SQLite/Repository/Handler，仅替换正文Loader，记录实际调度和取消。没有真实站点请求、模型或用户偏好收益数据。

新增操作使用系统 SLEEP(223)、WAKEUP(224)，只在无密码模拟器 dismiss-keyguard，要求 PowerManager.isInteractive 与实际Controller前后台状态同时匹配。息屏后验证动作额度被清除、Loader不busy；再交付已取消请求的迟到正文，要求共享缓存仍为空。息屏期间每250ms触发一次实际viewport路径，共8次，要求请求数不增长且Loader保持空闲。唤醒后要求真正恢复前台且请求数增长，不以发出唤醒命令视为通过。随后仍运行原21.5秒空闲到期及新操作恢复检查，不能用自动布局续租。

artifacts/local-ai/screen-off-prefetch-20261028/ 中 lifecycle.txt 与 lifecycle-repeat.txt 分别24检查/0失败、唯一INSTRUMENTATION_CODE -1。两轮均包括实际HOME、Activity重建、窗口取消/快速返回、旧请求迟到拒绝、息屏/唤醒和额度到期。首次24项与重复24项各自完整执行，不计作48次真实网络下载。

## 包与环境

隔离App SHA256 3d982aa996cae43e982e2682eb5363283fbebba1b81baf3659d1ab3c830f0047，驱动 e5de7bd7e35e53db0213ad760be83f134f8977bbf1fe9d8e0db8742e2cc9fcb9。build.log 为初次普通debug构建，isolated-build.log 为最终隔离构建，均终态成功。没有将本debug结果算作普通签名R8包或ARM验收。

初次普通debug安装被模拟器旧普通App签名不匹配拒绝（INSTALL_FAILED_UPDATE_INCOMPATIBLE），没有运行测试或清除数据。随后改为已有隔离包配置，安装成功后才开始两轮。一次单纯状态查询的自动审批超时，未执行；后续模拟器操作获准执行。均不属于产品测试失败，也没有隐藏成测试通过。

cleanup.json记录测试/App force-stop并关闭模拟器，匹配进程0、adb中emulator-5554消失，userdata保留；没有操作手机、修改其亮屏设置、安装驱动或绕过认证。

## 边界

仅证明该受控正文调度路径在API36模拟器息屏/唤醒和空闲回调中的取消、迟到结果隔离及恢复。2秒息屏观察不证明长期CPU零占用；不验证真实HTTP取消时间、模型退休、所有阅读页/登录页生命周期、ColorOS策略、持续内存峰值或拔线能耗。普通候选仍055b1f1c，手机仍f0d4dd5a；整体验收保持开放，未推送或发版。
