package com.sa.prayerqibla.widget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.abs
import kotlin.math.roundToInt

/** Returns [hue 0..360, saturation 0..1, value 0..1] for an 0xRRGGBB color. */
internal fun rgbToHsv(rgb: Int): FloatArray {
    val r = ((rgb shr 16) and 0xFF) / 255.0
    val g = ((rgb shr 8) and 0xFF) / 255.0
    val b = (rgb and 0xFF) / 255.0
    val max = maxOf(r, g, b)
    val delta = max - minOf(r, g, b)
    val hue = when {
        delta == 0.0 -> 0.0
        max == r -> 60.0 * ((g - b) / delta % 6.0)
        max == g -> 60.0 * ((b - r) / delta + 2.0)
        else -> 60.0 * ((r - g) / delta + 4.0)
    }
    val saturation = if (max == 0.0) 0.0 else delta / max
    return floatArrayOf(((hue + 360.0) % 360.0).toFloat(), saturation.toFloat(), max.toFloat())
}

/** Converts HSV (hue 0..360, saturation/value 0..1) to 0xRRGGBB. */
internal fun hsvToRgb(hue: Float, saturation: Float, value: Float): Int {
    val h = (((hue.toDouble() % 360.0) + 360.0) % 360.0) / 60.0
    val s = saturation.toDouble().coerceIn(0.0, 1.0)
    val v = value.toDouble().coerceIn(0.0, 1.0)
    val c = v * s
    val x = c * (1.0 - abs(h % 2.0 - 1.0))
    val m = v - c
    val (r, g, b) = when (h.toInt()) {
        0 -> Triple(c, x, 0.0)
        1 -> Triple(x, c, 0.0)
        2 -> Triple(0.0, c, x)
        3 -> Triple(0.0, x, c)
        4 -> Triple(x, 0.0, c)
        else -> Triple(c, 0.0, x)
    }
    return (((r + m) * 255).roundToInt() shl 16) or
        (((g + m) * 255).roundToInt() shl 8) or
        ((b + m) * 255).roundToInt()
}

/** HSV picker for one widget color. Hue is a color convention and never mirrors in RTL. */
@Composable
internal fun ColorPickerDialog(initialRgb: Int, onRgb: (Int) -> Unit, onDismiss: () -> Unit) {
    val initial = remember(initialRgb) { rgbToHsv(initialRgb) }
    var hue by remember(initialRgb) { mutableFloatStateOf(initial[0]) }
    var saturation by remember(initialRgb) { mutableFloatStateOf(initial[1]) }
    var value by remember(initialRgb) { mutableFloatStateOf(initial[2]) }
    val selected = hsvToRgb(hue, saturation, value)
    val hueStops = remember {
        (0..360 step 15).map { Color(hsvToRgb(it.toFloat(), 1f, 1f)).copy(alpha = 1f) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Brush.horizontalGradient(hueStops))
                        .pointerInput(Unit) {
                            fun update(x: Float) {
                                hue = (x / size.width * 360f).coerceIn(0f, 359.999f)
                            }
                            detectTapGestures { update(it.x) }
                            detectDragGestures { change, _ -> update(change.position.x) }
                        }
                ) {
                    Canvas(Modifier.matchParentSize()) {
                        val x = hue / 360f * size.width
                        drawRect(Color.Black, Offset(x - 3.dp.toPx(), 0f), Size(6.dp.toPx(), size.height))
                        drawRect(Color.White, Offset(x - 2.dp.toPx(), 0f), Size(4.dp.toPx(), size.height))
                    }
                }

                Spacer(Modifier.height(12.dp))
                val base = Color(hsvToRgb(hue, 1f, 1f)).copy(alpha = 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .pointerInput(Unit) {
                            fun update(offset: Offset) {
                                saturation = (offset.x / size.width).coerceIn(0f, 1f)
                                value = (1f - offset.y / size.height).coerceIn(0f, 1f)
                            }
                            detectTapGestures { update(it) }
                            detectDragGestures { change, _ -> update(change.position) }
                        }
                ) {
                    Canvas(Modifier.matchParentSize()) {
                        drawRect(base)
                        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                        val center = Offset(saturation * size.width, (1f - value) * size.height)
                        drawCircle(Color.White, 8.dp.toPx(), center)
                        drawCircle(Color.Black, 8.dp.toPx(), center, style = Stroke(2.dp.toPx()))
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(selected).copy(alpha = 1f))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(hex(selected), style = MaterialTheme.typography.titleMedium)
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
                    TextButton(onClick = { onRgb(selected) }) { Text(stringResource(android.R.string.ok)) }
                }
            }
        }
    }
}
