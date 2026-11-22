# PrayerQibla Home-Screen Widgets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the removed RemoteViews widgets with three Jetpack Glance home-screen widgets (Compact, Countdown, Timetable) whose custom colors actually render, plus config/reconfigure and refresh wiring.

**Architecture:** A shared pure data/style layer reads the existing `adhan` preferences through `PrayerRepository`/`TimeMath` and computes next-prayer state without new math. Three independent `GlanceAppWidget`/`GlanceAppWidgetReceiver` pairs render from that layer using per-widget style stored in a separate `prayer_widgets` preferences file. A config activity launches from `android:configure` and from a Settings-linked manager; a lifecycle-aware inexact alarm keeps the countdown fresh.

**Tech Stack:** Kotlin, Jetpack Glance (`glance-appwidget`, `glance-material3` 1.2.0+), Jetpack Compose Material 3, `AlarmManager`, `AppWidgetManager`, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-23-prayer-widgets-design.md`

## Global Constraints

- minSdk = 26, compileSdk = 37, targetSdk = 37; Compose BOM `2026.11.01`; Kotlin Compose compiler plugin already applied.
- Add `androidx.glance:glance-appwidget` and `androidx.glance:glance-material3` at version 1.2.0 or higher.
- Each widget provider sets `sizeMode = SizeMode.Exact`.
- Widget-info `android:minWidth`/`android:minHeight` reference dimension resources from `res/values/dimens.xml`.
- Widget, config, and manager text follows `adhan.lang`, never the system locale (see `app/src/main/java/com/sa/prayerqibla/ui/UiLocals.kt`).
- Colors are RGB-only; the opacity slider solely owns background alpha, default `1f` (fully opaque), clamped to `0.15f..1f`; text and accent render at full alpha.
- Widget style is stored only in a `prayer_widgets` SharedPreferences file keyed by `appWidgetId`; never write new keys to `adhan`.
- No bitmaps and no `content://` URIs; layouts are text/drawable only.
- Release builds have minify enabled; no `ActionCallback` classes are planned. If one is introduced, add the zero-argument `<init>()` keep rule from the updates-and-memory skill.
- Verification command for every task: `JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug testDebugUnitTest lintDebug`
- Do not commit or push; changes stay in the working tree unless the user asks.
- `README.md` and `index.html` are not touched.

## Review Focus

- **Invalid or 8-digit hex color input:** must be rejected or normalized, never crash or render a transparent widget. Pinned by `WidgetStyleTest.parseRgb_*` in Task 1.
- **Next prayer after Isha:** must pick tomorrow's Fajr instead of nothing. Pinned by `WidgetDataTest.findNext_afterIshaUsesTomorrow` in Task 3.
- **Sunrise:** must never be selectable as the next prayer or a timetable highlight. Pinned by `WidgetDataTest.findNext_skipsSunrise` in Task 3.
- **Alarm lifecycle:** removing the last widget must stop the 60-second alarm; placing the first must start it. Pinned by `WidgetRefreshTest.shouldSchedule_*` in Task 5.
- **Blank city / unconfigured location:** must show a localized fallback string, never an empty field or a crash. Pinned by `WidgetDataTest.cityOrFallback_*` in Task 3, and checked on device in Task 8.

---

### Task 1: Glance dependencies and widget style model

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/sa/prayerqibla/widget/WidgetStyle.kt`
- Test: `app/src/test/java/com/sa/prayerqibla/widget/WidgetStyleTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `data class WidgetStyle(val bgRgb: Int, val textRgb: Int, val accentRgb: Int, val bgAlpha: Float)`; `object WidgetStyleParser` with `const val DEFAULT_BG_RGB = 0x0C1511`, `DEFAULT_TEXT_RGB = 0xF3F6F1`, `DEFAULT_ACCENT_RGB = 0xB4D29A`, `DEFAULT_BG_ALPHA = 1f`, `MIN_BG_ALPHA = 0.15f`, plus `parseRgb(input: String): Int?`, `clampAlpha(alpha: Float): Float`, `serialize(style: WidgetStyle): String`, `deserialize(raw: String?): WidgetStyle`, `defaults(): WidgetStyle`.

- [ ] **Step 1: Add Glance dependencies**

In `app/build.gradle.kts` `dependencies`, add `implementation("androidx.glance:glance-appwidget:1.2.0")` and `implementation("androidx.glance:glance-material3:1.2.0")` beside the existing Compose dependencies.

- [ ] **Step 2: Write failing tests for style parsing**

Create `WidgetStyleTest.kt`:
```kotlin
@Test fun parseRgb_acceptsSixDigitHex() {
    assertEquals(0x0C1511, WidgetStyleParser.parseRgb("#0C1511"))
}
@Test fun parseRgb_normalizesEightDigitHexToRgb() {
    assertEquals(0x0C1511, WidgetStyleParser.parseRgb("#800C1511"))
}
@Test fun parseRgb_acceptsHexWithoutHash() {
    assertEquals(0xF3F6F1, WidgetStyleParser.parseRgb("F3F6F1"))
}
@Test fun parseRgb_rejectsMalformedInput() {
    assertNull(WidgetStyleParser.parseRgb("#12345"))
    assertNull(WidgetStyleParser.parseRgb("not-a-color"))
    assertNull(WidgetStyleParser.parseRgb(""))
}
@Test fun clampAlpha_boundsToAllowedRange() {
    assertEquals(WidgetStyleParser.MIN_BG_ALPHA, WidgetStyleParser.clampAlpha(0.01f))
    assertEquals(1f, WidgetStyleParser.clampAlpha(4f))
    assertEquals(0.5f, WidgetStyleParser.clampAlpha(0.5f))
}
@Test fun defaults_areFullyOpaque() {
    assertEquals(1f, WidgetStyleParser.defaults().bgAlpha)
}
@Test fun deserialize_roundTripsAndFallsBack() {
    val style = WidgetStyle(0x112233, 0xAABBCC, 0xDDEEFF, 0.4f)
    assertEquals(style, WidgetStyleParser.deserialize(WidgetStyleParser.serialize(style)))
    assertEquals(WidgetStyleParser.defaults(), WidgetStyleParser.deserialize(null))
    assertEquals(WidgetStyleParser.defaults(), WidgetStyleParser.deserialize("garbage"))
}
```

`serialize` emits `#RRGGBB|#RRGGBB|#RRGGBB|<alpha>`; `deserialize` returns defaults on any malformed field and clamps alpha through `clampAlpha`.

- [ ] **Step 3: Run tests to verify they fail**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest --tests 'com.sa.prayerqibla.widget.WidgetStyleTest'`
Expected: compile failure — `WidgetStyle` and `WidgetStyleParser` unresolved.

- [ ] **Step 4: Implement `WidgetStyle.kt`**

Define the data class and parser object with the exact signatures above. `parseRgb` accepts optional `#`, requires exactly 6 or 8 hex digits, and drops the leading two digits for 8-digit input. Use `String.format("%06X", ...)` for serialization.

- [ ] **Step 5: Run tests to verify they pass**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest --tests 'com.sa.prayerqibla.widget.WidgetStyleTest'`
Expected: PASS.

- [ ] **Step 6: Verify the full build**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug testDebugUnitTest lintDebug`
Expected: BUILD SUCCESSFUL.

---

### Task 2: Localized resources, dimensions, and widget info XMLs

**Files:**
- Modify: `app/src/main/res/values/strings.xml` and `values-ar/`, `values-ur/`, `values-fr/`, `values-in/`, `values-tr/` counterparts
- Create: `app/src/main/res/values/dimens.xml`
- Create: `app/src/main/res/xml/compact_widget_info.xml`
- Create: `app/src/main/res/xml/countdown_widget_info.xml`
- Create: `app/src/main/res/xml/timetable_widget_info.xml`
- Create: `app/src/main/java/com/sa/prayerqibla/widget/WidgetLocals.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `fun widgetContext(context: Context, lang: String): Context`; string resource names `widget_compact_label`, `widget_compact_desc`, `widget_countdown_label`, `widget_countdown_desc`, `widget_timetable_label`, `widget_timetable_desc`, `widget_city_fallback`, `widget_next_prefix`, `widget_no_location`, `widget_config_title`, `widget_config_bg`, `widget_config_text`, `widget_config_accent`, `widget_config_opacity`, `widget_config_save`, `widget_config_invalid`, `widget_manager_title`, `widget_manager_empty`, `widget_manager_edit`; dimension names `widget_compact_min_width`, `widget_compact_min_height`, `widget_countdown_min_width`, `widget_countdown_min_height`, `widget_timetable_min_width`, `widget_timetable_min_height`.

- [ ] **Step 1: Add all strings in six locales**

Add the listed names to every `values*/strings.xml`. English values: "Prayer · Compact", "Next prayer and time in a small card."; "Prayer · Countdown", "Large countdown to the next prayer."; "Prayer · Timetable", "All five prayer times for today."; `widget_city_fallback` "Prayer times"; `widget_next_prefix` "Next prayer · "; `widget_no_location` "Open the app to set your location"; config labels "Widget colors", "Background", "Text", "Accent", "Background opacity", "Save", "Enter a color like #0C1511"; manager labels "Home-screen widgets", "No widgets placed yet", "Edit". Translate the same meaning for ar, ur, fr, in, tr. Do not remove or rename any existing string.

- [ ] **Step 2: Add dimension resources**

Create `dimens.xml` with `110dp`/`70dp` for compact, `160dp`/`110dp` for countdown, `180dp`/`160dp` for timetable min width/height.

- [ ] **Step 3: Create three widget info XMLs**

Each `<appwidget-provider>` sets `android:minWidth`/`android:minHeight` to the matching `@dimen/…`, `android:updatePeriodMillis="0"`, `android:resizeMode="horizontal|vertical"`, `android:widgetCategory="home_screen"`, and `android:configure=".widget.WidgetConfigActivity"`.

- [ ] **Step 4: Implement `widgetContext`**

```kotlin
fun widgetContext(context: Context, lang: String): Context
```
Build a `Configuration` with `Locale.forLanguageTag(lang)` via `setLocales` (API 24+) and `setLocale` fallback, set layout direction, and return `context.createConfigurationContext(configuration)`.

- [ ] **Step 5: Verify the build**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug testDebugUnitTest lintDebug`
Expected: BUILD SUCCESSFUL.

---

### Task 3: Widget preferences and prayer data

**Files:**
- Create: `app/src/main/java/com/sa/prayerqibla/widget/WidgetPreferences.kt`
- Create: `app/src/main/java/com/sa/prayerqibla/widget/NextPrayerFinder.kt`
- Create: `app/src/main/java/com/sa/prayerqibla/widget/WidgetData.kt`
- Test: `app/src/test/java/com/sa/prayerqibla/widget/WidgetDataTest.kt`

**Interfaces:**
- Consumes: `WidgetStyle`, `WidgetStyleParser` (Task 1); `widgetContext` and strings (Task 2); `PrayerRepository`, `TimeMath`, `Formatting.formatClockLabel` (existing).
- Produces: `object WidgetPreferences { fun load(context: Context, appWidgetId: Int): WidgetStyle; fun save(context: Context, appWidgetId: Int, style: WidgetStyle) }`; `data class NextPrayer(val index: Int, val atMillis: Long, val fromMillis: Long?)`; `object NextPrayerFinder { fun find(nowMillis: Long, date: LocalDate, zone: ZoneId, todayHours: DoubleArray, tomorrowHours: DoubleArray): NextPrayer?; fun progress(nowMillis: Long, next: NextPrayer): Float; fun cityOrFallback(name: String?, fallback: String): String }`; `data class WidgetPrayerRow(val name: String, val time: String, val isNext: Boolean)`; `data class WidgetData(val city: String, val nextName: String, val nextTime: String, val remainingSeconds: Long, val progress: Float, val dateLabel: String, val rows: List<WidgetPrayerRow>)`; `object WidgetDataProvider { fun load(context: Context, lang: String): WidgetData }`.

`todayHours`/`tomorrowHours` are 6-element arrays indexed Fajr=0, Sunrise=1, Dhuhr=2, Asr=3, Maghrib=4, Isha=5. `find` considers indices `0, 2, 3, 4, 5` only, converts with `TimeMath.localHoursToMillis`, and sets `fromMillis` to the previous considered prayer's instant when available. `progress` returns `0f` when `fromMillis == null`, otherwise `((now - from) / (at - from)).coerceIn(0f, 1f)`.

- [ ] **Step 1: Write failing tests for next-prayer selection**

Create `WidgetDataTest.kt`:
```kotlin
private val zone = ZoneOffset.UTC
private val date = LocalDate.of(2026, 11, 23)
private val today = doubleArrayOf(5.0, 6.5, 12.0, 15.0, 18.0, 20.0)
private val tomorrow = doubleArrayOf(5.1, 6.6, 12.1, 15.1, 18.1, 20.1)

@Test fun findNext_afterIshaUsesTomorrow() {
    val now = date.atTime(21, 0).toInstant(zone).toEpochMilli()
    val next = NextPrayerFinder.find(now, date, zone, today, tomorrow)
    assertEquals(0, next?.index)
    assertEquals(date.plusDays(1).atTime(5, 6).toInstant(zone).toEpochMilli(), next?.atMillis)
}
@Test fun findNext_skipsSunrise() {
    val now = date.atTime(5, 30).toInstant(zone).toEpochMilli()
    val next = NextPrayerFinder.find(now, date, zone, today, tomorrow)
    assertEquals(2, next?.index)
}
@Test fun findNext_setsPreviousPrayerAndProgress() {
    val now = date.atTime(13, 0).toInstant(zone).toEpochMilli()
    val next = NextPrayerFinder.find(now, date, zone, today, tomorrow)!!
    assertEquals(date.atTime(12, 0).toInstant(zone).toEpochMilli(), next.fromMillis)
    assertEquals(0.25f, NextPrayerFinder.progress(now, next), 0.001f)
}
@Test fun cityOrFallback_usesFallbackForBlankCustomNames() {
    assertEquals("Prayer times", NextPrayerFinder.cityOrFallback(null, "Prayer times"))
    assertEquals("Prayer times", NextPrayerFinder.cityOrFallback("  ", "Prayer times"))
    assertEquals("Riyadh", NextPrayerFinder.cityOrFallback("Riyadh", "Prayer times"))
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest --tests 'com.sa.prayerqibla.widget.WidgetDataTest'`
Expected: compile failure — `NextPrayerFinder` unresolved.

- [ ] **Step 3: Implement preferences and data layer**

`WidgetPreferences` uses `context.applicationContext.getSharedPreferences("prayer_widgets", Context.MODE_PRIVATE)` with key `style_<appWidgetId>` and `WidgetStyleParser.serialize`/`deserialize`.

`WidgetDataProvider.load` reads `PrayerRepository(context).loadConfig()`, obtains a locale-wrapped context via `widgetContext(context, config.lang)`, computes today/tomorrow times with `repo.dayTimes(epochDay, config)`, resolves next prayer with `NextPrayerFinder`, formats clocks with `formatClockLabel` and prayer names with `context.getString` for `prayer_fajr` … `prayer_isha`, and builds five rows excluding sunrise. It returns the fallback city via `cityOrFallback(config.name, localizedFallbackString)`. If no valid location is configured, it returns a `WidgetData` whose rows are empty and whose `nextName` is the localized `widget_no_location` string.

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest --tests 'com.sa.prayerqibla.widget.WidgetDataTest'`
Expected: PASS.

- [ ] **Step 5: Verify the build**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug testDebugUnitTest lintDebug`
Expected: BUILD SUCCESSFUL.

---

### Task 4: Three Glance widgets and receiver registration

**Files:**
- Create: `app/src/main/java/com/sa/prayerqibla/widget/CompactWidget.kt`
- Create: `app/src/main/java/com/sa/prayerqibla/widget/CountdownWidget.kt`
- Create: `app/src/main/java/com/sa/prayerqibla/widget/TimetableWidget.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `WidgetStyleParser`, `WidgetPreferences`, `WidgetDataProvider`, `WidgetData`, `NextPrayerFinder` (Tasks 1–3); resources from Task 2.
- Produces: `class CompactWidget : GlanceAppWidget()`, `class CompactWidgetReceiver : GlanceAppWidgetReceiver()`, and the equivalent `CountdownWidget`/`CountdownWidgetReceiver`, `TimetableWidget`/`TimetableWidgetReceiver`. Each receiver exposes `override val glanceAppWidget: GlanceAppWidget`. `WidgetRefresh.enable/disableIfEmpty` from Task 5 are called in `onEnabled`/`onDisabled` — leave a clearly marked TODO if Task 5 has not run yet.

Each `GlanceAppWidget` sets `override val sizeMode = SizeMode.Exact`, loads style and data in `provideGlance` on `Dispatchers.Default`, and renders text/drawable-only content using `GlanceModifier.background(Color(style.bgRgb).copy(alpha = style.bgAlpha))`, `Text(color = Color(style.textRgb))`, and accent color for the next prayer/time. Taps use `actionStartActivity<ComposeActivity>()`.

- [ ] **Step 1: Implement the three widget and receiver pairs**

Compact shows `widget_next_prefix` + next name, next time, city, short date. Countdown shows `formatCountdown` of `remainingSeconds` in accent color, next prayer label, and a thin linear progress indicator using `NextPrayerFinder.progress`. Timetable shows five rows with `WidgetPrayerRow.name`/`time`, highlighting `isNext` in accent.

- [ ] **Step 2: Register the three receivers in the manifest**

Inside `<application>`, after the activities, add three `<receiver>` elements with `android:name=".widget.CompactWidgetReceiver"` (etc.), `android:exported="true"`, the `APPWIDGET_UPDATE` intent filter, and `android.appwidget.provider` meta-data pointing to `@xml/compact_widget_info` (etc.).

- [ ] **Step 3: Verify the build**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug testDebugUnitTest lintDebug`
Expected: BUILD SUCCESSFUL. Fix any Glance import or API errors (notably import `androidx.glance.unit.ColorProvider` only if a ColorProvider is actually used; backgrounds must use `GlanceModifier.background(Color)` directly).

---

### Task 5: Refresh lifecycle

**Files:**
- Create: `app/src/main/java/com/sa/prayerqibla/widget/WidgetRefresh.kt`
- Test: `app/src/test/java/com/sa/prayerqibla/widget/WidgetRefreshTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/sa/prayerqibla/BootReceiver.kt`
- Modify: `app/src/main/java/com/sa/prayerqibla/data/PrayerRepository.kt`
- Modify: the three receivers from Task 4 (replace their TODOs)

**Interfaces:**
- Consumes: the three `*Widget` classes (Task 4).
- Produces: `object WidgetRefresh { fun enable(context: Context); fun disableIfEmpty(context: Context); fun refreshAll(context: Context); fun shouldSchedule(activeKinds: Set<String>): Boolean }`; `class WidgetRefreshReceiver : BroadcastReceiver()`. `activeKinds` values are the exact class names `"CompactWidgetReceiver"`, `"CountdownWidgetReceiver"`, `"TimetableWidgetReceiver"`.

- [ ] **Step 1: Write failing tests for alarm lifecycle**

Create `WidgetRefreshTest.kt`:
```kotlin
@Test fun shouldSchedule_falseWhenNoWidgetsPlaced() {
    assertFalse(WidgetRefresh.shouldSchedule(emptySet()))
}
@Test fun shouldSchedule_trueWhenAnyWidgetPlaced() {
    assertTrue(WidgetRefresh.shouldSchedule(setOf("CompactWidgetReceiver")))
}
@Test fun shouldSchedule_falseAfterLastWidgetRemoved() {
    assertFalse(WidgetRefresh.shouldSchedule(emptySet()))
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest --tests 'com.sa.prayerqibla.widget.WidgetRefreshTest'`
Expected: compile failure — `WidgetRefresh` unresolved.

- [ ] **Step 3: Implement refresh wiring**

`shouldSchedule` returns `activeKinds.isNotEmpty()`. `enable` schedules an inexact, non-waking `ELAPSED_REALTIME` repeating `AlarmManager` alarm at 60-second intervals for `WidgetRefreshReceiver`. `disableIfEmpty` queries `AppWidgetManager` for all three providers and cancels the alarm when `shouldSchedule` is false. `refreshAll` launches on an app-scoped `CoroutineScope(SupervisorJob() + Dispatchers.Default)` and calls `CompactWidget().updateAll(context)`, `CountdownWidget().updateAll(context)`, `TimetableWidget().updateAll(context)`. `WidgetRefreshReceiver.onReceive` calls `refreshAll`.

Replace the TODOs in the three receivers: `onEnabled` calls `WidgetRefresh.enable(context)`, `onDisabled` calls `WidgetRefresh.disableIfEmpty(context)`, both after `super`. In `BootReceiver`, call `WidgetRefresh.refreshAll(ctx)` and re-arm via `WidgetRefresh.enable(ctx)` after `AdhanScheduler.schedule(ctx)`. In `PrayerRepository.saveConfig`, call `WidgetRefresh.refreshAll(appContext)` after `AdhanScheduler.schedule(appContext)`. Register `WidgetRefreshReceiver` in the manifest as a non-exported receiver.

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest --tests 'com.sa.prayerqibla.widget.WidgetRefreshTest'`
Expected: PASS.

- [ ] **Step 5: Verify the build**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug testDebugUnitTest lintDebug`
Expected: BUILD SUCCESSFUL.

---

### Task 6: Widget config activity

**Files:**
- Create: `app/src/main/java/com/sa/prayerqibla/widget/WidgetConfigActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `WidgetStyleParser`, `WidgetPreferences`, `WidgetRefresh` (Tasks 1, 3, 5); resources from Task 2.
- Produces: `class WidgetConfigActivity : ComponentActivity()` that reads `AppWidgetManager.EXTRA_APPWIDGET_ID` from its intent and derives widget kind from `AppWidgetManager.getAppWidgetInfo(id)?.provider?.className`.

- [ ] **Step 1: Implement the config screen**

A Compose screen with three `OutlinedTextField`s prefilled from `WidgetPreferences.load`, each showing a preview swatch; an opacity `Slider` from `MIN_BG_ALPHA` to `1f`; a validation message using `widget_config_invalid`; and a Save button. Validate on Save with `WidgetStyleParser.parseRgb`; block save and show the error when any field is invalid. Use `widgetContext` for localized strings. Store normalized RGB values and the slider alpha through `WidgetPreferences.save`.

- [ ] **Step 2: Finish with the correct result and refresh**

After saving, call `WidgetRefresh.refreshAll(this)`, then `setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))` and `finish()`. On an invalid or missing widget id, call `setResult(Activity.RESULT_CANCELED)` and `finish()`.

- [ ] **Step 3: Register the activity**

Add `<activity android:name=".widget.WidgetConfigActivity" android:exported="true" android:theme="@style/Theme.App.Compose" android:windowSoftInputMode="adjustResize" />` inside `<application>`.

- [ ] **Step 4: Verify the build**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug testDebugUnitTest lintDebug`
Expected: BUILD SUCCESSFUL.

---

### Task 7: Widget manager and Settings entry

**Files:**
- Create: `app/src/main/java/com/sa/prayerqibla/widget/WidgetManagerActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/sa/prayerqibla/ui/App.kt`
- Modify: `app/src/main/java/com/sa/prayerqibla/ui/ExtraStrings.kt`
- Modify: `app/src/main/java/com/sa/prayerqibla/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/com/sa/prayerqibla/ComposeActivity.kt`

**Interfaces:**
- Consumes: `WidgetConfigActivity` (Task 6), existing `AppActions` and `SettingsScreen` structure.
- Produces: `class WidgetManagerActivity : ComponentActivity()`; `AppActions.manageWidgets: () -> Unit` restored; `ExtraStrings.widgets` and `ExtraStrings.manageWidgets` restored.

- [ ] **Step 1: Implement the manager screen**

Query `AppWidgetManager.getInstance(context)` for the ids of all three providers. Show each id with its kind label and saved color swatch, with an "Edit" button launching `Intent(context, WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)`. Show `widget_manager_empty` when none are placed. Use `widgetContext` for localized strings.

- [ ] **Step 2: Restore the Settings entry**

Add `manageWidgets: () -> Unit` back to `AppActions`; add `widgets` and `manageWidgets` back to `ExtraStrings` with the six localized values used before the cleanup; add the `SectionCard` with a `FilledTonalButton` in `SettingsScreen` that calls `actions.manageWidgets`; in `ComposeActivity`, pass `manageWidgets = { startActivity(Intent(this, WidgetManagerActivity::class.java)) }` and import the activity.

- [ ] **Step 3: Register the manager activity**

Add `<activity android:name=".widget.WidgetManagerActivity" android:exported="false" android:theme="@style/Theme.App.Compose" />` to the manifest.

- [ ] **Step 4: Verify the build**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug testDebugUnitTest lintDebug`
Expected: BUILD SUCCESSFUL.

---

### Task 8: Full verification

**Files:** none (verification only).

**Interfaces:**
- Consumes: all prior tasks.

- [ ] **Step 1: Run the complete build gate**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug testDebugUnitTest lintDebug`
Expected: BUILD SUCCESSFUL with no new lint errors.

- [ ] **Step 2: Install and launch on the connected device**

Run:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.sa.prayerqibla/.ComposeActivity
```
Expected: install and launch succeed.

- [ ] **Step 3: Check the widget picker and rendering**

Open the home-screen widget picker and confirm exactly three Prayer entries appear (Compact, Countdown, Timetable), with no dial. Place one of each. Expected: cards render at normal grid sizes with readable localized text.

- [ ] **Step 4: Check color and reconfiguration behavior**

For each placed widget, open config, change background and accent hex values, save, and confirm the new colors are visible on the home screen. Reopen config from Settings → Widgets → Manage home-screen widgets and confirm the saved values are loaded. Move the opacity slider and confirm the background fades while text stays readable.

- [ ] **Step 5: Check refresh and alarm teardown**

Confirm the countdown advances over a couple of minutes. Remove all three widgets, then run `adb shell dumpsys alarm com.sa.prayerqibla` and confirm no repeating widget alarm remains. Check `adb logcat -d -v threadtime -s AndroidRuntime:E` and confirm no new `AndroidRuntime` exceptions.

- [ ] **Step 6: Confirm protected files are untouched**

Run: `git status --short README.md index.html`
Expected: no output.
