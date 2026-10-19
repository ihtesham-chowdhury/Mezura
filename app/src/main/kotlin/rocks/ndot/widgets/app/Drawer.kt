package rocks.ndot.widgets.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

        Row(
            Modifier
                .widthIn(max = maxWidth)
                .height(64.dp)
                .shadow(22.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(CircleShape)
                .background(Color(0xF2121212))
                .border(1.dp, Ink.Hairline, CircleShape)
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeDot(
                active = current == null,
                onClick = {
                    Feedback.press(context, Feedback.Sound.KEY_UP, Feedback.Haptic.TICK)
                    onHome()
                },
                onLongClick = {
                    AppPrefs.setDefault(context, AppPrefs.KEY_DEFAULT_STARTUP_ROUTE, "home", "Home set as default startup screen")
                },
            )

            val lazyListState = rememberLazyListState()
            val selectedFamilyIndex = remember(current?.family) {
                val idx = Family.entries.indexOf(current?.family)
                if (idx >= 0) idx else 0
            }

            androidx.compose.runtime.LaunchedEffect(selectedFamilyIndex) {
                if (current != null) {
                    lazyListState.animateScrollToItem(selectedFamilyIndex, scrollOffset = -80)
                }
            }

            LazyRow(
                state = lazyListState,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val fadeW = 20.dp.toPx()
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0.0f to Color.Transparent,
                                (fadeW / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.4f) to Color.Black,
                                (1f - fadeW / size.width.coerceAtLeast(1f)).coerceIn(0.6f, 1f) to Color.Black,
                                1.0f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(Family.entries) { index, family ->
                    FamilyLabel(
                        family.label,
                        active = current?.family == family,
                        open = openFamily == family,
                        onClick = {
                            Feedback.press(context, Feedback.Sound.DETENT, Feedback.Haptic.TICK)
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
                        },
                        onLongClick = {
                            val route = if (family.tools.size == 1) family.tools.first().id else family.routeId
                            val human = family.label.lowercase().replaceFirstChar { it.uppercase() }
                            AppPrefs.setDefault(context, AppPrefs.KEY_DEFAULT_STARTUP_ROUTE, route, "$human set as default startup screen")
                        },
                    )
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
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val color by animateColorAsState(if (active || open) Color.White else Ink.TEXT_MUTED_V4, animationSpec = tween(180), label = "family")
    val scale by animateFloatAsState(
        if (pressed) 0.91f else 1f,
        spring(dampingRatio = 0.65f, stiffness = 600f),
        label = "familyScale"
    )
    val dotAlpha by animateFloatAsState(if (active) 1f else 0f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow), label = "dotAlpha")
    val dotScale by animateFloatAsState(if (active) 1f else 0.3f, spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow), label = "dotScale")

    Column(
        Modifier
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .background(if (pressed || open) Color(0x18FFFFFF) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(text, style = labelStyle(color, 12.5.sp, FontWeight.SemiBold))
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .size(5.dp)
                .scale(dotScale)
                .clip(CircleShape)
                .background(Ink.Red.copy(alpha = dotAlpha))
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
