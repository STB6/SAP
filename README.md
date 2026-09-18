# SAP

<img src="assets/logo.svg" alt="SAP logo" width="128" height="128">

English · [简体中文](README.zh-CN.md)

An Android app that creates local Wi-Fi using Wi-Fi Direct. The subnet and phone IP address usually remain fixed, making local services easy to access. Addresses may change due to conflicts or differences between systems. Check the app for the current address. Internet sharing is not provided.

## Features

- Customize the SSID and password, with support for 2.4 GHz / 5 GHz.
- View connected devices and their IP and MAC addresses when available from the system.
- Keep local Wi-Fi running in the background, with optional automatic shutdown when no devices are connected.
- Toggle local Wi-Fi with a Quick Settings tile.
- Supports English and Simplified Chinese, with Material 3.

## Device requirements

Android 15 or later with Wi-Fi Direct support. Available bands, coexistence with other wireless features, and device address information depend on the hardware and operating system.

## Build

Install Android SDK 37, set `sdk.dir` in `local.properties`, then run:

```sh
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

By default, debug uses a local debug key and release is unsigned. Custom signing can be configured in `keystore.properties`.
