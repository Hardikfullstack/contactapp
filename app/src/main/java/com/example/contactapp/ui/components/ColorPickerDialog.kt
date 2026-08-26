package com.example.contactapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.contactapp.R
import kotlin.math.*

@Composable
fun ColorPickerDialog(
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var hsv by remember { mutableStateOf(floatArrayOf(0f, 1f, 1f)) } // Hue, Saturation, Value
    val currentColor = remember(hsv[0], hsv[1], hsv[2]) {
        Color.hsv(hsv[0], hsv[1], hsv[2])
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.color_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Current Color Preview
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(currentColor)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)
                            )
                        )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Color Wheel
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    ColorWheel(
                        hue = hsv[0],
                        saturation = hsv[1],
                        onColorChange = { h, s ->
                            hsv = floatArrayOf(h, s, hsv[2])
                        }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Brightness Slider
                Text(
                    text = stringResource(R.string.brightness_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )
                Slider(
                    value = hsv[2],
                    onValueChange = { hsv = floatArrayOf(hsv[0], hsv[1], it) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onColorSelected(currentColor.toArgb())
                            onDismiss()
                        },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(stringResource(R.string.select))
                    }
                }
            }
        }
    }
}

@Composable
fun ColorWheel(
    hue: Float,
    saturation: Float,
    onColorChange: (Float, Float) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val width = size.width.toFloat()
                    val radius = width / 2
                    val center = Offset(radius, radius)
                    val input = change.position - center
                    
                    val touchRadius = sqrt(input.x * input.x + input.y * input.y)
                    val angle = atan2(input.y, input.x)
                    
                    val h = (toDegrees(angle.toDouble()).toFloat() + 360f) % 360f
                    val s = (touchRadius / radius).coerceIn(0f, 1f)
                    
                    onColorChange(h, s)
                }
            }
    ) {
        val size = size.width
        val radius = size / 2
        
        // Draw Hue Gradient (Sweep)
        val hueColors = listOf(
            Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
        )
        drawCircle(
            brush = Brush.sweepGradient(hueColors),
            radius = radius
        )
        
        // Draw Saturation Gradient (Radial)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.Transparent),
                radius = radius
            ),
            radius = radius
        )

        // Draw Selection Indicator
        val angle = toRadians(hue.toDouble())
        val indicatorX = radius + (saturation * radius * cos(angle)).toFloat()
        val indicatorY = radius + (saturation * radius * sin(angle)).toFloat()
        
        drawCircle(
            color = Color.White,
            radius = 8.dp.toPx(),
            center = Offset(indicatorX, indicatorY),
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.5f),
            radius = 9.dp.toPx(),
            center = Offset(indicatorX, indicatorY),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

private fun toDegrees(radians: Double): Double = radians * 180.0 / PI
private fun toRadians(degrees: Double): Double = degrees * PI / 180.0
