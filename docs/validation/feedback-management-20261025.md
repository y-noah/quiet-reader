# 可调教推荐的反馈管理验证（2026-10-25）

## 范围与实现

文字平台个性化需要用户能查找并纠正已有内容、平台和行为反馈。此前反馈记录按事件最旧优先混列，长sample整段出现在列表中，点选即撤销。

DiscoveryActivity反馈管理现按最近优先展示；全部/内容偏好/平台偏好/阅读弱参考四种筛选独立。source事件作为平台反馈，READ/SAVED作为弱参考，其他为内容反馈。列表最多80码点预览，详情保留原完整样本、记录时间及有意义的失效时间。详情明确确认撤销，返回保留筛选，空分类仍可切换。SAVED详情说明撤销推荐参考不删除稍后读。只有显示顺序反转，存储事件顺序、模型、平台权重、反馈id与撤销协议未修改。

长文本/大字体现场发现原长按钮标签使底部堆叠，返回入口被裁剪；改为简明“返回 / 撤销反馈”，说明文字仍明确只撤销这一条。没有缩小用户字号、裁掉样本或扩大窗口越界。

## 证据

全部报告与截图位于artifacts/local-ai/；均使用隔离测试库、模拟器API36和合成反馈。

- feedback-history-baseline-api36.txt：既有平台15项通过，新管理检查失败，16/1。120条弱阅读与2条显式平台选择混列，缺少筛选。
- feedback-history-final-api36.txt：21/2。新对话框切换后无障碍树尚停留旧窗口，检查过早。后改为有界等待实际标题，不放宽断言；feedback-history-verified-api36.txt为21/0。
- 添加浅深配置时build-feedback-history-layout.txt因诊断局部变量作用域未插入而编译失败；修复诊断后build-feedback-history-layout-fixed.txt成功，生产逻辑未因此调整。
- 普通平台短样本320dp/150%浅深各21/0。加入长内容MORE后默认宽度23/0、窄屏浅深各23/0。完整sample可在滚动详情中取得，列表短预览未误作丢失数据。
- 上述23/0仍不足以证明按钮完整可见：目视长文本截图发现“返回记录”部分被裁。增强检查后feedback-history-footer-baseline-light-320-150-api36.txt为24/1，返回可见高度76px，密度3下约25dp，低于48dp。
- 缩短操作标签后，feedback-history-footer-final-light-320-150-api36.txt与feedback-history-footer-final-dark-320-150-api36.txt均24/0。返回与撤销完整可见高度153px（51dp），均落在活动窗口边界内；两个最终长详情截图已查看，底部同排完整呈现。筛选/回退/完整文字/撤销仍通过。
- 初版feedback-history-platform-default.png与detail-default.png截在进入动画中，不作为稳定透明度证据；最终截图增加350ms动画等待与两次postOnAnimation再捕获。
- discovery-ui-feedback-history-api36.txt为77/0，发生在最后仅缩短详情按钮标签之前；最终按钮链路由24项检查覆盖。
- 最终JVM XML：414项/0失败/0错误。git diff --check及新增未跟踪Java/脚本尾空白、冲突标记检查通过。

## 可重复执行

scripts/test-feedback-management.ps1 -Theme light / -Theme dark；默认320dp/1.5，可选360/412和1.0/1.3。限定项目API36 AVD，保存报告、截图并检查配置、24/0结果；finally恢复屏幕尺寸/密度/系统字号。诊断自身恢复App浅深外观，测试库结束删除。

## 验证边界

没有新增推理、云调用或反馈上传。本轮未验证真实用户推荐命中率、完整TalkBack或所有系统外观；界面改进不等于推荐算法质量验收。真实账号、独立个人留出集、Find X7 Ultra整App峰值RAM与耗电、精确签名升级仍待完成。未提交、推送、发版或改品牌。