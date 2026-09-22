package rocks.ndot.widgets.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** How much room the floating drawer bar takes at the bottom of every screen. */
val NavClearance: Dp = 100.dp

/** Reserves the floating bar's space (plus the gesture area) at the foot of a screen. */
@Composable
fun NavSpacer() {
    Spacer(Modifier.navigationBarsPadding().height(NavClearance))
}

data class ChipColors(
    val idle: Color,
    val idleText: Color,
    val selected: Color,
    val selectedText: Color,
) {
    companion object {
        val Dark = ChipColors(Color(0x14FFFFFF), Color(0xFFC8C8C8), Color(0xFFF2F2F2), Color(0xFF0B0B0B))
        val Light = ChipColors(Color(0x12000000), Color(0xFF3A3A3A), Color(0xFF1C1C1C), Color(0xFFF5F5F5))
    }
}

/** A pill that squashes slightly under the finger - every chip in the app is one of these. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Chip(
    text: String,
    selected: Boolean,
    colors: ChipColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, spring(stiffness = 900f), label = "chipScale")
    val bg by animateColorAsState(if (selected) colors.selected else colors.idle, label = "chipBg")
    val fg by animateColorAsState(if (selected) colors.selectedText else colors.idleText, label = "chipFg")
    Box(
        modifier
            .scale(scale)
            .clip(CircleShape)
            .background(bg)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = labelStyle(fg, 12.sp, FontWeight.SemiBold).copy(letterSpacing = 0.04.sp))
    }
}

/** A centred, horizontally scrollable row of chips. */
@Composable
fun ChipRow(
    items: List<String>,
    selected: Int,
    colors: ChipColors,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: ((Int) -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, label ->
            Chip(
                text = label,
                selected = index == selected,
                colors = colors,
                onClick = { onSelect(index) },
                onLongClick = onLongPress?.let { { it(index) } },
            )
        }
    }
}

/** The line at the top of every instrument: its name, small, and optionally an action. */
@Composable
fun ToolHeader(title: String, color: Color, trailing: @Composable (RowScope.() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 16.dp, top = 14.dp, bottom = 10.dp).height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(title, style = labelStyle(color, 11.sp, FontWeight.SemiBold))
        Spacer(Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

/** A small text button in the chrome style, e.g. "RESET" or "SOUND: WOOD". */
@Composable
fun TextAction(text: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = if (pressed) 0.16f else 0.08f))
            .combinedClickable(interactionSource = interaction, indication = null, onClick = {
                Feedback.press(context, Feedback.Sound.DETENT, Feedback.Haptic.TICK)
                onClick()
            })
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        BasicText(text, style = labelStyle(color, 11.sp, FontWeight.SemiBold))
    }
}

/** An on/off switch drawn as a pill with a sliding dot - no Material in sight. */
@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val offset by animateDpAsState(if (checked) 22.dp else 2.dp, spring(stiffness = 700f), label = "toggle")
    val track by animateColorAsState(if (checked) Ink.Text else Ink.Raised, label = "track")
    val knob by animateColorAsState(if (checked) Ink.Red else Ink.Muted, label = "knob")
    Box(
        Modifier
            .size(48.dp, 28.dp)
            .clip(CircleShape)
            .background(track)
            .border(1.dp, Ink.Hairline, CircleShape)
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                Feedback.press(context, Feedback.Sound.DETENT, Feedback.Haptic.TICK)
                onChange(!checked)
            },
    ) {
        Box(Modifier.offset(x = offset, y = 2.dp).size(24.dp).clip(CircleShape).background(knob))
    }
}

/** The dark sheet every dialog in the app sits on. */
@Composable
fun SheetDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Ink.Surface)
                .border(1.dp, Ink.Hairline, RoundedCornerShape(28.dp))
                .padding(22.dp),
        ) { content() }
    }
}

/** A round -/+ key that repeats while held. */
@Composable
fun StepKey(symbol: String, onStep: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp, dark: Boolean = true) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(stiffness = 900f), label = "step")
    var last by remember { mutableLongStateOf(0L) }
    Box(
        modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(if (dark) Ink.Raised else Color(0x12000000))
            .combinedClickable(
                interactionSource = interaction, indication = null,
                onClick = {
                    Feedback.press(context, Feedback.Sound.DETENT, Feedback.Haptic.TICK)
                    onStep()
                },
                onLongClick = {
                    // A long press takes a bigger stride rather than auto-repeating, which is
                    // easier to control and needs no timer running behind the button.
                    val now = System.currentTimeMillis()
                    if (now - last > 150) {
                        last = now
                        Feedback.press(context, Feedback.Sound.DETENT, Feedback.Haptic.CLICK)
                        repeat(5) { onStep() }
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            symbol,
            style = labelStyle(if (dark) Ink.Text else Color(0xFF222222), 18.sp, FontWeight.Normal).copy(textAlign = TextAlign.Center),
        )
    }
}

/**
 * Minutes and seconds, set with steppers. Used for the hourglass presets; a numeric keyboard
 * would be faster to type but far clumsier to glance at and nudge.
 */
@Composable
fun DurationDialog(
    title: String,
    initialSeconds: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var total by remember { androidx.compose.runtime.mutableLongStateOf(initialSeconds.coerceIn(5, 24 * 3600)) }
    SheetDialog(onDismiss) {
        BasicText(title, style = labelStyle(Ink.Muted, 11.sp, FontWeight.SemiBold))
        Spacer(Modifier.height(18.dp))
        val h = total / 3600
        val m = (total / 60) % 60
        val s = total % 60
        val shown = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
        BasicText(
            shown,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = Fonts.condensed, fontSize = 64.sp, fontWeight = FontWeight.Bold,
                color = Ink.Text, textAlign = TextAlign.Center,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        StepperRow("MINUTES", onMinus = { total = (total - 60).coerceAtLeast(5) }, onPlus = { total = (total + 60).coerceAtMost(24 * 3600) })
        Spacer(Modifier.height(10.dp))
        StepperRow("SECONDS", onMinus = { total = (total - 5).coerceAtLeast(5) }, onPlus = { total = (total + 5).coerceAtMost(24 * 3600) })
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onDelete != null) {
                TextAction("REMOVE", Ink.Red, onDelete)
            }
            Spacer(Modifier.weight(1f))
            TextAction("CANCEL", Ink.Muted, onDismiss)
            TextAction("SAVE", Ink.Text, { onConfirm(total) })
        }
    }
}

@Composable
private fun StepperRow(label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = labelStyle(Ink.Muted, 11.sp), modifier = Modifier.weight(1f))
        StepKey("−", onMinus)
        Spacer(Modifier.width(10.dp))
        StepKey("+", onPlus)
    }
}
