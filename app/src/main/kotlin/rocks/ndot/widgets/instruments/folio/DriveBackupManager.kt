package rocks.ndot.widgets.instruments.folio

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Handles packaging all local data (folio_store.json, habit_store.json, book covers)
 * into a portable .zip archive and restoring it seamlessly.
 * Works zero-config with Android's Storage Access Framework (Google Drive provider).
 */
object DriveBackupManager {

    fun generateBackupFileName(): String {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
        return "mezura_backup_$stamp.zip"
    }

    suspend fun createBackupZip(context: Context, destinationUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val outputStream = contentResolver.openOutputStream(destinationUri) ?: return@withContext false
            ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
                // 1. folio_store.json
                val folioStore = File(context.filesDir, "folio_store.json")
                if (folioStore.exists()) {
                    addFileToZip(folioStore, "folio_store.json", zipOut)
                }

                // 2. habit_store.json
                val habitStore = File(context.filesDir, "habits.json")
                if (habitStore.exists()) {
                    addFileToZip(habitStore, "habits.json", zipOut)
                }

                // 3. Cached book covers directory
                val coversDir = File(context.filesDir, "folio_covers")
                if (coversDir.exists() && coversDir.isDirectory) {
                    coversDir.listFiles()?.forEach { coverFile ->
                        if (coverFile.isFile && coverFile.name.endsWith(".webp")) {
                            addFileToZip(coverFile, "folio_covers/" + coverFile.name, zipOut)
                        }
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun restoreFromZip(context: Context, sourceUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(sourceUri) ?: return@withContext false
            ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                var entry: ZipEntry? = zipIn.nextEntry
                val coversDir = File(context.filesDir, "folio_covers").apply { mkdirs() }
                val buffer = ByteArray(8192)

                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory) {
                        val targetFile = when {
                            name == "folio_store.json" -> File(context.filesDir, "folio_store.json")
                            name == "habits.json" -> File(context.filesDir, "habits.json")
                            name.startsWith("folio_covers/") -> {
                                val fileName = name.substringAfter("folio_covers/")
                                File(coversDir, fileName)
                            }
                            else -> null
                        }

                        if (targetFile != null) {
                            FileOutputStream(targetFile).use { fileOut ->
                                var bytesRead: Int
                                while (zipIn.read(buffer).also { bytesRead = it } != -1) {
                                    fileOut.write(buffer, 0, bytesRead)
                                }
                            }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun addFileToZip(file: File, entryPath: String, zipOut: ZipOutputStream) {
        FileInputStream(file).use { fis ->
            val zipEntry = ZipEntry(entryPath)
            zipOut.putNextEntry(zipEntry)
            val buffer = ByteArray(8192)
            var len: Int
            while (fis.read(buffer).also { len = it } > 0) {
                zipOut.write(buffer, 0, len)
            }
            zipOut.closeEntry()
        }
    }
}
