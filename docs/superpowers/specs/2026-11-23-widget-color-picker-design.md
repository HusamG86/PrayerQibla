# Widget color picker — design

Date: 2026-11-23

## Intent
The widget setup screen currently edits background, text and accent colors by typing six-digit hex into three text fields. That works but is awkward on a phone. Add a visual picker inside that same screen so colors can be chosen by touch. Widgets themselves stay read-only (tap still opens the app).

## Goals
- Tapping a color's swatch opens a picker with a hue strip and a saturation/value pad, a live preview, and the current hex.
- Picking a color updates the existing hex field; typing hex still works and stays in sync with the picker.
- One reusable picker composable serves all three colors.
- Preserve arbitrary hex entry, the three color fields, and the background-only opacity slider exactly as they are.
- Localized UI follows the in-app language; no new user-facing strings are introduced.

## Non-goals
- No color control on the home-screen widget.
- No alpha control in the picker; background opacity stays owned by the existing slider.
- No new dependency, no data-format change, no manifest change, no widget rendering change.

## Constraints
- Jetpack Compose + Material 3, existing `PrayerTheme`.
- Config screen already sets `LocalLayoutDirection` from the in-app language; hue direction is a color convention, not text, so it does not mirror in RTL.
- Local unit tests are plain JUnit against stubbed `android.jar` (no Robolectric, no `returnDefaultValues`), so color math must be pure Kotlin to be testable. `android.graphics.Color.RGBToHSV` cannot be used in tests.

## Approaches
1. **In-app HSV picker (picked).** New `ColorPicker.kt` with pure-Kotlin HSV↔RGB helpers and a small M3 dialog: hue strip, saturation/value pad, preview, hex readout, OK/Cancel from `android.R` strings. No dependency; I own ~60 lines of color math and test it.
2. **Third-party Compose color-picker library (rejected).** Less custom code and slicker gestures, but adds a build dependency whose styling may fight the app's M3 theme, for a single dialog. Not worth the lifecycle cost here.

## How it will be built
- New `app/src/main/java/com/sa/prayerqibla/widget/ColorPicker.kt`:
  - `internal fun rgbToHsv(rgb: Int): FloatArray` → `[hue 0..360, saturation 0..1, value 0..1]`; `internal fun hsvToRgb(h: Float, s: Float, v: Float): Int` → `0xRRGGBB`. Pure Kotlin, no Android framework calls.
  - `@Composable fun ColorPickerDialog(initialRgb: Int, onRgb: (Int) -> Unit, onDismiss: () -> Unit)`: M3 dialog. `Canvas`-drawn hue gradient and saturation/value pad with a thumb and ring; `pointerInput` + `detectTapGestures`/`detectDragGestures` map offsets to HSV, clamped to range. Live preview box and hex readout use the existing `hex()` formatter. Buttons use `android.R.string.cancel` and `android.R.string.ok` (system-localized, no new strings).
- Edit `app/src/main/java/com/sa/prayerqibla/widget/WidgetConfigActivity.kt`:
  - Make `hex()` `internal` so the dialog can reuse it.
  - `ConfigScreen` holds `pickerTarget` (which of the three colors is being edited); each `ColorField` swatch becomes clickable with the localized field label as its accessibility content description; tapping opens `ColorPickerDialog`.
  - On OK, convert HSV→RGB, write the resulting `hex(rgb)` into that field's state and clear validation error; Cancel leaves the field untouched.
- New `app/src/test/java/com/sa/prayerqibla/widget/ColorPickerTest.kt`: known colors map to expected HSV, HSV maps back to known RGB, and RGB→HSV→RGB round-trips exactly for black, white, the three defaults (`#0C1511`, `#F3F6F1`, `#B4D29A`) and a mid-tone.

## Files touched
- new `widget/ColorPicker.kt`
- new `test/.../widget/ColorPickerTest.kt`
- edit `widget/WidgetConfigActivity.kt`

No dependencies, strings, manifest, preferences or widget-rendering changes.

## Verification
`JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug testDebugUnitTest lintDebug`, then on device: open widget config, tap each swatch, change a color, confirm the hex field and saved widget update; confirm the opacity slider still controls only background alpha.
