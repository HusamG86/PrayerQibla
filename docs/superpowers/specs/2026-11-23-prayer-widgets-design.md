# PrayerQibla home-screen widgets — design

Date: 2026-11-23

## Intent and why

The widgets shipped with the Compose migration had two defects the user reported:

1. The color setting never changed the rendered colors — only the transparency slider had a visible effect.
2. The round "dial" widget looked out of place next to normal home-screen icons.

The likely root cause of (1) is the rendering path: the removed widgets were classic
`RemoteViews`, where a stored color can be silently ignored by the launcher at draw
time. The replacement uses Jetpack Glance (per the two installed official Android
skills, `glance-canonical-layouts` and `glance-appwidget-updates-and-memory`), which
renders through Compose so a color value visibly applies.

The user chose option 3: three separate widget entries with custom colors.

## Goals

- Three picker entries: **Compact**, **Countdown**, **Timetable**. No dial.
- Custom colors that actually apply: background, text, and accent (hex input), plus a
  background-opacity slider.
- Widgets look like native cards on stock/Samsung home screens — rounded, normal grid
  sizes, no circular shapes.
- Correct prayer data from the existing `adhan` preferences and `PrayerCalc`/`TimeMath`.
  No new prayer math.
- Reconfiguration of already-placed widgets (this is how the user fixes colors later).
- Refresh on settings change, boot/time/date change, and periodically for the countdown.
- All six bundled UI languages (en, ar, ur, fr, in/id, tr).
- Alarms, app configuration, and the WebView fallback remain unchanged.

## Non-goals

- No round dial widget.
- No changes to alarm scheduling, `adhan` preference keys, or prayer math.
- No network or new data source; widgets read the same local preferences.
- No preset theme picker — the user chose custom colors.

## Anti-goals

- A color setting that saves but does not change the widget.
- A widget that does not fit the home-screen grid or looks foreign.
- A frozen countdown.

## Constraints

- minSdk 26, compileSdk 37, targetSdk 37. Compose BOM 2026.11.01 and the Kotlin Compose
  compiler plugin are already configured.
- Must use Jetpack Glance: add `androidx.glance:glance-appwidget` and
  `androidx.glance:glance-material3` (1.2.0+) to `app/build.gradle.kts`.
- The canonical-layout skill requires `sizeMode = SizeMode.Exact` in each provider and a
  manifest `<receiver>` per widget with a matching `android.appwidget.provider` XML.
  Widget-info `minWidth`/`minHeight` must reference dimension resources.
- Glance `updateAppWidgetState` / `update` / `updateAll` are suspend calls. From receivers
  and the repository, wrap them in a small app-scoped coroutine scope. Always follow a
  state write with an explicit UI update.
- Release builds have minify enabled. No `ActionCallback` classes are planned (config is
  launched through `android:configure`, taps use `actionStartActivity`), so no R8 keep
  rule is needed. If one is introduced, add the zero-argument `<init>()` keep rule from
  the updates-and-memory skill.
- No bitmaps or `content://` URIs are used, so the Android 17 widget bitmap-memory limits
  are not exercised. Keep layouts text/drawable-only.

## Approaches considered

1. **One responsive widget + preset themes.** Rejected: the user wants custom colors and
   three entries.
2. **Two widgets + custom colors.** Rejected: the user chose three entries.
3. **Three separate Glance widgets + custom colors.** **Chosen.** Closest to what the
   user had, minus the dial. It costs more code and more config surface; that tradeoff
   was accepted explicitly.

## Design

### Shared data and style

- A new shared `WidgetData` helper reads `adhan` through the existing `PrayerRepository` /
  `TimeMath` path and exposes the city label, today's local times, the next prayer
  (name, instant, seconds remaining), and five timetable rows — Fajr, Dhuhr, Asr,
  Maghrib, Isha (sunrise stays omitted, as before). Reuse the `prayer_fajr` …
  `prayer_isha` string resources for localized names.
- **Locale:** all widget, config, and manager text follows the in-app language stored in
  `adhan.lang`, never the system locale — the app deliberately decouples them (see
  `ui/UiLocals.kt`). Obtain resources through `createConfigurationContext` with that
  locale for widget text, date formatting, digits, and AM/PM labels, so a user who picks
  Arabic in Settings gets Arabic everywhere.
- **City label:** if the saved `name` is blank (custom coordinates before or without a
  successful reverse-geocode), fall back to a localized "Prayer times" string, matching
  the original `PrayerWidgetProvider`. Add that string in all six locales.
- Nothing new is written to `adhan`. Widget-only style lives in a separate
  `prayer_widgets` SharedPreferences file, keyed by `appWidgetId`.
- `WidgetStyle(bgArgb, textArgb, accentArgb, bgAlpha)`, stored as one string per id.
  Defaults match the original compact widget palette (bg `#0C1511`, text `#F3F6F1`,
  accent `#B4D29A`) so upgraded installs look familiar. **Default `bgAlpha` is `1f`,
  the Kotlin Float meaning fully opaque** — not the hex byte `0x1F`.
- Colors are RGB-only: `Color.parseColor` accepts `#RRGGBB`, and an entered `#AARRGGBB`
  is normalized by dropping the leading alpha byte. **The opacity slider is the sole
  owner of background alpha**, clamped to `0.15f..1f`; text and accent always render at
  full alpha. The config screen validates input, shows an inline error, and blocks save
  on invalid values.
- Config UI: three hex fields with preview swatches and one opacity slider affecting the
  background only — this keeps the transparency control the user liked while making the
  color controls actually work.

### The three widgets

One `GlanceAppWidget` + `GlanceAppWidgetReceiver` pair each, in a new
`com.sa.prayerqibla.widget` package:

- **CompactWidget** — next prayer name, **that prayer's clock time** (not the device's
  current time), city, short date. This matches the original widget's main field.
- **CountdownWidget** — large `HH:MM:SS` remaining, next prayer label, thin progress bar.
- **TimetableWidget** — five name/time rows with the next one highlighted in the accent
  color.

All three use `GlanceModifier.background(Color(bgArgb).copy(alpha = bgAlpha))` directly
(not `ColorProvider`, which the canonical-layout skill warns renders transparent),
`Text(color = Color(textArgb))`, accent for emphasis, and a modest `cornerRadius`.
Each sets `sizeMode = SizeMode.Exact`. Taps use `actionStartActivity<ComposeActivity>()`.

Three widget-info XMLs go under `res/xml/`, with dimensions from `res/values/dimens.xml`,
`android:configure` pointing at `WidgetConfigActivity`, and no required preview asset.

### Config and reconfiguration

- `WidgetConfigActivity` (exported, Compose) receives `EXTRA_APPWIDGET_ID` from
  `android:configure`. The widget kind is derived from
  `AppWidgetManager.getAppWidgetInfo(id)?.provider?.className`, so no extra is needed.
- On save: validate, persist the style, update that widget, then
  `setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))` and
  finish. Reconfiguring an existing widget uses the same activity.
- `WidgetManagerActivity` (not exported) lists placed widget ids for the three providers
  and opens `WidgetConfigActivity` for a chosen id. It is reached from
  Settings → Widgets → "Manage home-screen widgets"; restore the `AppActions.manageWidgets`
  callback and Settings card that were removed during cleanup.

### Refresh

- Lifecycle: each `GlanceAppWidgetReceiver` overrides `onEnabled` to start the refresh
  alarm when its first widget is placed and `onDisabled` to cancel it when its last widget
  is removed. `BootReceiver` re-arms it on boot/time/date changes. This avoids both a
  frozen countdown and a permanent alarm with no widgets on the home screen.
- A `WidgetRefreshReceiver` receives the tick: an inexact, non-waking repeating alarm at
  60-second intervals. Non-waking avoids battery drain; a widget may be up to about a
  minute stale after the device wakes, which is acceptable.
- On each tick, and from `PrayerRepository.saveConfig` and `BootReceiver` (after alarms
  are rescheduled), a shared `WidgetRefresh.refreshAll(context)` helper updates all three
  widgets. Restore the refresh hooks removed during cleanup.
- Glance sessions cache for roughly 45 seconds, so per-minute updates are safe.

### Localization

Add widget labels/descriptions, config labels (background, text, accent, opacity, save,
invalid-color error), and manager strings to all six `values*/strings.xml`.

### Tests

- `WidgetStyle` parsing/validation: valid hex, invalid rejected, alpha clamped, defaults.
- The pure "next prayer" selection helper, including the across-midnight case, if it is
  extracted as a function of now-instant plus today/tomorrow times.

### Files touched

- `app/build.gradle.kts` — Glance dependencies.
- `app/src/main/AndroidManifest.xml` — three receivers, config and manager activities,
  refresh receiver.
- New `app/src/main/java/com/sa/prayerqibla/widget/` package.
- New `res/xml/` widget-info files and `res/values/dimens.xml`.
- All six `values*/strings.xml`.
- `ui/App.kt`, `ui/SettingsScreen.kt`, `ui/ExtraStrings.kt`, `ComposeActivity.kt` —
  restore the manage-widgets entry.
- `data/PrayerRepository.kt`, `BootReceiver.kt` — restore refresh hooks.
- New unit tests under `app/src/test/java/com/sa/prayerqibla/widget/`.

## Left to the builder

Exact class names, dp sizes, layout composition, and coroutine-scope wiring are the
builder's call within these constraints.
