package rocks.ndot.widgets.instruments.folio

import android.graphics.*
import kotlin.math.*

/**
 * Procedural Vector & Typography Painters for Mezura Master Plates & Folio Stats.
 * Implements MEZURA_PLATE_FIX_GUIDE_V2 (Rule P0 - Zero flat patches, Rule P1 - Zero dynamic pixels).
 */
object PlateWidgets {

    // =========================================================================
    // 5.1 SPLIT-FLAP COUNTER (Style 01)
    // =========================================================================
    fun drawSplitFlapCounter(
        c: Canvas,
        w: Float,
        h: Float,
        totalCount: Int,
        lNorm: Float = 0.125f,
        tNorm: Float = 0.465f,
        rNorm: Float = 0.435f,
        bNorm: Float = 0.585f
    ) {
        val left = lNorm * w
        val top = tNorm * h
        val right = rNorm * w
        val bottom = bNorm * h
        val boxW = right - left
        val boxH = bottom - top

        // 1. Brass Housing Frame
        val frameRect = RectF(left, top, right, bottom)
        val brassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                left, top, right, bottom,
                intArrayOf(
                    Color.rgb(110, 82, 32),
                    Color.rgb(201, 162, 75),
                    Color.rgb(138, 106, 47),
                    Color.rgb(90, 68, 25)
                ),
                floatArrayOf(0f, 0.35f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val cornerR = 0.08f * boxH
        c.drawRoundRect(frameRect, cornerR, cornerR, brassPaint)

        // 4 Corner Screws (4 px radius sphere with center notch)
        val screwPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(45, 34, 18) }
        val screwHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(240, 215, 140) }
        val screwSlots = listOf(
            PointF(left + cornerR * 1.1f, top + cornerR * 1.1f),
            PointF(right - cornerR * 1.1f, top + cornerR * 1.1f),
            PointF(left + cornerR * 1.1f, bottom - cornerR * 1.1f),
            PointF(right - cornerR * 1.1f, bottom - cornerR * 1.1f)
        )
        val sr = 0.045f * boxH
        for (pt in screwSlots) {
            c.drawCircle(pt.x, pt.y, sr, screwPaint)
            c.drawCircle(pt.x - sr * 0.25f, pt.y - sr * 0.25f, sr * 0.5f, screwHighlight)
            c.drawLine(pt.x - sr * 0.6f, pt.y, pt.x + sr * 0.6f, pt.y, screwPaint)
        }

        // Inner Dark Cavity (#14100C)
        val padX = 0.065f * boxW
        val padY = 0.12f * boxH
        val cavRect = RectF(left + padX, top + padY, right - padX, bottom - padY)
        val cavPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 16, 12) }
        c.drawRoundRect(cavRect, cornerR * 0.6f, cornerR * 0.6f, cavPaint)

        // 2. Door Digits
        val rawDigits = totalCount.coerceAtLeast(0).toString().padStart(3, '0')
        val n = rawDigits.length.coerceAtLeast(3)
        val cavW = cavRect.width()
        val cavH = cavRect.height()
        val gap = (0.015f * cavW).coerceIn(4f, 8f)
        val doorW = (cavW - gap * (n + 1)) / n
        val doorH = cavH * 0.88f
        val doorTop = cavRect.centerY() - doorH / 2f
        val doorBottom = cavRect.centerY() + doorH / 2f

        val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(242, 239, 230) // Grotesque white #F2EFE6
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = doorH * 0.66f
            textAlign = Paint.Align.CENTER
        }
        val dfm = digitPaint.fontMetrics

        for (i in 0 until n) {
            val dl = cavRect.left + gap + i * (doorW + gap)
            val dr = dl + doorW
            val dmid = (doorTop + doorBottom) / 2f
            val dRect = RectF(dl, doorTop, dr, doorBottom)

            // Top flap (#241E18 -> #1B1714)
            val topFlapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(dl, doorTop, dl, dmid, Color.rgb(36, 30, 24), Color.rgb(27, 23, 20), Shader.TileMode.CLAMP)
            }
            c.drawRect(dl, doorTop, dr, dmid, topFlapPaint)

            // Bottom flap (#1B1714 -> #241E18)
            val botFlapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(dl, dmid, dl, doorBottom, Color.rgb(27, 23, 20), Color.rgb(36, 30, 24), Shader.TileMode.CLAMP)
            }
            c.drawRect(dl, dmid, dr, doorBottom, botFlapPaint)

            // Split-flap digit sitting optically split across hinge
            val textY = dmid - (dfm.ascent + dfm.descent) / 2f - (0.02f * doorH)
            c.drawText(rawDigits[i].toString(), (dl + dr) / 2f, textY, digitPaint)

            // Horizontal hinge line: 1.5 px #0A0A0A + 1 px highlight below
            c.drawLine(dl, dmid, dr, dmid, Paint().apply { color = Color.rgb(10, 10, 10); strokeWidth = 2f })
            c.drawLine(dl, dmid + 1.5f, dr, dmid + 1.5f, Paint().apply { color = Color.rgb(58, 48, 38); strokeWidth = 1f })
        }
    }

    // =========================================================================
    // 5.2 SUPERSCRIPT DURATION TYPOGRAPHY ("07h 37m 45s")
    // =========================================================================
    fun drawDurationSuperscript(
        c: Canvas,
        w: Float,
        h: Float,
        totalSeconds: Long,
        xNorm: Float,
        yNorm: Float,
        sizeNorm: Float,
        color: Int,
        serifTypeface: Typeface,
        align: Paint.Align = Paint.Align.LEFT
    ) {
        val hrs = (totalSeconds / 3600).coerceIn(0, 999)
        val mins = ((totalSeconds % 3600) / 60).coerceIn(0, 59)
        val secs = (totalSeconds % 60).coerceIn(0, 59)

        val fullSize = sizeNorm * h
        val supSize = fullSize * 0.55f
        val supRaise = fullSize * 0.35f

        val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.typeface = serifTypeface
            this.textSize = fullSize
        }
        val supPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.typeface = serifTypeface
            this.textSize = supSize
        }

        val hStr = "%02d".format(hrs)
        val mStr = "%02d".format(mins)
        val sStr = "%02d".format(secs)

        val wH = digitPaint.measureText(hStr)
        val wHUnit = supPaint.measureText("h ")
        val wM = digitPaint.measureText(mStr)
        val wMUnit = supPaint.measureText("m ")
        val wS = digitPaint.measureText(sStr)
        val wSUnit = supPaint.measureText("s")
        val totalWidth = wH + wHUnit + wM + wMUnit + wS + wSUnit

        var curX = when (align) {
            Paint.Align.CENTER -> xNorm * w - totalWidth / 2f
            Paint.Align.RIGHT -> xNorm * w - totalWidth
            else -> xNorm * w
        }
        val baseY = yNorm * h

        c.drawText(hStr, curX, baseY, digitPaint); curX += wH
        c.drawText("h ", curX, baseY - supRaise, supPaint); curX += wHUnit
        c.drawText(mStr, curX, baseY, digitPaint); curX += wM
        c.drawText("m ", curX, baseY - supRaise, supPaint); curX += wMUnit
        c.drawText(sStr, curX, baseY, digitPaint); curX += wS
        c.drawText("s", curX, baseY - supRaise, supPaint)
    }

    // =========================================================================
    // 5.3 BRASS SLIDE-CALIPER RULER HEADER (Stats Page)
    // =========================================================================
    fun drawSlideRuler(
        c: Canvas,
        w: Float,
        h: Float,
        currentRange: FolioStatsRange,
        vernierNormX: Float,
        lNorm: Float = 0.058f,
        tNorm: Float = 0.038f,
        rNorm: Float = 0.942f,
        bNorm: Float = 0.108f,
        drawBarBackground: Boolean = false
    ) {
        val left = lNorm * w
        val top = tNorm * h
        val right = rNorm * w
        val bottom = bNorm * h
        val barH = bottom - top
        val cornerR = barH * 0.5f

        if (drawBarBackground) {
            // 1. Brushed Metal Brass Bar
            val barRect = RectF(left, top, right, bottom)
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, top, 0f, bottom,
                    intArrayOf(Color.rgb(138, 106, 47), Color.rgb(201, 162, 75), Color.rgb(110, 82, 32)),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            c.drawRoundRect(barRect, cornerR, cornerR, barPaint)

            // 1 px darker outline #4A3A16
            c.drawRoundRect(barRect, cornerR, cornerR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(74, 58, 22); style = Paint.Style.STROKE; strokeWidth = 1.5f
            })

            // 2. Millimeter Ticks along left ~62% of bar
            val tickRight = left + (right - left) * 0.62f
            val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(59, 45, 16) }
            val tickStep = 9f
            var tx = left + cornerR
            var tickIdx = 0
            while (tx < tickRight) {
                val (tLen, alpha, sw) = when {
                    tickIdx % 10 == 0 -> Triple(0.28f * barH, 255, 2.5f)
                    tickIdx % 5 == 0 -> Triple(0.18f * barH, 150, 1.5f)
                    else -> Triple(0.10f * barH, 90, 1.0f)
                }
                tickPaint.alpha = alpha
                tickPaint.strokeWidth = sw
                c.drawLine(tx, top, tx, top + tLen, tickPaint)
                c.drawLine(tx, bottom - tLen, tx, bottom, tickPaint)
                tx += tickStep
                tickIdx++
            }

            // Tiny "mm" label far left
            val mmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(59, 45, 16); textSize = 0.12f * barH; typeface = Typeface.MONOSPACE
            }
            c.drawText("mm", left + cornerR * 0.35f, top + 0.26f * barH, mmPaint)
            c.drawText("mm", left + cornerR * 0.35f, bottom - 0.14f * barH, mmPaint)
        }

        // 3. Active Range Tab Amber Pill Highlight: 7D  30D  YEAR  ALL
        val tabs = listOf(
            FolioStatsRange.DAYS_7 to 0.180f,
            FolioStatsRange.DAYS_30 to 0.320f,
            FolioStatsRange.YEAR to 0.440f,
            FolioStatsRange.ALL to 0.710f
        )
        val tabPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 0.26f * barH
            textAlign = Paint.Align.CENTER
        }
        val tabFm = tabPaint.fontMetrics
        val tabCenterY = top + barH / 2f
        val tabTextY = tabCenterY - (tabFm.ascent + tabFm.descent) / 2f

        for ((rng, normX) in tabs) {
            val cx = lNorm * w + normX * (rNorm - lNorm) * w
            val isActive = (rng == currentRange)
            val lbl = rng.label

            if (isActive) {
                // Active: bold + filled warm amber pill #E5A93C
                val pw = tabPaint.measureText(lbl) + 0.36f * barH
                val ph = 0.54f * barH
                val pRect = RectF(cx - pw / 2f, tabCenterY - ph / 2f, cx + pw / 2f, tabCenterY + ph / 2f)
                c.drawRoundRect(pRect, ph / 2f, ph / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(229, 169, 60)
                })
                c.drawRoundRect(pRect, ph / 2f, ph / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(74, 58, 22); style = Paint.Style.STROKE; strokeWidth = 1.5f
                })
                tabPaint.color = Color.rgb(43, 29, 14)
                tabPaint.typeface = Typeface.DEFAULT_BOLD
                c.drawText(lbl, cx, tabTextY, tabPaint)
            } else if (drawBarBackground) {
                // Inactive when drawing background
                tabPaint.color = Color.argb(90, 232, 206, 140)
                tabPaint.typeface = Typeface.DEFAULT
                c.drawText(lbl, cx + 1f, tabTextY + 1f, tabPaint)
                tabPaint.color = Color.rgb(74, 58, 22)
                c.drawText(lbl, cx, tabTextY, tabPaint)
            }
        }

        // 4. Slide Caliper (Clamp & Carriage) with High Visual Contrast
        val clampX = lNorm * w + vernierNormX * (rNorm - lNorm) * w
        val clampW = 0.14f * w
        val clampH = 1.42f * barH
        val clampTop = top + (barH - clampH) / 2f
        val clampRect = RectF(clampX - clampW / 2f, clampTop, clampX + clampW / 2f, clampTop + clampH)

        // Drop shadow behind clamp
        val shadowRect = RectF(clampRect.left + 2f, clampRect.top + 3f, clampRect.right + 2f, clampRect.bottom + 3f)
        c.drawRoundRect(shadowRect, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 0, 0, 0)
        })

        // Clamp brass block with rounded corners & multi-stop metallic shading
        c.drawRoundRect(clampRect, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                clampRect.left, clampRect.top, clampRect.right, clampRect.bottom,
                intArrayOf(
                    Color.rgb(225, 192, 105),
                    Color.rgb(182, 145, 65),
                    Color.rgb(128, 96, 38),
                    Color.rgb(85, 60, 22)
                ),
                floatArrayOf(0f, 0.35f, 0.70f, 1f),
                Shader.TileMode.CLAMP
            )
        })
        // Top highlight chamfer
        c.drawLine(clampRect.left + 8f, clampRect.top + 1f, clampRect.right - 8f, clampRect.top + 1f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(248, 228, 160); strokeWidth = 2f
        })
        // Outer dark border #3E2C14
        c.drawRoundRect(clampRect, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(62, 44, 20); style = Paint.Style.STROKE; strokeWidth = 2.2f
        })

        // Central Round Brass Rivet
        val rivetR = 0.18f * clampW
        val rivetCx = clampRect.centerX()
        val rivetCy = clampRect.centerY()
        // Rivet outer shadow
        c.drawCircle(rivetCx, rivetCy, rivetR + 1.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(48, 34, 16); style = Paint.Style.STROKE; strokeWidth = 2f
        })
        // Rivet metallic sphere
        c.drawCircle(rivetCx, rivetCy, rivetR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                rivetCx - rivetR * 0.35f, rivetCy - rivetR * 0.35f, rivetR,
                intArrayOf(Color.rgb(255, 238, 175), Color.rgb(195, 155, 75), Color.rgb(95, 68, 24)),
                floatArrayOf(0f, 0.65f, 1f),
                Shader.TileMode.CLAMP
            )
        })
        c.drawCircle(rivetCx, rivetCy, rivetR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(62, 44, 20); style = Paint.Style.STROKE; strokeWidth = 1.4f
        })

        // Razor-Sharp Engraved Double-Headed Arrow Button
        val arrowStemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(32, 20, 10); strokeWidth = 5f; strokeCap = Paint.Cap.ROUND
        }
        val arrowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(32, 20, 10); style = Paint.Style.FILL
        }
        val arrowHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 255, 240, 180); strokeWidth = 1.5f; style = Paint.Style.STROKE
        }

        val aTopY = clampRect.top + 0.12f * clampH
        val aBotY = clampRect.bottom - 0.12f * clampH
        val arrowW = 0.18f * clampW
        val arrowH = 0.11f * clampH

        // Arrow vertical shaft passing under/over rivet
        c.drawLine(rivetCx, aTopY + arrowH * 0.5f, rivetCx, rivetCy - rivetR * 1.25f, arrowStemPaint)
        c.drawLine(rivetCx, rivetCy + rivetR * 1.25f, rivetCx, aBotY - arrowH * 0.5f, arrowStemPaint)

        // Top Arrowhead (Solid Triangle pointing UP)
        val topArrowPath = Path().apply {
            moveTo(rivetCx, aTopY)
            lineTo(rivetCx - arrowW, aTopY + arrowH)
            lineTo(rivetCx + arrowW, aTopY + arrowH)
            close()
        }
        c.drawPath(topArrowPath, arrowFillPaint)
        c.drawLine(rivetCx - arrowW, aTopY + arrowH + 1f, rivetCx + arrowW, aTopY + arrowH + 1f, arrowHighlightPaint)

        // Bottom Arrowhead (Solid Triangle pointing DOWN)
        val botArrowPath = Path().apply {
            moveTo(rivetCx, aBotY)
            lineTo(rivetCx - arrowW, aBotY - arrowH)
            lineTo(rivetCx + arrowW, aBotY - arrowH)
            close()
        }
        c.drawPath(botArrowPath, arrowFillPaint)
        c.drawLine(rivetCx - arrowW, aBotY - arrowH - 1f, rivetCx + arrowW, aBotY - arrowH - 1f, arrowHighlightPaint)

        // Sharp Vernier Pointer Needle pointing left toward range tabs
        val needleLen = 0.13f * clampW
        val needleH = 0.15f * clampH
        val needlePathTop = Path().apply {
            moveTo(clampRect.left - needleLen, rivetCy)
            lineTo(clampRect.left, rivetCy - needleH)
            lineTo(clampRect.left, rivetCy)
            close()
        }
        val needlePathBot = Path().apply {
            moveTo(clampRect.left - needleLen, rivetCy)
            lineTo(clampRect.left, rivetCy)
            lineTo(clampRect.left, rivetCy + needleH)
            close()
        }
        // Top half highlighted gold, bottom half shaded bronze
        c.drawPath(needlePathTop, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(238, 205, 115) })
        c.drawPath(needlePathBot, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(138, 102, 42) })
        val needleBorder = Path().apply {
            moveTo(clampRect.left - needleLen, rivetCy)
            lineTo(clampRect.left, rivetCy - needleH)
            lineTo(clampRect.left, rivetCy + needleH)
            close()
        }
        c.drawPath(needleBorder, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(62, 44, 20); style = Paint.Style.STROKE; strokeWidth = 1.8f
        })
    }

    // =========================================================================
    // 5.4 WOODCUT FLOWER RATING ICONS (Style 07 Atlas)
    // =========================================================================
    fun drawWoodcutFlowerRating(
        c: Canvas,
        w: Float,
        h: Float,
        rating: Int,
        centerXNorm: Float,
        centerYNorm: Float,
        flowerSizeNorm: Float = 0.024f,
        inkColor: Int = Color.rgb(46, 42, 36)
    ) {
        val cx = centerXNorm * w
        val cy = centerYNorm * h
        val flR = flowerSizeNorm * h
        val gap = flR * 2.3f
        val startX = cx - (gap * 2f)

        for (i in 0 until 5) {
            val fx = startX + i * gap
            val isEarned = (i < rating)

            // Jitter for hand-stamped authenticity
            val seed = (i * 37 + 11).toDouble()
            val jRot = ((sin(seed) * 1.5) * PI / 180.0)

            c.save()
            c.translate(fx, cy)
            c.rotate(Math.toDegrees(jRot).toFloat())

            val flowerPath = Path()
            for (p in 0 until 5) {
                val theta = (p * 72.0) * PI / 180.0
                val px = (flR * 0.75f * cos(theta)).toFloat()
                val py = (flR * 0.75f * sin(theta)).toFloat()
                val rPetal = flR * 0.42f
                flowerPath.addCircle(px, py, rPetal, Path.Direction.CW)
            }
            flowerPath.addCircle(0f, 0f, flR * 0.28f, Path.Direction.CW)

            if (isEarned) {
                c.drawPath(flowerPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = inkColor; style = Paint.Style.FILL
                })
            } else {
                c.drawPath(flowerPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = inkColor; style = Paint.Style.STROKE; strokeWidth = 2f; alpha = 140
                })
            }
            c.restore()
        }

        // Underneath: "N/5 STARS" in serif italic
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            textSize = 0.019f * h
            textAlign = Paint.Align.CENTER
        }
        c.drawText("$rating/5 STARS", cx, cy + flR * 1.8f, labelPaint)
    }

    // =========================================================================
    // 5.5 BLUEPRINT RUBBER STAMP (Style 05)
    // =========================================================================
    fun drawBlueprintRubberStamp(
        c: Canvas,
        w: Float,
        h: Float,
        cxNorm: Float = 0.725f,
        cyNorm: Float = 0.428f,
        radiusNorm: Float = 0.048f
    ) {
        val cx = cxNorm * w
        val cy = cyNorm * h
        val rOut = radiusNorm * h
        val rIn = rOut * 0.81f

        c.save()
        c.translate(cx, cy)
        c.rotate(-8f)

        val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(122, 74, 143) // Purple ink #7A4A8F
            style = Paint.Style.STROKE
            alpha = 150
        }
        stampPaint.strokeWidth = 3f
        c.drawCircle(0f, 0f, rOut, stampPaint)
        stampPaint.strokeWidth = 1.5f
        c.drawCircle(0f, 0f, rIn, stampPaint)

        // Arc texts: "MEZURA" top, "LIBRARY LEDGER" bottom
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(122, 74, 143)
            alpha = 160
            typeface = Typeface.DEFAULT_BOLD
            textSize = rOut * 0.17f
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.18f
        }
        val pathTop = Path().apply {
            addArc(RectF(-rIn, -rIn, rIn, rIn), 195f, 150f)
        }
        c.drawTextOnPath("MEZURA", pathTop, 0f, 0f, textPaint)

        val pathBot = Path().apply {
            addArc(RectF(-rIn, -rIn, rIn, rIn), 15f, 150f)
        }
        c.drawTextOnPath("LIBRARY LEDGER", pathBot, 0f, 0f, textPaint)

        c.restore()
    }

    // =========================================================================
    // 5.6 TELEMETRY LED MATRIX TITLE & LOGS (Style 09)
    // =========================================================================
    fun drawTelemetryTitle(
        c: Canvas,
        w: Float,
        h: Float,
        title: String,
        xNorm: Float = 0.500f,
        yNorm: Float = 0.105f,
        sizeNorm: Float = 0.034f
    ) {
        val cx = xNorm * w
        val cy = yNorm * h
        val pxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 210, 102) // Valve display amber
            typeface = Typeface.MONOSPACE
            textSize = sizeNorm * h
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.12f
        }
        // Engraved / glowing shadow
        c.drawText(title.uppercase(), cx + 1f, cy + 1f, Paint(pxPaint).apply { color = Color.rgb(120, 80, 20); alpha = 180 })
        c.drawText(title.uppercase(), cx, cy, pxPaint)
    }

    fun drawTelemetryRating(
        c: Canvas,
        w: Float,
        h: Float,
        rating: Int,
        cxNorm: Float = 0.500f,
        cyNorm: Float = 0.855f
    ) {
        val cx = cxNorm * w
        val cy = cyNorm * h
        val orbR = 0.012f * h
        val gap = orbR * 2.8f
        val startX = cx - (gap * 2f)

        for (i in 0 until 5) {
            val ox = startX + i * gap
            val isEarned = (i < rating)

            // Square cell outline
            val cellRect = RectF(ox - orbR * 1.2f, cy - orbR * 1.2f, ox + orbR * 1.2f, cy + orbR * 1.2f)
            c.drawRoundRect(cellRect, 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(28, 65, 95); style = Paint.Style.STROKE; strokeWidth = 1.5f
            })

            if (isEarned) {
                // Glowing amber orb
                c.drawCircle(ox, cy, orbR * 1.3f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(255, 184, 51); alpha = 90
                })
                c.drawCircle(ox, cy, orbR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        ox - orbR * 0.3f, cy - orbR * 0.3f, orbR,
                        Color.rgb(255, 240, 180), Color.rgb(255, 160, 30), Shader.TileMode.CLAMP
                    )
                })
            }
        }

        // Cyan label
        val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 229, 255)
            typeface = Typeface.MONOSPACE
            textSize = 0.018f * h
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
        }
        c.drawText("$rating/5 RATING", cx, cy + orbR * 2.6f, lblPaint)
    }

    fun drawTelemetrySideLogs(c: Canvas, w: Float, h: Float) {
        val logPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(40, 110, 160)
            alpha = 180
            typeface = Typeface.MONOSPACE
            textSize = 0.0115f * h
        }
        val leftLogs = listOf(
            "SYS.01: 007B 79H6",
            "RAD.AZ: 142.8 DEG",
            "DOP.VF: +0.48 M/S",
            "SIG.STR: 98.4 DB"
        )
        var ly = 0.245f * h
        for (line in leftLogs) {
            c.drawText(line, 0.065f * w, ly, logPaint)
            ly += 0.018f * h
        }

        val rightLogs = listOf(
            "TLM.HEX: EA42 09F1",
            "ORB.DEC: -18.2 DEG",
            "SYN.CLK: 1024 KHZ",
            "MOD.CAL: LOCKED"
        )
        var ry = 0.245f * h
        for (line in rightLogs) {
            c.drawText(line, 0.755f * w, ry, logPaint)
            ry += 0.018f * h
        }
    }

    // =========================================================================
    // 5.8 STREAK PILL (Styles 01, 04)
    // =========================================================================
    fun drawStreakPill(
        c: Canvas,
        w: Float,
        h: Float,
        streakDays: Int,
        xNorm: Float = 0.755f,
        yNorm: Float = 0.812f
    ) {
        val cx = xNorm * w
        val cy = yNorm * h
        val pillW = 0.165f * w
        val pillH = 0.038f * h
        val pRect = RectF(cx - pillW / 2f, cy - pillH / 2f, cx + pillW / 2f, cy + pillH / 2f)

        c.drawRoundRect(pRect, pillH / 2f, pillH / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(42, 35, 28); style = Paint.Style.STROKE; strokeWidth = 2.2f
        })

        val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(32, 26, 20)
            typeface = Typeface.DEFAULT_BOLD
            textSize = pillH * 0.52f
            textAlign = Paint.Align.CENTER
        }
        val fm = txtPaint.fontMetrics
        val lbl = "$streakDays ${if (streakDays == 1) "DAY" else "DAYS"}"
        c.drawText(lbl, cx, cy - (fm.ascent + fm.descent) / 2f, txtPaint)
    }

    // =========================================================================
    // 6.5 PROCEDURAL MONTHLY BOOK SPINES (Stats Page)
    // =========================================================================
    private val ANTIQUE_LEATHERS = intArrayOf(
        Color.rgb(122, 59, 46),  // #7A3B2E
        Color.rgb(156, 107, 48), // #9C6B30
        Color.rgb(78, 74, 69),   // #4E4A45
        Color.rgb(176, 141, 87), // #B08D57
        Color.rgb(92, 58, 36),   // #5C3A24
        Color.rgb(140, 133, 120) // #8C8578
    )

    fun drawProceduralBookSpine(
        c: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        monthIndex: Int,
        monthCount: Int,
        maxCount: Int
    ) {
        val spineW = right - left
        val spineH = bottom - top
        val leatherColor = ANTIQUE_LEATHERS[monthIndex % ANTIQUE_LEATHERS.size]

        // Per-spine slight lean (+-1.2 deg)
        val lean = ((sin(monthIndex * 1.8) * 1.2)).toFloat()
        c.save()
        c.rotate(lean, (left + right) / 2f, bottom)

        // Spine leather body with vertical cylinder shading
        val r = 4f
        val spineRect = RectF(left, top, right, bottom)
        val leatherPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                left, 0f, right, 0f,
                intArrayOf(Color.argb(220, Color.red(leatherColor)/2, Color.green(leatherColor)/2, Color.blue(leatherColor)/2), leatherColor, Color.argb(240, Color.red(leatherColor)/3, Color.green(leatherColor)/3, Color.blue(leatherColor)/3)),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRoundRect(spineRect, r, r, leatherPaint)

        // Gilt bands (#D9B36A at 60%)
        val giltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(217, 179, 106); strokeWidth = 2f; alpha = 150
        }
        c.drawLine(left + 2f, top + 0.18f * spineH, right - 2f, top + 0.18f * spineH, giltPaint)
        c.drawLine(left + 2f, top + 0.22f * spineH, right - 2f, top + 0.22f * spineH, giltPaint)
        c.drawLine(left + 2f, bottom - 0.20f * spineH, right - 2f, bottom - 0.20f * spineH, giltPaint)

        c.restore()
    }

    // =========================================================================
    // 6.6 READING CONSISTENCY (371-DOT MATRIX)
    // =========================================================================
    fun draw371DotMatrix(
        c: Canvas,
        w: Float,
        h: Float,
        cadenceList: List<Boolean>,
        longestStreakDays: Int,
        lNorm: Float = 0.100f,
        tNorm: Float = 0.724f,
        rNorm: Float = 0.900f,
        bNorm: Float = 0.788f,
        drawBaseEmptyDots: Boolean = false
    ) {
        val startX = lNorm * w
        val endX = rNorm * w
        val startY = tNorm * h
        val endY = bNorm * h
        val cols = 53
        val rows = 7
        val stepX = (endX - startX) / (cols - 1)
        val stepY = (endY - startY) / (rows - 1)
        val baseR = stepX * 0.34f

        for (col in 0 until cols) {
            for (row in 0 until rows) {
                val idx = col * rows + row
                val isRead = cadenceList.getOrElse(idx) { false }
                val isStreak = isRead && (col >= cols - (longestStreakDays / 7).coerceAtLeast(1))

                val cx = startX + col * stepX
                val cy = startY + row * stepY

                if (isStreak) {
                    // Amber streak rivet with golden glow
                    c.drawCircle(cx, cy, baseR * 1.25f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(95, 255, 195, 60)
                    })
                    val streakFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(
                            cx - baseR * 0.25f, cy - baseR * 0.25f, baseR,
                            Color.rgb(255, 228, 135), Color.rgb(195, 142, 45), Shader.TileMode.CLAMP
                        )
                    }
                    c.drawCircle(cx, cy, baseR, streakFill)
                    c.drawCircle(cx, cy, baseR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(125, 90, 28); style = Paint.Style.STROKE; strokeWidth = 1.2f
                    })
                    c.drawCircle(cx - baseR * 0.25f, cy - baseR * 0.25f, baseR * 0.35f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(180, 255, 250, 220)
                    })
                } else if (isRead) {
                    // Inked active day rivet
                    val readFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(
                            cx - baseR * 0.25f, cy - baseR * 0.25f, baseR,
                            Color.rgb(115, 90, 65), Color.rgb(45, 34, 22), Shader.TileMode.CLAMP
                        )
                    }
                    c.drawCircle(cx, cy, baseR, readFill)
                    c.drawCircle(cx, cy, baseR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(40, 30, 20); style = Paint.Style.STROKE; strokeWidth = 1.2f
                    })
                    c.drawCircle(cx - baseR * 0.25f, cy - baseR * 0.25f, baseR * 0.32f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(120, 255, 240, 200)
                    })
                } else if (drawBaseEmptyDots) {
                    // Empty brass socket (used when background plate does not have baked sockets)
                    c.drawCircle(cx, cy, baseR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(
                            cx - baseR * 0.25f, cy - baseR * 0.25f, baseR,
                            Color.rgb(215, 185, 115), Color.rgb(145, 110, 55), Shader.TileMode.CLAMP
                        )
                    })
                    c.drawCircle(cx, cy, baseR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(75, 55, 25); style = Paint.Style.STROKE; strokeWidth = 1.0f
                    })
                }
            }
        }
    }

    // =========================================================================
    // 6.7 GENRE DISTRIBUTION SLIDER (Stats Page)
    // =========================================================================
    fun drawGenreSlider(
        c: Canvas,
        w: Float,
        h: Float,
        genreName: String,
        percentage: Int,
        railLNorm: Float = 0.422f,
        railRNorm: Float = 0.832f,
        yNorm: Float = 0.836f,
        serifTypeface: Typeface
    ) {
        val railL = railLNorm * w
        val railR = railRNorm * w
        val railW = railR - railL
        val cy = yNorm * h
        val railH = 0.024f * h

        // 1. Genre Title Left
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(42, 28, 16); typeface = serifTypeface; textSize = 0.0175f * h; textAlign = Paint.Align.LEFT
        }
        val fm = labelPaint.fontMetrics
        c.drawText(genreName, 0.100f * w, cy + (fm.descent - fm.ascent) * 0.35f, labelPaint)

        // 2. Brass Caliper Jaw Gripping Rail
        val pctClamped = percentage.coerceIn(0, 100) / 100f
        val claspX = railL + railW * pctClamped
        val claspW = 0.022f * w
        val claspH = railH * 1.8f
        val claspRect = RectF(claspX - claspW / 2f, cy - claspH / 2f, claspX + claspW / 2f, cy + claspH / 2f)

        c.drawRoundRect(claspRect, 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                claspRect.left, claspRect.top, claspRect.right, claspRect.bottom,
                intArrayOf(Color.rgb(201, 162, 75), Color.rgb(138, 106, 47), Color.rgb(85, 62, 20)),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        })
        c.drawRoundRect(claspRect, 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(74, 58, 22); style = Paint.Style.STROKE; strokeWidth = 1.2f
        })

        // Knurled thumb screw (top & bottom)
        val screwR = claspW * 0.35f
        c.drawCircle(claspX, claspRect.top, screwR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(85, 62, 20) })
        c.drawCircle(claspX, claspRect.bottom, screwR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(85, 62, 20) })

        // 3. Right Percentage Readout
        val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(90, 70, 50); typeface = Typeface.create(serifTypeface, Typeface.BOLD); textSize = 0.0175f * h; textAlign = Paint.Align.LEFT
        }
        c.drawText("$percentage%", (railRNorm + 0.015f) * w, cy + (fm.descent - fm.ascent) * 0.35f, pctPaint)
    }
}
