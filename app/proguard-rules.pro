# Minification is off by default (see build.gradle.kts). If you turn it on, AppWidgetProvider
# and Service subclasses are instantiated by the OS via manifest class names, not by app
# code, so make sure they survive shrinking:
-keep class rocks.ndot.widgets.widgets.** extends android.appwidget.AppWidgetProvider { *; }
-keep class rocks.ndot.widgets.** extends android.app.Service { *; }
-keep class rocks.ndot.widgets.** extends android.content.BroadcastReceiver { *; }
