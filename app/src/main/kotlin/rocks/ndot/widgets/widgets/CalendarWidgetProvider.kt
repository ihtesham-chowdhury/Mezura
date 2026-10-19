package rocks.ndot.widgets.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.widget.RemoteViews
import rocks.ndot.widgets.MainActivity
import rocks.ndot.widgets.R
import rocks.ndot.widgets.common.WidgetPrefs
import rocks.ndot.widgets.common.WidgetSizing
import rocks.ndot.widgets.instruments.folio.BookStore
import rocks.ndot.widgets.instruments.habits.HabitStore
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Calendar
import java.util.Locale

enum class CalendarWidgetStyle { APPLE_GLASS, PAPER }
enum class CalendarWidgetSpan { MONTH, WEEK, YEAR }

object CalendarWidgetRenderer {
    fun renderToBitmap(
        context: Context,
        widthPx: Int,
        heightPx: Int,
        style: CalendarWidgetStyle,
        span: CalendarWidgetSpan = CalendarWidgetSpan.MONTH,
        yearMonth: YearMonth = YearMonth.now(),
        today: LocalDate = LocalDate.now(),
        showDots: Boolean = true,
        firstDay: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
        showBottomInfo: Boolean = true
    ): Bitmap {
        val w = widthPx.coerceAtLeast(64)
        val h = heightPx.coerceAtLeast(64)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        CalendarCanvasDrawer.draw(
            context = context,
            canvas = canvas,
            width = w.toFloat(),
            height = h.toFloat(),
            style = style,
            span = span,
            yearMonth = yearMonth,
            today = today,
            showDots = showDots,
            firstDay = firstDay,
            showBottomInfo = showBottomInfo
        )
        return bitmap
    }
}

object CalendarCanvasDrawer {

    fun draw(
        context: Context,
        canvas: Canvas,
        width: Float,
        height: Float,
        style: CalendarWidgetStyle,
        span: CalendarWidgetSpan,
        yearMonth: YearMonth,
        today: LocalDate,
        showDots: Boolean,
        firstDay: DayOfWeek,
        showBottomInfo: Boolean = true
    ) {
        val density = context.resources.displayMetrics.density
        val locale = Locale.getDefault()

        if (style == CalendarWidgetStyle.APPLE_GLASS) {
            drawAppleGlass(context, canvas, width, height, span, yearMonth, today, firstDay, locale, density, showDots, showBottomInfo)
        } else {
            drawPaper(context, canvas, width, height, span, yearMonth, today, firstDay, locale, density, showDots, showBottomInfo)
        }
    }

    private fun drawAppleGlass(
        context: Context,
        canvas: Canvas,
        w: Float,
        h: Float,
        span: CalendarWidgetSpan,
        yearMonth: YearMonth,
        today: LocalDate,
        firstDay: DayOfWeek,
        locale: Locale,
        density: Float,
        showDots: Boolean,
        showBottomInfo: Boolean
    ) {
        val corner = 28f * density
        val bottomH = if (showBottomInfo && span == CalendarWidgetSpan.MONTH) 46f * density else 0f
        val bounds = RectF(2f * density, 2f * density, w - 2f * density, h - bottomH - 2f * density)

        // 1. Base: smoked glass #B3141418 -> #99101216
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                bounds.left, bounds.top, bounds.left, bounds.bottom,
                intArrayOf(0xB3141418.toInt(), 0x99101216.toInt()),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(bounds, corner, corner, bgPaint)

        // 2. Inner light: diagonal white 14% -> 0%
        val innerLightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                bounds.left, bounds.top, bounds.right, bounds.bottom,
                intArrayOf(0x24FFFFFF.toInt(), 0x00FFFFFF.toInt()),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(bounds, corner, corner, innerLightPaint)

        // 3. Specular rim: 1.25dp stroke (white 55% -> 6% -> 28%)
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 1.25f * density
            shader = LinearGradient(
                bounds.left, bounds.top, bounds.right, bounds.bottom,
                intArrayOf(0x8CFFFFFF.toInt(), 0x10FFFFFF.toInt(), 0x47FFFFFF.toInt()),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(bounds, corner, corner, rimPaint)

        // 4. Top edge highlight: 1dp line, inset 1.5dp, white 18%
        val topHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x2EFFFFFF.toInt()
            strokeWidth = 1f * density
        }
        canvas.drawLine(bounds.left + corner, bounds.top + 1.5f * density, bounds.right - corner, bounds.top + 1.5f * density, topHighlightPaint)

        if (span == CalendarWidgetSpan.WEEK) {
            drawWeekContent(context, canvas, bounds, today, firstDay, locale, density, textColor = 0xFFFFFFFF.toInt(), accentColor = 0xFFFF3B30.toInt(), isGlass = true, showDots = showDots)
        } else {
            drawMonthContent(context, canvas, bounds, yearMonth, today, firstDay, locale, density, textColor = 0xFFFFFFFF.toInt(), dimTextColor = 0x55FFFFFF.toInt(), headerColor = 0xFFFFFFFF.toInt(), weekLabelColor = 0x80FFFFFF.toInt(), todayAccentColor = 0xFFFF3B30.toInt(), isGlass = true, showDots = showDots)
            if (showBottomInfo) {
                drawBottomInfo(canvas, bounds, today, locale, density, isGlass = true)
            }
        }
    }

    private fun drawPaper(
        context: Context,
        canvas: Canvas,
        w: Float,
        h: Float,
        span: CalendarWidgetSpan,
        yearMonth: YearMonth,
        today: LocalDate,
        firstDay: DayOfWeek,
        locale: Locale,
        density: Float,
        showDots: Boolean,
        showBottomInfo: Boolean
    ) {
        val corner = 14f * density
        val bottomH = if (showBottomInfo && span == CalendarWidgetSpan.MONTH) 46f * density else 0f
        val bounds = RectF(4f * density, 4f * density, w - 8f * density, h - bottomH - 8f * density)

        // 1. Lifted shadow (black 22%)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x38000000.toInt()
        }
        val shadowBounds = RectF(bounds.left + 2f * density, bounds.top + 3f * density, bounds.right + 2f * density, bounds.bottom + 3f * density)
        canvas.drawRoundRect(shadowBounds, corner, corner, shadowPaint)

        // 2. Warm Archival Paper: #F4EFE6
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF4EFE6.toInt()
        }
        canvas.drawRoundRect(bounds, corner, corner, bgPaint)

        // 3. Spiral wire strip across top: 9 punched holes
        val headerH = 22f * density
        val headerBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x141A1917.toInt()
        }
        canvas.drawRoundRect(RectF(bounds.left, bounds.top, bounds.right, bounds.top + headerH), corner, corner, headerBandPaint)

        val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000.toInt() }
        val wirePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF88847C.toInt()
            this.style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
        }
        val holeCount = 9
        val step = bounds.width() / (holeCount + 1)
        for (i in 1..holeCount) {
            val hx = bounds.left + i * step
            val hy = bounds.top + 7f * density
            canvas.drawCircle(hx, hy, 2f * density, holePaint)
            canvas.drawArc(RectF(hx - 2.5f * density, hy - 4f * density, hx + 2.5f * density, hy + 2f * density), 180f, 180f, false, wirePaint)
        }

        // Hairline rule
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 1f * density
            color = 0x221A1917.toInt()
        }
        canvas.drawRoundRect(bounds, corner, corner, borderPaint)

        if (span == CalendarWidgetSpan.WEEK) {
            drawWeekContent(context, canvas, bounds, today, firstDay, locale, density, textColor = 0xFF1A1917.toInt(), accentColor = 0xFFC83E2B.toInt(), isGlass = false, showDots = showDots)
        } else {
            drawMonthContent(context, canvas, bounds, yearMonth, today, firstDay, locale, density, textColor = 0xFF1A1917.toInt(), dimTextColor = 0x441A1917.toInt(), headerColor = 0xFFC83E2B.toInt(), weekLabelColor = 0x881A1917.toInt(), todayAccentColor = 0xFFC83E2B.toInt(), isGlass = false, showDots = showDots)
            if (showBottomInfo) {
                drawBottomInfo(canvas, bounds, today, locale, density, isGlass = false)
            }
        }
    }

    private fun drawMonthContent(
        context: Context,
        canvas: Canvas,
        bounds: RectF,
        yearMonth: YearMonth,
        today: LocalDate,
        firstDay: DayOfWeek,
        locale: Locale,
        density: Float,
        textColor: Int,
        dimTextColor: Int,
        headerColor: Int,
        weekLabelColor: Int,
        todayAccentColor: Int,
        isGlass: Boolean,
        showDots: Boolean
    ) {
        val pad = 14f * density
        var currentY = bounds.top + pad + 16f * density

        // Header: Month in Title Case + Year (e.g., "October 2026")
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = headerColor
            textSize = 17f * density
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val monthRaw = yearMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
        val monthTitleCase = monthRaw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        val headerText = "$monthTitleCase ${yearMonth.year}"
        canvas.drawText(headerText, bounds.left + pad, currentY, headerPaint)

        // Circular Chevron Indicators on right: ‹ ›
        val btnRadius = 12f * density
        val btnY = currentY - 5f * density
        val rightBtnX = bounds.right - pad - btnRadius
        val leftBtnX = rightBtnX - btnRadius * 2 - 8f * density

        val chevronBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isGlass) 0x33FFFFFF.toInt() else 0x1A1A1917.toInt()
            style = Paint.Style.FILL
        }
        val chevronTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isGlass) 0xFFFFFFFF.toInt() else 0xFF1A1917.toInt()
            textSize = 13f * density
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawCircle(leftBtnX, btnY, btnRadius, chevronBgPaint)
        canvas.drawCircle(rightBtnX, btnY, btnRadius, chevronBgPaint)
        canvas.drawText("‹", leftBtnX, btnY + 4f * density, chevronTextPaint)
        canvas.drawText("›", rightBtnX, btnY + 4f * density, chevronTextPaint)

        currentY += 18f * density

        // Weekday header 3-letter uppercase (e.g. SUN, MON, TUE, WED, THU, FRI, SAT)
        val weekPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = weekLabelColor
            textSize = 9.5f * density
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val gridWidth = bounds.width() - pad * 2
        val colWidth = gridWidth / 7f
        val weekdays = (0 until 7).map { firstDay.plus(it.toLong()) }
        weekdays.forEachIndexed { i, dow ->
            val cx = bounds.left + pad + colWidth * i + colWidth / 2f
            val sym = dow.getDisplayName(TextStyle.SHORT, locale).uppercase(locale)
            canvas.drawText(sym, cx, currentY, weekPaint)
        }

        currentY += 8f * density

        // 6-row date grid
        val availableHeight = bounds.bottom - currentY - pad
        val rowHeight = (availableHeight / 6f).coerceAtLeast(14f * density)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (rowHeight * 0.44f).coerceIn(10f * density, 14.5f * density)
            textAlign = Paint.Align.CENTER
        }
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        val firstOfMonth = yearMonth.atDay(1)
        val lead = ((firstOfMonth.dayOfWeek.value - firstDay.value) + 7) % 7
        val startDay = firstOfMonth.minusDays(lead.toLong())

        for (row in 0 until 6) {
            val cellY = currentY + row * rowHeight + rowHeight * 0.7f
            val cellCenterY = currentY + row * rowHeight + rowHeight / 2f
            for (col in 0 until 7) {
                val date = startDay.plusDays((row * 7 + col).toLong())
                val inMonth = YearMonth.from(date) == yearMonth
                val isToday = date == today
                val cx = bounds.left + pad + col * colWidth + colWidth / 2f

                if (isToday) {
                    val radius = (rowHeight * 0.44f).coerceAtMost(colWidth * 0.44f)
                    circlePaint.color = todayAccentColor
                    circlePaint.style = Paint.Style.FILL
                    canvas.drawCircle(cx, cellCenterY, radius, circlePaint)
                }

                textPaint.color = when {
                    isToday -> 0xFFFFFFFF.toInt()
                    inMonth -> textColor
                    else -> dimTextColor
                }
                textPaint.typeface = Typeface.create("sans-serif", if (isToday) Typeface.BOLD else Typeface.NORMAL)
                canvas.drawText(date.dayOfMonth.toString(), cx, cellY, textPaint)

                // Dots (2.5dp) under dates with habit/reading
                if (showDots && inMonth && !isToday) {
                    val hasHabit = (date.dayOfMonth % 2 == 0) // sample indicator
                    if (hasHabit) {
                        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = if (isGlass) 0xFFD9663F.toInt() else 0xFFC83E2B.toInt()
                        }
                        canvas.drawCircle(cx, cellCenterY + radiusOr(rowHeight, colWidth) + 2f * density, 1.25f * density, dotPaint)
                    }
                }
            }
        }
    }

    private fun drawBottomInfo(
        canvas: Canvas,
        bounds: RectF,
        today: LocalDate,
        locale: Locale,
        density: Float,
        isGlass: Boolean
    ) {
        val pad = 12f * density
        val infoLeft = bounds.left + pad
        val dateY = bounds.bottom + 18f * density
        val subY = dateY + 16f * density

        val fullDatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isGlass) 0xFFFFFFFF.toInt() else 0xFF1A1917.toInt()
            textSize = 14.5f * density
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val fullDateStr = "${today.dayOfWeek.getDisplayName(TextStyle.FULL, locale)}, ${today.dayOfMonth} ${today.month.getDisplayName(TextStyle.FULL, locale)} ${today.year}"
        canvas.drawText(fullDateStr, infoLeft, dateY, fullDatePaint)

        val subInfoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isGlass) 0x8CFFFFFF.toInt() else 0x881A1917.toInt()
            textSize = 9.5f * density
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            letterSpacing = 0.08f
        }
        val weekNum = today.get(WeekFields.of(locale).weekOfWeekBasedYear())
        val subInfoStr = "DAY ${today.dayOfYear} OF ${today.lengthOfYear()} · WEEK $weekNum · TODAY"
        canvas.drawText(subInfoStr, infoLeft, subY, subInfoPaint)
    }

    private fun radiusOr(rowHeight: Float, colWidth: Float): Float =
        (rowHeight * 0.35f).coerceAtMost(colWidth * 0.35f)

    private fun drawWeekContent(
        context: Context,
        canvas: Canvas,
        bounds: RectF,
        today: LocalDate,
        firstDay: DayOfWeek,
        locale: Locale,
        density: Float,
        textColor: Int,
        accentColor: Int,
        isGlass: Boolean,
        showDots: Boolean
    ) {
        val pad = 12f * density
        var currentY = bounds.top + pad + 14f * density

        // Header: Month label
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isGlass) 0xFFFFFFFF.toInt() else 0xFFC83E2B.toInt()
            textSize = 13f * density
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val ym = YearMonth.from(today)
        val monthLabel = "${ym.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).uppercase(locale)} ${ym.year}"
        canvas.drawText(monthLabel, bounds.left + pad, currentY, headerPaint)

        currentY += 14f * density

        // 7 large day cells
        val lead = ((today.dayOfWeek.value - firstDay.value) + 7) % 7
        val weekStart = today.minusDays(lead.toLong())
        val gridW = bounds.width() - pad * 2
        val colW = gridW / 7f
        val cellH = bounds.bottom - currentY - pad

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
        }
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        for (i in 0 until 7) {
            val date = weekStart.plusDays(i.toLong())
            val isToday = date == today
            val cx = bounds.left + pad + i * colW + colW / 2f

            // Day letter
            textPaint.color = if (isToday) accentColor else if (isGlass) 0x88FFFFFF.toInt() else 0x881A1917.toInt()
            textPaint.textSize = 9.5f * density
            textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            val dowStr = date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale).uppercase(locale)
            canvas.drawText(dowStr, cx, currentY + 10f * density, textPaint)

            // Today raised pill
            val cy = currentY + cellH * 0.65f
            if (isToday) {
                circlePaint.color = if (isGlass) 0x47FFB14A.toInt() else 0x24C83E2B.toInt()
                circlePaint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, 14f * density, circlePaint)
                circlePaint.color = accentColor
                circlePaint.style = Paint.Style.STROKE
                circlePaint.strokeWidth = 1.2f * density
                canvas.drawCircle(cx, cy, 14f * density, circlePaint)
            }

            // Date numeral
            textPaint.color = if (isToday) (if (isGlass) 0xFFFFFFFF.toInt() else accentColor) else textColor
            textPaint.textSize = 15f * density
            textPaint.typeface = Typeface.create("sans-serif", if (isToday) Typeface.BOLD else Typeface.NORMAL)
            canvas.drawText(date.dayOfMonth.toString(), cx, cy + 5f * density, textPaint)
        }
    }
}

// -----------------------------------------------------------------------------
// Calendar Providers: Month (4x4) and Week (4x2)
// -----------------------------------------------------------------------------

open class CalendarBaseProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action == Intent.ACTION_DATE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED) {
            CalendarMonthWidgetProvider.updateAll(context)
            CalendarWeekWidgetProvider.updateAll(context)
            scheduleMidnightAlarm(context)
        }
    }

    protected fun scheduleMidnightAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val midnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 1)
            set(Calendar.MILLISECOND, 0)
        }
        val intent = Intent(context, CalendarMonthWidgetProvider::class.java).apply {
            action = Intent.ACTION_DATE_CHANGED
        }
        val pending = PendingIntent.getBroadcast(
            context, 9001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            am.setAndAllowWhileIdle(AlarmManager.RTC, midnight.timeInMillis, pending)
        }
    }
}

class CalendarMonthWidgetProvider : CalendarBaseProvider() {

    companion object {
        const val PREFS_STYLE = "style"
        const val PREFS_MONTH_OFFSET = "month_offset"
        const val PREFS_SHOW_BOTTOM_INFO = "show_bottom_info"
        const val PREFS_DOTS = "dots"

        fun prefs(context: Context) = WidgetPrefs(context, "calendar_month")

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, CalendarMonthWidgetProvider::class.java))
            ids.forEach { updateOne(context, mgr, it) }
        }

        fun updateOne(context: Context, mgr: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_calendar)
            val p = prefs(context)
            val styleStr = p.getString(id, PREFS_STYLE, CalendarWidgetStyle.APPLE_GLASS.name)
            val style = runCatching { CalendarWidgetStyle.valueOf(styleStr ?: "") }.getOrDefault(CalendarWidgetStyle.APPLE_GLASS)
            val monthOffset = p.getInt(id, PREFS_MONTH_OFFSET, 0)
            val showBottomInfo = p.getBoolean(id, PREFS_SHOW_BOTTOM_INFO, true)
            val showDots = p.getBoolean(id, PREFS_DOTS, true)
            val ym = YearMonth.now().plusMonths(monthOffset.toLong())

            val defaultH = if (showBottomInfo) 310 else 260
            val (wPx, hPx) = WidgetSizing.sizePx(context, mgr, id, 260, defaultH)
            val bmp = CalendarWidgetRenderer.renderToBitmap(
                context = context,
                widthPx = wPx,
                heightPx = hPx,
                style = style,
                span = CalendarWidgetSpan.MONTH,
                yearMonth = ym,
                today = LocalDate.now(),
                showDots = showDots,
                showBottomInfo = showBottomInfo
            )
            views.setImageViewBitmap(R.id.calendarImage, bmp)

            // Tap body -> Open MainActivity on Calendar
            val clickIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("instrument", "calendar")
            }
            val pending = PendingIntent.getActivity(
                context, id, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.calendarWidgetRoot, pending)
            views.setOnClickPendingIntent(R.id.calendarImage, pending)

            mgr.updateAppWidget(id, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateOne(context, appWidgetManager, it) }
        scheduleMidnightAlarm(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        updateOne(context, appWidgetManager, appWidgetId)
    }
}

class CalendarWeekWidgetProvider : CalendarBaseProvider() {

    companion object {
        const val PREFS_STYLE = "style"

        fun prefs(context: Context) = WidgetPrefs(context, "calendar_week")

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, CalendarWeekWidgetProvider::class.java))
            ids.forEach { updateOne(context, mgr, it) }
        }

        fun updateOne(context: Context, mgr: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_calendar)
            val p = prefs(context)
            val styleStr = p.getString(id, PREFS_STYLE, CalendarWidgetStyle.APPLE_GLASS.name)
            val style = runCatching { CalendarWidgetStyle.valueOf(styleStr ?: "") }.getOrDefault(CalendarWidgetStyle.APPLE_GLASS)

            val (wPx, hPx) = WidgetSizing.sizePx(context, mgr, id, 260, 110)
            val bmp = CalendarWidgetRenderer.renderToBitmap(
                context = context,
                widthPx = wPx,
                heightPx = hPx,
                style = style,
                span = CalendarWidgetSpan.WEEK,
                today = LocalDate.now(),
                showDots = true
            )
            views.setImageViewBitmap(R.id.calendarImage, bmp)

            val clickIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("instrument", "calendar")
            }
            val pending = PendingIntent.getActivity(
                context, id, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.calendarWidgetRoot, pending)
            views.setOnClickPendingIntent(R.id.calendarImage, pending)

            mgr.updateAppWidget(id, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateOne(context, appWidgetManager, it) }
        scheduleMidnightAlarm(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        updateOne(context, appWidgetManager, appWidgetId)
    }
}

/** Legacy provider alias for WidgetSettingsActivity compatibility. */
object CalendarWidgetProvider {
    fun updateAll(context: Context) {
        CalendarMonthWidgetProvider.updateAll(context)
        CalendarWeekWidgetProvider.updateAll(context)
    }
}
