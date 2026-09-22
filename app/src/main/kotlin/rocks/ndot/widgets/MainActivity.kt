package rocks.ndot.widgets

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import rocks.ndot.widgets.app.InstrumentsApp
import rocks.ndot.widgets.common.Foreground
import rocks.ndot.widgets.kit.Feedback

/**
 * The instruments app: one activity, drawn edge to edge, with every instrument inside it.
 * Widgets, notifications and the widget settings screen open a specific instrument through
 * [openIntent].
 */
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN = "rocks.ndot.widgets.extra.OPEN"

        /** An intent that brings the app forward on [tool] (a Tool id, e.g. "pomodoro"). */
        fun openIntent(context: Context, tool: String): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN, tool)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    private val requested = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) requested.value = intent?.getStringExtra(EXTRA_OPEN)
        Feedback.prewarm(this)
        setContent { InstrumentsApp(requested) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_OPEN)?.let { requested.value = it }
    }

    override fun onStart() {
        super.onStart()
        Foreground.appVisible = true
    }

    override fun onStop() {
        Foreground.appVisible = false
        super.onStop()
    }
}
