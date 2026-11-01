# LanCam

Turn an Android phone into an **offline Wi-Fi / LAN camera** (up to 8K, H.265) that any device on the same router can discover and use, e.g. OBS Studio on a laptop.

## How it works
- **Camera2** captures at the highest resolution the phone supports (8K if available, otherwise 4K/1080p).
- **Hardware H.265 (HEVC)** encoding, falls back to H.264 if HEVC is unavailable.
- A built-in **RTSP server** (via the open-source RootEncoder / RTSP-Server libraries) runs on the phone, port `8554`.
- The phone advertises itself on the LAN with **mDNS / DNS-SD** (`_rtsp._tcp`) using Android NSD.
- **No internet use**: no analytics, no cloud, no update checks. The only network traffic is the RTSP stream to clients that connect to it and mDNS on the local subnet. Any RTSP client whose IP is not a private/link-local LAN address is disconnected immediately. Still, don't port-forward 8554 on your router.

> Android requires the `INTERNET` permission for *any* socket, including LAN-only ones. The app never opens outbound connections.

## Download
Every push to `main` builds a debug APK via GitHub Actions: open the **Actions** tab, pick the latest *Build APK* run, and download the `LanCam-debug-apk` artifact.

## Build locally
1. Open the project in Android Studio (Ladybug or newer), JDK 17.
2. Build & install: `chmod +x gradlew && ./gradlew installDebug` (on Windows: `gradlew.bat installDebug`)
3. Grant camera + microphone permissions.

## Use with OBS Studio
1. Connect phone and laptop to the same Wi-Fi router (router can be fully offline).
2. Open LanCam, pick resolution, tap **Start**. The app shows the stream URL, e.g. `rtsp://192.168.1.42:8554/live`.
3. In OBS: **Sources > + > Media Source**, untick *Local File*, paste the URL in *Input*, set *Network Buffering* to ~1 MB, and untick *Restart playback when source becomes active* if you want lower latency.

### Discovering the camera
- macOS: `dns-sd -B _rtsp._tcp` then `dns-sd -L "LanCam-<model>" _rtsp._tcp`
- Linux: `avahi-browse -rt _rtsp._tcp`
- Windows: any Bonjour/mDNS browser, or use the URL shown on the phone.

## 8K notes
- Only some phones (recent Samsung Galaxy S/Ultra, some Xiaomi) expose 8K (7680x4320) to third-party apps via Camera2; many cap third-party apps at 4K.
- 8K30 HEVC needs ~80-120 Mbps sustained. Use **Wi-Fi 6/6E at 5 or 6 GHz**, close to the router.
- Phones get hot at 8K; keep it plugged in and ventilated.

## Settings
Resolution, FPS and bitrate are selected on the main screen. Port and stream path are constants in `StreamConfig.kt`.
