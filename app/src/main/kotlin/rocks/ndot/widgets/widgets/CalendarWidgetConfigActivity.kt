package rocks.ndot.widgets.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.ndot.widgets.kit.Fonts
import rocks.ndot.widgets.kit.Ink
import rocks.ndot.widgets.kit.NdotSegmented
import rocks.ndot.widgets.kit.ndotScreenInsets
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class CalendarWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            CalendarConfigScreen(
                appWidgetId = appWidgetId,
                onSave = { style, startDay, showDots, showBottomInfo ->
                    saveConfigAndFinish(style, startDay, showDots, showBottomInfo)
                }
            )
        }
    }

    private fun saveConfigAndFinish(style: CalendarWidgetStyle, startDay: DayOfWeek, showDots: Boolean, showBottomInfo: Boolean) {
        val mgr = AppWidgetManager.getInstance(this)
        val info = mgr.getAppWidgetInfo(appWidgetId)
        val isWeek = info?.provider?.className?.contains("Week") == true

        if (isWeek) {
            CalendarWeekWidgetProvider.prefs(this).edit(appWidgetId) { p ->
                putString("${p}style", style.name)
                putBoolean("${p}dots", showDots)
            }
            CalendarWeekWidgetProvider.updateOne(this, mgr, appWidgetId)
        } else {
            CalendarMonthWidgetProvider.prefs(this).edit(appWidgetId) { p ->
                putString("${p}style", style.name)
                putBoolean("${p}dots", showDots)
                putBoolean("${p}show_bottom_info", showBottomInfo)
            }
            CalendarMonthWidgetProvider.updateOne(this, mgr, appWidgetId)
        }

        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }
}

@Composable
private fun CalendarConfigScreen(
    appWidgetId: Int,
    onSave: (CalendarWidgetStyle, DayOfWeek, Boolean, Boolean) -> Unit
) {
    val context = LocalContext.current
    var selectedStyle by remember { mutableStateOf(CalendarWidgetStyle.APPLE_GLASS) }
    var selectedStartDay by remember { mutableStateOf(DayOfWeek.MONDAY) }
    var showDots by remember { mutableStateOf(true) }
    var showBottomInfo by remember { mutableStateOf(true) }

    val density = context.resources.displayMetrics.density
    val previewW = (240 * density).toInt()
    val previewH = if (showBottomInfo) (260 * density).toInt() else (220 * density).toInt()

    val glassBitmap = remember(selectedStartDay, showDots, showBottomInfo) {
        CalendarWidgetRenderer.renderToBitmap(
            context = context,
            widthPx = previewW,
            heightPx = previewH,
            style = CalendarWidgetStyle.APPLE_GLASS,
            span = CalendarWidgetSpan.MONTH,
            yearMonth = YearMonth.now(),
            today = LocalDate.now(),
            showDots = showDots,
            firstDay = selectedStartDay,
            showBottomInfo = showBottomInfo
        ).asImageBitmap()
    }

    val paperBitmap = remember(selectedStartDay, showDots, showBottomInfo) {
        CalendarWidgetRenderer.renderToBitmap(
            context = context,
            widthPx = previewW,
            heightPx = previewH,
            style = CalendarWidgetStyle.PAPER,
            span = CalendarWidgetSpan.MONTH,
            yearMonth = YearMonth.now(),
            today = LocalDate.now(),
            showDots = showDots,
            firstDay = selectedStartDay,
            showBottomInfo = showBottomInfo
        ).asImageBitmap()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.GROUND)
            .ndotScreenInsets()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BasicText(
            text = "CONFIGURE CALENDAR WIDGET",
            style = TextStyle(color = Ink.AMBER, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
        )

        // Live Selectable Finish Previews
        BasicText("CHOOSE FINISH", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp, fontFamily = Fonts.mono))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Apple Glass Finish Card
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selectedStyle == CalendarWidgetStyle.APPLE_GLASS) Ink.SURFACE_3 else Ink.SURFACE_1)
                    .border(
                        width = if (selectedStyle == CalendarWidgetStyle.APPLE_GLASS) 2.dp else 1.dp,
                        color = if (selectedStyle == CalendarWidgetStyle.APPLE_GLASS) Ink.AMBER else Ink.HAIRLINE_V4,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { selectedStyle = CalendarWidgetStyle.APPLE_GLASS }
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    bitmap = glassBitmap,
                    contentDescription = "Apple Glass",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(8.dp))
                BasicText(
                    text = "Apple Glass",
                    style = TextStyle(
                        color = if (selectedStyle == CalendarWidgetStyle.APPLE_GLASS) Ink.AMBER else Ink.TEXT_WARM,
                        fontSize = 12.sp,
                        fontFamily = Fonts.mono,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // Archival Paper Finish Card
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selectedStyle == CalendarWidgetStyle.PAPER) Ink.SURFACE_3 else Ink.SURFACE_1)
                    .border(
                        width = if (selectedStyle == CalendarWidgetStyle.PAPER) 2.dp else 1.dp,
                        color = if (selectedStyle == CalendarWidgetStyle.PAPER) Ink.AMBER else Ink.HAIRLINE_V4,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { selectedStyle = CalendarWidgetStyle.PAPER }
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    bitmap = paperBitmap,
                    contentDescription = "Archival Paper",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(8.dp))
                BasicText(
                    text = "Archival Paper",
                    style = TextStyle(
                        color = if (selectedStyle == CalendarWidgetStyle.PAPER) Ink.AMBER else Ink.TEXT_WARM,
                        fontSize = 12.sp,
                        fontFamily = Fonts.mono,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        // Options: Week starts on
        BasicText("WEEK STARTS ON", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp, fontFamily = Fonts.mono))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                DayOfWeek.SATURDAY to "Saturday",
                DayOfWeek.SUNDAY to "Sunday",
                DayOfWeek.MONDAY to "Monday"
            ).forEach { (dow, label) ->
                val isSel = selectedStartDay == dow
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSel) Ink.AMBER else Ink.SURFACE_2)
                        .clickable { selectedStartDay = dow }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = label,
                        style = TextStyle(
                            color = if (isSel) Ink.GROUND else Ink.TEXT_WARM,
                            fontSize = 11.sp,
                            fontFamily = Fonts.mono,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                }
            }
        }

        // Habit & Reading Dots Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Ink.SURFACE_2)
                .clickable { showDots = !showDots }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                BasicText("Show Activity Dots", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 13.sp, fontFamily = Fonts.sans))
                BasicText("Displays completed habits and reading logs", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp))
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (showDots) Ink.AMBER else Ink.SURFACE_3),
                contentAlignment = Alignment.Center
            ) {
                if (showDots) {
                    BasicText("✓", style = TextStyle(color = Ink.GROUND, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                }
            }
        }

        // Show Date & Yearline Info Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Ink.SURFACE_2)
                .clickable { showBottomInfo = !showBottomInfo }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                BasicText("Show Date & Yearline Info", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 13.sp, fontFamily = Fonts.sans))
                BasicText("Displays full date, day of year, and week number below calendar", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp))
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (showBottomInfo) Ink.AMBER else Ink.SURFACE_3),
                contentAlignment = Alignment.Center
            ) {
                if (showBottomInfo) {
                    BasicText("✓", style = TextStyle(color = Ink.GROUND, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Confirm Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Ink.AMBER)
                .clickable {
                    onSave(selectedStyle, selectedStartDay, showDots, showBottomInfo)
                },
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = "APPLY TO WIDGET",
                style = TextStyle(
                    color = Ink.GROUND,
                    fontSize = 13.sp,
                    fontFamily = Fonts.mono,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}
