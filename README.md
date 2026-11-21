# eSIM transfer

Android application for transferring a SIM from the current Android device through Google Play services and saving download information for the receiving device.

[Downloads](https://github.com/lilyzhaun/esim-transfer/releases) · [Chinese README](README.zh-CN.md)

## Use

Enable the module in LSPosed, select Google Play services in its scope, and reload Google Play services. Open the app and connect the service. Select the source SIM, enter the target EID and IMEI and any carrier-required fields, then complete the system's source-device verification. Use the result on the target device.

The interface uses miuix and provides configurable appearance, floating navigation, saved targets, and persistent transfer history. Download information does not by itself confirm installation on the target device. Availability depends on the carrier, account, and devices.

Transfer history, carrier web verification, and task-log export are available in the app. Carrier and Google Play services compatibility can change.

The application UI remains in Simplified Chinese. English repository documentation and build messages do not change the application's language.

## Source and licensing

Files in this repository are licensed under Apache-2.0 unless otherwise noted. Bundled components retain their respective terms; see `NOTICE.md` and the in-app license notices.

## Building

Clone this repository. Install JDK 21, Android SDK Platform 37, and Build Tools 36.0.0. Set `ANDROID_HOME` or configure `sdk.dir` in `local.properties`. The Gradle wrapper resolves dependencies from Google Maven and Maven Central.

```sh
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

APKs are written to `app/build/outputs/apk/`. Debug builds are signed with your local debug key; release builds require your own signing key to install. A locally signed APK cannot update an installation signed with a different key.
