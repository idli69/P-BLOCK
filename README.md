# P-BLOCK

A minimal, easy-to-build Android app (Kotlin + Jetpack Compose) that implements a personal-use DNS-based porn blocker with an accountability partner unlock.

## Features
- **DNS-only Blocking**: Uses a local `VpnService` to intercept DNS queries (UDP). Returns `NXDOMAIN` for blocked domains.
- **Accountability Partner**: Set a recovery code with an accountability partner. This code is encrypted securely via Android Keystore (AES-GCM).
- **Emergency Unlock**: A built-in cooldown timer (e.g., 15m, 1h) allows emergency unlocking when the partner cannot be reached.

## Limitations
- **No TLS Interception**: This app does NOT intercept HTTPS traffic.
- **DoH / DoT / QUIC / ECH**: Browsers or apps using DNS-over-HTTPS (DoH), DNS-over-TLS (DoT), or QUIC may bypass this filter. You should disable Secure DNS in your browser settings (e.g., Chrome -> Privacy -> Secure DNS -> Disable).

## Build and Run Instructions (Dockerized)

You can build this app without installing Android Studio or SDKs on your laptop, using Docker.

1. Generate the Gradle wrapper (and build):
   ```bash
   docker-compose up --build
   ```
2. The APK will be available at:
   `app/build/outputs/apk/debug/app-debug.apk`
3. Install on your device:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## Post-Installation Setup
1. Open P-BLOCK and follow the onboarding to get your partner code.
2. Enable Protection (VPN).
3. **CRITICAL:** To prevent bypassing, go to your Android Settings -> Network & internet -> VPN -> P-BLOCK -> Enable **Always-on VPN** and **Block connections without VPN**.

## Architecture Highlights
- `BlockingVpnService.kt`: TUN-based DNS proxy intercepting UDP port 53.
- `FilterEngine.kt`: Fast suffix/pattern matcher for blocklists and allowlists.
- `SecureKeyManager.kt`: Encrypts recovery codes using Android Keystore.
- No telemetry, no root, fully offline.
