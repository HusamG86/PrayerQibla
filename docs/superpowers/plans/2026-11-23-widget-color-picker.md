# Widget Color Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a visual HSV color picker to the widget setup screen so background, text and accent colors can be chosen by touch while hex entry and the opacity slider keep working.

**Architecture:** Pure-Kotlin HSV↔RGB helpers live beside a single reusable Compose/M3 dialog in a new `ColorPicker.kt`. The existing `ConfigScreen` keeps its three hex fields as the source of truth; tapping a swatch opens the dialog with that field's current color, and confirming writes the picked color back through the existing `hex()` formatter. No data format, preferences, widget rendering or manifest changes.

**Tech Stack:** Kotlin, Jetpack Compose Material 3 (`Dialog`, `Canvas`, `pointerInput`), JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-23-widget-color-picker-design.md`

## Global Constraints

- No new dependency; no new user-facing strings (use `android.R.string.cancel` / `android.R.string.ok`).
- No manifest, preferences, data-format or widget-rendering changes.
- Color math must be pure Kotlin — local unit tests are plain JUnit against stubbed `android.jar`, so no `android.graphics.Color` calls in `rgbToHsv`/`hsvToRgb`.
- Picker edits RGB only; background opacity stays solely owned by the existing slider (default `1f`, range `0.15f..1f`).
- Widget/config text follows the in-app language via the existing `LocalContext`/`LocalLayoutDirection`; hue strip direction is a color convention and does not mirror in RTL.
- Compose `Color(0xRRGGBB)` has no alpha; use `.copy(alpha = 1f)` when rendering picked colors.
- minSdk = 26, compileSdk = 37, Kotlin Compose plugin already applied.
- Verification command: `JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug testDebugUnitTest lintDebug`
- Do not commit or push; changes stay in the working tree. Do not touch `README.md` or `index.html`.

## Review Focus

- **Dark default colors losing precision on RGB→HSV→RGB:** must round-trip exactly. Pinned by `ColorPickerTest.roundTrip_preservesDefaultsAndMidTone` in Task 1.
- **Achromatic colors (black/white/gray) where hue is undefined:** must give zero saturation and a stable hue without crashing. Pinned by `ColorPickerTest.rgbToHsv_achromaticColorsHaveZeroSaturation` in Task 1.
- **Dragging past the hue/pad edges:** out-of-range HSV must normalize to a valid color, never crash or produce a bad hex. Pinned by `ColorPickerTest.hsvToRgb_normalizesOutOfRangeInputs` in Task 1 and checked on device in Task 2.
- **Hex field staying in sync after a pick:** write-back must use the shared formatter so the field remains valid six-digit uppercase hex. Pinned by `WidgetStyleTest.hex_formatsRgbWithLeadingZeros` in Task 2 and checked on device.
- **Opacity semantics unchanged:** the picker must never touch alpha. Existing `WidgetStyleTest.clampAlpha_boundsToAllowedRange` and `defaults_areFullyOpaque` rerun in Task 2's gate, with the slider checked on device.

---

### Task 1: Pure HSV↔RGB color math

**Files:**
- Create: `app/src/main/java/com/sa/prayerqibla/widget/ColorPicker.kt`
- Test: `app/src/test/java/com/sa/prayerqibla/widget/ColorPickerTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `internal fun rgbToHsv(rgb: Int): FloatArray` → `floatArrayOf(hue, saturation, value)`, hue `0f..360f` (exclusive 360), saturation/value `0f..1f`; `internal fun hsvToRgb(hue: Float, saturation: Float, value: Float): Int` → `0xRRGGBB`, normalizing hue modulo 360 and clamping saturation/value to `0f..1f`.

- [ ] **Step 1: Write the failing tests**

Create `ColorPickerTest.kt`:
```kotlin
package com.sa.prayerqibla.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorPickerTest {
    @Test fun rgbToHsv_primaryColors() {
        assertHsv(0xFF0000, 0f, 1f, 1f)
        assertHsv(0x00FF00, 120f, 1f, 1f)
        assertHsv(0x0000FF, 240f, 1f, 1f)
    }

    @Test fun rgbToHsv_achromaticColorsHaveZeroSaturation() {
        assertEquals(0f, rgbToHsv(0x000000)[1], 0f)
        assertEquals(0f, rgbToHsv(0x000000)[2], 0f)
        assertEquals(0f, rgbToHsv(0xFFFFFF)[1], 0f)
        assertEquals(1f, rgbToHsv(0xFFFFFF)[2], 0f)
        assertEquals(0f, rgbToHsv(0x808080)[1], 0f)
    }

    @Test fun hsvToRgb_primaryColors() {
        assertEquals(0xFF0000, hsvToRgb(0f, 1f, 1f))
        assertEquals(0x00FF00, hsvToRgb(120f, 1f, 1f))
        assertEquals(0x0000FF, hsvToRgb(240f, 1f, 1f))
    }

    @Test fun hsvToRgb_normalizesOutOfRangeInputs() {
        assertEquals(0xFF0000, hsvToRgb(360f, 1f, 1f))
        assertEquals(0xFF0000, hsvToRgb(-360f, 1f, 1f))
        assertEquals(0x800000, hsvToRgb(0f, 2f, 0.5f))
        assertEquals(0x808080, hsvToRgb(0f, 0f, 0.5f))
    }

    @Test fun roundTrip_preservesDefaultsAndMidTone() {
        for (rgb in listOf(0x000000, 0xFFFFFF, 0x0C1511, 0xF3F6F1, 0xB4D29A, 0x3A7BD5)) {
            val hsv = rgbToHsv(rgb)
            assertEquals(rgb, hsvToRgb(hsv[0], hsv[1], hsv[2]))
        }
    }

    private fun assertHsv(rgb: Int, hue: Float, saturation: Float, value: Float) {
        val hsv = rgbToHsv(rgb)
        assertEquals(hue, hsv[0], 0.5f)
        assertEquals(saturation, hsv[1], 0.01f)
        assertEquals(value, hsv[2], 0.01f)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest --tests 'com.sa.prayerqibla.widget.ColorPickerTest'`
Expected: compile failure — `rgbToHsv`/`hsvToRgb` unresolved.

- [ ] **Step 3: Implement both functions in `ColorPicker.kt`**

Use `Double` intermediates internally and `kotlin.math.roundToInt()` per channel; `FloatArray`/`Float` are only the API boundary. `rgbToHsv` masks input with `0xFFFFFF` and returns hue `0f` for achromatic colors. `hsvToRgb` applies `((hue % 360.0) + 360.0) % 360.0`, clamps saturation/value with `coerceIn(0.0, 1.0)`, then packs `(r shl 16) or (g shl 8) or b`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest --tests 'com.sa.prayerqibla.widget.ColorPickerTest'`
Expected: PASS.

### Task 2: Picker dialog and config-screen wiring

**Files:**
- Modify: `app/src/main/java/com/sa/prayerqibla/widget/ColorPicker.kt`
- Modify: `app/src/main/java/com/sa/prayerqibla/widget/WidgetConfigActivity.kt`
- Test: `app/src/test/java/com/sa/prayerqibla/widget/WidgetStyleTest.kt`

**Interfaces:**
- Consumes: `rgbToHsv`, `hsvToRgb` from Task 1; `WidgetStyleParser.parseRgb`, `WidgetStyleParser.DEFAULT_BG_RGB`/`DEFAULT_TEXT_RGB`/`DEFAULT_ACCENT_RGB`, and `hex()` from `WidgetConfigActivity.kt`.
- Produces: `@Composable fun ColorPickerDialog(initialRgb: Int, onRgb: (Int) -> Unit, onDismiss: () -> Unit)`; `internal fun hex(rgb: Int): String` (existing function, visibility widened); `ConfigScreen` swatch-tap behavior and write-back.

- [ ] **Step 1: Write a failing test for the shared hex formatter**

Add to `WidgetStyleTest.kt`:
```kotlin
@Test fun hex_formatsRgbWithLeadingZeros() {
    assertEquals("#0C1511", hex(0x0C1511))
    assertEquals("#00000F", hex(0x00000F))
    assertEquals("#FFFFFF", hex(0xFFFFFFFF.toInt()))
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest --tests 'com.sa.prayerqibla.widget.WidgetStyleTest'`
Expected: compile failure — `hex` is private in `WidgetConfigActivity.kt`.

- [ ] **Step 3: Widen `hex()` visibility**

In `WidgetConfigActivity.kt`, change `private fun hex(rgb: Int): String` to `internal fun hex(rgb: Int): String`. Keep the body unchanged.

- [ ] **Step 4: Run the test to verify it passes**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest --tests 'com.sa.prayerqibla.widget.WidgetStyleTest'`
Expected: PASS.

- [ ] **Step 5: Implement `ColorPickerDialog` in `ColorPicker.kt`**

Add an M3 `Dialog` holding: a `Canvas` hue strip (horizontal gradient across 360 hues with a thumb at the current hue), a `Canvas` saturation/value pad (base hue from `hsvToRgb(hue, 1f, 1f)` with a white horizontal and black vertical gradient overlay, plus a ring thumb), a live preview box, a read-only hex readout via `hex()`, and Cancel/OK text buttons using `android.R.string.cancel` / `android.R.string.ok`.

Wire `pointerInput` with `detectTapGestures` and `detectDragGestures` on both controls; map offsets to `hue = x / width * 360f`, `saturation = x / width`, `value = 1f - y / height`, clamping offsets to the control bounds before converting. Initialize HSV from `rgbToHsv(initialRgb)` in `remember`. OK calls `onRgb(hsvToRgb(hue, saturation, value))`; Cancel calls `onDismiss()`. Render Compose colors as `Color(hsvToRgb(...)).copy(alpha = 1f)`.

- [ ] **Step 6: Wire the dialog into `ConfigScreen`**

In `WidgetConfigActivity.kt`: add `private enum class PickerTarget { BACKGROUND, TEXT, ACCENT }`; hold `var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }` in `ConfigScreen`; add `onPick: () -> Unit` to `ColorField` and make its swatch `Box` clickable with `Modifier.clickable(onClick = onPick).semantics { contentDescription = label }` (the label is already localized). Pass one target per field. When `pickerTarget != null`, show `ColorPickerDialog` with `initialRgb` = `WidgetStyleParser.parseRgb(fieldText) ?: WidgetStyleParser.DEFAULT_*_RGB`; on OK write `hex(rgb)` into that field's state, set `invalid = false`, then clear `pickerTarget`; on dismiss just clear it. Existing hex text fields, validation, Save/Cancel and the opacity slider stay exactly as they are.

- [ ] **Step 7: Run the full verification gate**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug testDebugUnitTest lintDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Verify on device**

Using the installed `com.sa.prayerqibla.dev` build, open an existing widget's config (long-press widget → reconfigure, or Settings → Widgets → Manage → Edit). For each of the three swatches: tap it, confirm the dialog opens with hue strip, pad, preview and hex readout; drag in the pad past its edges and confirm the color stays valid; tap OK and confirm the hex field updates and the widget re-renders with the chosen color. Confirm typing hex still works and that the background-opacity slider still changes only background alpha.
