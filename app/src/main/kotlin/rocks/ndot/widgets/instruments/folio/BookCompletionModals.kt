package rocks.ndot.widgets.instruments.folio

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import rocks.ndot.widgets.kit.Feedback
import rocks.ndot.widgets.kit.Fonts
import rocks.ndot.widgets.kit.Ink

@Composable
fun BookCompletionFlowHost(
    flowState: BookCompletionFlowState,
    onRatingSubmitted: (BookItem, Int) -> Unit,
    onOpenShareStudio: (BookCompletionStats) -> Unit,
    onStyleSelected: (BookStatsDesignStyle) -> Unit,
    onDismiss: () -> Unit
) {
    when (flowState) {
        is BookCompletionFlowState.Idle -> Unit

        is BookCompletionFlowState.AwaitingStarRating -> {
            MezuraBookRatingSheet(
                book = flowState.book,
                initialRating = flowState.initialRating,
                onConfirmRating = { rating ->
                    onRatingSubmitted(flowState.book, rating)
                },
                onDismiss = onDismiss
            )
        }

        is BookCompletionFlowState.PromptToShareStats -> {
            MezuraSharePromptSheet(
                stats = flowState.stats,
                onChooseDesignAndShare = {
                    onOpenShareStudio(flowState.stats)
                },
                onDismiss = onDismiss
            )
        }

        is BookCompletionFlowState.SelectingShareStyle -> {
            MezuraBookStatsStudioDialog(
                stats = flowState.stats,
                selectedStyle = flowState.selectedStyle,
                onStyleSelected = onStyleSelected,
                onDismiss = onDismiss
            )
        }
    }
}

/**
 * Tactile brass star drawing component for the 5-star rating bar.
 */
@Composable
fun TactileBrassStarIcon(
    isFilled: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerR = size.minDimension / 2f * 0.94f
        val innerR = outerR * 0.44f
        drawFacetedBrassStar(cx, cy, outerR, innerR, isFilled = isFilled, showGlow = true)
    }
}

/**
 * STAGE 1: Tactile Hardware Star Rating Sheet
 */
@Composable
fun MezuraBookRatingSheet(
    book: BookItem,
    initialRating: Int,
    onConfirmRating: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedRating by remember(book.id) { mutableIntStateOf(if (initialRating in 1..5) initialRating else 5) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xB3000000))
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF222428), Color(0xFF17181B))
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF484C54), Color(0xFF25282E))
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BasicText(
                    text = "MEZURA · READING RECORD",
                    style = TextStyle(
                        color = Color(0xFFC99B53),
                        fontSize = 10.sp,
                        letterSpacing = 2.4.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Fonts.mono
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                BasicText(
                    text = "BOOK COMPLETED",
                    style = TextStyle(
                        color = Color(0xFFF4EFE6),
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                BasicText(
                    text = "${book.title} — ${book.author}",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = Color(0xFFB8B2A6),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                val totalP = book.totalPages.coerceAtLeast(1)
                BasicText(
                    text = "$totalP / $totalP PAGES LOGGED",
                    style = TextStyle(
                        color = Color(0xFF8C867B),
                        fontSize = 10.sp,
                        letterSpacing = 1.5.sp,
                        fontFamily = Fonts.mono
                    )
                )

                Spacer(modifier = Modifier.height(22.dp))
                BasicText(
                    text = "RATE THIS BOOK",
                    style = TextStyle(
                        color = Color(0xFFD6C7B2),
                        fontSize = 11.sp,
                        letterSpacing = 2.sp,
                        fontFamily = Fonts.mono
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Interactive 5-Star Tactile Bar
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF101114))
                        .border(1.dp, Color(0xFF2C2F36), RoundedCornerShape(14.dp))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (star in 1..5) {
                        val isLit = star <= selectedRating
                        val scale by animateFloatAsState(
                            targetValue = if (isLit) 1.12f else 0.95f,
                            animationSpec = spring(
                                dampingRatio = 0.55f,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "starScale"
                        )
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .scale(scale)
                                .clip(CircleShape)
                                .clickable {
                                    selectedRating = star
                                    Feedback.tick(context)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            TactileBrassStarIcon(
                                isFilled = isLit,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Primary Confirm Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFC89B4E), Color(0xFF9E712B))
                            )
                        )
                        .clickable {
                            Feedback.haptic(context, Feedback.Haptic.DONE)
                            onConfirmRating(selectedRating)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = "SAVE RATING & CONTINUE",
                        style = TextStyle(
                            color = Color(0xFF14120E),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.6.sp,
                            fontFamily = Fonts.mono
                        )
                    )
                }
            }
        }
    }
}

/**
 * STAGE 2: Prompt asking if the user wants to share their stats across the 9 design languages.
 */
@Composable
fun MezuraSharePromptSheet(
    stats: BookCompletionStats,
    onChooseDesignAndShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(stats) {
        MezuraMasterPlateEngine.prefetchAsync(context, stats)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xB3000000))
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF1C1E22))
                    .border(1.dp, Color(0xFF3A3E47), RoundedCornerShape(22.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BasicText(
                    text = "RATING RECORDED (${stats.starRating}/5 STARS)",
                    style = TextStyle(
                        color = Color(0xFFD8A657),
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Fonts.mono
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                BasicText(
                    text = "Share Book Report",
                    style = TextStyle(
                        color = Color(0xFFF5F0E6),
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                BasicText(
                    text = "Choose from 7 precision Mezura design languages—from Archival Split-Flap and Dot-Matrix LED to Astrolabe and Obsidian Editorial.",
                    style = TextStyle(
                        color = Color(0xFFB0ABA0),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                )
                Spacer(modifier = Modifier.height(22.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFD4A455), Color(0xFFA87830))
                            )
                        )
                        .clickable { onChooseDesignAndShare() },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = "SHARE BOOK REPORT",
                        style = TextStyle(
                            color = Color(0xFF14120E),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                            fontFamily = Fonts.mono
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = "MAYBE LATER",
                        style = TextStyle(
                            color = Color(0xFF8C9099),
                            fontSize = 11.sp,
                            letterSpacing = 1.4.sp,
                            fontFamily = Fonts.mono
                        )
                    )
                }
            }
        }
    }
}

/**
 * STAGE 3: Full-screen 9-Style Reading Record Studio with Serial 1..9 Selector and Share Export.
 */
@Composable
fun MezuraBookStatsStudioDialog(
    stats: BookCompletionStats,
    selectedStyle: BookStatsDesignStyle,
    onStyleSelected: (BookStatsDesignStyle) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSharing by remember { mutableStateOf(false) }

    LaunchedEffect(stats) {
        MezuraMasterPlateEngine.prefetchAsync(context, stats)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E0F12))
                .systemBarsPadding()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Studio Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = "CLOSE",
                    style = TextStyle(
                        color = Color(0xFF9EA3AE),
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp,
                        fontFamily = Fonts.mono
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDismiss() }
                        .padding(8.dp)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText(
                        text = "SHARE BOOK REPORT",
                        style = TextStyle(
                            color = Color(0xFFD6A85A),
                            fontSize = 10.sp,
                            letterSpacing = 2.2.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Fonts.mono
                        )
                    )
                    BasicText(
                        text = selectedStyle.displayTitle,
                        style = TextStyle(
                            color = Color(0xFFEAECEF),
                            fontSize = 12.sp,
                            fontFamily = Fonts.sans
                        )
                    )
                }
                val curIdx = BookStatsDesignStyle.SERIAL_LIST.indexOf(selectedStyle).let { if (it >= 0) it + 1 else 1 }
                BasicText(
                    text = "$curIdx / ${BookStatsDesignStyle.SERIAL_LIST.size}",
                    style = TextStyle(
                        color = Color(0xFFD6A85A),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Fonts.mono
                    ),
                    modifier = Modifier.padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Center Dynamic Artboard Live Card Preview matching exact native aspect ratio
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .aspectRatio(selectedStyle.aspectRatio)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFF2B2E38), RoundedCornerShape(14.dp))
            ) {
                MezuraBookStatsCardRenderer(
                    stats = stats,
                    style = selectedStyle,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Serial 1..9 Design Language Picker Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BookStatsDesignStyle.SERIAL_LIST.forEach { style ->
                    val isSelected = style == selectedStyle
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) Color(0xFFD6A85A) else Color(0xFF1E2026)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFFFFD480) else Color(0xFF323640),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                Feedback.tick(context)
                                onStyleSelected(style)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        BasicText(
                            text = style.shortBadge,
                            style = TextStyle(
                                color = if (isSelected) Color(0xFF14120E) else Color(0xFFD0D4DC),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp,
                                fontFamily = Fonts.mono
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Share CTA Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFD9A959), Color(0xFFA6762C))
                        )
                    )
                    .clickable(enabled = !isSharing) {
                        isSharing = true
                        Feedback.tick(context)
                        scope.launch {
                            BookStatsBitmapExporter.renderAndShare(
                                context = context,
                                stats = stats,
                                style = selectedStyle
                            )
                            isSharing = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = if (isSharing) "RENDERING HIGH-RES RECORD..." else "SHARE READING RECORD",
                    style = TextStyle(
                        color = Color(0xFF12100C),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp,
                        fontFamily = Fonts.mono
                    )
                )
            }
        }
    }
}
