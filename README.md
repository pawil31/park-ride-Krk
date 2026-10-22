# P+R Kraków for Android Auto

Shows how many spaces are free at Kraków's Park & Ride car parks on the car screen, using the data
published by ZTP at <https://ztp.krakow.pl/parkingi-pr/sprawdz-wolne-miejsca-pr>.

- **Car screen (Android Auto):** the text of the ZTP page, without the site header, footer and
  menus, refreshed every minute while the screen is open. There is also a **Refresh** button. The
  last row shows when the data was last refreshed.
- **Phone screen:** the ZTP page itself, with the site header, footer and menus hidden, reloaded
  every minute.

Android Auto does not let apps show web pages, so the car screen can't embed the website. Instead
the app loads the page in an invisible browser (so content built by JavaScript is included),
removes the header, footer and menus, and shows the remaining text as list rows, three lines per
row. If the page embeds the data from another site in a frame, that frame's text is added too.

## Project layout

| Module | What it does |
| --- | --- |
| `core` | Plain Kotlin + [jsoup]. Turns the page HTML into its content text (`PageContentExtractor`). Unit-tested. |
| `app` | The Android app: Android Auto screen (`car/`), page loading (`data/`), and the phone `MainActivity`. |

## Build and install

You need Android Studio (or the Android SDK with JDK 17+).

```bash
./gradlew :core:test          # content extraction tests
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

- While driving, Android Auto limits how many rows a list may show (often 6). With three lines per
  row that is about 15 lines of page text, and the rest is cut off. A later
  version can pick out just the car parks and their free spaces so they fit.
- The ZTP page is not an official API. If ZTP changes its layout, the text shown may change too.

[jsoup]: https://jsoup.org/
