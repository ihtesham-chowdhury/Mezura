package rocks.ndot.widgets.instruments.folio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

enum class CoverSource {
    GOOGLE_BOOKS,
    OPEN_LIBRARY,
    WIKIPEDIA,
    GALLERY
}

data class OnlineCoverCandidate(
    val title: String,
    val author: String,
    val coverUrl: String,
    val source: String, // "Open Library" or "Google Books"
    val resolutionLabel: String = "High Resolution"
)

data class BookSearchResult(
    val title: String,
    val authors: List<String>,
    val pageCount: Int,
    val categories: List<String>,
    val description: String?,
    val coverUrl: String?,
    val source: CoverSource = CoverSource.GOOGLE_BOOKS,
    val confidence: Float = 0.5f,
    val pixelResolution: String = "600 × 900",
)

object BookMetadataFetcher {

    private const val TAG = "FolioCover"
    private val queryCache = LruCache<String, Pair<Long, List<BookSearchResult>>>(50)

    suspend fun searchBooks(query: String, author: String? = null): List<BookSearchResult> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        val trimmedAuthor = author?.trim().orEmpty()
        if (trimmedQuery.isEmpty() && trimmedAuthor.isEmpty()) return@withContext emptyList()

        val cacheKey = "${trimmedQuery.lowercase()}:::${trimmedAuthor.lowercase()}"
        val cached = queryCache.get(cacheKey)
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.first) < 10 * 60 * 1000L) {
            Log.d(TAG, "Cache hit for query: $trimmedQuery")
            return@withContext cached.second
        }

        val results = mutableListOf<BookSearchResult>()

        // 1. Google Books API
        try {
            val qBase = URLEncoder.encode(trimmedQuery, "UTF-8")
            val gUrl = if (trimmedAuthor.isNotBlank()) {
                "https://www.googleapis.com/books/v1/volumes?q=$qBase+${URLEncoder.encode(trimmedAuthor, "UTF-8")}&maxResults=20"
            } else {
                "https://www.googleapis.com/books/v1/volumes?q=$qBase&maxResults=20"
            }

            val jsonStr = httpGet(gUrl)
            if (!jsonStr.isNullOrBlank()) {
                val root = JSONObject(jsonStr)
                val items = root.optJSONArray("items")
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val info = item.optJSONObject("volumeInfo") ?: continue

                        val t = info.optString("title", trimmedQuery)
                        val authorsArr = info.optJSONArray("authors")
                        val authorsList = mutableListOf<String>()
                        if (authorsArr != null) {
                            for (j in 0 until authorsArr.length()) {
                                authorsList.add(authorsArr.getString(j))
                            }
                        }
                        val pCount = info.optInt("pageCount", 0).let { if (it > 0) it else info.optInt("printedPageCount", 0) }
                        val catArr = info.optJSONArray("categories")
                        val cats = mutableListOf<String>()
                        if (catArr != null) {
                            for (j in 0 until catArr.length()) {
                                cats.add(catArr.getString(j))
                            }
                        }
                        val desc = info.optString("description", "")
                        val imageLinks = info.optJSONObject("imageLinks")
                        var thumb = when {
                            imageLinks == null -> null
                            imageLinks.has("extraLarge") -> imageLinks.getString("extraLarge")
                            imageLinks.has("large") -> imageLinks.getString("large")
                            imageLinks.has("medium") -> imageLinks.getString("medium")
                            imageLinks.has("thumbnail") -> imageLinks.getString("thumbnail")
                            imageLinks.has("smallThumbnail") -> imageLinks.getString("smallThumbnail")
                            else -> null
                        }

                        if (thumb != null) {
                            thumb = thumb.replace("http://", "https://").replace("&edge=curl", "")
                        }

                        val score = calculateScore(trimmedQuery, trimmedAuthor, t, authorsList, thumb != null, pCount > 0)
                        results.add(
                            BookSearchResult(
                                title = t,
                                authors = authorsList,
                                pageCount = pCount,
                                categories = if (cats.isNotEmpty()) cats else listOf("General"),
                                description = if (desc.isNotBlank()) desc else null,
                                coverUrl = thumb,
                                source = CoverSource.GOOGLE_BOOKS,
                                confidence = score,
                                pixelResolution = if (thumb != null) "600 × 900" else "Unknown"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google Books error: ${e.message}")
        }

        // 2. Open Library Search API
        try {
            val qEncoded = URLEncoder.encode(trimmedQuery.ifEmpty { trimmedAuthor }, "UTF-8")
            val openLibUrl = "https://openlibrary.org/search.json?q=$qEncoded&limit=20"

            val jsonStr = httpGet(openLibUrl)
            if (!jsonStr.isNullOrBlank()) {
                val root = JSONObject(jsonStr)
                val docs = root.optJSONArray("docs")
                if (docs != null) {
                    for (i in 0 until docs.length()) {
                        val doc = docs.getJSONObject(i)
                        val t = doc.optString("title", trimmedQuery)
                        val authorArr = doc.optJSONArray("author_name")
                        val aList = mutableListOf<String>()
                        if (authorArr != null) {
                            for (j in 0 until authorArr.length()) {
                                aList.add(authorArr.getString(j))
                            }
                        }
                        val pages = doc.optInt("number_of_pages_median", 0).let { if (it > 0) it else doc.optInt("number_of_pages", 0) }
                        val coverId = doc.optLong("cover_i", 0L)
                        val coverUrl = if (coverId > 0) "https://covers.openlibrary.org/b/id/$coverId-M.jpg" else null

                        val score = calculateScore(trimmedQuery, trimmedAuthor, t, aList, coverUrl != null, pages > 0)
                        results.add(
                            BookSearchResult(
                                title = t,
                                authors = aList,
                                pageCount = pages,
                                categories = listOf("Literature"),
                                description = null,
                                coverUrl = coverUrl,
                                source = CoverSource.OPEN_LIBRARY,
                                confidence = score,
                                pixelResolution = if (coverUrl != null) "700 × 1050" else "Unknown"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Open Library error: ${e.message}")
        }

        // Sort: candidates with covers first, then by confidence score
        val sorted = results.distinctBy { (it.coverUrl ?: it.title) }.sortedWith(
            compareByDescending<BookSearchResult> { it.coverUrl != null }
                .thenByDescending { it.confidence }
                .thenByDescending { it.pageCount > 0 }
        )

        Log.d(TAG, "Found ${sorted.size} candidates for '$trimmedQuery'. Top score: ${sorted.firstOrNull()?.confidence}")
        queryCache.put(cacheKey, Pair(now, sorted))
        sorted
    }

    suspend fun searchOnlineCovers(query: String, author: String? = null): List<OnlineCoverCandidate> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        val trimmedAuthor = author?.trim().orEmpty()
        if (trimmedQuery.isEmpty() && trimmedAuthor.isEmpty()) return@withContext emptyList()

        val results = mutableListOf<OnlineCoverCandidate>()
        val seenUrls = mutableSetOf<String>()

        // 1. Check if query is an ISBN (10 or 13 digits)
        val cleanIsbn = trimmedQuery.replace(Regex("[^0-9X]"), "")
        if (cleanIsbn.length == 10 || cleanIsbn.length == 13) {
            val isbnUrl = "https://covers.openlibrary.org/b/isbn/$cleanIsbn-L.jpg"
            results.add(
                OnlineCoverCandidate(
                    title = "ISBN: $cleanIsbn",
                    author = "",
                    coverUrl = isbnUrl,
                    source = "Open Library",
                    resolutionLabel = "Direct ISBN"
                )
            )
            seenUrls.add(isbnUrl)
        }

        // 2. Open Library Search API
        try {
            val qEncoded = URLEncoder.encode(
                if (trimmedAuthor.isNotBlank()) "$trimmedQuery $trimmedAuthor" else trimmedQuery,
                "UTF-8"
            )
            val openLibUrl = "https://openlibrary.org/search.json?q=$qEncoded&limit=25"
            val jsonStr = httpGet(openLibUrl)
            if (!jsonStr.isNullOrBlank()) {
                val root = JSONObject(jsonStr)
                val docs = root.optJSONArray("docs")
                if (docs != null) {
                    for (i in 0 until docs.length()) {
                        val doc = docs.getJSONObject(i)
                        val t = doc.optString("title", trimmedQuery)
                        val authorArr = doc.optJSONArray("author_name")
                        val a = if (authorArr != null && authorArr.length() > 0) authorArr.optString(0, "") else ""
                        val coverId = doc.optLong("cover_i", 0L)
                        val isbnArr = doc.optJSONArray("isbn")
                        val firstIsbn = if (isbnArr != null && isbnArr.length() > 0) isbnArr.optString(0, "") else ""

                        val coverUrl = when {
                            coverId > 0L -> "https://covers.openlibrary.org/b/id/$coverId-L.jpg"
                            firstIsbn.isNotBlank() -> "https://covers.openlibrary.org/b/isbn/$firstIsbn-L.jpg"
                            else -> null
                        }

                        if (coverUrl != null && seenUrls.add(coverUrl)) {
                            results.add(
                                OnlineCoverCandidate(
                                    title = t,
                                    author = a,
                                    coverUrl = coverUrl,
                                    source = "Open Library",
                                    resolutionLabel = "L-Size"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Open Library cover search error: ${e.message}")
        }

        // 3. Google Books Search API
        try {
            val qBase = URLEncoder.encode(trimmedQuery, "UTF-8")
            val gUrl = if (trimmedAuthor.isNotBlank()) {
                "https://www.googleapis.com/books/v1/volumes?q=$qBase+${URLEncoder.encode(trimmedAuthor, "UTF-8")}&maxResults=25"
            } else {
                "https://www.googleapis.com/books/v1/volumes?q=$qBase&maxResults=25"
            }
            val jsonStr = httpGet(gUrl)
            if (!jsonStr.isNullOrBlank()) {
                val root = JSONObject(jsonStr)
                val items = root.optJSONArray("items")
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val info = item.optJSONObject("volumeInfo") ?: continue
                        val t = info.optString("title", trimmedQuery)
                        val authorsArr = info.optJSONArray("authors")
                        val a = if (authorsArr != null && authorsArr.length() > 0) authorsArr.optString(0, "") else ""
                        val imageLinks = info.optJSONObject("imageLinks")
                        var thumb = when {
                            imageLinks == null -> null
                            imageLinks.has("extraLarge") -> imageLinks.getString("extraLarge")
                            imageLinks.has("large") -> imageLinks.getString("large")
                            imageLinks.has("medium") -> imageLinks.getString("medium")
                            imageLinks.has("thumbnail") -> imageLinks.getString("thumbnail")
                            imageLinks.has("smallThumbnail") -> imageLinks.getString("smallThumbnail")
                            else -> null
                        }
                        if (thumb != null) {
                            thumb = thumb.replace("http://", "https://")
                                .replace("&edge=curl", "")
                                .replace("zoom=1", "zoom=2")
                            if (seenUrls.add(thumb)) {
                                results.add(
                                    OnlineCoverCandidate(
                                        title = t,
                                        author = a,
                                        coverUrl = thumb,
                                        source = "Google Books",
                                        resolutionLabel = "Hi-Res"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google Books cover search error: ${e.message}")
        }

        if (results.isEmpty() && trimmedAuthor.isNotEmpty()) {
            return@withContext searchOnlineCovers(trimmedQuery, null)
        }
        if (results.isEmpty()) {
            val latinOnly = trimmedQuery.replace(Regex("[^a-zA-Z0-9\\s\\-':,]"), " ").trim().replace(Regex("\\s+"), " ")
            if (latinOnly.isNotEmpty() && latinOnly != trimmedQuery) {
                return@withContext searchOnlineCovers(latinOnly, null)
            }
        }

        results
    }

    suspend fun searchCoversWithAutoFallback(rawQuery: String, titleOnly: String = ""): List<OnlineCoverCandidate> {
        val primary = searchOnlineCovers(rawQuery.trim())
        if (primary.isNotEmpty()) return primary
        val cleanTitle = titleOnly.trim()
        if (cleanTitle.isNotEmpty() && !cleanTitle.equals(rawQuery.trim(), ignoreCase = true)) {
            val fallback = searchOnlineCovers(cleanTitle)
            if (fallback.isNotEmpty()) return fallback
        }
        val latinOnly = rawQuery.replace(Regex("[^a-zA-Z0-9\\s\\-':,]"), " ").trim().replace(Regex("\\s+"), " ")
        if (latinOnly.isNotEmpty() && latinOnly != rawQuery.trim() && latinOnly != cleanTitle) {
            return searchOnlineCovers(latinOnly)
        }
        return emptyList()
    }

    private fun downloadBytesWithRedirects(imageUrl: String): ByteArray? {
        var currentUrl = imageUrl.replace("http://", "https://")
        var redirects = 0
        while (redirects < 6) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 10000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Mezura/1.0")
                }
                connection.connect()
                val code = connection.responseCode
                if (code in 300..399) {
                    val loc = connection.getHeaderField("Location") ?: break
                    currentUrl = if (loc.startsWith("http")) loc else URL(url, loc).toString()
                    currentUrl = currentUrl.replace("http://", "https://")
                    connection.disconnect()
                    redirects++
                    continue
                }
                if (code in 200..299) {
                    val bytes = connection.inputStream.use { it.readBytes() }
                    if (bytes.size > 150) {
                        return bytes
                    }
                }
                break
            } catch (_: Exception) {
                break
            } finally {
                connection?.disconnect()
            }
        }
        return null
    }

    suspend fun downloadCoverToCustom(context: Context, bookIdPrefix: String, imageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val bytes = downloadBytesWithRedirects(imageUrl) ?: return@withContext null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
            val maxDim = 800
            val scaled = scaleBitmap(bitmap, maxDim)
            val dir = File(context.filesDir, "custom_covers").apply { mkdirs() }
            val safeId = bookIdPrefix.ifBlank { "book" }.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val target = File(dir, "cover_${safeId}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(target).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            if (scaled != bitmap) scaled.recycle()
            bitmap.recycle()
            target.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "downloadCoverToCustom error: ${e.message}")
            null
        }
    }

    suspend fun downloadAndCacheCover(context: Context, bookId: String, imageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val bytes = downloadBytesWithRedirects(imageUrl) ?: return@withContext null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
            val scaled = scaleBitmap(bitmap, 600)
            val dir = File(context.filesDir, "folio_covers").apply { mkdirs() }
            val target = File(dir, "$bookId.webp")
            FileOutputStream(target).use { out ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, out)
                } else {
                    @Suppress("DEPRECATION")
                    scaled.compress(Bitmap.CompressFormat.WEBP, 85, out)
                }
            }
            if (scaled != bitmap) scaled.recycle()
            bitmap.recycle()
            target.name
        } catch (e: Exception) {
            Log.e(TAG, "Cover download failed: ${e.message}")
            null
        }
    }

    suspend fun fetchCoverBitmap(imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bytes = downloadBytesWithRedirects(imageUrl) ?: return@withContext null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
            if (bitmap.width <= 2 || bitmap.height <= 2) {
                bitmap.recycle()
                return@withContext null
            }
            val scaled = scaleBitmap(bitmap, 400)
            if (scaled != bitmap) bitmap.recycle()
            scaled
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateScore(
        targetTitle: String,
        targetAuthor: String,
        candTitle: String,
        candAuthors: List<String>,
        hasCover: Boolean,
        hasPages: Boolean
    ): Float {
        val titleSim = titleSimilarity(targetTitle, candTitle)
        val authorSim = if (targetAuthor.isBlank()) 0.5f else authorSimilarity(targetAuthor, candAuthors)
        val coverScore = if (hasCover) 1f else 0f
        val pageScore = if (hasPages) 1f else 0f

        return (0.60f * titleSim + 0.25f * authorSim + 0.10f * coverScore + 0.05f * pageScore).coerceIn(0f, 1f)
    }

    private fun titleSimilarity(t1: String, t2: String): Float {
        val n1 = normalizeText(t1)
        val n2 = normalizeText(t2)
        if (n1.isEmpty() || n2.isEmpty()) return 0f
        if (n1 == n2) return 1f

        // Check before colon
        val base1 = n1.substringBefore(":")
        val base2 = n2.substringBefore(":")
        if (base1.isNotEmpty() && base1 == base2) return 0.95f

        val isBangla = containsBengali(t1) || containsBengali(t2)
        val jw = jaroWinkler(n1, n2)
        val tokenOverlap = tokenSetOverlap(n1, n2)
        val raw = max(jw, tokenOverlap)
        return if (isBangla) {
            when {
                tokenOverlap >= 0.35f -> max(raw, 0.90f)
                n1.contains(n2) || n2.contains(n1) -> max(raw, 0.85f)
                else -> raw
            }
        } else {
            raw
        }
    }

    private fun authorSimilarity(target: String, authors: List<String>): Float {
        if (authors.isEmpty()) return 0.3f
        val nTarget = normalizeText(target)
        val isBangla = containsBengali(target)
        val maxSim = authors.maxOfOrNull { a ->
            val nA = normalizeText(a)
            val jw = jaroWinkler(nTarget, nA)
            val overlap = tokenSetOverlap(nTarget, nA)
            val raw = max(jw, overlap)
            if (isBangla && (overlap >= 0.35f || nTarget.contains(nA) || nA.contains(nTarget))) {
                max(raw, 0.85f)
            } else {
                raw
            }
        } ?: 0.3f
        return maxSim
    }

    private fun tokenSetOverlap(s1: String, s2: String): Float {
        val t1 = s1.split(" ").filter { it.length > 1 }.toSet()
        val t2 = s2.split(" ").filter { it.length > 1 }.toSet()
        if (t1.isEmpty() || t2.isEmpty()) return 0f
        val intersection = t1.intersect(t2).size.toFloat()
        return (2f * intersection) / (t1.size + t2.size).toFloat()
    }

    private fun normalizeText(str: String): String {
        return Normalizer.normalize(str, Normalizer.Form.NFC)
            .lowercase()
            .replace(Regex("[\\p{Punct}\\u200C\\u200D]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun jaroWinkler(s1: String, s2: String): Float {
        if (s1 == s2) return 1f
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0 || len2 == 0) return 0f

        val matchDistance = max(len1, len2) / 2 - 1
        val s1Matches = BooleanArray(len1)
        val s2Matches = BooleanArray(len2)

        var matches = 0
        for (i in 0 until len1) {
            val start = max(0, i - matchDistance)
            val end = min(i + matchDistance + 1, len2)
            for (j in start until end) {
                if (s2Matches[j]) continue
                if (s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0f

        var transpositions = 0
        var k = 0
        for (i in 0 until len1) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        val m = matches.toFloat()
        val jaro = (m / len1 + m / len2 + (m - transpositions / 2f) / m) / 3f

        var prefix = 0
        for (i in 0 until min(4, min(len1, len2))) {
            if (s1[i] == s2[i]) prefix++ else break
        }

        return (jaro + prefix * 0.1f * (1f - jaro))
    }

    private fun containsBengali(str: String): Boolean {
        for (c in str) {
            if (c in '\u0980'..'\u09FF') return true
        }
        return false
    }

    private fun scaleBitmap(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxDim && h <= maxDim) return src
        val ratio = w.toFloat() / h.toFloat()
        val targetW: Int
        val targetH: Int
        if (ratio > 1f) {
            targetW = maxDim
            targetH = (maxDim / ratio).toInt()
        } else {
            targetH = maxDim
            targetW = (maxDim * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(src, targetW.coerceAtLeast(1), targetH.coerceAtLeast(1), true)
    }

    private fun httpGet(urlStr: String, retries: Int = 1): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mezura/1.0 (Android)")
                setRequestProperty("Accept", "application/json")
            }
            conn.connect()
            val code = conn.responseCode
            if (code == 429 && retries > 0) {
                Thread.sleep(1000L)
                return httpGet(urlStr, retries - 1)
            }
            if (code in 200..299) {
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
