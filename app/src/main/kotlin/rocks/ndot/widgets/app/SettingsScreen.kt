package rocks.ndot.widgets.app

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.ndot.widgets.WidgetSettingsActivity
import rocks.ndot.widgets.kit.Feedback
import rocks.ndot.widgets.kit.Fonts
import rocks.ndot.widgets.kit.Ink
import rocks.ndot.widgets.kit.NavSpacer
import rocks.ndot.widgets.kit.TextAction
import rocks.ndot.widgets.kit.Toggle
import rocks.ndot.widgets.kit.ToolHeader
import rocks.ndot.widgets.kit.labelStyle

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var sound by remember { mutableStateOf(AppPrefs.sound(context)) }
    var haptics by remember { mutableStateOf(AppPrefs.haptics(context)) }
    var awake by remember { mutableStateOf(AppPrefs.keepAwake(context)) }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    LightChrome(false)

    Column(Modifier.fillMaxSize().background(Ink.Black)) {
        ToolHeader("SETTINGS", Ink.Muted) { TextAction("‹ BACK", Ink.Muted, onBack) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Group {
                SwitchRow("Sound", "Key clicks, detents and chimes", sound) { sound = it; AppPrefs.setSound(context, it) }
                SwitchRow("Haptics", "A tap you can feel with every press", haptics) {
                    haptics = it
                    AppPrefs.setHaptics(context, it)
                    if (it) Feedback.haptic(context, Feedback.Haptic.CLICK)
                }
                SwitchRow("Keep screen on", "While an instrument is running", awake) { awake = it; AppPrefs.setKeepAwake(context, it) }
            }
            Spacer(Modifier.height(14.dp))
            Group {
                LinkRow("Widget settings", "Colour, transparency, focus lengths, permissions") {
                    context.startActivity(Intent(context, WidgetSettingsActivity::class.java))
                }
            }
            Spacer(Modifier.height(22.dp))
            BasicText(
                "NDOT $version · Everything stays on this phone. No account, no network, no tracking.",
                style = labelStyle(Ink.Faint, 10.sp),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        NavSpacer()
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Ink.Surface)
            .border(1.dp, Ink.Hairline, RoundedCornerShape(22.dp)).padding(vertical = 4.dp),
    ) { content() }
}

@Composable
private fun SwitchRow(title: String, caption: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = TextStyle(fontFamily = Fonts.sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Ink.Text))
            BasicText(caption, style = TextStyle(fontFamily = Fonts.sans, fontSize = 13.sp, color = Ink.Muted))
        }
        Toggle(checked, onChange)
    }
}

@Composable
private fun LinkRow(title: String, caption: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = TextStyle(fontFamily = Fonts.sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Ink.Text))
            BasicText(caption, style = TextStyle(fontFamily = Fonts.sans, fontSize = 13.sp, color = Ink.Muted))
        }
        BasicText("›", style = labelStyle(Ink.Muted, 20.sp))
    }
}
