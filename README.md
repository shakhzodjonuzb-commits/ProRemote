# Pro Remote — Android TV ADB Controller

A native Kotlin Android app for controlling an Android TV over classic ADB TCP. The project is intended to open directly in Android Studio and also includes a small Gradle bootstrap script for command-line builds.

## Features

- **ADB over TCP:** connect to a TV by IP address and port (default `5555`); persistent RSA host key; first-connection authorization prompt support.
- **Remote controls:** Power, Home, Back, Menu, Settings, Info, Guide, Exit, TV/Input, D-pad + OK, volume/mute, channel, media transport, colored TV keys, subtitles, Teletext, sleep, and app switch.
- **NumPad:** digits 0–9, minus, Enter.
- **Text input:** send text to the focused TV text field using Android's `input text` command.
- **Mouse / touchpad:** tap and drag are mapped to TV screen coordinates; display dimensions are read from `wm size`.
- **App launcher:** reads installed launcher activities from the TV and starts them with `monkey`; includes search and refresh.
- **System shortcuts:** Wi-Fi, Bluetooth, Display, Sound, Apps, Date & Time, Notifications, Search, Recent Apps, and device information.
- **Dark, responsive, dependency-light UI:** native Android Views; no third-party runtime libraries.

## Important compatibility note

This first build supports **classic ADB TCP sockets**, normally `TV_IP:5555`. It does **not** implement the Android 11+ Wireless Debugging QR/pairing flow or its dynamically assigned TLS connect port. On the TV, enable a setting named something like **ADB debugging**, **Network debugging**, or **ADB over network**, then use the displayed IP and port. Some TV firmwares expose port `5555` automatically.

A connection attempt may trigger an **Allow USB debugging / Allow ADB debugging** RSA authorization dialog on the TV. Accept it, preferably checking **Always allow from this computer/device** if the TV offers that option. The app stores its generated ADB RSA key in its private app storage. Clearing app data generates a new key and may require authorizing it again.

Both devices must be on the same reachable local network. Guest Wi-Fi isolation, VLAN separation, client isolation, or a firewall can block the connection even when both devices show the same Wi-Fi name.

## Build

### Android Studio

1. Open the `ProRemote` folder in Android Studio.
2. Use JDK 17 or newer (JDK 21 is fine).
3. Install Android SDK Platform 36 and Android SDK Build-Tools 36.0.0 when prompted.
4. Sync Gradle, then run the `app` configuration on an Android phone.

### Command line

```bash
./gradlew assembleDebug
```

On Windows:

```bat
gradlew.bat assembleDebug
```

The included `gradlew`/`gradlew.bat` are lightweight bootstrap scripts: they download Gradle 8.14 from the official Gradle distribution host when it is not already installed in the user's Gradle cache. They are not the binary Gradle Wrapper JAR. An internet connection is therefore needed on the first CLI build, as well as for Android Gradle Plugin resolution.

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions

The included workflow builds the debug APK on pushes and pull requests and uploads it as a workflow artifact. It can also be run manually from the Actions tab.

## Notes and limitations

- Android TV vendors do not implement every Android key event. Power, TV input, Guide, color keys, Teletext, app switch, and some settings shortcuts may be ignored or mapped differently by a particular firmware.
- The app launcher lists apps with a launcher activity when the TV's package-manager shell command supports the query. A fallback package list is used on older or vendor-modified systems; a package without a launchable activity may not open.
- App names are shown as package names except for a small set of commonly recognized TV apps. This avoids pretending that package IDs are localized labels.
- `input text` depends on the TV's active keyboard and may not enter every Unicode symbol. Text is sent as keystrokes, not clipboard synchronization.
- Mouse/touchpad events depend on the TV firmware accepting the Android `input mouse`/touch injection source. Some TVs expose only D-pad navigation.
- Volume/media/remote functions use standard Android key events; they are not guaranteed to match every physical remote's vendor-specific IR codes.
- This app does not use root, Shizuku, a cloud relay, or an external server.

## Security

ADB grants powerful shell-level control over the TV. Only connect to a TV you own or are authorized to administer, and do not leave network debugging enabled on an untrusted network. The app does not upload the ADB key or TV data.

## Project details

- Package: `uz.ilhomvich.proremote`
- Min SDK: 26
- Target SDK: 35
- Compile SDK: 36
- Kotlin: 2.1.20
- Android Gradle Plugin: 8.10.1
- Gradle: 8.14
- License: MIT
