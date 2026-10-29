# Find X7 Ultra 原包升级试用（2026-10-25）

用户本轮明确授权替换手机原App并参与实际浏览反馈。此授权更新先前仅隔离安装限制；未授权公开发版、改品牌或清除数据。

实际设备43062fa0/PHY110，原app.quietreader为46/0.3.35、target35。pull原base.apk留存original-v46.apk，未获取/导出私人数据库或Cookie。原/新apksigner verify均成功，证书SHA256同为ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37。

使用既有-NormalUpgrade离线构建成功（BUILD SUCCESSFUL），默认工程版本仍46/0.3.35；安装包为47/0.4.0-upgrade-probe、target36、普通包名、non-debuggable/R8/内置双模型。SHA256 7F18E190444F1163CA355C2F24322B62465C61DC62B553A830115C7D22F66359，319414926bytes。同目录归档App与mapping，不将隔离App D457C933…的测试自动视为本包全通过。

adb install -r --no-incremental返回Success；没有uninstall/pm clear/降级/合成候选seed，没有安装测试驱动。安装后version47，首次安装时间仍2026-10-02 19:01:15；安装后的base.apk再次pull散列与归档完全相同。am start -W DiscoveryActivity返回Status ok、进程存在，所查crash buffer未出现普通App fatal记录。这不是完整页面/登录/数据内容验证。

截图全黑后核对系统状态：mWakefulness=Dozing、Keyguard showing=true，手机处于锁屏。没有绕过锁屏或改变安全设置，真实页面视觉需用户解锁后继续确认。现有收藏/历史/登录保持依赖同签名-r升级，尚未逐项检查用户真实数据，不能写为全部保持已验证。

证据：artifacts/local-ai/findx7-user-upgrade-20261025/中的package-before/after.txt、install.txt、installed-hash.json、crash-buffer.txt和归档APK；构建日志build-findx7-user-upgrade-20261025.txt。安全截图保存在独立review目录。没有公开发布、推送、改名/Logo。用户正常试用优先，自动固定100条脚本不能在原包随意seed或覆盖用户画像。
## 后续短测、授权和清理

用户要求先由开发侧测试后再拔线。安装已归档隔离App D457C933…及驱动9FDCF860…，两次成功。普通App真实解锁首页已观察到真实来源候选，首页上部确实占用较多空间；未通过私人数据库或Cookie导出验证全部数据保持。

findx7-dual-online-short-20261025-a COMPLETE0/1，34ms/0采样，初始化IllegalStateException。定位测试驱动要求停止通知，但ColorOS初装默认POST_NOTIFICATION ignore。ADB pm grant被系统SecurityException拒绝，未绕过；通过标准APP_NOTIFICATION_SETTINGS页面观察并开启该隔离App的允许通知开关，appops随后为allow。权限只属于临时包，未给用户原App新增通知权限。

b轮同App/同驱动DUAL/cold/online/主动预读，COMPLETE5/1，20082ms/9采样。实际100候选准备、101向量/6精排对、点击前10条正文证据、首篇真实展开/滚动/返回链已运行，随后第8索引预期标题不可见，整轮失败。首篇Activity/DOM/稳定控件观察109/128/288ms，非帧时序或性能验收；完整100条及30分钟真机未通过。尚未隔离列表变化/定位/时序根因，新增失败时activeSnapshot/viewport范围/树/截图诊断，未在手机重跑。

用户随后询问能否拔线，已停止隔离App、卸载flowprobe.test与flowprobe，均Success；pm list packages无临时包、两目录均不存在，证据phone-cleanup.txt。普通app.quietreader仍47/0.4.0-upgrade-probe，重新启动Status ok，未清除数据。用户可拔线日常试用，本轮没有完成30分钟真机长测或手机耗电验收，不能包装为通过。

用户新增首页要求：删除稍后读、科技/社会/文化等分类、卡片序号和行内排序说明，压缩首屏操作到设置所在工具栏。已写入主需求文档覆盖旧条款，后续代码正在电脑构建/布局验证，手机已装47不含该轮UI变更。名称/Logo仍未更改。