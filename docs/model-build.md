# 本地模型开发构建

本说明是开发环境复现，不是向用户交付“请自己搭环境”。试用包仍需完整离线模型与已验证初始化。

1. 在隔离Python环境安装 `scripts/models/requirements.txt`。Windows使用较短路径，避免SDK/编译工具的路径长度问题。
2. `prepare_models.py` 从固定公开revision下载并检查模型与词表；无Key/登录。已有完整文件可加 `--offline`。生成 `app/src/main/assets/local-ai-manifest.json` 和忽略的 `.tools/model-package/models`。
3. `prepare_native.py --root <短路径>` 获取固定官方SentencePiece源码并核对SHA256与源文件；生成随包许可证。源码不进普通Git。
4. Android SDK platform36、NDK28.2.13676358、CMake3.22.1、JDK21；Gradle8.11.1、AGP8.9.2。
5. Gradle传 `-PsentencepieceSource=<短路径>/sources/sentencepiece-31646a467d2051eb904e0b45de3a73e91fe1c1e3` 与 `-PnativeBuildRoot=<短路径>/build`，正常编译JNI；仅目标库构建，不编译训练器。默认保留四ABI。
6. 需要离线完整模型包时加 `-PbundleLocalModels`。普通开发可沿用设备已安装且hash一致的私有模型；不得把无模型诊断包当完整试用包。
7. `verify_models.py`生成HF原始分词/桌面INT8 golden fixtures；`compare_quantization.py`用固定社区FP32作烟雾对照。
8. `scripts/models/build-probe.ps1 -Offline -BundleModels` 是当前本地短路径环境的便捷入口。`-Target store` 测SQLite，`-Target back` 测系统返回，默认 `models`。实际签名安装使用既有 `install-diagnostic.ps1`，仅允许已核对的项目AVD。
9. 模型检查分两次新的instrumentation进程：`-e mode parity` 与 `-e mode lifecycle`。默认从APK安装；历史外部目录导入须显式 `-e modelSource external`，API36不依赖此路径。
10. `check_apk.py <已签APK> --output <证据json>`检查64位ELF16KB对齐、记录大小/hash；还要跑Android zipalign和真实ARM设备，不能只看静态检查。

`-Target store` 的 `FeedStoreInstrumentation` 默认运行真实SQLite与受控多源并发检查，不访问平台。显式 `-e mode live-sources` 才请求九个平台热门入口；`-e mode live-browser` 对贴吧/B站/抖音逐个运行正常WebView，单来源最多30秒，不自动登录或操作验证码。这两种live诊断的成功退出只表示探针完成，必须查看每个来源的实际条数/状态，不能当成九源全部可用。网页诊断样本只留在忽略的artifacts和项目模拟器测试目录。

版本、数字、失败/未知状态以 `validation/local-ai-p0-20261023.md` 和实际证据为准。没有发布、没有改品牌、没有新增云AI依赖。
