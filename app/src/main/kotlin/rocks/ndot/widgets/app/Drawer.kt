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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
enum class Family(val label: String) {
    TIME("TIME"),
    RHYTHM("RHYTHM"),
    CALENDAR("CALENDAR"),
    HABITS("HABITS");

    val tools: List<Tool> get() = Tool.entries.filter { it.family == this }
}

enum class Tool(val id: String, val label: String, val family: Family) {
    STOPWATCH("stopwatch", "STOPWATCH", Family.TIME),
    POMODORO("pomodoro", "POMODORO", Family.TIME),
    HOURGLASS("hourglass", "HOURGLASS", Family.TIME),
    METRONOME("metronome", "METRONOME", Family.RHYTHM),
    CALENDAR("calendar", "CALENDAR", Family.CALENDAR),
    HABITS("habits", "HABITS", Family.HABITS);

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
                    DrawerItem(tool.label, selected = tool == current) {
                        Feedback.press(context, Feedback.Sound.KEY_UP, Feedback.Haptic.CLICK)
                        onTool(tool)
                    }
                }
            }
        }

        Row(
            Modifier
                .widthIn(max = maxWidth)
                .height(58.dp)
                .shadow(22.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(CircleShape)
                .background(Color(0xF2121212))
                .border(1.dp, Ink.Hairline, CircleShape)
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeDot(active = current == null) {
                Feedback.press(context, Feedback.Sound.KEY_UP, Feedback.Haptic.TICK)
                onHome()
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(start = 4.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Family.entries.forEach { family ->
                    FamilyLabel(
                        family.label,
                        active = current?.family == family,
                        open = openFamily == family,
                    ) {
                        Feedback.press(context, Feedback.Sound.DETENT, Feedback.Haptic.TICK)
                        val tools = family.tools
                        when {
                            tools.size == 1 -> onTool(tools.first())
                            openFamily == family -> onOpenFamily(null)
                            else -> onOpenFamily(family)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeDot(active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, spring(stiffness = 900f), label = "homeDot")
    Box(
        Modifier.size(40.dp).scale(scale).clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(28.dp)) {
            drawCircle(Color.White.copy(alpha = if (active) 0.55f else 0.22f), radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.2.dp.toPx()))
            drawCircle(Ink.Red, radius = 4.dp.toPx(), center = Offset(size.width / 2, size.height / 2))
        }
    }
}

@Composable
private fun FamilyLabel(text: String, active: Boolean, open: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val color by animateColorAsState(if (active || open) Ink.Text else Color(0xFF7E7E7E), label = "family")
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .background(if (pressed || open) Color(0x14FFFFFF) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(text, style = labelStyle(color, 12.5.sp, FontWeight.SemiBold))
        Spacer(Modifier.height(4.dp))
        Box(Modifier.size(4.dp).clip(CircleShape).background(if (active) Ink.Red else Color.Transparent))
    }
}

@Composable
private fun DrawerItem(text: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(stiffness = 900f), label = "drawerItem")
    val bg by animateColorAsState(if (selected) Ink.Text else Color.Transparent, label = "drawerBg")
    Box(
        Modifier.scale(scale).clip(CircleShape).background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        BasicText(text, style = labelStyle(if (selected) Color(0xFF0B0B0B) else Color(0xFFC4C4C4), 12.sp, FontWeight.SemiBold))
    }
}
