@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.sa.prayerqibla.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sa.prayerqibla.data.PrayerViewModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val KAABA_LAT = 21.4225
private const val KAABA_LNG = 39.8262

/** Initial great-circle bearing from the device to the Kaaba, in degrees clockwise from north. */
private fun qiblaBearing(lat: Double, lng: Double): Double {
    val fromLat = Math.toRadians(lat)
    val toLat = Math.toRadians(KAABA_LAT)
    val deltaLng = Math.toRadians(KAABA_LNG - lng)
    val y = sin(deltaLng) * cos(toLat)
    val x = cos(fromLat) * sin(toLat) - sin(fromLat) * cos(toLat) * cos(deltaLng)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

@Composable
fun QiblaScreen(
    vm: PrayerViewModel,
    compass: CompassController,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val config by vm.config.collectAsStateWithLifecycle()
    val heading by compass.heading.collectAsStateWithLifecycle()
    val active by compass.active.collectAsStateWithLifecycle()
    val accuracy by compass.accuracy.collectAsStateWithLifecycle()

    var sensorMissing by remember { mutableStateOf(false) }
    var smoothHeading by remember { mutableFloatStateOf(heading ?: 0f) }
    LaunchedEffect(heading) {
        heading?.let { raw ->
            val current = ((smoothHeading % 360f) + 360f) % 360f
            val delta = ((raw - current + 540f) % 360f) - 180f
            smoothHeading += delta
        }
    }
    val animatedHeading by animateFloatAsState(
        targetValue = smoothHeading,
        animationSpec = spring(stiffness = 120f, dampingRatio = 0.85f),
        label = "compass-heading"
    )

    val trueBearing = remember(config.lat, config.lng) { qiblaBearing(config.lat, config.lng) }
    val magneticBearing = remember(config.lat, config.lng, trueBearing) {
        ((trueBearing - vm.declination() + 360.0) % 360.0).toFloat()
    }
    val bearingLabel = digits(magneticBearing.roundToInt().coerceAtMost(359), config.lang)

    val colors = MaterialTheme.colorScheme
    val scroll = rememberScrollState()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            strings.qt,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Box(Modifier.size(320.dp), contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(
                progress = { if (active && heading != null) 1f else 0f },
                modifier = Modifier.fillMaxSize()
            )
            Canvas(Modifier.size(288.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f - 12.dp.toPx()
                drawCircle(color = colors.surfaceContainerLow, radius = radius)
                drawCircle(
                    color = colors.outlineVariant,
                    radius = radius,
                    style = Stroke(width = 1.dp.toPx())
                )

                for (tick in 0 until 36) {
                    rotate(degrees = tick * 10f, pivot = center) {
                        val long = tick % 3 == 0
                        drawLine(
                            color = if (long) colors.onSurfaceVariant else colors.outlineVariant,
                            start = Offset(center.x, center.y - radius + 6.dp.toPx()),
                            end = Offset(
                                center.x,
                                center.y - radius + (if (long) 20.dp else 12.dp).toPx()
                            ),
                            strokeWidth = (if (long) 2.dp else 1.dp).toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // The Qibla marker rotates with the device so the needle always points to it.
                rotate(degrees = magneticBearing - animatedHeading, pivot = center) {
                    drawLine(
                        color = colors.primary,
                        start = Offset(center.x, center.y - radius + 22.dp.toPx()),
                        end = Offset(center.x, center.y + radius - 44.dp.toPx()),
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = colors.primary,
                        radius = 9.dp.toPx(),
                        center = Offset(center.x, center.y - radius + 18.dp.toPx())
                    )
                }

                // Fixed device arrow at the top of the dial.
                drawLine(
                    color = colors.onSurface,
                    start = Offset(center.x, center.y + radius - 26.dp.toPx()),
                    end = Offset(center.x, center.y + radius - 6.dp.toPx()),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        Text(
            "$bearingLabel° ${strings.from}",
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center
        )

        if (active) {
            val accuracyText = when (accuracy) {
                "high" -> strings.accuracyHigh
                "medium" -> strings.accuracyMedium
                "low" -> strings.accuracyLow
                else -> strings.accuracyUnreliable
            }
            Text(
                "${strings.compassOn} · $accuracyText",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                strings.qn,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        if (sensorMissing) {
            Text(
                strings.compassUnavailable,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = {
                if (active) {
                    compass.stop()
                    sensorMissing = false
                } else {
                    sensorMissing = !compass.start()
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) {
            Text(if (active) strings.stopCompass else strings.compass)
        }
    }
}
