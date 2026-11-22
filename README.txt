PrayerQibla — prayer times, adhan alerts, and Qibla compass
===========================================================

A small offline Android app. All prayer times are calculated on the device;
no account, no server, no analytics.

Requirements
------------
- Android Studio (latest) or a command-line JDK 17+.
- Android SDK Platform 37 and Build-Tools installed.
- The first Gradle sync needs internet to download dependencies.


Build and run
-------------
Android Studio:
1. File > Open, select this PrayerQibla folder, and let Gradle sync finish.
2. Plug in a phone with USB debugging, or start an emulator, then press Run.
3. For an installable debug file: Build > Build APK(s). The APK is at
   app/build/outputs/apk/debug/app-debug.apk.

Command line:
  ./gradlew assembleDebug
  ./gradlew testDebugUnitTest
  ./gradlew lintDebug
  ./gradlew assembleRelease      # unsigned unless keystore.properties exists

Debug APK:   app/build/outputs/apk/debug/app-debug.apk
Release APK: app/build/outputs/apk/release/app-release.apk when signed,
             or app-release-unsigned.apk when keystore.properties is absent.


Release signing
---------------
Release signing is optional and is read from a git-ignored file named
keystore.properties in the project root. Copy keystore.properties.example to
keystore.properties and fill in the real values:

  storeFile=/absolute/path/to/your-keystore.jks
  storePassword=...
  keyAlias=...
  keyPassword=...

Relative storeFile paths are resolved from the project root. Never commit the
keystore or keystore.properties. If keystore.properties is missing, Gradle
still builds an unsigned release APK/AAB so CI can run without secrets.

Release builds enable R8 code shrinking, resource shrinking, and the keep rule
for the @JavascriptInterface bridge in app/proguard-rules.pro.


Adhan alerts
------------
Alerts work with the app closed and the phone locked.
- Open the app, pick a city or tap "My location", then enable alerts either
  per prayer or with the main alert button.
- Allow notifications when Android asks.
- On Android 12+, allow "Alarms & reminders" for the app so alerts fire on the
  exact minute. The in-app permission card shows Missing/Allowed for
  notifications, exact alarms, and battery optimization, with a Fix button.
- Some brands (Xiaomi, Huawei, Oppo, Samsung, etc.) also need battery
  optimization disabled for the app.
- The default sound is app/src/main/res/raw/adhan.mp3. Replace that file with
  your own adhan (MP3, OGG, WAV, etc., same base name "adhan") and rebuild.
- Alarms are scheduled seven days ahead and rebuilt after boot, an app update,
  a clock/time-zone change, or a date change.
- Changing the adhan sound or vibration settings requires a versioned
  notification channel; the app already uses channel id "adhan_v2_custom", so
  a reinstall is not normally needed after an app update.


Permissions
-----------
- INTERNET: only used by the bundled local WebView asset loader; there is no
  remote content and cleartext traffic is disabled.
- POST_NOTIFICATIONS: adhan notifications on Android 13+.
- SCHEDULE_EXACT_ALARM: exact-minute adhan alarms on Android 12+.
- RECEIVE_BOOT_COMPLETED: restore alarms after reboot.
- VIBRATE: optional vibration with notifications.
- ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION: optional "My location" and
  the Qibla compass. You can use the built-in city list without granting it.

Backup and device transfer are disabled (allowBackup=false and
res/xml/data_extraction_rules.xml), so preferences stay on the device.


Settings
--------
Open the gear icon to change:
- Calculation method: Umm al-Qura, Muslim World League, ISNA, Egyptian
  Authority, University of Karachi.
- Asr calculation: Standard (Shafi'i/Maliki/Hanbali) or Hanafi.
- High-latitude rule: Angle-based, One-seventh of night, Middle of night, None.
- Time format: Auto, 12-hour, 24-hour.

The UI also has a city selector, "My location", a live countdown, a monthly
timetable, a Qibla compass, six UI languages (Arabic, English, Urdu, French,
Indonesian, Turkish), light/dark/system theme, and a native compass with a
WebView device-orientation fallback.


Project layout
--------------
app/src/main/java/sa/prayerqibla/
  MainActivity.kt     WebView host, native bridge, permissions, location, compass, theme
  PrayerCalc.kt       Authoritative prayer-time math (methods, Asr, high lat)
  TimeMath.kt         IANA time-zone/DST conversion helpers
  AdhanScheduler.kt   Seven-day alarm scheduling and exact-alarm handling
  AdhanReceiver.kt    Notification channel, localized notification, sound
  BootReceiver.kt     Reschedules after boot/time/time-zone/date changes

app/src/main/assets/
  index.html          Markup with a strict Content-Security-Policy
  style.css           Layout, themes, RTL support
  app.js              UI logic, native bridge calls, JS fallback calculation

app/src/main/res/
  raw/adhan.mp3       Bundled adhan sound (replace to customize)
  values*/            Localized app strings
  xml/                Data extraction (backup) rules

app/src/test/java/sa/prayerqibla/
  PrayerCalcTest.kt   Prayer-time regression tests
  TimeMathTest.kt     Time-zone/DST and rollover tests


How the time math works
-----------------------
Kotlin is authoritative inside the app: app.js asks MainActivity for times
through the NativeAdhan JavaScript bridge, and the bridge uses PrayerCalc.
app.js keeps a JavaScript implementation only as a browser fallback. Times use
IANA time zones, not fixed UTC offsets, so daylight-saving transitions and
travel do not shift prayer times. The UI refreshes itself at midnight and when
the app returns to the foreground.
