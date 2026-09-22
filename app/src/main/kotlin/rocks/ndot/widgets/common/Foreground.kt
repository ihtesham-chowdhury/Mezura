package rocks.ndot.widgets.common

/**
 * Whether the instruments app is on screen. When it is, the instrument itself chimes and
 * buzzes as a timer runs out, so [rocks.ndot.widgets.widgets.TimerService] stays quiet
 * instead of stacking a notification sound on top. Same process, so a volatile flag is enough.
 */
object Foreground {
    @Volatile
    var appVisible: Boolean = false
}
