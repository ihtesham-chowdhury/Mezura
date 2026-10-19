package rocks.ndot.widgets.instruments.habits

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import rocks.ndot.widgets.kit.Ink
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 2:1 Isometric Vector City Renderer (§10.5 & §6).
 * Renders a living 10x10 plate with 3D shaded buildings, gilded gold-day structures,
 * Dream Towers with animated cranes and scaffolded floors, Achievement landmarks,
 * roads, tiny cars, rotating Ferris wheel, drifting hot-air balloon, and real-clock day/night lighting.
 */
@Composable
fun CityCanvas(
    slots: List<CitySlot>,
    onBuildingTapped: (CitySlot) -> Unit,
    onGroundTapped: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Zoom and pan state
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Day/Night detection (19:00 - 06:00 is night)
    val hour = LocalTime.now().hour
    val isNight = hour >= 19 || hour < 6

    val infiniteTransition = rememberInfiniteTransition(label = "cityAnim")

    // Ferris Wheel rotation animation (40s cycle)
    val wheelAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(40_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ferrisWheel"
    )

    // Balloon drift animation (60s cycle)
    val balloonXProgress by infiniteTransition.animateFloat(
        initialValue = -80f,
        targetValue = 380f,
        animationSpec = infiniteRepeatable(
            animation = tween(60_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "balloon"
    )

    // Cars motion animation (12s cycle)
    val carProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cars"
    )

    // Crane gentle swing animation (±12° over 6 seconds round-trip)
    val craneAngle by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "craneAngle"
    )

    val activeSlots = remember(slots) { slots }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    zoomScale = (zoomScale * zoom).coerceIn(0.70f, 2.0f)
                    panOffset += pan
                }
            }
            .pointerInput(activeSlots, zoomScale, panOffset) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        val centerX = size.width / 2f + panOffset.x
                        val centerY = size.height / 2f + panOffset.y
                        val relX = (tapOffset.x - centerX) / zoomScale
                        val relY = (tapOffset.y - centerY) / zoomScale

                        val tapped = activeSlots.reversed().firstOrNull { slot ->
                            val ox = slot.gx - 5f
                            val oy = slot.gy - 5f
                            val base = isoToScreen(ox + 0.5f, oy + 0.5f, 64f, 32f)
                            val bH = slot.floors * 14f
                            val dx = relX - base.x
                            val dy = relY - (base.y - bH / 2f)
                            (dx * dx + dy * dy) < (24f * 24f)
                        }
                        if (tapped != null) {
                            onBuildingTapped(tapped)
                        } else {
                            onGroundTapped()
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f + panOffset.x, size.height / 2f + panOffset.y)

            scale(zoomScale, pivot = center) {
                translate(center.x, center.y) {
                    val tileW = 64f
                    val tileH = 32f

                    // 1. Draw 10x10 Base Plate with Soil Edge
                    drawBasePlate(tileW, tileH)

                    // 2. Draw Roads (cross at row 4/5, col 4/5)
                    drawRoads(tileW, tileH)

                    // 3. Draw Cars on Roads
                    drawCars(tileW, tileH, carProgress)

                    // 4. Draw Buildings sorted back-to-front by (gx + gy)
                    val sortedSlots = activeSlots.sortedWith(
                        compareBy<CitySlot> { it.gx + it.gy }.thenBy { it.gx }
                    )

                    for (slot in sortedSlots) {
                        drawBuilding(
                            slot = slot,
                            tileW = tileW,
                            tileH = tileH,
                            isNight = isNight,
                            wheelAngle = wheelAngle,
                            craneAngle = craneAngle
                        )
                    }

                    // 5. Drifting Hot-Air Balloon (unlocks when city reaches 20 structures)
                    if (activeSlots.size >= 20) {
                        drawHotAirBalloon(balloonXProgress)
                    }

                    // 6. Night overlay if between 19:00 and 06:00
                    if (isNight) {
                        drawRect(
                            color = Ink.GROUND.copy(alpha = 0.35f),
                            topLeft = Offset(-size.width, -size.height),
                            size = Size(size.width * 2, size.height * 2)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CityCanvas(
    buildingsCount: Int,
    onBuildingTapped: (CitySlot) -> Unit,
    onGroundTapped: () -> Unit,
    modifier: Modifier = Modifier
) {
    val slots = remember(buildingsCount) {
        CityModel.MASTER_PLAN.take(buildingsCount)
    }
    CityCanvas(
        slots = slots,
        onBuildingTapped = onBuildingTapped,
        onGroundTapped = onGroundTapped,
        modifier = modifier
    )
}

// -----------------------------------------------------------------------------
// Isometric Geometry & Drawing Functions
// -----------------------------------------------------------------------------

private fun isoToScreen(gx: Float, gy: Float, tileW: Float, tileH: Float): Offset {
    val sx = (gx - gy) * (tileW / 2f)
    val sy = (gx + gy) * (tileH / 2f)
    return Offset(sx, sy)
}

private fun DrawScope.drawBasePlate(tileW: Float, tileH: Float) {
    val soilDepth = 22f

    // Corner vertices in isometric coordinates (centered around (5,5))
    val top = isoToScreen(-5f, -5f, tileW, tileH)
    val right = isoToScreen(5f, -5f, tileW, tileH)
    val bottom = isoToScreen(5f, 5f, tileW, tileH)
    val left = isoToScreen(-5f, 5f, tileW, tileH)

    // Soil Left Face (#74482F)
    val soilLeftPath = Path().apply {
        moveTo(left.x, left.y)
        lineTo(bottom.x, bottom.y)
        lineTo(bottom.x, bottom.y + soilDepth)
        lineTo(left.x, left.y + soilDepth)
        close()
    }
    drawPath(soilLeftPath, color = Color(0xFF74482F))

    // Soil Right Face (#8A5A3C)
    val soilRightPath = Path().apply {
        moveTo(bottom.x, bottom.y)
        lineTo(right.x, right.y)
        lineTo(right.x, right.y + soilDepth)
        lineTo(bottom.x, bottom.y + soilDepth)
        close()
    }
    drawPath(soilRightPath, color = Color(0xFF8A5A3C))

    // Soil bottom shadow
    val shadowPath = Path().apply {
        moveTo(left.x, left.y + soilDepth)
        lineTo(bottom.x, bottom.y + soilDepth + 12f)
        lineTo(right.x, right.y + soilDepth)
        lineTo(bottom.x, bottom.y + soilDepth)
        close()
    }
    drawPath(shadowPath, color = Color(0x40000000))

    // Top Grass Plate (#4C8C4A with subtle tonal variations)
    for (gx in 0 until 10) {
        for (gy in 0 until 10) {
            val ox = gx - 5f
            val oy = gy - 5f
            val p0 = isoToScreen(ox, oy, tileW, tileH)
            val p1 = isoToScreen(ox + 1f, oy, tileW, tileH)
            val p2 = isoToScreen(ox + 1f, oy + 1f, tileW, tileH)
            val p3 = isoToScreen(ox, oy + 1f, tileW, tileH)

            val tilePath = Path().apply {
                moveTo(p0.x, p0.y)
                lineTo(p1.x, p1.y)
                lineTo(p2.x, p2.y)
                lineTo(p3.x, p3.y)
                close()
            }

            val isAlt = (gx + gy) % 2 == 0
            val grassColor = if (isAlt) Color(0xFF4C8C4A) else Color(0xFF468244)
            drawPath(tilePath, color = grassColor)
        }
    }
}

private fun DrawScope.drawRoads(tileW: Float, tileH: Float) {
    val roadColor = Color(0xFF3A3F47)
    val roadStripeColor = Color(0xFFE9E2CE)

    // Draw horizontal road row (gx = 4 & 5)
    for (gy in 0 until 10) {
        listOf(4, 5).forEach { gx ->
            val ox = gx - 5f
            val oy = gy - 5f
            drawTile(ox, oy, tileW, tileH, roadColor)
        }
        val c0 = isoToScreen(4.5f - 5f, gy - 5f + 0.2f, tileW, tileH)
        val c1 = isoToScreen(4.5f - 5f, gy - 5f + 0.8f, tileW, tileH)
        drawLine(color = roadStripeColor, start = c0, end = c1, strokeWidth = 1.2f)
    }

    // Draw vertical road col (gy = 4 & 5)
    for (gx in 0 until 10) {
        listOf(4, 5).forEach { gy ->
            val ox = gx - 5f
            val oy = gy - 5f
            drawTile(ox, oy, tileW, tileH, roadColor)
        }
        val c0 = isoToScreen(gx - 5f + 0.2f, 4.5f - 5f, tileW, tileH)
        val c1 = isoToScreen(gx - 5f + 0.8f, 4.5f - 5f, tileW, tileH)
        drawLine(color = roadStripeColor, start = c0, end = c1, strokeWidth = 1.2f)
    }
}

private fun DrawScope.drawTile(ox: Float, oy: Float, tileW: Float, tileH: Float, color: Color) {
    val p0 = isoToScreen(ox, oy, tileW, tileH)
    val p1 = isoToScreen(ox + 1f, oy, tileW, tileH)
    val p2 = isoToScreen(ox + 1f, oy + 1f, tileW, tileH)
    val p3 = isoToScreen(ox, oy + 1f, tileW, tileH)

    val tilePath = Path().apply {
        moveTo(p0.x, p0.y)
        lineTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        lineTo(p3.x, p3.y)
        close()
    }
    drawPath(tilePath, color = color)
}

private fun DrawScope.drawCars(tileW: Float, tileH: Float, progress: Float) {
    val carRed = Color(0xFFE5533D)
    val carYellow = Color(0xFFFFD08A)
    val carBlue = Color(0xFF6FA8E8)

    val y1 = -5f + progress * 10f
    val c1 = isoToScreen(4.2f - 5f, y1, tileW, tileH)
    drawRect(color = carRed, topLeft = Offset(c1.x - 2f, c1.y - 2f), size = Size(4f, 6f))

    val y2 = 5f - progress * 10f
    val c2 = isoToScreen(5.2f - 5f, y2, tileW, tileH)
    drawRect(color = carYellow, topLeft = Offset(c2.x - 2f, c2.y - 2f), size = Size(4f, 6f))

    val x3 = -5f + progress * 10f
    val c3 = isoToScreen(x3, 4.2f - 5f, tileW, tileH)
    drawRect(color = carBlue, topLeft = Offset(c3.x - 3f, c3.y - 2f), size = Size(6f, 4f))
}

private fun DrawScope.drawBuilding(
    slot: CitySlot,
    tileW: Float,
    tileH: Float,
    isNight: Boolean,
    wheelAngle: Float,
    craneAngle: Float
) {
    val ox = slot.gx - 5f
    val oy = slot.gy - 5f
    val floorUnit = 14f
    val bH = slot.floors * floorUnit

    // Check if slot is a Dream Tower or Achievement Landmark
    if (slot.slotKind == SlotKind.DREAM_TOWER || slot.slotKind == SlotKind.ACHIEVEMENT_LANDMARK) {
        drawLandmarkBuilding(
            slot = slot,
            ox = ox,
            oy = oy,
            tileW = tileW,
            tileH = tileH,
            isNight = isNight,
            craneAngle = craneAngle,
            floorUnit = floorUnit
        )
        return
    }

    val baseColor = Color(slot.colorHex)

    when (slot.archetype) {
        BuildingArchetype.PARK -> {
            drawTile(ox, oy, tileW, tileH, Color(0xFF3B7A39))
            val tCenter = isoToScreen(ox + 0.5f, oy + 0.5f, tileW, tileH)
            drawLine(color = Color(0xFF5E3929), start = tCenter, end = Offset(tCenter.x, tCenter.y - 8f), strokeWidth = 2f)
            drawCircle(color = Color(0xFF2E632C), radius = 6f, center = Offset(tCenter.x, tCenter.y - 10f))
        }

        BuildingArchetype.FOUNTAIN_PLAZA -> {
            val pCenter = isoToScreen(ox + 0.5f, oy + 0.5f, tileW, tileH)
            drawOval(color = Color(0xFFD6CEBD), topLeft = Offset(pCenter.x - 14f, pCenter.y - 7f), size = Size(28f, 14f))
            drawOval(color = Color(0xFF4FA0E2), topLeft = Offset(pCenter.x - 10f, pCenter.y - 5f), size = Size(20f, 10f))
            drawCircle(color = Color.White, radius = 2f, center = pCenter)
        }

        BuildingArchetype.FERRIS_WHEEL -> {
            val center = isoToScreen(ox + 0.5f, oy + 0.5f, tileW, tileH)
            val wheelCenter = Offset(center.x, center.y - 32f)

            drawLine(color = Color(0xFF6E7380), start = center, end = wheelCenter, strokeWidth = 2f)

            rotate(wheelAngle, pivot = wheelCenter) {
                drawCircle(color = Color(0xFFE8B25C), radius = 22f, center = wheelCenter, style = Stroke(width = 1.5f))
                for (spoke in 0 until 6) {
                    val a = spoke * (PI / 3.0)
                    val sx = wheelCenter.x + cos(a).toFloat() * 22f
                    val sy = wheelCenter.y + sin(a).toFloat() * 22f
                    drawLine(color = Color(0xFFE8B25C), start = wheelCenter, end = Offset(sx, sy), strokeWidth = 1f)
                    drawCircle(color = Color(0xFFE5533D), radius = 2.5f, center = Offset(sx, sy))
                }
            }
        }

        BuildingArchetype.MONUMENT -> {
            val pCenter = isoToScreen(ox + 0.5f, oy + 0.5f, tileW, tileH)
            val obeliskTop = Offset(pCenter.x, pCenter.y - 36f)
            val path = Path().apply {
                moveTo(pCenter.x - 6f, pCenter.y)
                lineTo(pCenter.x + 6f, pCenter.y)
                lineTo(obeliskTop.x + 1f, obeliskTop.y)
                lineTo(obeliskTop.x - 1f, obeliskTop.y)
                close()
            }
            drawPath(path, color = Color(0xFFDFDACF))
        }

        else -> {
            // 3D Extruded Box
            val colTop = baseColor
            val colLeft = Color(
                red = baseColor.red * 0.82f,
                green = baseColor.green * 0.82f,
                blue = baseColor.blue * 0.82f
            )
            val colRight = Color(
                red = baseColor.red * 0.65f,
                green = baseColor.green * 0.65f,
                blue = baseColor.blue * 0.65f
            )

            val g0 = isoToScreen(ox + 0.1f, oy + 0.1f, tileW, tileH)
            val g1 = isoToScreen(ox + 0.9f, oy + 0.1f, tileW, tileH)
            val g2 = isoToScreen(ox + 0.9f, oy + 0.9f, tileW, tileH)
            val g3 = isoToScreen(ox + 0.1f, oy + 0.9f, tileW, tileH)

            val t0 = Offset(g0.x, g0.y - bH)
            val t1 = Offset(g1.x, g1.y - bH)
            val t2 = Offset(g2.x, g2.y - bH)
            val t3 = Offset(g3.x, g3.y - bH)

            val leftFace = Path().apply {
                moveTo(g3.x, g3.y)
                lineTo(g2.x, g2.y)
                lineTo(t2.x, t2.y)
                lineTo(t3.x, t3.y)
                close()
            }
            drawPath(leftFace, color = colLeft)

            val rightFace = Path().apply {
                moveTo(g2.x, g2.y)
                lineTo(g1.x, g1.y)
                lineTo(t1.x, t1.y)
                lineTo(t2.x, t2.y)
                close()
            }
            drawPath(rightFace, color = colRight)

            val topFace = Path().apply {
                moveTo(t0.x, t0.y)
                lineTo(t1.x, t1.y)
                lineTo(t2.x, t2.y)
                lineTo(t3.x, t3.y)
                close()
            }
            drawPath(topFace, color = colTop)

            // Windows on side faces
            if (slot.floors >= 2) {
                val windowColor = if (isNight) Color(0xFFFFD08A) else Color(0x77000000)
                for (f in 0 until slot.floors - 1) {
                    val fy = t2.y + (f + 0.5f) * floorUnit
                    drawRect(color = windowColor, topLeft = Offset(g3.x + (g2.x - g3.x) * 0.45f - 1.5f, fy - 2.5f), size = Size(3f, 5f))
                    drawRect(color = windowColor, topLeft = Offset(g2.x + (g1.x - g2.x) * 0.45f - 1.5f, fy - 2.5f), size = Size(3f, 5f))
                }
            }

            // Gabled Roof for Cottage
            if (slot.archetype == BuildingArchetype.COTTAGE) {
                val ridgeTop = Offset((t0.x + t2.x) / 2f, t0.y - 10f)
                val roofLeft = Path().apply {
                    moveTo(t3.x, t3.y)
                    lineTo(t2.x, t2.y)
                    lineTo(ridgeTop.x, ridgeTop.y)
                    close()
                }
                drawPath(roofLeft, color = Color(0xFFC9694A))
            }

            // Roof antenna for Office Tower
            if (slot.archetype == BuildingArchetype.OFFICE_TOWER) {
                val centerTop = Offset((t0.x + t2.x) / 2f, (t0.y + t2.y) / 2f)
                drawLine(color = Color(0xFFE5533D), start = centerTop, end = Offset(centerTop.x, centerTop.y - 12f), strokeWidth = 1.5f)
                drawCircle(color = Color(0xFFE5533D), radius = 1.5f, center = Offset(centerTop.x, centerTop.y - 12f))
            }

            // Gilded Brass Roof Edge and Crest (§6.2)
            if (slot.isGilded) {
                drawPath(topFace, color = Color(0xFFE8B25C), style = Stroke(width = 1.8f))
                val crestCenter = Offset((t0.x + t2.x) / 2f, (t0.y + t2.y) / 2f)
                drawCircle(color = Color(0xFFFFD08A), radius = 2.5f, center = crestCenter)
            }
        }
    }
}

private fun DrawScope.drawLandmarkBuilding(
    slot: CitySlot,
    ox: Float,
    oy: Float,
    tileW: Float,
    tileH: Float,
    isNight: Boolean,
    craneAngle: Float,
    floorUnit: Float
) {
    val style = slot.landmarkStyle ?: LandmarkStyle.TOWER

    // Style-specific canonical palettes (§6.3)
    val (baseColor, roofColor) = when (style) {
        LandmarkStyle.TOWER -> Pair(Color(0xFF6FA8E8), Color(0xFF4A7AB0))         // Slender glass blue
        LandmarkStyle.ACADEMY -> Pair(Color(0xFFEFE3C8), Color(0xFFDFDACF))       // Cream stone
        LandmarkStyle.LIBRARY -> Pair(Color(0xFFC9694A), Color(0xFFA65035))       // Warm brick
        LandmarkStyle.OBSERVATORY -> Pair(Color(0xFF7E8794), Color(0xFFE8B25C))   // Slate cylinder with brass dome
        LandmarkStyle.ATELIER -> Pair(Color(0xFFD9B77E), Color(0xFFC9694A))       // Gabled studio sand
        LandmarkStyle.DOMED_HALL -> Pair(Color(0xFFE5C388), Color(0xFFD9B77E))    // Sand-toned rounded dome
    }

    val colLeft = Color(baseColor.red * 0.82f, baseColor.green * 0.82f, baseColor.blue * 0.82f)
    val colRight = Color(baseColor.red * 0.65f, baseColor.green * 0.65f, baseColor.blue * 0.65f)

    // Base ground points
    val g0 = isoToScreen(ox + 0.15f, oy + 0.15f, tileW, tileH)
    val g1 = isoToScreen(ox + 0.85f, oy + 0.15f, tileW, tileH)
    val g2 = isoToScreen(ox + 0.85f, oy + 0.85f, tileW, tileH)
    val g3 = isoToScreen(ox + 0.15f, oy + 0.85f, tileW, tileH)

    val totalFloors = slot.floors.coerceAtLeast(1)
    val litCount = slot.litFloors.coerceIn(0, totalFloors)

    // Draw floor-by-floor from ground up
    for (f in 0 until totalFloors) {
        val isLit = f < litCount
        val bGroundOffset = f * floorUnit
        val bTopOffset = (f + 1) * floorUnit

        val fg0 = Offset(g0.x, g0.y - bGroundOffset)
        val fg1 = Offset(g1.x, g1.y - bGroundOffset)
        val fg2 = Offset(g2.x, g2.y - bGroundOffset)
        val fg3 = Offset(g3.x, g3.y - bGroundOffset)

        val ft0 = Offset(g0.x, g0.y - bTopOffset)
        val ft1 = Offset(g1.x, g1.y - bTopOffset)
        val ft2 = Offset(g2.x, g2.y - bTopOffset)
        val ft3 = Offset(g3.x, g3.y - bTopOffset)

        val leftFace = Path().apply {
            moveTo(fg3.x, fg3.y)
            lineTo(fg2.x, fg2.y)
            lineTo(ft2.x, ft2.y)
            lineTo(ft3.x, ft3.y)
            close()
        }
        val rightFace = Path().apply {
            moveTo(fg2.x, fg2.y)
            lineTo(fg1.x, fg1.y)
            lineTo(ft1.x, ft1.y)
            lineTo(ft2.x, ft2.y)
            close()
        }
        val topFace = Path().apply {
            moveTo(ft0.x, ft0.y)
            lineTo(ft1.x, ft1.y)
            lineTo(ft2.x, ft2.y)
            lineTo(ft3.x, ft3.y)
            close()
        }

        if (isLit) {
            // Solid finished floor
            drawPath(leftFace, color = colLeft)
            drawPath(rightFace, color = colRight)
            if (f == totalFloors - 1) {
                drawPath(topFace, color = baseColor)
            }

            // Windows on solid floors
            val windowColor = if (isNight) Color(0xFFFFD08A) else Color(0x77000000)
            val wy = (ft2.y + fg2.y) / 2f
            drawRect(color = windowColor, topLeft = Offset(fg3.x + (fg2.x - fg3.x) * 0.45f - 1.5f, wy - 2f), size = Size(3f, 4f))
            drawRect(color = windowColor, topLeft = Offset(fg2.x + (fg1.x - fg2.x) * 0.45f - 1.5f, wy - 2f), size = Size(3f, 4f))
        } else {
            // Unlit scaffolding floor (§6.3: thin outlines at 40% alpha, no windows)
            val scaffoldColor = Color(0x66A0A0A0)
            drawPath(leftFace, color = scaffoldColor, style = Stroke(width = 1f))
            drawPath(rightFace, color = scaffoldColor, style = Stroke(width = 1f))
            if (f == totalFloors - 1) {
                drawPath(topFace, color = scaffoldColor, style = Stroke(width = 1f))
            }
            // Diagonal cross-bracing
            drawLine(color = scaffoldColor, start = Offset(fg3.x, fg3.y), end = Offset(ft2.x, ft2.y), strokeWidth = 0.8f)
            drawLine(color = scaffoldColor, start = Offset(fg2.x, fg2.y), end = Offset(ft1.x, ft1.y), strokeWidth = 0.8f)
        }
    }

    // Top floor features
    val topTotalH = totalFloors * floorUnit
    val topT0 = Offset(g0.x, g0.y - topTotalH)
    val topT1 = Offset(g1.x, g1.y - topTotalH)
    val topT2 = Offset(g2.x, g2.y - topTotalH)
    val topT3 = Offset(g3.x, g3.y - topTotalH)
    val roofCenter = Offset((topT0.x + topT2.x) / 2f, (topT0.y + topT2.y) / 2f)

    // Architectural silhouettes per style
    when (style) {
        LandmarkStyle.OBSERVATORY -> {
            // Hemispherical brass dome
            drawOval(
                color = Color(0xFFE8B25C),
                topLeft = Offset(roofCenter.x - 7f, roofCenter.y - 9f),
                size = Size(14f, 10f)
            )
            // Slit
            drawLine(
                color = Color(0xFF222328),
                start = Offset(roofCenter.x, roofCenter.y - 9f),
                end = Offset(roofCenter.x, roofCenter.y - 1f),
                strokeWidth = 1.2f
            )
        }
        LandmarkStyle.ACADEMY -> {
            // Pediment
            val pedimentTop = Offset(roofCenter.x, topT0.y - 7f)
            val pedimentPath = Path().apply {
                moveTo(topT3.x, topT3.y)
                lineTo(topT2.x, topT2.y)
                lineTo(pedimentTop.x, pedimentTop.y)
                close()
            }
            drawPath(pedimentPath, color = Color(0xFFDFDACF))
            // Clock face
            drawCircle(color = Color(0xFF222328), radius = 2f, center = Offset(pedimentTop.x, pedimentTop.y + 4f))
        }
        LandmarkStyle.ATELIER -> {
            // Gabled sloped window
            val ridgeTop = Offset((topT0.x + topT2.x) / 2f, topT0.y - 8f)
            val windowSlope = Path().apply {
                moveTo(topT3.x, topT3.y)
                lineTo(topT2.x, topT2.y)
                lineTo(ridgeTop.x, ridgeTop.y)
                close()
            }
            drawPath(windowSlope, color = Color(0xFF6FA8E8))
        }
        LandmarkStyle.DOMED_HALL -> {
            // Rounded dome
            drawOval(
                color = Color(0xFFD9B77E),
                topLeft = Offset(roofCenter.x - 8f, roofCenter.y - 8f),
                size = Size(16f, 10f)
            )
        }
        LandmarkStyle.TOWER -> {
            // Slender antenna
            drawLine(
                color = Color(0xFF6E7380),
                start = roofCenter,
                end = Offset(roofCenter.x, roofCenter.y - 12f),
                strokeWidth = 1.2f
            )
            drawCircle(color = Color(0xFFE8B25C), radius = 1.5f, center = Offset(roofCenter.x, roofCenter.y - 12f))
        }
        LandmarkStyle.LIBRARY -> {
            // Arched cornice
            drawLine(
                color = Color(0xFFA65035),
                start = topT3,
                end = topT2,
                strokeWidth = 2.5f
            )
        }
    }

    // Dream Tower Crane vs Crown Flag (§6.3)
    if (slot.slotKind == SlotKind.DREAM_TOWER) {
        if (!slot.isComplete) {
            // Construction crane swinging ±12°
            val mastTop = Offset(roofCenter.x, roofCenter.y - 13f)
            drawLine(color = Color(0xFF6E7380), start = roofCenter, end = mastTop, strokeWidth = 1.8f)

            rotate(craneAngle, pivot = mastTop) {
                val boomEnd = Offset(mastTop.x + 13f, mastTop.y - 4f)
                val counterWeight = Offset(mastTop.x - 6f, mastTop.y + 1f)
                drawLine(color = Color(0xFFE8B25C), start = counterWeight, end = boomEnd, strokeWidth = 1.4f)
                // Hanging cable & hook
                val cableEnd = Offset(boomEnd.x, boomEnd.y + 8f)
                drawLine(color = Color(0xFFDFDACF), start = boomEnd, end = cableEnd, strokeWidth = 0.9f)
                drawCircle(color = Color(0xFF6E7380), radius = 1.2f, center = cableEnd)
            }
        } else {
            // Achieved: Brass flag crowns tower with soft gold glow
            drawCircle(color = Color(0x33FFD08A), radius = 9f, center = roofCenter)
            val mastTop = Offset(roofCenter.x, roofCenter.y - 15f)
            drawLine(color = Color(0xFFDFDACF), start = roofCenter, end = mastTop, strokeWidth = 1.5f)
            val flagPath = Path().apply {
                moveTo(mastTop.x, mastTop.y)
                lineTo(mastTop.x + 8f, mastTop.y + 3.5f)
                lineTo(mastTop.x, mastTop.y + 7f)
                close()
            }
            drawPath(flagPath, color = Color(0xFFE8B25C))
        }
    } else if (slot.slotKind == SlotKind.ACHIEVEMENT_LANDMARK) {
        if (slot.isGilded) {
            drawCircle(color = Color(0x33FFD08A), radius = 7f, center = roofCenter)
            drawCircle(color = Color(0xFFFFD08A), radius = 2.5f, center = roofCenter)
        }
    }

    // Gilded brass edge on roof if applicable
    if (slot.isGilded) {
        val topOutline = Path().apply {
            moveTo(topT0.x, topT0.y)
            lineTo(topT1.x, topT1.y)
            lineTo(topT2.x, topT2.y)
            lineTo(topT3.x, topT3.y)
            close()
        }
        drawPath(topOutline, color = Color(0xFFE8B25C), style = Stroke(width = 1.8f))
    }
}

private fun DrawScope.drawHotAirBalloon(xPos: Float) {
    val bCenter = Offset(xPos, -90f)

    drawOval(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFE5533D), Color(0xFFFFB14A), Color(0xFF7FE0F5))
        ),
        topLeft = Offset(bCenter.x - 14f, bCenter.y - 20f),
        size = Size(28f, 32f)
    )

    drawLine(color = Color(0xFF5E3929), start = Offset(bCenter.x - 6f, bCenter.y + 12f), end = Offset(bCenter.x - 4f, bCenter.y + 18f), strokeWidth = 1f)
    drawLine(color = Color(0xFF5E3929), start = Offset(bCenter.x + 6f, bCenter.y + 12f), end = Offset(bCenter.x + 4f, bCenter.y + 18f), strokeWidth = 1f)
    drawRect(color = Color(0xFF8A5A3C), topLeft = Offset(bCenter.x - 4f, bCenter.y + 18f), size = Size(8f, 6f))
}
