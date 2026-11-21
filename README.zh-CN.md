# eSIM transfer

通过 Google Play 服务转出当前 Android 设备上的 SIM，并保存接收设备下载信息的应用。

[下载正式版](https://github.com/lilyzhaun/esim-transfer/releases) · [English](README.md)

## 使用

在 LSPosed 中启用模块，作用域勾选 Google Play 服务，重新加载 Google Play 服务后回到应用连接。选择源 SIM，填写目标 EID、IMEI 和运营商要求的信息，按系统提示验证源设备身份，再在目标设备使用返回的下载信息。

应用支持 miuix 外观、悬浮导航、常用目标设备和转移记录。取得下载信息不代表目标设备已经安装成功，实际资格由运营商、账户和设备决定。

应用支持转移记录、运营商网页验证和任务日志导出。实际可用性取决于运营商及 Google Play 服务版本。

App 界面保持简体中文；仓库的英文 README、代码说明和构建提示不改变应用语言。

## 源码与授权

除另有说明外，本仓库文件采用 Apache-2.0 许可。随应用分发的组件遵循各自许可，见 `NOTICE.md` 和应用内许可证声明。

## 构建

克隆本仓库，安装 JDK 21、Android SDK Platform 37 和 Build Tools 36.0.0，设置 `ANDROID_HOME` 或在 `local.properties` 配置 `sdk.dir`。Gradle Wrapper 会从 Google Maven 和 Maven Central 解析依赖。

```sh
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

产物位于 `app/build/outputs/apk/`。调试包由本机调试密钥签名，发布构建需用自己的密钥签名后才能安装。签名不同的安装包不能覆盖升级。
