package rocks.ndot.widgets.instruments.folio

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object BookStatsBitmapExporter {

    private const val EXPORT_WIDTH_PX = 1080
    private const val EXPORT_HEIGHT_PX = 1440

    /**
     * Renders the selected Mezura design language (1..9) into a crisp 1080x1440 PNG
     * and opens the native Android Share Sheet.
     */
    suspend fun renderAndShare(
        context: Context,
        stats: BookCompletionStats,
        style: BookStatsDesignStyle
    ) {
        val shareFile = withContext(Dispatchers.Default) {
            val bitmap = renderToBitmap(context, stats, style)
            val sharedDir = File(context.cacheDir, "shared_reading_records").apply { mkdirs() }
            val safeTitle = stats.bookTitle.replace(Regex("[^\\p{L}\\p{N}_-]"), "_").take(24)
            val outFile = File(
                sharedDir,
                "Mezura_Reading_Record_${style.serialNumber}_${safeTitle}.png"
            )
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
            }
            outFile
        }

        withContext(Dispatchers.Main) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                shareFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Completed \"${stats.bookTitle}\" (${stats.pagesRead} pages) — Recorded on Mezura."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Mezura Reading Record").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    fun renderToBitmap(
        context: Context,
        stats: BookCompletionStats,
        style: BookStatsDesignStyle,
        widthPx: Int = EXPORT_WIDTH_PX,
        heightPx: Int = EXPORT_HEIGHT_PX
    ): Bitmap {
        val master = MezuraMasterPlateEngine.renderMasterPlate(context, stats, style, widthPx, heightPx)
        if (master != null && master.width > 0 && master.height > 0) {
            return master
        }
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val androidCanvas = android.graphics.Canvas(bitmap)
        val canvasDrawScope = CanvasDrawScope()
        val size = Size(widthPx.toFloat(), heightPx.toFloat())
        canvasDrawScope.draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(androidCanvas),
            size = size
        ) {
            val s = widthPx / 360f
            drawSelectedMezuraStyle(stats, style, s)
        }
        return bitmap
    }
}
