# 虎扑共享会话原生首选读取

## 真实问题与定位

早间手机718f6e1f中，“山姆就是这样在中国火起来的吗？”来源页已登录，手动读取正文/评论成功，自动打开与重新读取报会话变化。没有导出手机Cookie/账号存储，不能声称已掌握手机具体变化字段。

手机归还后，仅在指定隔离模拟器对同一当前公开热帖复现。浏览器首轮22.027秒超时，已有bestContent=true及64个区块；随后两轮未改生产代码也能在17.930/16.511秒成功，说明并非每次必失败。Cookie字段名时间线记录到acw_tc、sajssdk_2015_cross_new_user、sensorsdata2015jssdkcross及.thumbcache_33f5730e7694fd15728921e201b4826a随页面加载变化，触发一次重取。字段值从未写入日志，未知字段不作身份豁免；这些模拟器字段不冒充手机已登录账号的精确根因。

显式原生对照使用同一CookieManager会话，2.919秒得到主帖+63条回复、64 sections/96 blocks，无Cookie变化。对照的后续分页仍遵循旧HUPU-web历史偏好，落入浏览器并报会话变化。这揭示过去打开登录页就永久强制WebView的路由不适合虎扑服务端正文。

## 修改与边界

SourceSession对HUPU采用与V2EX同样的原生首选策略。历史登录/来源浏览仍保存会话配置，完整Cookie继续传给原生请求；不忽略Cookie字段，不给旧DOM补盖新会话，不放宽账号变化、namespace、epoch或请求版本检查。

前台PendingArticle、主动预读与DiscussionStream统一采用nativeDocumentReady：有效正文直接使用；虎扑原生LOGIN_SEE/登录占位须回退共享WebView，不能因为canPresent包含权限状态就提前结束读取。真实不可用状态仍展示；浏览器也无权限时保持受限、不缓存受限正文。其余来源权限状态沿用原路由。

## 验证

- 最终生产代码618 JVM，failures/errors/skipped均0；Debug/测试包、Debug+Release Lint和签名隔离R8构建通过，日志hupu-session-20261027-route-build.txt。R8仅构建证据，未宣称实际运行。
- HupuNativeRoutingProbe在真实MainActivity→PendingArticle→HTTP和实际WebView管线上16/0：历史HUPU-web=true仍先发原生请求，携带合成会话Cookie；原生可读不建浏览器；登录占位和HTTP403各只建一个浏览器并取得有效主帖+评论；浏览器仍受限时不解除权限、不缓存。返回文档缓存会话与当时实际会话一致。HTTP及HTML为合成夹具，不冒充真实登录账号。
- 此探针初次因CookieManager导入重名编译失败；前两轮运行4/1，夹具在动态导航开始后stopLoading再接管，产生“来源跳转”失败。等待合成Cookie完成仍复现，最终把HTTP响应暂缓，在SourceSurface首次附加WebView时接管，再放行原生响应；首次导航由既有生产代码发起，不再停止导航或伪造会话通过。所有失败日志保留，不将其归为已证实的生产故障。
- 修改后的正常生产路由（非强制native测试选项）：同一公开热帖1.678秒，browser=false，主帖+63回复可读且缓存有效；第二页0.326秒，63→78回复，主帖/原身份保留，无Cookie字段变化。只证明这一公开帖当前页及一页续读，不证明登录权限或全部历史讨论。
- 同份最终生产Debug：登录导入75/0、实际讨论/缓存/续读72/0、Cookie变化重取33/0；连同前台原生/浏览器路由16/0共196/0。保持账号变化只进行一次新取得、第二次变化拒绝旧正文、原截止时间及取消边界，不引入无限重读。真实账号及R8运行未由这些受控结果证明。

证据均在artifacts/local-ai/hupu-session-20261027-*，含baseline、timeline、native、native-forced、live-final、routing-first-navigation、route-build及源码/APK identities.json。手机未再次使用或更新，仍是718f6e1f，不包含本修复。没有推送/公开发版；真实Find X7 Ultra登录帖自动打开、重新读取及受限评论仍须复核。

回归结束后已停止emulator-5560上的隔离应用及测试进程。没有保持后台测试，也未枚举或访问手机。


# 2026-10-27 真机虎扑来源对照

普通f0生产代码不变。真实手机来源响应识别signedIn=true、公开范围ALL_SEE、主帖29字/70渲染回复。首轮自动阅读45秒内无结构化正文6/1/code0；新增测试端诊断后第二轮9/0、产品重新读取10/0/code-1，完整主帖匹配、67讨论节及前三条回复前缀3/3。不能据后续成功抹去首次失败，根因仍未知；70对67差额和受限权限未验收。临时驱动hash匹配并卸载，亮屏设置0恢复且息屏；无发布。见 [原始结果及边界](./phone-hupu-source-20261027.md)。
