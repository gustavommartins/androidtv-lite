# androidtv-lite

**English** · [Português (Brasil)](README.pt-BR.md) · [Español](README.es.md)

A **minimal, lightweight Android TV launcher** for TVs with little memory and a weak processor. It is a single-screen app in plain Java: no Leanback, no Compose and no external libraries.

The look is inspired by the Apple TV (tvOS) home screen. This project is **not affiliated with Apple** or with any of the brands of the apps it shows.

![Home screen](docs/screenshots/home-en.jpg)

<details>
<summary>Other languages (screenshots)</summary>

| Português (Brasil) | Español |
|---|---|
| ![Tela inicial em português](docs/screenshots/home-pt.jpg) | ![Pantalla de inicio en español](docs/screenshots/home-es.jpg) |

</details>

> The Portuguese screenshot was taken on a TV set to Portuguese. The English and Spanish ones were taken on the same TV with the app's language forced by a debug build, so they show what a TV set to those languages displays.

## Table of contents

- [Features](#features)
- [Language support](#language-support)
- [Requirements](#requirements)
- [Build](#build)
- [Install on the TV](#install-on-the-tv)
- [Make it the default launcher](#make-it-the-default-launcher)
- [Go back to the original launcher / uninstall](#go-back-to-the-original-launcher--uninstall)
- [Customize](#customize)
- [How apps are chosen](#how-apps-are-chosen)
- [Known limitations](#known-limitations)
- [Troubleshooting](#troubleshooting)
- [Project structure](#project-structure)
- [License](#license)

## Features

- **Top navigation bar, centered:** `Home` · `Google Play` · `Settings`, in a translucent pill. The focused tab turns into a white pill, like in tvOS.
- **Date in the top-left corner and clock in the top-right.**
- **Centered app grid**, 4 columns and several rows, with no sideways scrolling. The focused app lifts, grows and shows its name below.
- **Alphabetical order** by default, ignoring accents and case. Once you customize the Home, your own order is kept.
- **Only known streaming apps** by default, plus any app you install yourself. Manufacturer and Google system apps are left out until you add them.
- **Customize the Home**, iOS style. Hold **OK** on an app and the apps start to shake. In this mode:
  - **OK** on an app opens a menu with **Move · Remove from Home · Uninstall**. *Uninstall* only appears for apps that are not system apps, and it opens the system's uninstall dialog.
  - **Move**: the arrow keys move the app and the others shift over, like on iOS. OK or Back drops it.
  - **Add apps** (bottom center) lists every TV app that is not on Home, including system apps. OK on one puts it at the end of Home. **Done** leaves edit mode.
  - **Back** goes out one layer at a time: drop the app, close the menu, close *Add apps*, then leave edit mode.
  - The order and removals are saved on the TV. New apps you install go to the end of Home; removed apps come back only through *Add apps*.
- **Background image** scaled to 1280 px and loaded as RGB_565 to use little memory. Easy to replace.
- **No network, no ads, no telemetry.** The only permission is `REQUEST_DELETE_PACKAGES` (a normal permission, granted automatically), used only to open the system's uninstall dialog.

## Language support

The launcher follows the **TV's system language**. Nothing to configure.

| TV language | Navigation bar | Date example |
|---|---|---|
| English (default) | Home · Google Play · Settings | Sunday, October 18 |
| Português | Início · Google Play · Configurações | domingo, 18 de outubro |
| Español | Inicio · Google Play · Configuración | domingo, 18 de octubre |

- Any other language falls back to **English**.
- The date follows the language's own format, and the clock follows the TV's **12 h / 24 h** setting (the 12 h format has no AM/PM marker).
- App names come from each app, so they are shown in whatever language the app provides.

**Adding a language:** create `res/values-xx/strings.xml` (`xx` = language code, for example `fr`) with the strings below and rebuild. Pull requests are welcome.

```xml
<resources>
    <string name="app_name">...</string>
    <string name="nav_home">...</string>
    <string name="nav_settings">...</string>
    <string name="edit_add_apps">...</string>
    <string name="edit_done">...</string>
    <string name="action_move">...</string>
    <string name="action_remove">...</string>
    <string name="action_uninstall">...</string>
    <string name="add_title">...</string>
    <string name="add_empty">...</string>
</resources>
```

The full list, with the English texts, is in `res/values/strings.xml`.

## Requirements

**On the TV**

- Android TV with **Android 8.0 (API 26) or later**.
- ADB debugging enabled (steps below).

Tested on a TCL TV with **Android 9**, **1.4 GB of RAM** and slow eMMC storage. It has not been tested on other devices. If it works (or not) on yours, please open an issue.

**On the computer (to build)**

- **JDK 17** or newer. With JDK 22+ see the [note about D8](#note-about-new-jdks-and-d8).
- **Android SDK** with `build-tools` and `platforms;android-28`.
- **ADB** (`platform-tools`) to install.

## Build

The project does not use Gradle. The `build.ps1` script (Windows/PowerShell) calls `aapt2`, `javac`, `d8`, `zipalign` and `apksigner` directly.

### 1. Get the minimum Android SDK

If you do not have the SDK yet, download the *command-line tools* from <https://developer.android.com/studio#command-line-tools-only> and run:

```powershell
sdkmanager --sdk_root=C:\Android\sdk "build-tools;34.0.0" "platforms;android-28"
```

You will need to accept the Android SDK licenses.

### 2. Build

```powershell
git clone https://github.com/gustavommartins/androidtv-lite.git
cd androidtv-lite
.\build.ps1 -SdkRoot C:\Android\sdk
```

The APK is written to `build\androidtv-lite.apk`. It is signed with a test key created locally in `build\debug.keystore`. That folder is not versioned, and **the key is kept between builds**, so you can update an installed copy with `adb install -r`.

> If PowerShell blocks scripts, run:
> `powershell -ExecutionPolicy Bypass -File .\build.ps1 -SdkRoot C:\Android\sdk`

### Note about new JDKs and D8

The D8 that ships in `build-tools` 34 can fail on bytecode produced by very new JDKs (for example JDK 25), with a `NullPointerException` when converting anonymous classes. There are two ways out:

1. Build with **JDK 17**; or
2. Pass a newer R8/D8 from Google's Maven repository:

```powershell
Invoke-WebRequest "https://dl.google.com/dl/android/maven2/com/android/tools/r8/8.7.18/r8-8.7.18.jar" -OutFile r8.jar
.\build.ps1 -SdkRoot C:\Android\sdk -R8Jar .\r8.jar
```

### On Linux or macOS

The script is for Windows, but the steps are the same: `aapt2 compile` → `aapt2 link` → `javac --release 8` → `d8` → add `classes.dex` to the APK → `zipalign` → `apksigner sign`. Read `build.ps1` as a reference.

## Install on the TV

### 1. Enable developer mode on the TV

1. **Settings → Device Preferences → About**.
2. Click **Build** (or "OS version") **7 times** until it says *"You are now a developer"*.
3. Go back and open **Device Preferences → Developer options**.
4. Turn on **USB debugging** and, if present, **Network/Wi-Fi debugging (ADB)**.

On many TVs, turning on USB debugging already opens the ADB port on the network (port 5555), even without the separate network option.

### 2. Find the TV's IP address

**Settings → Network & Internet →** current connection. Note the IP address (something like `192.168.0.50`).

### 3. Connect and install

```powershell
adb connect 192.168.0.50:5555
adb devices
adb install -r build\androidtv-lite.apk
```

On the first connection the TV shows **"Allow USB debugging?"**. Tick **"Always allow from this computer"** and confirm. If `adb devices` shows `unauthorized`, accept the prompt on the TV and connect again.

### 4. Open it

The app appears in the TV's app list as **Lite Launcher** (or the translated name). You can already open it from there without making it the default. Nothing else on the TV was changed.

## Make it the default launcher

```powershell
adb shell cmd package set-home-activity com.androidtvlite.launcher/.MainActivity
```

This registers the launcher as the preferred home screen. The TV should open on it when powered on.

### If the Home button still opens the original launcher

Some manufacturers call the original launcher directly when you press **Home**, ignoring the preference. That was the case on the TCL TV this was developed on. To force Home to land on this launcher you have to **disable the original launcher** for your user only:

```powershell
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

Before doing this, know that:

- It is a Google launcher, and other parts of the system may try to open it.
- If this launcher fails while the original is disabled, the TV shows the manufacturer's recovery screen. You can still undo it from the computer (commands below).
- System or Play Store updates may re-enable it.
- The original launcher's package name may differ on your TV. Check with `adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME`.

## Go back to the original launcher / uninstall

```powershell
# Re-enable the original launcher (if you disabled it)
adb shell pm enable com.google.android.tvlauncher

# Point the preference back to the original
adb shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity

# Remove this launcher
adb shell pm uninstall com.androidtvlite.launcher
```

If something goes badly wrong, a **factory reset** of the TV restores everything.

## Customize

The UI and logic are in `src/com/androidtvlite/launcher/MainActivity.java`, and the ordering rules (default list, saved order, moves, grid navigation) are in `HomeOrder.java`. After editing, run `build.ps1` and reinstall with `adb install -r`.

| What to change | Where |
|---|---|
| "Known" streaming apps | `PREFERRED` constant (list of package names) |
| Number of grid columns | `COLUMNS` constant |
| Card size | `cardW` and `cardH` in `makeTile` |
| Focus zoom | `1.15f` in `makeTile` (and `1.04f` in `makeTab`) |
| Navigation bar items | `makeTab(...)` calls in `onCreate` |
| Texts (bar, app name) | `res/values*/strings.xml` |
| Darkening over the background | the `scrim` `GradientDrawable` in `onCreate` |
| Background image | `res/drawable-nodpi/bg.jpg` |
| Ordering rules (saved order, new and hidden apps) | `HomeOrder.merge` in `HomeOrder.java` |

### Running the HomeOrder tests

`HomeOrder` has no Android dependencies, so its tests run with the plain JDK (no SDK, no Gradle). From the project root:

```powershell
javac -d build\test src\com\androidtvlite\launcher\HomeOrder.java test\com\androidtvlite\launcher\HomeOrderTest.java
java -cp build\test com.androidtvlite.launcher.HomeOrderTest
```

If everything passes, it prints `OK`. A failed check throws an `AssertionError` with the name of the case.

### Changing the background image

Replace `res/drawable-nodpi/bg.jpg` with your own image and rebuild. Tips:

- **1280 px wide** is enough and uses little memory. Avoid 4K images, which waste RAM.
- JPEG, quality around 85.
- The background is cropped to fill the screen (`centerCrop`), so keep the main subject in the middle.
- The photo shipped in the repository (a seaside village at sunset) is the one chosen by the project author. **It is not covered by the MIT license**: the rights belong to the photographer, and the original license is unknown to me. If you are the owner and want it removed, or if you redistribute the project, replace the file with an image of your own or one under a free license.
- If you use a third-party photo, respect its license.

## How apps are chosen

Until you customize the Home, the default order applies:

1. Asks the system for apps with the `LEANBACK_LAUNCHER` category (apps made for TV).
2. Skips the launcher itself, Google Play and Settings (they are already in the bar).
3. Keeps an app if it is in the `PREFERRED` list **or** if it is **not a system app**, that is, any app you install. Sorts by name, ignoring accents and case.

After the first customization, the saved order applies:

4. Apps you install later go to the end of the Home.
5. Hidden apps (removed from the Home) do not come back on their own. To bring them back, use **Add apps**.
6. **Add apps** lists every TV app that is not on the Home, including manufacturer and Google system apps, in alphabetical order. Only Google Play, Settings and the launcher itself are left out.
7. The list is rebuilt every time you return to the home screen, so a newly installed app shows up without a reboot.

## Known limitations

- **Phone-only apps** (with no Android TV version) do not appear. That is an Android rule; no TV launcher shows them.
- **No search, no recommendations, no "continue watching".** This is on purpose: the goal is to be light.
- **No widgets and no Android TV home channels.**
- **No app icon of its own** for the launcher entry.
- The behavior of the **Home** button and of boot varies between manufacturers. See the section on the default launcher.
- Sizes are in `dp`, but the layout was only checked on a TV that renders at 1280×720. On other screens you may need to adjust `cardW`/`cardH` and the margins.

## Troubleshooting

| Symptom | What to do |
|---|---|
| `adb connect` fails | Check the IP, that the TV and PC are on the same network and that debugging is on. Try turning USB debugging off and on. |
| `adb devices` shows `unauthorized` | Accept the "Allow debugging" prompt on the TV screen and connect again. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | The installed copy was signed with a different key. Uninstall it first: `adb shell pm uninstall com.androidtvlite.launcher`. Keep `build\debug.keystore` from then on. |
| The app is not in the app list | Check with `adb shell pm list packages com.androidtvlite.launcher`. |
| Home opens the original launcher | See [If the Home button still opens the original launcher](#if-the-home-button-still-opens-the-original-launcher). |
| A streaming app does not appear | It needs the `LEANBACK_LAUNCHER` category. Check with `adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER`. |
| `build.ps1` fails in D8 with `NullPointerException` | Use JDK 17 or pass `-R8Jar` (see [Note about new JDKs and D8](#note-about-new-jdks-and-d8)). |
| The text is in the wrong language | The launcher follows the TV's system language. Change it in the TV settings; unsupported languages show English. |
| I need the old launcher back right now | `adb shell pm enable com.google.android.tvlauncher`, then the original's `set-home-activity`. |

## Project structure

```
androidtv-lite/
├── AndroidManifest.xml                       # HOME + LEANBACK_LAUNCHER activity; one permission (REQUEST_DELETE_PACKAGES)
├── build.ps1                                 # builds and signs the APK (no Gradle)
├── docs/screenshots/                         # README images
├── res/
│   ├── drawable-nodpi/bg.jpg                 # background (photo, not under the MIT license)
│   ├── values/strings.xml                    # English (default)
│   ├── values-pt/strings.xml                 # Português
│   └── values-es/strings.xml                 # Español
├── src/com/androidtvlite/launcher/
│   ├── HomeOrder.java                        # ordering rules (pure Java, no Android APIs)
│   └── MainActivity.java                     # all UI and logic (~1050 lines)
└── test/com/androidtvlite/launcher/
    └── HomeOrderTest.java                    # plain-Java tests for HomeOrder
```

The whole interface is built in code (no layout XML), which keeps the app small. The final APK is about 200 KB, almost all of it the background photo (the code itself is only a few tens of KB).

## License

[MIT](LICENSE) for the **code**. Use, copy and modify freely.

The MIT license **does not cover** the background image `res/drawable-nodpi/bg.jpg` (see the section on changing it).

The brands and logos of the apps shown (Netflix, YouTube, Prime Video, Globoplay, Disney+, Apple TV, Crunchyroll and others) belong to their respective owners. They appear only because each app supplies its own banner to the system.
