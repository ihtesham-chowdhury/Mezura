package rocks.ndot.widgets.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import rocks.ndot.widgets.instruments.calendar.CalendarScreen
import rocks.ndot.widgets.instruments.habits.HabitsScreen
import rocks.ndot.widgets.instruments.hourglass.HourglassScreen
import rocks.ndot.widgets.instruments.metronome.MetronomeScreen
import rocks.ndot.widgets.instruments.pomodoro.PomodoroScreen
import rocks.ndot.widgets.instruments.stopwatch.StopwatchScreen
import rocks.ndot.widgets.kit.Ink

/**
 * Whether the screen underneath the system bars is light, so their icons can be drawn dark.
 * Each instrument declares it for the design it's currently wearing - the Prism hourglass, the
 * Pocket timer and the Paper calendar are light; everything else is dark.
 */
val LocalLightChrome = compositionLocalOf<MutableState<Boolean>> { mutableStateOf(false) }

/**
 * Call from an instrument to say whether its current design has a light background. Keyed on
 * the value, so it applies when a screen appears or changes design - and a screen that is only
 * animating out can't overwrite the one coming in.
 */
@Composable
fun LightChrome(light: Boolean) {
    val chrome = LocalLightChrome.current
    DisposableEffect(light) {
        chrome.value = light
        onDispose { }
    }
}

/**
 * Asks for notification permission (Android 13+) the first time a timer is started - the only
 * moment it's obviously needed: a timer that runs out with the app closed has to say so.
 */
val LocalAskNotifications = compositionLocalOf<() -> Unit> { {} }

/** Keeps the screen on while [active] - a running timer shouldn't let the phone doze off. */
@Composable
fun KeepAwake(active: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(active) {
        view.keepScreenOn = active && AppPrefs.keepAwake(context)
        onDispose { view.keepScreenOn = false }
    }
}

private const val HOME = "home"
private const val SETTINGS = "settings"

@Composable
fun InstrumentsApp(requested: MutableState<String?>) {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(HOME) }
    var openFamily by remember { mutableStateOf<Family?>(null) }
    val lightChrome = remember { mutableStateOf(false) }

    // A notification, widget or shortcut asked for a particular instrument.
    LaunchedEffect(requested.value) {
        val wanted = requested.value
        if (wanted != null) {
            if (Tool.byId(wanted) != null || wanted == SETTINGS) screen = wanted
            requested.value = null
            openFamily = null
        }
    }

    BackHandler(enabled = screen != HOME || openFamily != null) {
        if (openFamily != null) openFamily = null else screen = HOME
    }

    val tool = Tool.byId(screen)
    val view = LocalView.current
    val isLight = lightChrome.value && (tool != null)
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = isLight
            isAppearanceLightNavigationBars = isLight
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val askNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 && !AppPrefs.askedNotifications(context) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            AppPrefs.setAskedNotifications(context)
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    CompositionLocalProvider(LocalLightChrome provides lightChrome, LocalAskNotifications provides askNotifications) {
        Box(Modifier.fillMaxSize().background(Ink.Black)) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    (fadeIn(tween(240, delayMillis = 60)) + scaleIn(tween(300, delayMillis = 60), initialScale = 0.975f))
                        .togetherWith(fadeOut(tween(120)))
                },
                label = "screen",
            ) { key ->
                when (key) {
                    HOME -> HomeScreen(
                        onOpen = { screen = it.id; AppPrefs.setLastTool(context, it.id) },
                        onSettings = { screen = SETTINGS },
                    )
                    SETTINGS -> SettingsScreen(onBack = { screen = HOME })
                    else -> when (Tool.byId(key)) {
                        Tool.STOPWATCH -> StopwatchScreen()
                        Tool.POMODORO -> PomodoroScreen()
                        Tool.HOURGLASS -> HourglassScreen()
                        Tool.METRONOME -> MetronomeScreen()
                        Tool.CALENDAR -> CalendarScreen()
                        Tool.HABITS -> HabitsScreen()
                        null -> HomeScreen(onOpen = { screen = it.id }, onSettings = { screen = SETTINGS })
                    }
                }
            }

            // Tapping anywhere outside the open drawer closes it.
            if (openFamily != null) {
                Box(
                    Modifier.fillMaxSize().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { openFamily = null }
                )
            }

            DrawerBar(
                current = tool,
                openFamily = openFamily,
                onOpenFamily = { openFamily = it },
                onHome = { screen = HOME; openFamily = null },
                onTool = {
                    screen = it.id
                    openFamily = null
                    AppPrefs.setLastTool(context, it.id)
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
