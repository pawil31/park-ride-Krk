# P+R Kraków for Android Auto

Shows how many spaces are free at Kraków's Park & Ride car parks on the car screen, using the data
published by ZTP at <https://ztp.krakow.pl/parkingi-pr/sprawdz-wolne-miejsca-pr>.

- **Car screen (Android Auto):** a list of car parks with free spaces, colour-coded
  (green / yellow / red), refreshed every minute while the screen is open. There is also a
  **Refresh** button. Tap a car park to start navigation to it. The last row shows when the data
  was last refreshed.
- **Phone screen:** the ZTP page itself, with the site header, footer and menus hidden, reloaded
  every minute.

Android Auto does not let apps show web pages, so the car screen can't simply embed the website.
Instead the app downloads the page, removes the header, footer and menus, and turns the
availability content into a list. If it can't recognise the page layout, it shows the page's
text as is.

## Project layout

| Module | What it does |
| --- | --- |
| `core` | Plain Kotlin + [jsoup]. Downloads and parses the ZTP page (`AvailabilityParser`). Unit-tested. |
| `app` | The Android app: Android Auto screen (`car/`), data loading (`data/`), and the phone `MainActivity`. |

The app loads data in three steps, stopping at the first one that finds car parks:

1. Download the page's HTML and parse it.
2. Follow an embedded `<iframe>`, in case the numbers come from a widget.
3. Render the page in an off-screen WebView, in case the numbers are filled in by JavaScript.

## Build and install

You need Android Studio (or the Android SDK with JDK 17+).

```bash
./gradlew :core:test          # parser unit tests
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

- The ZTP page is not an official API. If ZTP changes its layout, the parser may no longer
  recognise the car parks. The car screen then shows the page's text instead, so you still see
  the numbers. In that case, adjust `AvailabilityParser` and add a test with the new HTML.
- While driving, Android Auto limits how many rows a list may show (often 6). The app shows as
  many car parks as allowed and keeps the last row for the refresh status.

[jsoup]: https://jsoup.org/
