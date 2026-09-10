# Pi Web Android Client

Android native client for [pi-web](https://github.com/jmfederico/pi-web) server.

## Features
- **Native Material 3 UI** (Jetpack Compose)
- **REST + WebSocket streaming** (real-time agent responses via OkHttp & Coroutines Flow)
- **Multi-session browsing and switching**
- **Custom endpoint support** (Tailscale MagicDNS, Cloudflare Tunnel, LAN IP, optional Auth Token)
- **CI/CD Automated Build** (.github/workflows/build-apk.yml)

## How to build
Push this repo to GitHub, and the GitHub Action workflow will automatically compile the debug APK and upload it as an artifact.
