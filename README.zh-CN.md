# eSIM transfer

用于选择源 SIM、完成线路转移验证，并保存目标设备下载信息的 Android 应用。

[下载正式版](https://github.com/lilyzhaun/esim-transfer/releases) · [English](README.md)

## 构建

本仓库只保存公开生产源码和最小 Android 构建配置。`runtime` 是固定到具体提交的私有源码依赖，完整构建需要私有仓库读取权限；仅克隆公开仓库不会获得完整实现。

需要 JDK 21、Android SDK 37、Build Tools 36.0.0，以及私有依赖访问权限。

```sh
git clone --recurse-submodules https://github.com/lilyzhaun/esim-transfer.git
cd esim-transfer
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleRelease
```

请使用仓库固定的依赖提交，不要随意替换为其他分支最新版本。默认输出未签名 APK；本机未跟踪的 `signing.properties` 可提供 `storeFile`、`storePassword`、`keyAlias` 和 `keyPassword`。禁止提交签名资料。

正式签名 APK 与 SHA-256 校验文件通过 Releases 发布。应用支持 miuix 外观、悬浮导航、常用目标设备和转移记录。取得下载信息不代表目标设备已经安装成功，实际资格由运营商、账户和设备决定。

## 源码与授权

公开自有源码使用 Apache-2.0，第三方署名见 `NOTICE.md`。私有运行时使用独立授权，不属于公开仓库源码许可证的范围。开发、测试和研究在独立工作区完成；本仓库仅通过 PR 接收干净的生产代码。
