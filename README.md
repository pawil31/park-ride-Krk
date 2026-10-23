# P+R Kraków for Android Auto

Shows how many spaces are free at Kraków's Park & Ride car parks on the car screen, using the data
published by ZTP at <https://ztp.krakow.pl/parkingi-pr/sprawdz-wolne-miejsca-pr>.

- **Car screen (Android Auto):** one row per car park with the number of free spaces, coloured
  green / yellow / red like the progress bars on the ZTP page. Refreshed every minute while the
  screen is open. The button in the top corner shows the time of the last update; tap it to refresh
  now.
- **Phone screen:** the ZTP page itself, with the site header, footer and menus hidden, reloaded
  every minute.

Android Auto does not let apps show web pages, so the car screen can't embed the website. Instead
the app downloads the page and reads each car park card (`p.fw-bold` name, `Wolnych: N`, progress
bar colour). If that ever stops working, it renders the page with JavaScript and, failing that,
shows the page's text without header and footer, so the numbers are still visible.

## Project layout

| Module | What it does |
| --- | --- |
| `core` | Plain Kotlin + [jsoup]. Reads the car parks from the page (`ParkingParser`) and extracts its text (`PageContentExtractor`). Tested against a saved copy of the page. |
| `app` | The Android app: Android Auto screen (`car/`), page loading (`data/`), and the phone `MainActivity`. |
| `web-page` | The ZTP page saved from Chrome, for reference. |

## Install on your phone

No Android Studio needed. GitHub builds the app on every push (see `.github/workflows/build.yml`)
and attaches it to the **latest** release.

1. On the phone, open the repository's **Releases** page on GitHub and open **Latest build**.
2. Download `park-ride-krk.apk` and open it.
3. If Android asks, allow your browser to install unknown apps, then tap **Install**.

Every build is signed with the same debug key (`app/debug.keystore`), so a newer APK installs over
the old one and keeps your settings.

To build it yourself instead (needs JDK 17 and the Android SDK):

```bash
./gradlew :core:test          # parser tests against the saved page
./gradlew :app:assembleDebug  # builds app/build/outputs/apk/debug/app-debug.apk
```

## Run it in the car

Apps installed outside the Play Store only show up in Android Auto after you allow unknown sources:

1. On the phone, open **Settings → Apps → Android Auto → Additional settings in the app**
   (or open the Android Auto app).
2. Tap **Version** about 10 times until developer mode turns on.
3. In the ⋮ menu, open **Developer settings** and enable **Unknown sources**.
4. Connect to the car (or start the Desktop Head Unit, see [below](#7-optional-car-screen-on-the-computer-desktop-head-unit)).
   **P+R Kraków** appears in the app launcher.

## Debug with adb only

Everything below needs only `adb` from Google's Android SDK Platform Tools, no Android Studio.

### 1. Install adb on your computer

- **Windows / macOS / Linux:** download *SDK Platform-Tools* from
  <https://developer.android.com/tools/releases/platform-tools>, unzip it, and run `adb` from that
  folder (or add the folder to your `PATH`).
- **Or with a package manager:** `sudo apt install adb` (Debian/Ubuntu),
  `brew install --cask android-platform-tools` (macOS), `winget install Google.PlatformTools` (Windows).

Check it works: `adb version`.

### 2. Connect the phone

1. On the phone, open **Settings → About phone** and tap **Build number** 7 times to turn on
   developer options.
2. In **Settings → System → Developer options**, enable **USB debugging**.
3. Connect the phone with a USB cable, run `adb devices`, and accept the prompt on the phone.
   The phone should be listed as `device` (not `unauthorized`).

**Without a cable (Android 11+), e.g. while the phone is plugged into the car:** in Developer
options open **Wireless debugging**, turn it on, and tap **Pair device with pairing code**. Then,
with the computer on the same Wi-Fi as the phone:

```bash
adb pair 192.168.1.23:37123        # IP:port and the code shown in the pairing dialog
adb connect 192.168.1.23:41234     # IP:port shown on the Wireless debugging screen itself
adb devices
```

### 3. Install or update the app

```bash
curl -L -o park-ride-krk.apk \
  https://github.com/pawil31/park-ride-Krk/releases/download/latest/park-ride-krk.apk
adb install -r park-ride-krk.apk   # -r replaces the installed version and keeps its data
```

If the repository is private, download the APK from the **Releases** page in a browser instead.
If install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, an APK signed with a different key is
installed; remove it first with `adb uninstall com.pawil31.parkridekrk`.

### 4. Read the logs

Show what the app logs on each refresh (which page source was used, how many car parks were read,
and any errors):

```bash
adb logcat -c   # clear old logs
adb logcat -s AvailabilityRepository:V AvailabilityScreen:V CarApp:V AndroidRuntime:E
```

A healthy refresh logs a line like `AvailabilityRepository: Downloaded page: 7 car parks` every
minute while the app is open on the car screen. `CarApp` is the tag of the Android Auto library;
template errors (for example too many rows) show up there. `AndroidRuntime` shows crashes.

Other useful log commands:

```bash
# All logs of the app only (start the app first)
adb logcat --pid="$(adb shell pidof -s com.pawil31.parkridekrk)"

# Only crashes
adb logcat -b crash

# Save everything to a file to share
adb logcat -d > park-ride-log.txt
```

### 5. Control the app

```bash
adb shell am start -n com.pawil31.parkridekrk/.MainActivity   # open the phone screen
adb shell am force-stop com.pawil31.parkridekrk                # stop it (also ends the car session)
adb shell dumpsys package com.pawil31.parkridekrk | grep versionName
adb shell pm clear com.pawil31.parkridekrk                     # reset the app's data
```

### 6. Inspect the web pages

The app is a debug build, so its WebViews (the phone screen and the hidden page renderer) can be
inspected from desktop Chrome: with the phone connected over adb, open `chrome://inspect/#devices`
in Chrome on the computer and click **inspect** under *com.pawil31.parkridekrk*.

### 7. Optional: car screen on the computer (Desktop Head Unit)

The Desktop Head Unit (DHU) shows the Android Auto screen in a window on your computer, so you can
test without a car. It is part of the Android SDK, and can be installed with the command-line
`sdkmanager` (from *Command line tools only* at <https://developer.android.com/studio#command-tools>):

```bash
sdkmanager "extras;google;auto"
```

1. On the phone, open the Android Auto settings, tap ⋮ → **Start head unit server**.
2. On the computer:
   ```bash
   adb forward tcp:5277 tcp:5277
   <android-sdk>/extras/google/auto/desktop-head-unit   # desktop-head-unit.exe on Windows
   ```
3. **P+R Kraków** appears in the DHU launcher. Logs from step 4 work at the same time.

## Known limits

- While driving, Android Auto limits how many rows a list may show (often 6). If there are more car
  parks than rows, the last row lists the remaining ones on two lines.
- The ZTP page is not an official API. If ZTP changes its layout, update `ParkingParser`, replace
  `core/src/test/resources/sprawdz-wolne-miejsca-pr.html` with a fresh copy and run the tests.

[jsoup]: https://jsoup.org/
