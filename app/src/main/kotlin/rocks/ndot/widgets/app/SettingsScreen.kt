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
import androidx.compose.ui.graphics.Color
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

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import rocks.ndot.widgets.instruments.folio.DriveBackupManager
import rocks.ndot.widgets.kit.ChipColors
import rocks.ndot.widgets.kit.ChipRow
import rocks.ndot.widgets.kit.ColorTheme

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sound by remember { mutableStateOf(AppPrefs.sound(context)) }
    var haptics by remember { mutableStateOf(AppPrefs.haptics(context)) }
    var awake by remember { mutableStateOf(AppPrefs.keepAwake(context)) }
    var currentTheme by remember {
        mutableStateOf(
            ColorTheme.byId(AppPrefs.getDefault(context, AppPrefs.KEY_DEFAULT_COLOR_THEME, ColorTheme.CLASSIC.id))
        )
    }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    LightChrome(false)

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = DriveBackupManager.createBackupZip(context, uri)
                Toast.makeText(
                    context,
                    if (ok) "Backup bundle saved to Drive / Storage" else "Failed to create backup",
                    Toast.LENGTH_SHORT
                ).show()
                if (ok) Feedback.confirmDefault(context)
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = DriveBackupManager.restoreFromZip(context, uri)
                Toast.makeText(
                    context,
                    if (ok) "Archive restored successfully" else "Failed to restore archive",
                    Toast.LENGTH_SHORT
                ).show()
                if (ok) Feedback.confirmDefault(context)
            }
        }
    }

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
                Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                    BasicText("Color Theme", style = TextStyle(fontFamily = Fonts.sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Ink.Text))
                    BasicText("Tap to pick, press & hold to lock as persistent default", style = TextStyle(fontFamily = Fonts.sans, fontSize = 13.sp, color = Ink.Muted))
                    Spacer(Modifier.height(10.dp))
                    ChipRow(
                        items = ColorTheme.entries.map { it.label },
                        selected = currentTheme.ordinal,
                        colors = ChipColors(Color(0x14FFFFFF), Color(0xFFC8C8C8), currentTheme.accent, Color.White),
                        onSelect = {
                            currentTheme = ColorTheme.entries[it]
                            Feedback.press(context, Feedback.Sound.DETENT, Feedback.Haptic.TICK)
                        },
                        onLongPress = {
                            val picked = ColorTheme.entries[it]
                            currentTheme = picked
                            AppPrefs.setDefault(context, AppPrefs.KEY_DEFAULT_COLOR_THEME, picked.id, "${picked.label} theme")
                        },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Group {
                LinkRow("Backup to Google Drive / Files", "Export Folio library, cached covers, habits & settings") {
                    backupLauncher.launch(DriveBackupManager.generateBackupFileName())
                }
                LinkRow("Restore from Backup Archive", "Import a previous mezura_backup_*.zip or ndot_backup_*.zip archive") {
                    restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                }
            }
            Spacer(Modifier.height(14.dp))
            Group {
                LinkRow("Widget settings", "Colour, transparency, focus lengths, permissions") {
                    context.startActivity(Intent(context, WidgetSettingsActivity::class.java))
                }
            }
            Spacer(Modifier.height(22.dp))
            BasicText(
                "MEZURA $version · Everything stays on this phone. No account, no network, no tracking.",
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
