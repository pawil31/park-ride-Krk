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

## Build and install

You need Android Studio (or the Android SDK with JDK 17+).

```bash
./gradlew :core:test          # parser tests against the saved page
./gradlew :app:assembleDebug  # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug   # installs on a connected phone
```

## Run it in the car

Apps installed outside the Play Store only show up in Android Auto after you allow unknown sources:

1. On the phone, open **Settings → Apps → Android Auto → Additional settings in the app**
   (or open the Android Auto app).
2. Tap **Version** about 10 times until developer mode turns on.
3. In the ⋮ menu, open **Developer settings** and enable **Unknown sources**.
4. Connect to the car (or start the Desktop Head Unit, see below). **P+R Kraków** appears in the
   app launcher.

### Test on a computer with the Desktop Head Unit (DHU)

1. Install **Android Auto Desktop Head Unit Emulator** from Android Studio's SDK Manager
   (SDK Tools tab).
2. On the phone, in Android Auto's ⋮ menu, choose **Start head unit server**.
3. Run:
   ```bash
   adb forward tcp:5277 tcp:5277
   $ANDROID_SDK_ROOT/extras/google/auto/desktop-head-unit
   ```

## Known limits

- While driving, Android Auto limits how many rows a list may show (often 6). If there are more car
  parks than rows, the last row lists the remaining ones on two lines.
- The ZTP page is not an official API. If ZTP changes its layout, update `ParkingParser`, replace
  `core/src/test/resources/sprawdz-wolne-miejsca-pr.html` with a fresh copy and run the tests.

[jsoup]: https://jsoup.org/
