package rocks.ndot.widgets.app

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import rocks.ndot.widgets.kit.Fonts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.ndot.widgets.kit.Feedback
import rocks.ndot.widgets.kit.Ink
import rocks.ndot.widgets.kit.labelStyle

/**
 * The instruments are grouped the way you'd sort a desk drawer: by what they measure.
 * Adding a new instrument later means adding a [Tool] - the drawer, home screen and routing
 * all follow from these two lists.
 */
enum class Family(val label: String, val routeId: String) {
    TIME("TIME", "time"),
    RHYTHM("RHYTHM", "metronome"),
    CALENDAR("CALENDAR", "calendar"),
    HABITS("HABITS", "habits"),
    FOLIO("READING", "folio"),
    BREATHING("BREATHING", "breathing"),
    ALMANAC("YEARLINE", "almanac");

    val tools: List<Tool> get() = Tool.entries.filter { it.family == this }
}

enum class Tool(val id: String, val label: String, val family: Family) {
    STOPWATCH("stopwatch", "STOPWATCH", Family.TIME),
    POMODORO("pomodoro", "POMODORO", Family.TIME),
    HOURGLASS("hourglass", "HOURGLASS", Family.TIME),
    METRONOME("metronome", "METRONOME", Family.RHYTHM),
    CALENDAR("calendar", "CALENDAR", Family.CALENDAR),
    HABITS("habits", "HABITS", Family.HABITS),
    FOLIO("folio", "READING", Family.FOLIO),
    BREATHING("breathing", "BREATHING", Family.BREATHING),
    ALMANAC("almanac", "YEARLINE", Family.ALMANAC);

    companion object {
        fun byId(id: String?): Tool? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The floating bar. The lower pill holds the families and scrolls sideways if they outgrow the
 * screen; tapping a family that holds several instruments slides a second pill up out of it -
 * the drawer opening - and tapping one of those opens it. A family with a single instrument
 * skips the drawer and opens straight away.
 */
@Composable
fun DrawerBar(
    current: Tool?,
    openFamily: Family?,
    onOpenFamily: (Family?) -> Unit,
    onHome: () -> Unit,
    onTool: (Tool) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val maxWidth = LocalConfiguration.current.screenWidthDp.dp - 24.dp
    // Keeps the drawer's contents on screen while its exit animation plays.
    var shownFamily by remember { mutableStateOf(openFamily) }
    if (openFamily != null) shownFamily = openFamily

    Column(modifier.navigationBarsPadding().padding(bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(
            visible = openFamily != null,
            enter = fadeIn(tween(150)) +
                slideInVertically(spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) { it / 2 } +
                scaleIn(spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.86f, transformOrigin = TransformOrigin(0.5f, 1f)),
            exit = fadeOut(tween(110)) + slideOutVertically(tween(160)) { it / 3 } +
                scaleOut(tween(160), targetScale = 0.9f, transformOrigin = TransformOrigin(0.5f, 1f)),
        ) {
            Row(
                Modifier
                    .padding(bottom = 8.dp)
                    .widthIn(max = maxWidth)
                    .shadow(18.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(CircleShape)
                    .background(Color(0xF7161616))
                    .border(1.dp, Ink.Hairline, CircleShape)
                    .horizontalScroll(rememberScrollState())
                    .padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                shownFamily?.tools?.forEach { tool ->
                    DrawerItem(
                        text = tool.label,
                        selected = tool == current,
                        onClick = {
                            Feedback.press(context, Feedback.Sound.KEY_UP, Feedback.Haptic.CLICK)
                            onTool(tool)
                        },
                        onLongClick = {
                            if (tool.family == Family.TIME) {
                                val human = tool.label.lowercase().replaceFirstChar { it.uppercase() }
                                AppPrefs.setDefault(context, AppPrefs.KEY_DEFAULT_TIME_INSTRUMENT, tool.id, "$human set as default Time instrument")
                            }
                        },
                    )
                }
            }
        }

        val selectedFamilyIndex = remember(current?.family) {
            val idx = Family.entries.indexOf(current?.family)
            if (idx >= 0) idx else 0
        }

        val scrollState = rememberScrollState()
        val coroutineScope = rememberCoroutineScope()

        val itemOffsetsPx = remember(Family.entries.size) { IntArray(Family.entries.size) }
        val itemWidthsPx = remember(Family.entries.size) { IntArray(Family.entries.size) }
        var viewportWidthPx by remember { mutableIntStateOf(0) }

        // Automatically center the active tab in the middle of the pill viewport
        // (or pin cleanly to the far-left / far-right edge via coerceIn(0, scrollState.maxValue))
        androidx.compose.runtime.LaunchedEffect(selectedFamilyIndex, viewportWidthPx, scrollState.maxValue) {
            if (viewportWidthPx > 0 && selectedFamilyIndex in Family.entries.indices) {
                val itemCenter = itemOffsetsPx[selectedFamilyIndex] + itemWidthsPx[selectedFamilyIndex] / 2f
                val desiredScroll = (itemCenter - viewportWidthPx / 2f)
                    .roundToInt()
                    .coerceIn(0, scrollState.maxValue)
                scrollState.animateScrollTo(
                    value = desiredScroll,
                    animationSpec = spring(
                        dampingRatio = 0.86f,
                        stiffness = 380f
                    )
                )
            }
        }

        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .height(62.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(31.dp))
                    .background(Color(0xFF141519).copy(alpha = 0.96f))
                    .padding(start = 10.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Left Circular Machined Red-Dot Beacon Button
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1C1E24))
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                Feedback.tick(context)
                                onHome()
                            },
                            onLongClick = {
                                AppPrefs.setDefault(context, AppPrefs.KEY_DEFAULT_STARTUP_ROUTE, "home", "Home set as default startup screen")
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Outer subtle ring
                        drawCircle(
                            color = Color(0xFF32353E),
                            radius = size.minDimension / 2f - 1.dp.toPx(),
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                        // Soft red ambient bloom
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0x66E5533D), Color.Transparent),
                                center = center,
                                radius = 12.dp.toPx()
                            ),
                            radius = 12.dp.toPx(),
                            center = center
                        )
                        // Core glowing red dot
                        drawCircle(
                            color = Color(0xFFE5533D),
                            radius = 5.5.dp.toPx(),
                            center = center
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // 2. Scrollable Tab Strip with 20.dp Edge Dissolve Masks & Generous Spacing
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onGloballyPositioned { coords ->
                            viewportWidthPx = coords.size.width
                        }
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val fadePx = 20.dp.toPx()
                            val w = size.width
                            // Left edge fade mask (only when scrolled away from the far-left start)
                            if (scrollState.value > 4) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, Color.Black),
                                        startX = 0f,
                                        endX = fadePx
                                    ),
                                    topLeft = Offset.Zero,
                                    size = Size(fadePx, size.height),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                            // Right edge fade mask (only when more items exist to the right)
                            if (scrollState.value < scrollState.maxValue - 4) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color.Black, Color.Transparent),
                                        startX = w - fadePx,
                                        endX = w
                                    ),
                                    topLeft = Offset(w - fadePx, 0f),
                                    size = Size(fadePx, size.height),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .horizontalScroll(scrollState)
                            // Generous horizontal padding at start/end + 22.dp spacing between items
                            // so labels NEVER look squished or cramped together!
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Family.entries.forEachIndexed { index, family ->
                            val isSelected = current?.family == family

                            Column(
                                modifier = Modifier
                                    .onGloballyPositioned { coords ->
                                        itemOffsetsPx[index] = coords.positionInParent().x.roundToInt()
                                        itemWidthsPx[index] = coords.size.width
                                    }
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            Feedback.tick(context)
                                            val tools = family.tools
                                            when {
                                                tools.size == 1 -> onTool(tools.first())
                                                current?.family != family -> {
                                                    val defaultToolId = if (family == Family.TIME) {
                                                        AppPrefs.getDefault(context, AppPrefs.KEY_DEFAULT_TIME_INSTRUMENT, Tool.HOURGLASS.id)
                                                    } else {
                                                        tools.first().id
                                                    }
                                                    val target = Tool.byId(defaultToolId) ?: tools.first()
                                                    onTool(target)
                                                }
                                                openFamily == family -> onOpenFamily(null)
                                                else -> onOpenFamily(family)
                                            }
                                            // Immediately animate scroll to center the clicked item
                                            if (viewportWidthPx > 0) {
                                                val center = itemOffsetsPx[index] + itemWidthsPx[index] / 2f
                                                val target = (center - viewportWidthPx / 2f)
                                                    .roundToInt()
                                                    .coerceIn(0, scrollState.maxValue)
                                                coroutineScope.launch {
                                                    scrollState.animateScrollTo(
                                                        value = target,
                                                        animationSpec = spring(
                                                            dampingRatio = 0.86f,
                                                            stiffness = 380f
                                                        )
                                                    )
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            val route = if (family.tools.size == 1) family.tools.first().id else family.routeId
                                            val human = family.label.lowercase().replaceFirstChar { it.uppercase() }
                                            AppPrefs.setDefault(context, AppPrefs.KEY_DEFAULT_STARTUP_ROUTE, route, "$human set as default startup screen")
                                        }
                                    )
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Glowing Red Dot Indicator next to the active/clicked tab
                                    if (isSelected) {
                                        Canvas(modifier = Modifier.size(6.dp)) {
                                            drawCircle(
                                                color = Color(0xFFE5533D),
                                                radius = 3.dp.toPx()
                                            )
                                        }
                                    }

                                    BasicText(
                                        text = family.label.uppercase(),
                                        maxLines = 1,
                                        softWrap = false,
                                        style = TextStyle(
                                            fontFamily = Fonts.mono,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            letterSpacing = 0.14.sp,
                                            color = if (isSelected) Color(0xFFF4F1EA) else Color(0xFF8A8D98)
                                        )
                                    )
                                }

                                Spacer(Modifier.height(5.dp))

                                // Brass/Amber underline bar under the active tab
                                Box(
                                    modifier = Modifier
                                        .width(if (isSelected) 22.dp else 0.dp)
                                        .height(2.5.dp)
                                        .clip(RoundedCornerShape(1.25.dp))
                                        .background(
                                            if (isSelected) Color(0xFFE8B25C) else Color.Transparent
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HomeDot(active: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, spring(stiffness = 900f), label = "homeDot")
    Box(
        Modifier.size(40.dp).scale(scale).clip(CircleShape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(28.dp)) {
            drawCircle(Color.White.copy(alpha = if (active) 0.55f else 0.22f), radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.2.dp.toPx()))
            drawCircle(Ink.Red, radius = 4.dp.toPx(), center = Offset(size.width / 2, size.height / 2))
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FamilyLabel(
    text: String,
    active: Boolean,
    open: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val color by animateColorAsState(if (active || open) Color.White else Ink.TEXT_MUTED_V4, animationSpec = tween(180), label = "family")
    val scale by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = 0.8f, stiffness = 900f),
        label = "familyScale"
    )
    val indicatorAlpha by animateFloatAsState(
        if (active) 1f else 0f,
        spring(dampingRatio = 0.9f, stiffness = 450f),
        label = "indicatorAlpha"
    )
    val indicatorScale by animateFloatAsState(
        if (active) 1f else 0.4f,
        spring(dampingRatio = 0.9f, stiffness = 450f),
        label = "indicatorScale"
    )

    Column(
        modifier
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .background(if (pressed || open) Color(0x18FFFFFF) else Color.Transparent)
            .padding(horizontal = 5.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = text,
            style = labelStyle(color, 10.sp, FontWeight.SemiBold),
            maxLines = 1,
            softWrap = false
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(18.dp)
                .height(2.5.dp)
                .scale(scaleX = indicatorScale, scaleY = 1f)
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color(0xCCF5B851).copy(alpha = indicatorAlpha))
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DrawerItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(stiffness = 900f), label = "drawerItem")
    val bg by animateColorAsState(if (selected) Ink.Text else Color.Transparent, label = "drawerBg")
    Box(
        Modifier.scale(scale).clip(CircleShape).background(bg)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        BasicText(text, style = labelStyle(if (selected) Color(0xFF0B0B0B) else Color(0xFFC4C4C4), 12.sp, FontWeight.SemiBold))
    }
}
