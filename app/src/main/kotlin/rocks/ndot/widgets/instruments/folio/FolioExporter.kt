package rocks.ndot.widgets.instruments.folio

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object FolioExporter {

    suspend fun exportToSpreadsheetXml(context: Context, destinationUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val books = BookStore.loadAll(context)
            val sessions = BookStore.loadSessions(context)

            val outputStream = context.contentResolver.openOutputStream(destinationUri) ?: return@withContext false
            val writer = BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8))

            val xml = buildString {
                append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
                append("""<?mso-application progid="Excel.Sheet"?>""").append("\n")
                append("""<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" """)
                append("""xmlns:o="urn:schemas-microsoft-com:office:office" """)
                append("""xmlns:x="urn:schemas-microsoft-com:office:excel" """)
                append("""xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet" """)
                append("""xmlns:html="http://www.w3.org/TR/REC-html40">""").append("\n")

                // Styles
                append("""<Styles>""").append("\n")
                append("""  <Style ss:ID="Header">""").append("\n")
                append("""    <Font ss:Bold="1" ss:Color="#FFFFFF" />""").append("\n")
                append("""    <Interior ss:Color="#1A1C24" ss:Pattern="Solid" />""").append("\n")
                append("""  </Style>""").append("\n")
                append("""  <Style ss:ID="Cell">""").append("\n")
                append("""    <Font ss:Color="#111111" />""").append("\n")
                append("""  </Style>""").append("\n")
                append("""</Styles>""").append("\n")

                // Sheet 1: Library Archive
                append("""<Worksheet ss:Name="Library Archive">""").append("\n")
                append("""  <Table>""").append("\n")
                append(makeRow("Header", listOf("Book ID", "Title", "Author", "Categories", "Status", "Current Page", "Total Pages", "Progress %", "Star Rating", "Start Date", "Finish Date")))

                val zone = ZoneId.systemDefault()
                val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())

                books.forEach { b ->
                    val startDate = Instant.ofEpochMilli(b.startedEpochMillis).atZone(zone).toLocalDate().format(dateFmt)
                    val finishDate = b.finishedEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().format(dateFmt) } ?: "-"
                    val progressPct = "${(b.progressFraction * 100).toInt()}%"
                    val ratingStr = if (b.rating > 0) "${b.rating} / 5" else "-"
                    val cats = b.categories.joinToString(", ")

                    append(makeRow("Cell", listOf(
                        b.id, b.title, b.author, cats, b.status.name,
                        b.currentPage.toString(), b.totalPages.toString(), progressPct,
                        ratingStr, startDate, finishDate
                    )))
                }
                append("""  </Table>""").append("\n")
                append("""</Worksheet>""").append("\n")

                // Sheet 2: Reading Sessions
                append("""<Worksheet ss:Name="Reading Sessions">""").append("\n")
                append("""  <Table>""").append("\n")
                append(makeRow("Header", listOf("Session Date", "Book Title", "Start Page", "End Page", "Pages Read", "Duration (Mins)", "Speed (Pages/Hr)", "Notes")))

                val bookMap = books.associateBy { it.id }
                sessions.forEach { s ->
                    val sDate = Instant.ofEpochMilli(s.timestampMillis).atZone(zone).toLocalDate().format(dateFmt)
                    val bookTitle = bookMap[s.bookId]?.title ?: "Unknown Book"
                    val speed = if (s.durationMinutes > 0) "%.1f".format((s.pagesRead.toFloat() / s.durationMinutes.toFloat()) * 60f) else "-"

                    append(makeRow("Cell", listOf(
                        sDate, bookTitle, s.startPage.toString(), s.endPage.toString(),
                        s.pagesRead.toString(), s.durationMinutes.toString(), speed, s.note
                    )))
                }
                append("""  </Table>""").append("\n")
                append("""</Worksheet>""").append("\n")

                // Sheet 3: Monthly Summary
                append("""<Worksheet ss:Name="Monthly Summary">""").append("\n")
                append("""  <Table>""").append("\n")
                append(makeRow("Header", listOf("Month", "Pages Logged", "Books Completed")))
                val currentYear = LocalDate.now().year
                for (m in 1..12) {
                    val mStr = String.format("%04d-%02d", currentYear, m)
                    append(makeRow("Cell", listOf(mStr, "0", "0")))
                }
                append("""  </Table>""").append("\n")
                append("""</Worksheet>""").append("\n")

                append("""</Workbook>""")
            }

            writer.write(xml)
            writer.flush()
            writer.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun makeRow(styleId: String, cells: List<String>): String {
        return buildString {
            append("    <Row>\n")
            cells.forEach { c ->
                val escaped = c.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                append("""      <Cell ss:StyleID="$styleId"><Data ss:Type="String">$escaped</Data></Cell>""").append("\n")
            }
            append("    </Row>\n")
        }
    }

    fun generateCsv(context: Context): String {
        val books = BookStore.loadAll(context)
        val sessions = BookStore.loadSessions(context)
        val sb = StringBuilder()
        sb.append("# FOLIO LIBRARY ARCHIVE\n")
        sb.append("ID,Title,Author,Categories,Status,CurrentPage,TotalPages,Progress,Rating\n")
        books.forEach { b ->
            val titleEsc = "\"${b.title.replace("\"", "\"\"")}\""
            val authEsc = "\"${b.author.replace("\"", "\"\"")}\""
            val catsEsc = "\"${b.categories.joinToString(";").replace("\"", "\"\"")}\""
            val pct = "${(b.progressFraction * 100).toInt()}%"
            sb.append("${b.id},$titleEsc,$authEsc,$catsEsc,${b.status.name},${b.currentPage},${b.totalPages},$pct,${b.rating}\n")
        }
        sb.append("\n# READING SESSIONS\n")
        sb.append("ID,BookID,Timestamp,StartPage,EndPage,PagesRead,DurationMinutes,DurationSeconds,ManuallyLogged,Note\n")
        sessions.forEach { s ->
            val noteEsc = "\"${s.note.replace("\"", "\"\"")}\""
            sb.append("${s.id},${s.bookId},${s.timestampMillis},${s.startPage},${s.endPage},${s.pagesRead},${s.durationMinutes},${s.durationSeconds},${s.manuallyLogged},$noteEsc\n")
        }
        return sb.toString()
    }

    fun generateJson(context: Context): String {
        val books = BookStore.loadAll(context)
        val sessions = BookStore.loadSessions(context)
        val root = org.json.JSONObject()
        val booksArr = org.json.JSONArray()
        books.forEach { b ->
            val obj = org.json.JSONObject().apply {
                put("id", b.id)
                put("title", b.title)
                put("author", b.author)
                put("totalPages", b.totalPages)
                put("currentPage", b.currentPage)
                put("categories", org.json.JSONArray(b.categories))
                put("status", b.status.name)
                put("rating", b.rating)
                put("startedEpochMillis", b.startedEpochMillis)
                put("finishedEpochMillis", b.finishedEpochMillis ?: org.json.JSONObject.NULL)
                put("customCoverPath", b.customCoverPath ?: org.json.JSONObject.NULL)
            }
            booksArr.put(obj)
        }
        root.put("books", booksArr)

        val sessionsArr = org.json.JSONArray()
        sessions.forEach { s ->
            val obj = org.json.JSONObject().apply {
                put("id", s.id)
                put("bookId", s.bookId)
                put("timestampMillis", s.timestampMillis)
                put("startPage", s.startPage)
                put("endPage", s.endPage)
                put("pagesRead", s.pagesRead)
                put("durationMinutes", s.durationMinutes)
                put("durationSeconds", s.durationSeconds)
                put("note", s.note)
                put("manuallyLogged", s.manuallyLogged)
            }
            sessionsArr.put(obj)
        }
        root.put("sessions", sessionsArr)
        return root.toString(2)
    }

    fun shareExport(context: Context, content: String, mimeType: String, title: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(android.content.Intent.EXTRA_SUBJECT, title)
            putExtra(android.content.Intent.EXTRA_TEXT, content)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(android.content.Intent.createChooser(intent, title).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
