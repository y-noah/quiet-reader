# 息屏只读端点检查（2026-10-28）

用户询问息屏是否能继续使用手机测试，已有整夜连接测试授权。本轮仅通过ADB读取，不唤醒、不解锁、不安装、不启动App，不访问页面/账号，不改变手机设置。USB调试设备43062fa0在线，06:45附近读取电源为Dozing、充电中、stayOn=false。

`artifacts/local-ai/phone-screen-off-20261028/`保存限定结果。meminfo返回`No process found for: app.quietreader`；再次ps -A按包名前缀与UID10380筛选0行，包管理查询确认`package:app.quietreader uid:10380`；activity services指定本包返回(nothing)。Manifest的实际模型进程名为:ranking，已由包名前缀检查覆盖，不使用先前pidof仅枚举的错误模型后缀作为证据。power.txt中的本包launch/AudioMix记录是历史事件，不能解释为当前仍持锁。

当前端点未见New主进程、命名模型进程或本包活动服务，不代表持续整夜无进程、无唤醒、零耗电，也不能据此识别App退出的原因。没有将充电息屏观察当作拔线能耗验收。保留的activity片段包含历史安装任务提及，不作为当前App前台/存活证明。

息屏且ADB在线时可以继续日志/进程等检查；实际页面操作/可见性验收需要亮屏。系统若要求认证，不在用户睡眠期间尝试或绕过。生产代码、普通候选f382、手机安装版本均未修改，未发布。
