package rocks.ndot.widgets.instruments.folio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rocks.ndot.widgets.kit.Feedback
import rocks.ndot.widgets.kit.Fonts
import rocks.ndot.widgets.kit.Ink
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.net.URLEncoder

enum class CoverDialogTab(val label: String, val icon: String) {
    CATALOG("CATALOGS", "📚"),
    GOOGLE("GOOGLE", "🔍"),
    BING("BING", "🌐")
}

enum class CoverSourceFilter(val label: String) {
    ALL("ALL SOURCES"),
    OPEN_LIBRARY("OPEN LIBRARY"),
    GOOGLE_BOOKS("GOOGLE BOOKS")
}

private const val JS_INTERCEPTOR = """
(function() {
    if (window.__coverInterceptorInstalled) return;
    window.__coverInterceptorInstalled = true;

    function getBestImageUrl(el) {
        if (!el) return null;

        // 1. Bing Images m-attribute JSON containing full-res murl
        let mElem = el.closest('[m]');
        if (mElem) {
            try {
                let data = JSON.parse(mElem.getAttribute('m'));
                if (data && data.murl && data.murl.startsWith('http')) return data.murl;
                if (data && data.turl && data.turl.startsWith('http')) return data.turl;
            } catch(e) {}
        }

        // 2. Google Images anchor ?imgurl= parameter for original image
        let a = el.closest('a');
        if (a && a.href) {
            let match = a.href.match(/[?&]imgurl=([^&]+)/);
            if (match && match[1]) {
                try {
                    return decodeURIComponent(match[1]);
                } catch(e) {
                    return match[1];
                }
            }
        }

        // 3. Directly clicked or descendant <img>
        let img = el.tagName === 'IMG' ? el : (el.querySelector('img') || el.closest('img'));
        if (img) {
            let src = img.getAttribute('src') || img.src || img.getAttribute('data-src') || img.currentSrc;
            if (src && (src.startsWith('http') || src.startsWith('data:image'))) {
                return src;
            }
        }

        return null;
    }

    document.addEventListener('click', function(e) {
        let url = getBestImageUrl(e.target);
        if (url) {
            e.preventDefault();
            e.stopPropagation();
            if (window.CoverBridge) {
                window.CoverBridge.onImageClicked(url);
            }
        }
    }, true);
})();
"""

/**
 * JavaScript Bridge for Android WebView that receives clicked image URLs.
 */
private class CoverBridge(
    private val onImageSelected: (String) -> Unit
) {
    @JavascriptInterface
    fun onImageClicked(url: String) {
        onImageSelected(url)
    }
}

/**
 * Online Cover Search & Picker Dialog.
 * Provides 3 distinct source tabs:
 * 1. CATALOGS — Curated Open Library & Google Books API 3-column grid + direct URL pasting
 * 2. GOOGLE — Embedded Google Image Search with 1-tap image interception (zero browser exit)
 * 3. BING — Embedded Bing Image Search with 1-tap image interception (zero browser exit)
 */
@Composable
fun OnlineCoverPickerDialog(
    initialQuery: String,
    bookId: String = "book",
    onCoverSelected: (localFilePath: String, remoteUrl: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(CoverDialogTab.CATALOG) }
    var searchQuery by remember { mutableStateOf(initialQuery.trim()) }
    var pasteUrl by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(CoverSourceFilter.ALL) }
    var isSearchingCatalog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadingStatus by remember { mutableStateOf("DOWNLOADING COVER...") }
    var downloadingUrl by remember { mutableStateOf<String?>(null) }
    var candidates by remember { mutableStateOf<List<OnlineCoverCandidate>>(emptyList()) }
    var hasSearchedCatalog by remember { mutableStateOf(false) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun runCatalogSearch(q: String) {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) return
        isSearchingCatalog = true
        hasSearchedCatalog = true
        scope.launch {
            val results = BookMetadataFetcher.searchOnlineCovers(trimmed)
            candidates = results
            isSearchingCatalog = false
        }
    }

    fun buildGoogleSearchUrl(q: String): String {
        val query = if (q.contains("cover", ignoreCase = true)) q else "$q book cover"
        val enc = URLEncoder.encode(query, "UTF-8")
        return "https://www.google.com/search?tbm=isch&q=$enc"
    }

    fun buildBingSearchUrl(q: String): String {
        val query = if (q.contains("cover", ignoreCase = true)) q else "$q book cover"
        val enc = URLEncoder.encode(query, "UTF-8")
        return "https://www.bing.com/images/search?q=$enc"
    }

    fun handlePickedImageUrl(rawUrl: String) {
        if (isDownloading) return
        isDownloading = true
        downloadingStatus = "APPLYING SELECTED COVER..."
        downloadingUrl = rawUrl

        scope.launch(Dispatchers.IO) {
            val localPath = try {
                if (rawUrl.startsWith("data:image/")) {
                    val base64Data = rawUrl.substringAfter("base64,")
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        val coversDir = File(context.filesDir, "folio_covers").apply { mkdirs() }
                        val outFile = File(coversDir, "custom_${bookId}_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(outFile).use { fos ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                        }
                        outFile.absolutePath
                    } else {
                        null
                    }
                } else if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                    BookMetadataFetcher.downloadCoverToCustom(context, bookId, rawUrl)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }

            withContext(Dispatchers.Main) {
                isDownloading = false
                downloadingUrl = null
                if (localPath != null) {
                    Feedback.success(context)
                    Toast.makeText(context, "Cover applied successfully!", Toast.LENGTH_SHORT).show()
                    onCoverSelected(localPath, rawUrl)
                    onDismiss()
                } else {
                    Toast.makeText(context, "Failed to capture image, tap another image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (initialQuery.isNotBlank()) {
            runCatalogSearch(initialQuery)
        }
    }

    val filteredCandidates = remember(candidates, selectedFilter) {
        when (selectedFilter) {
            CoverSourceFilter.ALL -> candidates
            CoverSourceFilter.OPEN_LIBRARY -> candidates.filter { it.source.contains("Open Library", ignoreCase = true) }
            CoverSourceFilter.GOOGLE_BOOKS -> candidates.filter { it.source.contains("Google Books", ignoreCase = true) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xEE08090C))
                .padding(horizontal = 10.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF14161C))
                    .border(1.dp, Color(0xFF2E333E), RoundedCornerShape(18.dp))
                    .padding(14.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        BasicText(
                            text = "ONLINE BOOK COVERS",
                            style = TextStyle(
                                color = Ink.BRASS,
                                fontSize = 13.sp,
                                fontFamily = Fonts.mono,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        )
                        Spacer(Modifier.height(2.dp))
                        BasicText(
                            text = "Tap any image to set it directly as book cover",
                            style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF22252C))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText("✕", style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Mode Tabs Switcher (CATALOGS · GOOGLE IMAGES · BING IMAGES)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F1116))
                        .border(1.dp, Color(0xFF262A35), RoundedCornerShape(10.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CoverDialogTab.entries.forEach { tab ->
                        val isSel = tab == activeTab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSel) {
                                        Brush.verticalGradient(listOf(Color(0xFFE2B86E), Color(0xFFB58739)))
                                    } else {
                                        SolidColor(Color.Transparent)
                                    }
                                )
                                .clickable {
                                    activeTab = tab
                                    Feedback.tick(context)
                                    if (tab == CoverDialogTab.GOOGLE) {
                                        val url = buildGoogleSearchUrl(searchQuery)
                                        webViewRef?.loadUrl(url)
                                    } else if (tab == CoverDialogTab.BING) {
                                        val url = buildBingSearchUrl(searchQuery)
                                        webViewRef?.loadUrl(url)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicText(
                                    text = "${tab.icon} ",
                                    style = TextStyle(fontSize = 11.sp)
                                )
                                BasicText(
                                    text = tab.label,
                                    style = TextStyle(
                                        color = if (isSel) Color(0xFF0C0E12) else Color(0xFFCBD2DE),
                                        fontSize = 11.sp,
                                        fontFamily = Fonts.mono,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Search Input & Action Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1C1F27))
                        .border(1.dp, Color(0xFF333845), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Default),
                        cursorBrush = SolidColor(Ink.BRASS),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                BasicText("Search title, author, or keywords...", style = TextStyle(color = Color(0xFF6B7280), fontSize = 13.sp))
                            }
                            innerTextField()
                        }
                    )

                    Spacer(Modifier.width(6.dp))

                    if (activeTab != CoverDialogTab.CATALOG) {
                        // Web Navigation: Back
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF262B36))
                                .clickable {
                                    if (webViewRef?.canGoBack() == true) {
                                        webViewRef?.goBack()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText("◀", style = TextStyle(color = Color.White, fontSize = 11.sp))
                        }

                        Spacer(Modifier.width(4.dp))

                        // Web Navigation: Reload
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF262B36))
                                .clickable { webViewRef?.reload() },
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText("↻", style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                        }

                        Spacer(Modifier.width(6.dp))
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Ink.BRASS)
                            .clickable {
                                Feedback.tick(context)
                                when (activeTab) {
                                    CoverDialogTab.CATALOG -> runCatalogSearch(searchQuery)
                                    CoverDialogTab.GOOGLE -> {
                                        val url = buildGoogleSearchUrl(searchQuery)
                                        webViewRef?.loadUrl(url)
                                    }
                                    CoverDialogTab.BING -> {
                                        val url = buildBingSearchUrl(searchQuery)
                                        webViewRef?.loadUrl(url)
                                    }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        BasicText(
                            text = "SEARCH",
                            style = TextStyle(color = Color.Black, fontSize = 10.5.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Content View for Active Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (activeTab) {
                        CoverDialogTab.CATALOG -> {
                            CatalogCoversView(
                                filteredCandidates = filteredCandidates,
                                allCandidates = candidates,
                                selectedFilter = selectedFilter,
                                onFilterSelected = { selectedFilter = it },
                                isSearching = isSearchingCatalog,
                                hasSearched = hasSearchedCatalog,
                                isDownloading = isDownloading,
                                downloadingUrl = downloadingUrl,
                                onSelectCandidate = { candidate ->
                                    handlePickedImageUrl(candidate.coverUrl)
                                }
                            )
                        }

                        CoverDialogTab.GOOGLE -> {
                            WebCoverView(
                                initialUrl = buildGoogleSearchUrl(searchQuery),
                                onWebViewCreated = { webViewRef = it },
                                onImageSelected = { url -> handlePickedImageUrl(url) },
                                engineName = "Google Images"
                            )
                        }

                        CoverDialogTab.BING -> {
                            WebCoverView(
                                initialUrl = buildBingSearchUrl(searchQuery),
                                onWebViewCreated = { webViewRef = it },
                                onImageSelected = { url -> handlePickedImageUrl(url) },
                                engineName = "Bing Images"
                            )
                        }
                    }

                    // Full-screen downloading/saving overlay
                    if (isDownloading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xDD0B0D12)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF1B1E26))
                                    .border(1.dp, Ink.BRASS, RoundedCornerShape(14.dp))
                                    .padding(24.dp)
                            ) {
                                BasicText(
                                    text = downloadingStatus,
                                    style = TextStyle(
                                        color = Ink.BRASS,
                                        fontSize = 12.sp,
                                        fontFamily = Fonts.mono,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                )
                                Spacer(Modifier.height(8.dp))
                                BasicText(
                                    text = "Extracting, saving and applying to book card...",
                                    style = TextStyle(color = Color(0xFFC0CAD8), fontSize = 11.sp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Paste Direct Image URL Bar (Visible across all tabs for convenience)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF181A20))
                        .border(1.dp, Color(0xFF2A2E38), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = pasteUrl,
                        onValueChange = { pasteUrl = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = Color(0xFFD4D8E2), fontSize = 11.sp, fontFamily = Fonts.mono),
                        cursorBrush = SolidColor(Ink.BRASS),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (pasteUrl.isEmpty()) {
                                BasicText("Or paste direct image URL (jpg / png / webp)...", style = TextStyle(color = Color(0xFF555B68), fontSize = 11.sp, fontFamily = Fonts.mono))
                            }
                            innerTextField()
                        }
                    )

                    if (pasteUrl.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF2E3A4E))
                                .clickable {
                                    val trimmedUrl = pasteUrl.trim()
                                    if (trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://") || trimmedUrl.startsWith("data:image/")) {
                                        handlePickedImageUrl(trimmedUrl)
                                    } else {
                                        Toast.makeText(context, "Enter a valid http/https image link", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            BasicText(
                                text = "APPLY",
                                style = TextStyle(color = Color(0xFF8AB4F8), fontSize = 10.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogCoversView(
    filteredCandidates: List<OnlineCoverCandidate>,
    allCandidates: List<OnlineCoverCandidate>,
    selectedFilter: CoverSourceFilter,
    onFilterSelected: (CoverSourceFilter) -> Unit,
    isSearching: Boolean,
    hasSearched: Boolean,
    isDownloading: Boolean,
    downloadingUrl: String?,
    onSelectCandidate: (OnlineCoverCandidate) -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Source Filter Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CoverSourceFilter.entries.forEach { filter ->
                val isSel = filter == selectedFilter
                val count = when (filter) {
                    CoverSourceFilter.ALL -> allCandidates.size
                    CoverSourceFilter.OPEN_LIBRARY -> allCandidates.count { it.source.contains("Open Library", ignoreCase = true) }
                    CoverSourceFilter.GOOGLE_BOOKS -> allCandidates.count { it.source.contains("Google Books", ignoreCase = true) }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) Ink.BRASS else Color(0xFF1F222B))
                        .border(1.dp, if (isSel) Ink.BRASS else Color(0xFF333847), RoundedCornerShape(8.dp))
                        .clickable {
                            onFilterSelected(filter)
                            Feedback.tick(context)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    BasicText(
                        text = if (count > 0) "${filter.label} ($count)" else filter.label,
                        style = TextStyle(
                            color = if (isSel) Color.Black else Color(0xFFC0CAD8),
                            fontSize = 10.sp,
                            fontFamily = Fonts.mono,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                isSearching -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BasicText(
                                text = "QUERYING OPEN LIBRARY & GOOGLE BOOKS...",
                                style = TextStyle(color = Ink.BRASS, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                            )
                            Spacer(Modifier.height(6.dp))
                            BasicText("Fetching high-resolution editions", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp))
                        }
                    }
                }

                hasSearched && filteredCandidates.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                            BasicText(
                                text = "NO COVERS FOUND IN API CATALOGS",
                                style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp, fontFamily = Fonts.mono)
                            )
                            Spacer(Modifier.height(6.dp))
                            BasicText(
                                text = "Switch to GOOGLE or BING tab above to find millions of covers directly.",
                                style = TextStyle(color = Color(0xFF8E95A5), fontSize = 11.sp, textAlign = TextAlign.Center)
                            )
                        }
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredCandidates) { candidate ->
                            val isThisDownloading = isDownloading && downloadingUrl == candidate.coverUrl
                            CoverCandidateCard(
                                candidate = candidate,
                                isDownloading = isThisDownloading,
                                onClick = { onSelectCandidate(candidate) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebCoverView(
    initialUrl: String,
    onWebViewCreated: (WebView) -> Unit,
    onImageSelected: (String) -> Unit,
    engineName: String
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Helpful Tip Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF17202E))
                .border(1.dp, Color(0xFF223652), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText("⚡ ", style = TextStyle(fontSize = 11.sp))
            BasicText(
                text = "Tap any image in $engineName to insert it as book cover directly",
                style = TextStyle(color = Color(0xFF90CAF9), fontSize = 10.5.sp, fontFamily = Fonts.mono)
            )
        }

        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF262C38), RoundedCornerShape(8.dp))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                        }

                        addJavascriptInterface(CoverBridge { rawUrl ->
                            post { onImageSelected(rawUrl) }
                        }, "CoverBridge")

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val reqUrl = request?.url?.toString() ?: ""
                                if (reqUrl.contains("imgurl=")) {
                                    val match = Regex("""imgurl=([^&]+)""").find(reqUrl)
                                    if (match != null) {
                                        val decoded = try {
                                            URLDecoder.decode(match.groupValues[1], "UTF-8")
                                        } catch (e: Exception) {
                                            match.groupValues[1]
                                        }
                                        post { onImageSelected(decoded) }
                                        return true
                                    }
                                }
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                view?.evaluateJavascript(JS_INTERCEPTOR, null)
                            }
                        }

                        onWebViewCreated(this)
                        loadUrl(initialUrl)
                    }
                }
            )
        }
    }
}

@Composable
private fun CoverCandidateCard(
    candidate: OnlineCoverCandidate,
    isDownloading: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1B1E25))
            .border(1.dp, Color(0xFF2C313C), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF111317)),
            contentAlignment = Alignment.Center
        ) {
            AsyncCoverThumbnail(
                imageUrl = candidate.coverUrl,
                fallbackTitle = candidate.title,
                fallbackAuthor = candidate.author,
                modifier = Modifier.fillMaxSize()
            )

            if (isDownloading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCC000000)),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = "APPLYING...",
                        style = TextStyle(color = Ink.BRASS, fontSize = 9.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        val badgeColor = if (candidate.source.contains("Open Library")) Color(0xFFD4AF37) else Color(0xFF64B5F6)
        BasicText(
            text = candidate.source.uppercase(),
            style = TextStyle(
                color = badgeColor,
                fontSize = 8.sp,
                fontFamily = Fonts.mono,
                fontWeight = FontWeight.Bold
            )
        )

        if (candidate.title.isNotBlank()) {
            BasicText(
                text = candidate.title,
                style = TextStyle(color = Color(0xFFCBD2DE), fontSize = 10.sp, fontFamily = FontFamily.Default),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
