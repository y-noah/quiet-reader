# 推荐式更新接线与本地验证

状态：曝光、质量层传递、显式换批已接到真实首页，并完成 Debug、隔离 R8 和保留普通 userdata 的 schema7→8 升级检查。不是完整功能验收/发布；手机和 current-candidate.json 仍是 c315361c/schema7，未访问手机、推送或发布。

## 实现与边界

- Row 保存 semantic-category-v1 的 0/1/2 兴趣层。缺失、错误类型、不支持版本等视为未知；不解析原因文案。SemanticOrder 产生新分类，平台代表/来源筛选保留分类。未知行作为顺序屏障。
- 显式更新从当前 pending 或当前池读取候选，检查来源/偏好版本、来源筛选及每个成员当前完整元数据和原有语言/视频/来源排除规则。无效则用当前合法候选重建，不能复用旧兴趣分类。
- 同一个连续质量层内稳定排列未见、近期曝光、有效读过。曝光冷却2小时、有效阅读24小时，均为初始参数；未点击不产生负面反馈。没有新内容不随机洗牌。每次换批保存原相关性位置，冷却结束恢复原相关性顺序；时间排序保持不变。
- SQLite 事务内读事实、检查上下文、按需提交快照；顺序不变时复用原快照。准备快照不激活，失败保留原列表。曝光/换批不改变模型缓存或偏好版本。
- 连点合并成一个进行中的动作；较新 pending 在同一动作内最多追读2次，持续出现更新时保留额外 pending。离开首页、后台、真正开始滚动时取消迟到的 UI 发布；无轮询。显式更新先换现有池，无 pending 时随后获取来源新候选，自动抓取/模型结果仍只准备 pending，不被授权替换阅读中的列表。
- 尚无可靠的重要进展检测，verifiedProgressAt仍为0。换标题/排名上涨/重复报道不能绕过冷却。没有扩大来源池或承诺每次刷新都有新优质内容。

## 证据

目录 artifacts/local-ai/interest-band-20261028/。所有下列 instrumentation 成功文件均含 INSTRUMENTATION_CODE: -1；shell exit0不算通过。

- JVM最终731项、110套，0失败/错误/跳过（兴趣层11项，包括SQLite序列化的独立JSON部分、版本/类型拒绝、真实语义分类、质量屏障、基线恢复）。Debug/Release完整Lint通过。
- final-refresh-snapshot.txt 11/0：真实SQLite质量层往返、未见/曝光/已读层内顺序、准备不激活、偏好版本独立、重复刷新不造快照、筛选隔离、受控失败事务回滚、冷却恢复、时间排序、偏好变更/禁用来源拒绝。
- final-recommendation-refresh.txt 与 repeat-recommendation-refresh.txt 各6/0：真实圆点与Controller/SQLite接线；受控曝光事实，有意不冒充自然曝光样本。连点合并、实际前排换入未见、已读更后、激活身份、后台取消/不激活。
- final-pending-acceptance-race.txt 15/0：当前、旧偏好、旧来源三种场景下，点击中的新结果竞争仍被正确接受，不丢新pending。
- final-exposure-viewport.txt 7/0：实际绑定可见行/前后台，不是实际手势或帧耗电测试。圆点原回归 update-dot-top-raw.txt 与 repeat-update-dot-top.txt 各13/0。
- repeat-explicit-feed-cache-action.txt 6/0：显式动作重新授予有限预读额度，自然到期及后台无新运输。首次 final-explicit-feed-cache-action.txt 为4/1：测试等待snapshot==clicked，该断言与允许换批创建新id的新行为不兼容，首次失败没有输出实际最终id，不能唯一归因。修正检查为已消费clicked池且新id>=clicked、无进行中任务/pending后复验6/0；另在cache-pool-identity-final.txt加强所有成员id+textHash集合与条数校验，并输出实际id。原失败保留，不作为通过。
- r8-refresh.txt 7/0：无生产类反射，实际非debug混淆首页自然停留产生曝光、屏外不曝光、真实圆点换入第一条未见、30行质量层保存、无兴趣反馈。合成中文候选，不是本人推荐收益。隔离R8 app 48454d4134e517a6f05532ebdb76d36897b02ecf94893e05096cd5f23110a72c，driver d004e5b9c8f453d06bb2a361373386daa48efd14ea4d51e8e6af0af466376795，归档r8/。

## 普通保数据升级

保留 quiet-reader-upgrade-api36 的 c315/schema7，未wipe、卸载或重种旧保存夹具。升级前第一次未等待boot_completed，归档驱动安装被系统拒绝；随后系统启动后现有旧驱动运行，upgrade-before.txt 为12项1失败：合成.invalid Cookie过期。其余数据检查通过，不能记作整轮成功。

显式检查boot_completed=1后安装归档schema7驱动。既有探针仅在原Cookie TTL确已超过且域为空时续期同一nonce，upgrade-renew-expired-cookie.txt 8/0；没有修改旧历史/收藏夹具。升级前复验 upgrade-before-repeat.txt 12/0。原签名-r升级后 upgrade-after.txt 12/0、normal-models.txt 19/0、upgrade-repeat.txt 12/0，合计55项成功检查，另保留首次失败与续期记录。旧配置/阅读位置与展开/收藏/schema8迁移、合成持久会话以及双模型JNI/退出通过；合成Cookie不证明真实平台登录权限。

普通候选仅归档normal/app.apk：449ca853952227a0f82fe1dd65e30b9fa71939c7eb8fcc1b2c3950d875a21b0e，319466194字节；driver 2ac56f0fd32ad4baa148062049013fdddcf509c4368e7c4fc4c7d3e5ab673641。实际拉取的模拟器base.apk散列完全匹配，证书ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37与原包相同。未更新公开/手机候选指针。

## 未关闭的门槛

terminal-summary.json逐项核对上述最终探针各唯一成功终态。缓存成员身份加强检查cache-pool-identity-final.txt为6/0/code -1，clicked=3、actual=4，全部成员身份集合一致。cleanup.json确认本轮两个模拟器的匹配进程0，userdata保留；current-candidate仍c315361c，手机未操作。

重要进展证据与重现、实际本人多轮推荐收益、来源供给评估、当前普通包真机整App≤1GB峰值/拔线耗电温升、长期缓存、真实受限账号权限、完整贴吧分页和图片重载早期续页问题仍开放。不能把本轮合成换新率称为推荐准确率，也不把混淆隔离包专项追称普通包全部页面都测过。
