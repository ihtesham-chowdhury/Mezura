package rocks.ndot.widgets.instruments.folio

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class BookStatus { READING, COMPLETED, WISHLIST }

enum class BookTrackMode { PAGES, PERCENTAGE }

data class BookItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val author: String,
    val totalPages: Int,
    val currentPage: Int = 0,
    val categories: List<String> = emptyList(),
    val coverLocalPath: String? = null,
    val coverRemoteUrl: String? = null,
    val customCoverPath: String? = null,
    val rating: Int = 0,
    val startedEpochMillis: Long = System.currentTimeMillis(),
    val finishedEpochMillis: Long? = null,
    val status: BookStatus = BookStatus.READING,
    val trackMode: BookTrackMode = BookTrackMode.PAGES,
) {
    val progressFraction: Float
        get() = if (trackMode == BookTrackMode.PERCENTAGE) {
            (currentPage.toFloat() / 100f).coerceIn(0f, 1f)
        } else {
            if (totalPages > 0) (currentPage.toFloat() / totalPages).coerceIn(0f, 1f) else 0f
        }

    fun resolveCoverFile(context: Context): File? {
        val custom = customCoverPath?.let { p ->
            val fileName = if (p.contains(File.separatorChar)) p.substringAfterLast(File.separatorChar) else p
            val file = File(File(context.filesDir, "folio_covers"), fileName)
            if (file.exists()) file else File(p).takeIf { it.exists() }
        }
        if (custom != null) return custom

        val p = coverLocalPath ?: return null
        val fileName = if (p.contains(File.separatorChar)) p.substringAfterLast(File.separatorChar) else p
        val file = File(File(context.filesDir, "folio_covers"), fileName)
        return if (file.exists()) file else File(p).takeIf { it.exists() }
    }
}

data class ReadingSessionLog(
    val id: String = UUID.randomUUID().toString(),
    val bookId: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val startPage: Int,
    val endPage: Int,
    val pagesRead: Int = (endPage - startPage).coerceAtLeast(0),
    val durationMinutes: Int = 0,
    val durationSeconds: Long = durationMinutes * 60L,
    val note: String = "",
    val manuallyLogged: Boolean = false,
)

object BookStore {
    private const val FILE_NAME = "folio_store.json"

    private val _booksFlow = MutableStateFlow<List<BookItem>>(emptyList())
    val booksFlow: StateFlow<List<BookItem>> = _booksFlow.asStateFlow()

    private val _sessionsFlow = MutableStateFlow<List<ReadingSessionLog>>(emptyList())
    val sessionsFlow: StateFlow<List<ReadingSessionLog>> = _sessionsFlow.asStateFlow()

    private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            _booksFlow.value = loadAll(context)
            _sessionsFlow.value = loadSessions(context)
            isInitialized = true
        }
    }

    fun loadAll(context: Context): List<BookItem> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            val defaults = defaultSeedBooks()
            saveAll(context, defaults)
            _booksFlow.value = defaults
            return defaults
        }
        return try {
            val jsonStr = file.readText()
            val obj = JSONObject(jsonStr)
            val arr = obj.optJSONArray("books") ?: JSONArray()
            val list = mutableListOf<BookItem>()
            for (i in 0 until arr.length()) {
                val b = arr.getJSONObject(i)
                val catArr = b.optJSONArray("categories") ?: JSONArray()
                val cats = (0 until catArr.length()).map { catArr.getString(it) }
                val totalP = b.optInt("totalPages", 100).coerceAtLeast(1)
                val currP = b.optInt("currentPage", 0).coerceIn(0, totalP)

                list.add(
                    BookItem(
                        id = b.optString("id", UUID.randomUUID().toString()),
                        title = b.optString("title", ""),
                        author = b.optString("author", ""),
                        totalPages = totalP,
                        currentPage = currP,
                        categories = cats,
                        coverLocalPath = if (b.isNull("coverLocalPath")) null else b.optString("coverLocalPath"),
                        coverRemoteUrl = if (b.isNull("coverRemoteUrl")) null else b.optString("coverRemoteUrl"),
                        customCoverPath = if (b.isNull("customCoverPath")) null else b.optString("customCoverPath"),
                        rating = b.optInt("rating", 0),
                        startedEpochMillis = b.optLong("startedEpochMillis", System.currentTimeMillis()),
                        finishedEpochMillis = if (b.isNull("finishedEpochMillis")) null else b.optLong("finishedEpochMillis"),
                        status = runCatching { BookStatus.valueOf(b.optString("status", BookStatus.READING.name)) }.getOrDefault(BookStatus.READING),
                        trackMode = runCatching { BookTrackMode.valueOf(b.optString("trackMode", BookTrackMode.PAGES.name)) }.getOrDefault(BookTrackMode.PAGES),
                    )
                )
            }
            if (list.isEmpty()) {
                val defaults = defaultSeedBooks()
                saveAll(context, defaults)
                _booksFlow.value = defaults
                defaults
            } else {
                _booksFlow.value = list
                list
            }
        } catch (_: Exception) {
            val defaults = defaultSeedBooks()
            _booksFlow.value = defaults
            defaults
        }
    }

    fun loadLastRead(context: Context): BookItem? {
        val books = loadAll(context)
        return books.firstOrNull { it.status == BookStatus.READING } ?: books.firstOrNull()
    }

    fun saveAll(context: Context, books: List<BookItem>) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            val obj = if (file.exists()) {
                runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())
            } else JSONObject()

            val arr = JSONArray()
            books.forEach { b ->
                val bObj = JSONObject().apply {
                    put("id", b.id)
                    put("title", b.title)
                    put("author", b.author)
                    put("totalPages", b.totalPages)
                    put("currentPage", b.currentPage.coerceIn(0, b.totalPages))
                    put("categories", JSONArray(b.categories))
                    put("coverLocalPath", b.coverLocalPath ?: JSONObject.NULL)
                    put("coverRemoteUrl", b.coverRemoteUrl ?: JSONObject.NULL)
                    put("customCoverPath", b.customCoverPath ?: JSONObject.NULL)
                    put("rating", b.rating)
                    put("startedEpochMillis", b.startedEpochMillis)
                    put("finishedEpochMillis", b.finishedEpochMillis ?: JSONObject.NULL)
                    put("status", b.status.name)
                    put("trackMode", b.trackMode.name)
                }
                arr.put(bObj)
            }
            obj.put("books", arr)
            file.writeText(obj.toString())
            _booksFlow.value = books
        } catch (_: Exception) {
        }
    }

    fun upsertBook(context: Context, book: BookItem) {
        val maxLimit = if (book.trackMode == BookTrackMode.PERCENTAGE) 100 else book.totalPages
        val sanitized = book.copy(currentPage = book.currentPage.coerceIn(0, maxLimit))
        val books = loadAll(context).toMutableList()
        val index = books.indexOfFirst { it.id == sanitized.id }
        if (index >= 0) {
            books[index] = sanitized
        } else {
            books.add(0, sanitized)
        }
        saveAll(context, books)
    }

    fun updatePage(context: Context, bookId: String, newPage: Int) {
        val books = loadAll(context).toMutableList()
        val index = books.indexOfFirst { it.id == bookId }
        if (index >= 0) {
            val b = books[index]
            val maxLimit = if (b.trackMode == BookTrackMode.PERCENTAGE) 100 else b.totalPages
            val clamped = newPage.coerceIn(0, maxLimit)
            val newStatus = if (clamped >= maxLimit && maxLimit > 0) BookStatus.COMPLETED else b.status
            val finished = if (newStatus == BookStatus.COMPLETED && b.finishedEpochMillis == null) System.currentTimeMillis() else b.finishedEpochMillis
            books[index] = b.copy(currentPage = clamped, status = newStatus, finishedEpochMillis = finished)
            saveAll(context, books)
        }
    }

    fun deleteBook(context: Context, bookId: String) {
        val books = loadAll(context).filter { it.id != bookId }
        saveAll(context, books)
    }

    fun loadSessions(context: Context): List<ReadingSessionLog> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return try {
            val obj = JSONObject(file.readText())
            val arr = obj.optJSONArray("sessions") ?: return emptyList()
            val list = mutableListOf<ReadingSessionLog>()
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                val durMin = s.optInt("durationMinutes", 0)
                val durSec = s.optLong("durationSeconds", durMin * 60L)
                list.add(
                    ReadingSessionLog(
                        id = s.optString("id", UUID.randomUUID().toString()),
                        bookId = s.optString("bookId", ""),
                        timestampMillis = s.optLong("timestampMillis", System.currentTimeMillis()),
                        startPage = s.optInt("startPage", 0),
                        endPage = s.optInt("endPage", 0),
                        pagesRead = s.optInt("pagesRead", 0),
                        durationMinutes = durMin,
                        durationSeconds = durSec,
                        note = s.optString("note", ""),
                        manuallyLogged = s.optBoolean("manuallyLogged", false),
                    )
                )
            }
            _sessionsFlow.value = list
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveSessions(context: Context, sessions: List<ReadingSessionLog>) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            val obj = if (file.exists()) runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject()) else JSONObject()
            val arr = JSONArray()
            sessions.forEach { s ->
                arr.put(
                    JSONObject().apply {
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
                )
            }
            obj.put("sessions", arr)
            file.writeText(obj.toString())
            _sessionsFlow.value = sessions
        } catch (_: Exception) {
        }
    }

    fun addSession(context: Context, session: ReadingSessionLog) {
        val sessions = loadSessions(context).toMutableList()
        sessions.add(0, session)
        saveSessions(context, sessions)
        // Update current page of book
        updatePage(context, session.bookId, session.endPage)
    }

    fun updateSession(context: Context, session: ReadingSessionLog) {
        val sessions = loadSessions(context).toMutableList()
        val idx = sessions.indexOfFirst { it.id == session.id }
        if (idx >= 0) {
            sessions[idx] = session
            saveSessions(context, sessions)
        }
    }

    fun deleteSession(context: Context, sessionId: String) {
        val sessions = loadSessions(context).filter { it.id != sessionId }
        saveSessions(context, sessions)
    }

    private fun defaultSeedBooks(): List<BookItem> {
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        return listOf(
            BookItem(
                id = "seed-1",
                title = "ইবনু তাইমিয়া: জীবন ও কর্ম",
                author = "আকরাম হোসাইন ও আবু তালহা",
                totalPages = 320,
                currentPage = 187,
                categories = listOf("Islamic", "History", "Biography"),
                rating = 5,
                startedEpochMillis = now - 21L * dayMs,
                status = BookStatus.READING,
            ),
            BookItem(
                id = "seed-2",
                title = "কসবি",
                author = "হরিশংকর জলদাস",
                totalPages = 280,
                currentPage = 142,
                categories = listOf("Bengali", "Novel"),
                rating = 4,
                startedEpochMillis = now - 35L * dayMs,
                status = BookStatus.READING,
            ),
            BookItem(
                id = "seed-3",
                title = "নিহিলিজম ও ইসলাম",
                author = "মারুফ শাহ",
                totalPages = 196,
                currentPage = 80,
                categories = listOf("Philosophy", "Religion"),
                rating = 4,
                startedEpochMillis = now - 27L * dayMs,
                status = BookStatus.READING,
            ),
            BookItem(
                id = "seed-4",
                title = "সম্রাট",
                author = "সৈয়দ মুজতবা আলী",
                totalPages = 240,
                currentPage = 240,
                categories = listOf("Bengali", "Literature"),
                rating = 4,
                startedEpochMillis = now - 45L * dayMs,
                finishedEpochMillis = now - 5L * dayMs,
                status = BookStatus.COMPLETED,
            ),
            BookItem(
                id = "seed-5",
                title = "Empire: How Britain Made the Modern World",
                author = "Niall Ferguson",
                totalPages = 416,
                currentPage = 98,
                categories = listOf("History", "Politics"),
                rating = 4,
                startedEpochMillis = now - 10L * dayMs,
                status = BookStatus.READING,
            ),
            BookItem(
                id = "seed-6",
                title = "আমারে দেব না ভুলিতে",
                author = "আসিফ এহসান রবি",
                totalPages = 310,
                currentPage = 310,
                categories = listOf("Memoir", "Literature"),
                rating = 5,
                startedEpochMillis = now - 60L * dayMs,
                finishedEpochMillis = now - 15L * dayMs,
                status = BookStatus.COMPLETED,
            )
        )
    }

    private const val ACTIVE_PREFS = "reading_active_session"
    private const val KEY_ACTIVE_BOOK_ID = "active_book_id"
    private const val KEY_ACTIVE_START_PAGE = "active_start_page"
    private const val KEY_ACTIVE_START_TIME = "active_start_time"

    fun setActiveReadingBook(context: Context, book: BookItem) {
        context.getSharedPreferences(ACTIVE_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACTIVE_BOOK_ID, book.id)
            .putInt(KEY_ACTIVE_START_PAGE, book.currentPage)
            .putLong(KEY_ACTIVE_START_TIME, System.currentTimeMillis())
            .apply()
    }

    fun getActiveReadingBook(context: Context): Pair<BookItem, Int>? {
        val sp = context.getSharedPreferences(ACTIVE_PREFS, Context.MODE_PRIVATE)
        val id = sp.getString(KEY_ACTIVE_BOOK_ID, null) ?: return null
        val startPage = sp.getInt(KEY_ACTIVE_START_PAGE, 0)
        val book = loadAll(context).firstOrNull { it.id == id } ?: return null
        return Pair(book, startPage)
    }

    fun getActiveReadingStartTime(context: Context): Long {
        return context.getSharedPreferences(ACTIVE_PREFS, Context.MODE_PRIVATE).getLong(KEY_ACTIVE_START_TIME, System.currentTimeMillis())
    }

    fun clearActiveReadingBook(context: Context) {
        context.getSharedPreferences(ACTIVE_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
