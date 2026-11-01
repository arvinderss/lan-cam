# LanCam

Turn an Android phone into an **offline Wi-Fi / LAN camera** (up to 4K, H.265) that any device on the same router can discover and use, e.g. OBS Studio on a laptop.

## How it works
- **Camera2** captures at up to 4K (falls back to 1440p/1080p).
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
3. Grant camera (and notifications) permission. Microphone is only requested if you enable it.

## Use with OBS Studio
1. Connect phone and laptop to the same Wi-Fi router (router can be fully offline).
2. Open LanCam, pick resolution, tap **Start**. The app shows the stream URL, e.g. `rtsp://192.168.1.42:8554/live`.
3. In OBS: **Sources > + > Media Source**, untick *Local File*, paste the URL in *Input*, set *Network Buffering* to ~1 MB, and untick *Restart playback when source becomes active* if you want lower latency.

### Discovering the camera
- macOS: `dns-sd -B _rtsp._tcp` then `dns-sd -L "LanCam-<model>" _rtsp._tcp`
- Linux: `avahi-browse -rt _rtsp._tcp`
- Windows: any Bonjour/mDNS browser, or use the URL shown on the phone.

## Live-streaming features
- **Runs as a foreground service**: keeps streaming with the screen locked or while using other apps. A notification shows status and has a **Stop** button.
- **Video only by default**: meant for setups where audio comes into OBS from a separate mixer. Turn on *Use phone microphone* to send phone audio too.
- **Do Not Disturb while live**: switches the phone to total silence when you go live and restores your previous mode when you stop. Needs one-time Do Not Disturb access.
- **Reject incoming calls while live**: needs LanCam to hold the *Caller ID & spam* (call-screening) role. Some Android versions only let screening apps see calls from numbers not in your contacts; DND still keeps those silent.
- Keeps CPU and Wi-Fi awake (low-latency Wi-Fi lock) while live.

## Tips
- Keep the phone plugged in and ventilated; 4K60 runs hot.
- In OBS Media Source, set *FFmpeg options* to `rtsp_transport=tcp` to avoid UDP packet loss.
- Exclude LanCam from battery optimisation (Settings > Apps > LanCam > Battery > Unrestricted), especially on Samsung/Xiaomi/OnePlus.

## Settings
Resolution, FPS and bitrate are selected on the main screen. Port and stream path are constants in `StreamConfig.kt`.
