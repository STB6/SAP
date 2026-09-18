# SAP

<img src="assets/logo.svg" alt="SAP logo" width="128" height="128">

[English](README.md) · 简体中文

一款通过 Wi-Fi Direct 创建本地 Wi-Fi 的 Android 应用。网段和本机 IP 通常保持固定，便于访问手机上的服务。地址可能因冲突或系统差异而变化，以应用显示为准。不提供互联网共享。

## 功能

- 自定义 SSID 和密码，支持 2.4 GHz / 5 GHz。
- 查看已连接设备及系统提供的 IP、MAC 地址。
- 支持后台运行，可设置无人连接时自动关闭。
- 支持快捷设置磁贴控制开关。
- 支持简体中文和英文，采用 Material 3。

## 设备要求

Android 15 或更高版本，设备需支持 Wi-Fi Direct。频段可用性、与其他无线功能并行工作的能力及设备地址信息取决于硬件和系统。

## 构建

安装 Android SDK 37，并在 `local.properties` 中设置 `sdk.dir`，然后运行：

```sh
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

默认 debug 使用本机调试密钥，release 不签名。可通过 `keystore.properties` 指定签名配置。
