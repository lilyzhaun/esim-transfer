# eSIM transfer

用于选择源 SIM、完成线路转移验证，并保存目标设备下载信息的 Android 应用。

[下载正式版](https://github.com/lilyzhaun/esim-transfer/releases) · [English](README.md)

## 使用

安装正式版，选择运行模式并连接所需服务，选择源 SIM，填写所需设备信息和目标 EID／IMEI。按提示验证源设备身份，再在目标设备使用返回的下载信息。

应用支持 miuix 外观、悬浮导航、常用目标设备和转移记录。取得下载信息不代表目标设备已经安装成功，实际资格由运营商、账户和设备决定。

App 界面保持简体中文；仓库的英文 README、代码说明和构建提示不改变应用语言。

## 源码与授权

除另有说明外，本仓库文件采用 Apache-2.0 许可。随应用分发的组件遵循各自许可，见 `NOTICE.md` 和应用内许可证声明。

## 构建

使用 JDK 21、Android SDK 37 和 Build Tools 36.0.0；额外构建输入及签名配置由构建环境提供。

```sh
./gradlew :app:assembleRelease -PbuildInputsDir=/path/to/build-inputs
```

需要直接安装时，请使用 Releases 中的签名 APK 和 SHA-256 校验文件。
