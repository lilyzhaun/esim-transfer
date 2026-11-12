# eSIM transfer

Android application for selecting a source SIM, verifying a transfer, and saving the download information for a target device.

[Downloads](https://github.com/lilyzhaun/esim-transfer/releases) · [Chinese README](README.zh-CN.md)

## Build

This repository contains the public production sources and the Android build configuration. The `runtime` submodule is a private source dependency. A complete build requires permission to read that repository; the public checkout alone does not contain the full implementation.

Requirements: JDK 21, Android SDK 37, Build Tools 36.0.0, and access to the private dependency.

```sh
git clone --recurse-submodules https://github.com/lilyzhaun/esim-transfer.git
cd esim-transfer
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleRelease
```

The dependency is pinned to an exact commit. Do not replace it with an arbitrary branch head.

The release APK is unsigned unless a local, untracked `signing.properties` file supplies `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`. Never commit signing material. Official signed APKs are published under Releases with SHA-256 checksums.

## Use

Install an official release. Select an execution mode, connect the required service, choose the source SIM, complete any required device information, and provide the target EID and IMEI. Follow the source-device verification flow and use the returned download information on the target device.

The interface uses miuix and provides configurable appearance, floating navigation, saved targets, and persistent transfer history. Download information does not by itself confirm installation on the target device. Availability depends on the carrier, account, and devices.

## Source and licensing

Public first-party sources are licensed under Apache-2.0. Third-party notices are retained in `NOTICE.md`. The private runtime has separate terms and is not included in this repository's source license. Official APKs include the runtime and their license notices.

Development takes place in a separate workspace. Production changes are submitted through pull requests. This repository intentionally contains only build and runtime inputs, not the development test or research workspace.
