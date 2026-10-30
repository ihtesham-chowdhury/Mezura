package rocks.ndot.widgets.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

/**
 * Production macOS Genie Funnel + Gaussian Magnetic Dock Magnification for Mezura's DrawerBar.
 * - Sliding or tapping across tabs creates a smooth Gaussian wave magnification (up to 132% scale + -6.dp lift)
 *   and curves the top refractive glass rim of the pill bar upward under the active/hovered tab.
 * - Expanding a secondary sub-pill (or switching instruments) unfurls a curved cubic-Bezier macOS Genie
 *   perspective funnel out of the active tab.
 */
@Composable
fun GenieMagneticDrawerBar(
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    onHomeDotClick: () -> Unit,
    subItems: List<String> = emptyList(),
    selectedSubIndex: Int = 0,
    onSubItemSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var fingerXNorm by remember { mutableStateOf<Float?>(null) }
    val activeXNorm by animateFloatAsState(
        targetValue = fingerXNorm ?: ((selectedIndex + 0.5f) / items.size.toFloat()),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "genieFocusX"
    )

    // Genie funnel expansion state (0f = collapsed into tab, 1f = fully unfurled)
    val genieExpand by animateFloatAsState(
        targetValue = if (subItems.isNotEmpty()) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 360f),
        label = "genieFunnelExpand"
    )

    // Subtle Genie emergence pulse whenever selectedIndex changes
    val geniePulse = remember { Animatable(1f) }
    LaunchedEffect(selectedIndex) {
        geniePulse.snapTo(0.15f)
        geniePulse.animateTo(1f, spring(dampingRatio = 0.66f, stiffness = 380f))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // 1. macOS Genie Funnel + Refractive Wave Rim Canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (subItems.isNotEmpty()) 104.dp else 64.dp)
        ) {
            val w = size.width
            val h = size.height
            val barH = 52.dp.toPx()
            val barTop = h - barH
            val focusX = (62.dp.toPx() + activeXNorm * (w - 62.dp.toPx())).coerceIn(72.dp.toPx(), w - 32.dp.toPx())

            // A. Curved Cubic-Bezier macOS Genie Funnel (during tab switch pulse or sub-pill expansion)
            val funnelProgress = if (subItems.isNotEmpty()) genieExpand else (1f - geniePulse.value)
            if (funnelProgress > 0.02f) {
                val topSpreadW = 190.dp.toPx() * (0.4f + 0.6f * funnelProgress)
                val funnelTopY = barTop - 36.dp.toPx() * funnelProgress
                val funnelCx = focusX.coerceIn(topSpreadW / 2f + 16.dp.toPx(), w - topSpreadW / 2f - 16.dp.toPx())

                val geniePath = Path().apply {
                    moveTo(focusX - 22.dp.toPx(), barTop + 6.dp.toPx())
                    cubicTo(
                        focusX - 18.dp.toPx(), barTop - 14.dp.toPx() * funnelProgress,
                        funnelCx - topSpreadW * 0.48f, funnelTopY + 18.dp.toPx() * funnelProgress,
                        funnelCx - topSpreadW * 0.48f, funnelTopY
                    )
                    lineTo(funnelCx + topSpreadW * 0.48f, funnelTopY)
                    cubicTo(
                        funnelCx + topSpreadW * 0.48f, funnelTopY + 18.dp.toPx() * funnelProgress,
                        focusX + 18.dp.toPx(), barTop - 14.dp.toPx() * funnelProgress,
                        focusX + 22.dp.toPx(), barTop + 6.dp.toPx()
                    )
                    close()
                }
                drawPath(
                    path = geniePath,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0x00181C26),
                            Color(0x881C202B),
                            Color(0x44F5B851)
                        ),
                        startY = funnelTopY,
                        endY = barTop + 6.dp.toPx()
                    )
                )
                // Glowing amber Genie funnel side edges
                drawPath(
                    path = geniePath,
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0x88F5B851), Color(0xCCF5B851)),
                        startY = funnelTopY,
                        endY = barTop
                    ),
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }

            // B. Liquid Glass Main Pill Body with Gaussian Wave Crest Over Focus X
            val waveCrestH = 6.dp.toPx()
            val waveHalfW = 54.dp.toPx()
            val pillPath = Path().apply {
                val r = barH / 2f
                moveTo(r, barTop)
                lineTo((focusX - waveHalfW).coerceAtLeast(r), barTop)
                cubicTo(
                    focusX - waveHalfW * 0.45f, barTop,
                    focusX - waveHalfW * 0.35f, barTop - waveCrestH,
                    focusX, barTop - waveCrestH
                )
                cubicTo(
                    focusX + waveHalfW * 0.35f, barTop - waveCrestH,
                    focusX + waveHalfW * 0.45f, barTop,
                    (focusX + waveHalfW).coerceAtMost(w - r), barTop
                )
                lineTo(w - r, barTop)
                arcTo(androidx.compose.ui.geometry.Rect(w - barH, barTop, w, h), -90f, 180f, false)
                lineTo(r, h)
                arcTo(androidx.compose.ui.geometry.Rect(0f, barTop, barH, h), 90f, 180f, false)
                close()
            }

            // Smoked Liquid Glass Fill
            drawPath(
                path = pillPath,
                brush = Brush.verticalGradient(
                    listOf(Color(0xE61B1F28), Color(0xF50C0E13)),
                    startY = barTop - waveCrestH,
                    endY = h
                )
            )
            // Refractive Specular Glass Border with Warm Amber Focus Glow
            drawPath(
                path = pillPath,
                brush = Brush.horizontalGradient(
                    0.0f to Color(0x33FFFFFF),
                    ((focusX - waveHalfW) / w).coerceIn(0.05f, 0.90f) to Color(0x44FFFFFF),
                    (focusX / w).coerceIn(0.10f, 0.95f) to Color(0xCCF5B851),
                    ((focusX + waveHalfW) / w).coerceIn(0.15f, 0.98f) to Color(0x44FFFFFF),
                    1.0f to Color(0x33FFFFFF)
                ),
                style = Stroke(width = 1.2.dp.toPx())
            )
        }

        // 2. Interactive Foreground Row (Home Red Dot + Gaussian Magnified Tab Items)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Home Hub Button (Red Dot inside Circular Bezel)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF171A22))
                    .border(1.dp, Color(0x44FFFFFF), CircleShape)
                    .clickable(onClick = onHomeDotClick),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B30))
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Scrollable / Scrubbable Tab Strip with macOS Magnetic Dock Magnification
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(rememberScrollState())
                    .pointerInput(items.size) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                fingerXNorm = (pos.x / size.width).coerceIn(0f, 1f)
                            },
                            onDragEnd = {
                                fingerXNorm?.let { nx ->
                                    val idx = (nx * items.size).toInt().coerceIn(0, items.size - 1)
                                    onItemSelected(idx)
                                }
                                fingerXNorm = null
                            },
                            onDragCancel = { fingerXNorm = null },
                            onDrag = { change, _ ->
                                change.consume()
                                val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                                fingerXNorm = nx
                            }
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items.forEachIndexed { idx, label ->
                    val itemNorm = (idx + 0.5f) / items.size.toFloat()
                    val dist = abs(itemNorm - activeXNorm)
                    val sigma = 0.11f
                    val gaussian = exp(-(dist * dist) / (2f * sigma * sigma))
                    val magScale = 1f + 0.30f * gaussian
                    val liftDp = (-5.5f * gaussian).dp
                    val isSel = (idx == selectedIndex)

                    Column(
                        modifier = Modifier
                            .offset(y = liftDp)
                            .graphicsLayer {
                                scaleX = magScale
                                scaleY = magScale
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onItemSelected(idx) }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        BasicText(
                            text = label,
                            style = TextStyle(
                                color = if (isSel) Color.White else Color(0xFF8C92A0),
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.08.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        if (isSel) {
                            Box(
                                modifier = Modifier
                                    .size(4.5.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF3B30))
                            )
                        } else {
                            Spacer(modifier = Modifier.height(4.5.dp))
                        }
                    }
                }
            }
        }
    }
}
