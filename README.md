# eSIM transfer

Android application for selecting a source SIM, verifying a transfer, and saving the download information for a target device.

[Downloads](https://github.com/lilyzhaun/esim-transfer/releases) · [Chinese README](README.zh-CN.md)

## Use

Install an official release. Select an execution mode, connect the required service, choose the source SIM, complete any required device information, and provide the target EID and IMEI. Follow the source-device verification flow and use the returned download information on the target device.

The interface uses miuix and provides configurable appearance, floating navigation, saved targets, and persistent transfer history. Download information does not by itself confirm installation on the target device. Availability depends on the carrier, account, and devices.

The application UI remains in Simplified Chinese. English repository documentation and build messages do not change the application's language.

## Source and licensing

Files in this repository are licensed under Apache-2.0 unless otherwise noted. Bundled components retain their respective terms; see `NOTICE.md` and the in-app license notices.

## Building

Release builds use JDK 21, Android SDK 37, and Build Tools 36.0.0. Additional build inputs and signing configuration are supplied by the build environment.

```sh
./gradlew :app:assembleRelease -PbuildInputsDir=/path/to/build-inputs
```

For ready-to-install packages, use the signed APKs and SHA-256 checksums under Releases.
