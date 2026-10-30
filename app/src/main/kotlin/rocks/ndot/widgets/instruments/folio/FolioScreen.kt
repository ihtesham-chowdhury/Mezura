package rocks.ndot.widgets.instruments.folio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rocks.ndot.widgets.app.AppPrefs
import rocks.ndot.widgets.kit.DotMatrixText
import rocks.ndot.widgets.kit.Feedback
import rocks.ndot.widgets.kit.Fonts
import rocks.ndot.widgets.kit.Ink
import rocks.ndot.widgets.kit.NavSpacer
import rocks.ndot.widgets.kit.NdotSegmented
import rocks.ndot.widgets.kit.NdotSheet
import rocks.ndot.widgets.kit.TextAction
import rocks.ndot.widgets.kit.ndotScreenInsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class FolioSortOrder(val label: String) {
    RECENTLY_READ("Recently read"),
    TITLE("Title"),
    AUTHOR("Author"),
    PROGRESS("Progress"),
    DATE_ADDED("Date added")
}

@Composable
fun FolioScreen(
    onNavigateToPomodoro: ((BookItem) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Initialize BookStore
    LaunchedEffect(Unit) {
        BookStore.init(context)
    }

    val books by BookStore.booksFlow.collectAsState()
    val sessions by BookStore.sessionsFlow.collectAsState()

    // Tab state (with default from AppPrefs)
    val savedDefaultTab = remember {
        AppPrefs.getDefault(context, AppPrefs.KEY_DEFAULT_FOLIO_TAB, "shelf")
    }
    val initialTabIdx = when (savedDefaultTab) {
        "counter", "caliper" -> 1
        "stats" -> 2
        else -> 0
    }
    var selectedTabIdx by remember { mutableIntStateOf(initialTabIdx) }
    val tabOptions = remember { listOf("Shelf", "Counter", "Stats") }

    var selectedBookId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(books) {
        if (selectedBookId == null && books.isNotEmpty()) {
            selectedBookId = books.first().id
        }
    }
    val selectedBook = books.firstOrNull { it.id == selectedBookId } ?: books.firstOrNull()

    // Modals
    var showAddSheet by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var contextBook by remember { mutableStateOf<BookItem?>(null) }
    var focusBook by remember { mutableStateOf<BookItem?>(null) }
    var editBook by remember { mutableStateOf<BookItem?>(null) }
    var logPastBook by remember { mutableStateOf<BookItem?>(null) }
    var bookActionTarget by remember { mutableStateOf<BookItem?>(null) }
    var completionFlowState by remember { mutableStateOf<BookCompletionFlowState>(BookCompletionFlowState.Idle) }

    // Filter & Sort
    var currentSort by remember { mutableStateOf(FolioSortOrder.RECENTLY_READ) }
    var currentFilter by remember { mutableStateOf<BookStatus?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // SAF Launchers for Excel & Drive
    val exportExcelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.ms-excel")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = FolioExporter.exportToSpreadsheetXml(context, uri)
                Toast.makeText(context, if (ok) "Exported library to Excel" else "Export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val backupZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = DriveBackupManager.createBackupZip(context, uri)
                Toast.makeText(context, if (ok) "Backup bundle saved to Drive" else "Backup failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val restoreZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = DriveBackupManager.restoreFromZip(context, uri)
                if (ok) {
                    BookStore.loadAll(context)
                    BookStore.loadSessions(context)
                    Toast.makeText(context, "Library restored successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Restore failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Filtered & Sorted books
    val displayedBooks = remember(books, currentSort, currentFilter, searchQuery) {
        var list = books
        if (currentFilter != null) {
            list = list.filter { it.status == currentFilter }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase()
            list = list.filter { it.title.lowercase().contains(q) || it.author.lowercase().contains(q) }
        }
        when (currentSort) {
            FolioSortOrder.RECENTLY_READ -> list.sortedByDescending { it.startedEpochMillis }
            FolioSortOrder.TITLE -> list.sortedBy { it.title.lowercase() }
            FolioSortOrder.AUTHOR -> list.sortedBy { it.author.lowercase() }
            FolioSortOrder.PROGRESS -> list.sortedByDescending { it.progressFraction }
            FolioSortOrder.DATE_ADDED -> list.sortedByDescending { it.startedEpochMillis }
        }
    }

    val shelfScrollState = rememberScrollState()
    val headerHeight = if (isSearchActive) 166.dp else 114.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.GROUND)
            .ndotScreenInsets()
    ) {
        // Main View Area (Shelf extends behind the floating liquid glass header!)
        Box(modifier = Modifier.fillMaxSize()) {
            Crossfade(targetState = selectedTabIdx, label = "folioTabCrossfade") { tabIdx ->
                when (tabIdx) {
                    0 -> { // Shelf
                        ShelfView(
                            books = displayedBooks,
                            selectedBook = selectedBook,
                            currentSort = currentSort,
                            currentFilter = currentFilter,
                            scrollState = shelfScrollState,
                            topPadding = headerHeight,
                            onOpenSortMenu = { showSortMenu = true },
                            onSelectFilter = { f -> currentFilter = f },
                            onSelectBook = { b ->
                                selectedBookId = b.id
                                bookActionTarget = b
                                Feedback.tick(context)
                            },
                            onContinueReading = { b ->
                                selectedBookId = b.id
                                Feedback.tick(context)
                                if (onNavigateToPomodoro != null) {
                                    onNavigateToPomodoro(b)
                                } else {
                                    focusBook = b
                                }
                            },
                            onAddFirstBook = { showAddSheet = true },
                            onBookLongClick = { b ->
                                bookActionTarget = b
                                Feedback.tick(context)
                            }
                        )
                    }
                    1 -> { // Counter
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = headerHeight)
                                .padding(bottom = 108.dp)
                        ) {
                            if (selectedBook != null) {
                                CounterView(
                                    book = selectedBook,
                                    onSaveProgress = { newP ->
                                        val wasCompleted = selectedBook.status == BookStatus.COMPLETED
                                        val isCompletedNow = newP >= selectedBook.totalPages && selectedBook.totalPages > 0
                                        val updated = selectedBook.copy(
                                            currentPage = newP,
                                            status = if (isCompletedNow) BookStatus.COMPLETED else selectedBook.status
                                        )
                                        BookStore.upsertBook(context, updated)
                                        if (!wasCompleted && isCompletedNow) {
                                            completionFlowState = BookCompletionFlowState.AwaitingStarRating(
                                                book = updated,
                                                initialRating = if (updated.rating in 1..5) updated.rating else 5
                                            )
                                        }
                                    },
                                    onLogSession = { log ->
                                        BookStore.addSession(context, log)
                                        Toast.makeText(context, "Logged ${log.pagesRead} pages (${log.durationMinutes}m)", Toast.LENGTH_SHORT).show()
                                    },
                                    onClose = { selectedTabIdx = 0 },
                                    onStartFocusReading = {
                                        if (onNavigateToPomodoro != null) {
                                            onNavigateToPomodoro(selectedBook)
                                        } else {
                                            focusBook = selectedBook
                                        }
                                    },
                                    onEditBook = { editBook = selectedBook },
                                    onLogPastSession = { logPastBook = selectedBook }
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    BasicText(
                                        "No book on your shelf. Tap + to add your first book.",
                                        style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 14.sp, fontFamily = FontFamily.Default)
                                    )
                                }
                            }
                        }
                    }
                    2 -> { // Stats
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = headerHeight)
                        ) {
                            GrandLibraryFolioStatsScreen(
                                books = books,
                                sessions = sessions,
                                selectedBook = selectedBook,
                                onOpenShareStudio = { stats, style ->
                                    completionFlowState = BookCompletionFlowState.SelectingShareStyle(stats, style)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Apple Liquid Glass Reading Header (floats on top of scrolling library!)
        LiquidGlassReadingHeader(
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // App Bar 56dp (below status bar inset)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left [+] Add Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Ink.SURFACE_2)
                            .clickable { showAddSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "+",
                            style = TextStyle(color = Ink.TEXT_WARM, fontSize = 20.sp, fontWeight = FontWeight.Light)
                        )
                    }

                    // Center Title: READING
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DotMatrixText(
                            text = "READING",
                            dotPitch = 2.4.dp,
                            litColor = Ink.TEXT_WARM,
                            glow = false
                        )
                    }

                    // Right Icons: Search and Overflow ⋮
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isSearchActive) Ink.SURFACE_3 else Ink.SURFACE_2)
                                .clickable { isSearchActive = !isSearchActive },
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText("🔍", style = TextStyle(fontSize = 14.sp))
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Ink.SURFACE_2)
                                .clickable { showOverflowMenu = true },
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText("⋮", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }

                // Search Bar (if active)
                if (isSearchActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ink.SURFACE_2)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp, fontFamily = FontFamily.Default),
                            cursorBrush = SolidColor(Ink.BRASS),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    BasicText("Search books by title or author...", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 13.sp))
                                }
                                innerTextField()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // NdotSegmented (Shelf | Counter | Stats) - 40dp tall
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    NdotSegmented(
                        options = tabOptions,
                        selectedIndex = selectedTabIdx,
                        onSelect = { idx ->
                            selectedTabIdx = idx
                            Feedback.tick(context)
                        },
                        onLongPressDefault = { idx ->
                            val key = when (idx) {
                                1 -> "counter"
                                2 -> "stats"
                                else -> "shelf"
                            }
                            AppPrefs.setDefault(context, AppPrefs.KEY_DEFAULT_FOLIO_TAB, key, tabOptions[idx])
                            Feedback.lock(context)
                            Toast.makeText(context, "${tabOptions[idx]} set as startup tab", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        BottomLiquidGlassVignette(modifier = Modifier.align(Alignment.BottomCenter))
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            NavSpacer()
        }

        // Add Book Sheet
        if (showAddSheet) {
            AddBookSheet(
                onDismiss = { showAddSheet = false },
                onBookAdded = { newBook ->
                    BookStore.upsertBook(context, newBook)
                    selectedBookId = newBook.id
                    showAddSheet = false
                    Toast.makeText(context, "Added \"${newBook.title}\" to shelf", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Overflow Sheet (Export, Drive Backup, Restore)
        if (showOverflowMenu) {
            NdotSheet(onDismiss = { showOverflowMenu = false }) {
                BasicText(
                    "FOLIO ARCHIVE & DRIVE SYNC",
                    style = TextStyle(color = Ink.BRASS, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(14.dp))
                BasicText(
                    "Export your library, reading logs, and covers with zero loss.",
                    style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 12.sp, fontFamily = FontFamily.Default)
                )
                Spacer(Modifier.height(18.dp))

                // Excel Export
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ink.SURFACE_3)
                        .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(14.dp))
                        .clickable {
                            showOverflowMenu = false
                            exportExcelLauncher.launch("mezura_library_export.xls")
                        }
                        .padding(14.dp)
                ) {
                    Column {
                        BasicText("EXPORT EXCEL WORKBOOK (.XLS)", style = TextStyle(color = Ink.BRASS, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(4.dp))
                        BasicText("Spreadsheet with full Bengali and English support.", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp))
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Drive Backup
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ink.SURFACE_3)
                        .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(14.dp))
                        .clickable {
                            showOverflowMenu = false
                            backupZipLauncher.launch(DriveBackupManager.generateBackupFileName())
                        }
                        .padding(14.dp)
                ) {
                    Column {
                        BasicText("BACKUP BUNDLE TO GOOGLE DRIVE (.ZIP)", style = TextStyle(color = Ink.LED, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(4.dp))
                        BasicText("Packages library store, preferences, and all high-DPI book covers.", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp))
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Restore
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ink.SURFACE_3)
                        .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(14.dp))
                        .clickable {
                            showOverflowMenu = false
                            restoreZipLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                        }
                        .padding(14.dp)
                ) {
                    Column {
                        BasicText("RESTORE FROM BACKUP (.ZIP)", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(4.dp))
                        BasicText("Restores books and covers from previously saved backup.", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp))
                    }
                }
            }
        }

        // Sort Options Sheet
        if (showSortMenu) {
            NdotSheet(onDismiss = { showSortMenu = false }) {
                BasicText("SORT SHELF BY", style = TextStyle(color = Ink.BRASS, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(14.dp))
                FolioSortOrder.entries.forEach { sort ->
                    val isSelected = currentSort == sort
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Ink.SURFACE_3 else Color.Transparent)
                            .clickable {
                                currentSort = sort
                                showSortMenu = false
                                Feedback.tick(context)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(
                            text = sort.label,
                            style = TextStyle(
                                color = if (isSelected) Ink.BRASS else Ink.TEXT_WARM,
                                fontSize = 14.sp,
                                fontFamily = Fonts.sans,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                        if (isSelected) {
                            BasicText("✓", style = TextStyle(color = Ink.BRASS, fontSize = 14.sp))
                        }
                    }
                }
            }
        }

        // Book Action Sheet (Tap on card or overflow)
        bookActionTarget?.let { b ->
            BookActionSheet(
                book = b,
                onDismiss = { bookActionTarget = null },
                onStartFocus = {
                    val target = b
                    bookActionTarget = null
                    if (onNavigateToPomodoro != null) {
                        onNavigateToPomodoro(target)
                    } else {
                        focusBook = target
                    }
                },
                onOpenCaliper = {
                    selectedBookId = b.id
                    selectedTabIdx = 1
                    bookActionTarget = null
                },
                onOpenStatsStudio = {
                    val target = b
                    bookActionTarget = null
                    val stats = BookStatsCalculator.compute(context, target, sessions)
                    completionFlowState = BookCompletionFlowState.SelectingShareStyle(stats)
                },
                onEdit = {
                    val target = b
                    bookActionTarget = null
                    editBook = target
                },
                onLogPast = {
                    val target = b
                    bookActionTarget = null
                    logPastBook = target
                },
                onToggleStatus = {
                    val newStatus = if (b.status == BookStatus.COMPLETED) BookStatus.READING else BookStatus.COMPLETED
                    val updated = b.copy(status = newStatus, currentPage = if (newStatus == BookStatus.COMPLETED) b.totalPages else b.currentPage)
                    BookStore.upsertBook(context, updated)
                    bookActionTarget = null
                    Feedback.success(context)
                },
                onDelete = {
                    BookStore.deleteBook(context, b.id)
                    bookActionTarget = null
                    Feedback.tick(context)
                }
            )
        }

        // Dedicated Book Focus Instrument (Timer with SystemClock.elapsedRealtime)
        focusBook?.let { b ->
            BookFocusSheet(
                book = b,
                onDismiss = { focusBook = null },
                onSaveSession = { sessionLog ->
                    BookStore.addSession(context, sessionLog)
                    val run = rocks.ndot.widgets.instruments.sequence.SequenceEngine.getActiveRun(context)
                    if (run != null && (run.state == rocks.ndot.widgets.instruments.sequence.SequenceState.RUNNING_STEP || run.state == rocks.ndot.widgets.instruments.sequence.SequenceState.PAUSED)) {
                        rocks.ndot.widgets.instruments.sequence.SequenceEngine.onStepCompleted(context, metadata = "Pages ${sessionLog.startPage}–${sessionLog.endPage}")
                    }
                },
                onUpdateBook = { updated ->
                    val wasCompleted = b.status == BookStatus.COMPLETED
                    val isNowCompleted = updated.status == BookStatus.COMPLETED || (updated.currentPage >= updated.totalPages && updated.totalPages > 0)
                    val finalUpdated = if (isNowCompleted && updated.status != BookStatus.COMPLETED) updated.copy(status = BookStatus.COMPLETED) else updated
                    BookStore.upsertBook(context, finalUpdated)
                    if (!wasCompleted && isNowCompleted) {
                        completionFlowState = BookCompletionFlowState.AwaitingStarRating(
                            book = finalUpdated,
                            initialRating = if (finalUpdated.rating in 1..5) finalUpdated.rating else 5
                        )
                    }
                }
            )
        }

        // Comprehensive Book Edit Sheet (Metadata, Pages, Status, Cover Picker)
        editBook?.let { b ->
            BookEditSheet(
                book = b,
                onDismiss = { editBook = null },
                onSave = { updated ->
                    BookStore.upsertBook(context, updated)
                    editBook = null
                    Feedback.success(context)
                    Toast.makeText(context, "Saved changes to \"${updated.title}\"", Toast.LENGTH_SHORT).show()
                },
                onDelete = {
                    BookStore.deleteBook(context, b.id)
                    editBook = null
                    Feedback.tick(context)
                    Toast.makeText(context, "Removed from shelf", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Log Past Session Sheet (Offline/Paper Reading)
        logPastBook?.let { b ->
            LogPastSessionSheet(
                book = b,
                onDismiss = { logPastBook = null },
                onSave = { sessionLog ->
                    BookStore.addSession(context, sessionLog)
                    val wasCompleted = b.status == BookStatus.COMPLETED
                    val nextCurrentPage = maxOf(b.currentPage, sessionLog.endPage).coerceAtMost(b.totalPages)
                    val isNowCompleted = nextCurrentPage >= b.totalPages && b.totalPages > 0
                    val nextStatus = if (isNowCompleted) BookStatus.COMPLETED else b.status
                    val updatedBook = b.copy(currentPage = nextCurrentPage, status = nextStatus)
                    BookStore.upsertBook(context, updatedBook)
                    logPastBook = null
                    Feedback.success(context)
                    val pctRead = if (b.totalPages > 0) ((sessionLog.pagesRead.toFloat() / b.totalPages) * 100f).roundToInt() else 0
                    val unitMsg = if (b.trackMode == BookTrackMode.PERCENTAGE) "+$pctRead% (${sessionLog.pagesRead} pages)" else "${sessionLog.pagesRead} pages"
                    Toast.makeText(context, "Logged $unitMsg (${sessionLog.durationMinutes}m)", Toast.LENGTH_SHORT).show()
                    if (!wasCompleted && isNowCompleted) {
                        completionFlowState = BookCompletionFlowState.AwaitingStarRating(
                            book = updatedBook,
                            initialRating = if (updatedBook.rating in 1..5) updatedBook.rating else 5
                        )
                    }
                }
            )
        }

        // 9-Design Reading Record & Completion Flow Dialog
        BookCompletionFlowHost(
            flowState = completionFlowState,
            onRatingSubmitted = { book, rating ->
                val updated = book.copy(rating = rating)
                BookStore.upsertBook(context, updated)
                val stats = BookStatsCalculator.compute(context, updated, sessions)
                completionFlowState = BookCompletionFlowState.PromptToShareStats(stats)
            },
            onOpenShareStudio = { stats ->
                completionFlowState = BookCompletionFlowState.SelectingShareStyle(stats)
            },
            onStyleSelected = { style ->
                val curr = completionFlowState
                if (curr is BookCompletionFlowState.SelectingShareStyle) {
                    completionFlowState = curr.copy(selectedStyle = style)
                }
            },
            onDismiss = {
                completionFlowState = BookCompletionFlowState.Idle
            }
        )
    }
}

@Composable
private fun ContextActionRow(label: String, textColor: Color = Ink.TEXT_WARM, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 10.dp)
    ) {
        BasicText(label, style = TextStyle(color = textColor, fontSize = 14.sp, fontFamily = Fonts.sans))
    }
}

// -----------------------------------------------------------------------------
// Shelf View (Modeled strictly on R4 Bookly)
// -----------------------------------------------------------------------------

@Composable
fun LiquidGlassReadingHeader(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    0.00f to Color(0xF40A0B0F),
                    0.65f to Color(0xEB0D0F16),
                    0.88f to Color(0xBF0E1018),
                    1.00f to Color(0x280E1018)
                )
            )
            .drawBehind {
                val h = size.height
                val lipY = h - 2.dp.toPx()

                // 1. Specular top highlight (1px white catchlight)
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color(0x35FFFFFF),
                            Color(0x60FFFFFF),
                            Color(0x35FFFFFF),
                            Color.Transparent
                        )
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )

                // 2. Frosted liquid glass golden amber lip line
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color(0x30FFFFFF),
                            Color(0x80F5C478),
                            Color(0xB0D69A4A),
                            Color(0x80F5C478),
                            Color(0x30FFFFFF),
                            Color.Transparent
                        )
                    ),
                    start = Offset(0f, lipY),
                    end = Offset(size.width, lipY),
                    strokeWidth = 1.2.dp.toPx()
                )

                // 3. Ambient soft shadow under the lip
                drawLine(
                    color = Color(0x45000000),
                    start = Offset(0f, lipY + 1.2.dp.toPx()),
                    end = Offset(size.width, lipY + 1.2.dp.toPx()),
                    strokeWidth = 2.5.dp.toPx()
                )
            }
            .padding(bottom = 6.dp)
    ) {
        content()
    }
}

@Composable
fun BottomLiquidGlassVignette(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp)
            .background(
                brush = Brush.verticalGradient(
                    0.00f to Color.Transparent,
                    0.35f to Color(0x8807080B),
                    0.72f to Color(0xD907080B),
                    1.00f to Color(0xFA060709)
                )
            )
    )
}

@Composable
fun ShelfBookStatusFooter(
    isCompleted: Boolean,
    progressFraction: Float,
    starRating: Int,
    startedDateLabel: String,
    modifier: Modifier = Modifier
) {
    val isFinished = isCompleted || starRating > 0
    Column(modifier = modifier.fillMaxWidth()) {
        if (!isFinished) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color(0xFF262830))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressFraction.coerceIn(0.04f, 1f))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFD69A4A), Color(0xFFF5C478))
                            )
                        )
                )
            }
            BasicText(
                text = startedDateLabel,
                style = TextStyle(
                    color = Color(0xFF8A909E),
                    fontSize = 11.sp,
                    fontFamily = Fonts.sans
                )
            )
        } else {
            Spacer(modifier = Modifier.height(6.dp))
            ShelfUnclippedStarRow(rating = starRating.coerceIn(1, 5))
        }
    }
}

@Composable
fun ShelfUnclippedStarRow(
    rating: Int,
    starRadiusDp: androidx.compose.ui.unit.Dp = 6.5.dp,
    spacingDp: androidx.compose.ui.unit.Dp = 3.5.dp
) {
    val totalWidth = (starRadiusDp * 2 * 5) + (spacingDp * 4) + 6.dp
    val totalHeight = (starRadiusDp * 2) + 4.dp
    Canvas(modifier = Modifier.size(width = totalWidth, height = totalHeight)) {
        val r = starRadiusDp.toPx()
        val gap = spacingDp.toPx()
        val startCx = r * 1.25f
        val cy = size.height / 2f
        for (i in 0 until 5) {
            val cx = startCx + i * (r * 2f + gap)
            val path = Path().apply {
                for (pt in 0 until 10) {
                    val rad = if (pt % 2 == 0) r else r * 0.42f
                    val ang = -Math.PI / 2.0 + pt * (Math.PI / 5.0)
                    val px = cx + (rad * kotlin.math.cos(ang)).toFloat()
                    val py = cy + (rad * kotlin.math.sin(ang)).toFloat()
                    if (pt == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            if (i < rating) {
                drawPath(path, color = Color(0xFFF2B857))
            } else {
                drawPath(path, color = Color(0xFF3A3E48), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.1.dp.toPx()))
            }
        }
    }
}

@Composable
private fun ShelfView(
    books: List<BookItem>,
    selectedBook: BookItem?,
    currentSort: FolioSortOrder,
    currentFilter: BookStatus?,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    topPadding: androidx.compose.ui.unit.Dp = 118.dp,
    onOpenSortMenu: () -> Unit,
    onSelectFilter: (BookStatus?) -> Unit,
    onSelectBook: (BookItem) -> Unit,
    onContinueReading: (BookItem) -> Unit,
    onAddFirstBook: () -> Unit,
    onBookLongClick: (BookItem) -> Unit
) {
    if (books.isEmpty()) {
        EmptyShelfView(onAddFirstBook, topPadding = topPadding)
        return
    }

    val heroBook = selectedBook ?: books.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(topPadding + 6.dp))

        // Hero "LAST READ" Card
        if (heroBook != null) {
            BasicText(
                text = "LAST READ",
                style = TextStyle(
                    color = Ink.TEXT_MUTED_V4,
                    fontSize = 11.sp,
                    fontFamily = Fonts.mono,
                    letterSpacing = 1.2.sp
                )
            )
            Spacer(Modifier.height(8.dp))

            HeroBookCard(
                book = heroBook,
                onClick = { onBookLongClick(heroBook) },
                onContinue = { onContinueReading(heroBook) },
                onQuickStep = { onBookLongClick(heroBook) }
            )

            Spacer(Modifier.height(18.dp))
        }

        // Sort & Filter Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sort Dropdown Button: ALL BOOKS ⌄
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenSortMenu() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = "${currentSort.label.uppercase()} ⌄",
                    style = TextStyle(
                        color = Ink.BRASS,
                        fontSize = 11.sp,
                        fontFamily = Fonts.mono,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // Filter Chips (ALL, READING, DONE)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    null to "ALL",
                    BookStatus.READING to "READING",
                    BookStatus.COMPLETED to "DONE"
                ).forEach { (status, label) ->
                    val isSel = currentFilter == status
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isSel) Ink.BRASS else Ink.SURFACE_2)
                            .clickable { onSelectFilter(status) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        BasicText(
                            text = label,
                            style = TextStyle(
                                color = if (isSel) Ink.GROUND else Ink.TEXT_MUTED_V4,
                                fontSize = 10.sp,
                                fontFamily = Fonts.mono,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 3-Column Shelf Grid (16dp margin, 12dp gutter)
        val chunkedBooks = books.chunked(3)
        chunkedBooks.forEach { rowBooks ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (i in 0 until 3) {
                    if (i < rowBooks.size) {
                        val book = rowBooks[i]
                        ShelfGridItem(
                            book = book,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectBook(book) },
                            onLongClick = { onBookLongClick(book) }
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // 148.dp bottom clearance spacer so last row of books clears the floating drawer bar completely
        Spacer(modifier = Modifier.height(148.dp))
    }
}

@Composable
private fun HeroBookCard(
    book: BookItem,
    onClick: () -> Unit = {},
    onContinue: () -> Unit,
    onQuickStep: () -> Unit
) {
    val dateStr = remember(book.startedEpochMillis) {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        sdf.format(Date(book.startedEpochMillis))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Ink.SURFACE_1)
            .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(24.dp))
            .clickable { onClick() }
    ) {
        // Ambient Cover Glow behind the card (BRASS / Dominant color at 18% alpha)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Ink.BRASS.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(size.width * 0.25f, size.height * 0.5f),
                    radius = size.width * 0.55f
                ),
                radius = size.width * 0.55f,
                center = Offset(size.width * 0.25f, size.height * 0.5f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isHeroFinished = book.status == BookStatus.COMPLETED || book.rating > 0 || (book.totalPages > 0 && book.currentPage >= book.totalPages)
            // Cover Image 96x144dp, radius 12dp
            CoverImage(
                book = book,
                cornerRadius = 12.dp,
                showProgressBar = !isHeroFinished,
                modifier = Modifier.size(96.dp, 144.dp)
            )

            // Details on right
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Title (Bangla-aware system font)
                BasicText(
                    text = book.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = Ink.TEXT_WARM,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Default,
                        lineHeight = 22.sp
                    )
                )

                // Author
                BasicText(
                    text = "By ${book.author.ifEmpty { "Unknown" }}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = Ink.TEXT_MUTED_V4,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Default
                    )
                )

                // Started date
                BasicText(
                    text = "Started on $dateStr",
                    style = TextStyle(
                        color = Ink.TEXT_FAINT_V4,
                        fontSize = 11.sp,
                        fontFamily = Fonts.sans
                    )
                )

                // Categories
                val catStr = book.categories.take(3).joinToString(" · ")
                if (catStr.isNotEmpty()) {
                    BasicText(
                        text = "▤ $catStr",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            color = Ink.TEXT_MUTED_V4,
                            fontSize = 10.sp,
                            fontFamily = Fonts.mono
                        )
                    )
                }

                Spacer(Modifier.height(2.dp))

                // Page count LED format: 187 / 320 · 58% or 58%
                val pct = if (book.totalPages > 0) (book.currentPage * 100 / book.totalPages) else 0
                val progressText = if (book.trackMode == BookTrackMode.PERCENTAGE) "$pct%" else "${book.currentPage} / ${book.totalPages} · $pct%"
                DotMatrixText(
                    text = progressText,
                    dotPitch = 2.0.dp,
                    litColor = Ink.LED,
                    glow = false
                )

                Spacer(Modifier.height(6.dp))

                // Action buttons: [ Continue reading ] (BRASS) + [ + ] (44dp circle)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ink.BRASS)
                            .clickable { onContinue() },
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "Continue reading",
                            style = TextStyle(
                                color = Ink.GROUND,
                                fontSize = 12.sp,
                                fontFamily = Fonts.mono,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Ink.SURFACE_3)
                            .border(1.dp, Ink.HAIRLINE_V4, CircleShape)
                            .clickable { onQuickStep() },
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText("+", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 20.sp, fontWeight = FontWeight.Light))
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfGridItem(
    book: BookItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dateStr = remember(book.startedEpochMillis) {
        val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
        sdf.format(Date(book.startedEpochMillis))
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        val isFinishedBook = book.status == BookStatus.COMPLETED || book.rating > 0 || (book.totalPages > 0 && book.currentPage >= book.totalPages)
        // Real Cover Image or Deterministic Fallback
        CoverImage(
            book = book,
            cornerRadius = 12.dp,
            showProgressBar = !isFinishedBook,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(6.dp))

        // Fixed 2-Line Height Title Container — Guarantees Stars Below Align Horizontally!
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
            contentAlignment = Alignment.TopStart
        ) {
            BasicText(
                text = book.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = Ink.TEXT_WARM,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Default,
                    lineHeight = 17.sp
                )
            )
        }

        Spacer(Modifier.height(2.dp))

        // Rating or Started on date with progress line
        val isCompleted = book.status == BookStatus.COMPLETED || book.rating > 0 || (book.totalPages > 0 && book.currentPage >= book.totalPages)
        ShelfBookStatusFooter(
            isCompleted = isCompleted,
            progressFraction = book.progressFraction,
            starRating = book.rating,
            startedDateLabel = "Started $dateStr"
        )
    }
}

@Composable
private fun EmptyShelfView(onAdd: () -> Unit, topPadding: androidx.compose.ui.unit.Dp = 118.dp) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .padding(top = topPadding, bottom = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Drawn Bookshelf Outline
            Canvas(modifier = Modifier.size(140.dp, 100.dp)) {
                val shelfW = size.width
                val shelfH = size.height

                // Planks
                drawLine(color = Ink.HAIRLINE_V4, start = Offset(0f, shelfH * 0.45f), end = Offset(shelfW, shelfH * 0.45f), strokeWidth = 3.dp.toPx())
                drawLine(color = Ink.HAIRLINE_V4, start = Offset(0f, shelfH * 0.90f), end = Offset(shelfW, shelfH * 0.90f), strokeWidth = 3.dp.toPx())

                // Book silhouettes on top shelf
                drawRoundRect(color = Ink.SURFACE_3, topLeft = Offset(20.dp.toPx(), shelfH * 0.15f), size = Size(16.dp.toPx(), shelfH * 0.30f), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()))
                drawRoundRect(color = Ink.SURFACE_2, topLeft = Offset(40.dp.toPx(), shelfH * 0.10f), size = Size(20.dp.toPx(), shelfH * 0.35f), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()))
            }

            BasicText("Your shelf is empty", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 16.sp, fontFamily = Fonts.sans, fontWeight = FontWeight.Bold))
            BasicText("Track reading, measure daily pace, and curate your personal library.", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 13.sp, fontFamily = FontFamily.Default, textAlign = TextAlign.Center))

            Spacer(Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Ink.BRASS)
                    .clickable { onAdd() }
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                BasicText("Add your first book", style = TextStyle(color = Ink.GROUND, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Add Book Sheet & State Machine (§5.4)
// -----------------------------------------------------------------------------

enum class AddBookSearchState {
    IDLE,
    SEARCHING,
    MATCHED,
    POSSIBLE,
    COVER_ONLY,
    NO_MATCH,
    OFFLINE
}

@Composable
private fun AddBookSheet(
    onDismiss: () -> Unit,
    onBookAdded: (BookItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var totalPagesStr by remember { mutableStateOf("300") }
    var trackMode by remember { mutableStateOf(BookTrackMode.PAGES) }
    var status by remember { mutableStateOf(BookStatus.READING) }
    var customCoverPath by remember { mutableStateOf<String?>(null) }
    var showSearchOnlineSheet by remember { mutableStateOf(false) }
    var showOnlineCoverPicker by remember { mutableStateOf(false) }

    var searchState by remember { mutableStateOf(AddBookSearchState.IDLE) }
    var candidates by remember { mutableStateOf<List<BookSearchResult>>(emptyList()) }
    var selectedCandidateIndex by remember { mutableIntStateOf(0) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var isAutoFilled by remember { mutableStateOf(false) }
    var userModifiedPages by remember { mutableStateOf(false) }

    val pickCoverLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (originalBitmap != null) {
                        val maxDim = 800
                        val scale = minOf(1f, maxDim.toFloat() / maxOf(originalBitmap.width, originalBitmap.height))
                        val finalBitmap = if (scale < 1f) {
                            Bitmap.createScaledBitmap(
                                originalBitmap,
                                (originalBitmap.width * scale).toInt(),
                                (originalBitmap.height * scale).toInt(),
                                true
                            )
                        } else originalBitmap

                        val coversDir = File(context.filesDir, "custom_covers").apply { mkdirs() }
                        val coverFile = File(coversDir, "cover_new_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(coverFile).use { out ->
                            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        customCoverPath = coverFile.absolutePath
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // 600ms Debounce Search on title/author keystrokes
    LaunchedEffect(title, author) {
        val trimmedT = title.trim()
        val trimmedA = author.trim()

        if (trimmedT.isEmpty() && trimmedA.isEmpty()) {
            searchState = AddBookSearchState.IDLE
            candidates = emptyList()
            return@LaunchedEffect
        }

        searchJob?.cancel()
        searchJob = scope.launch {
            delay(600L)
            searchState = AddBookSearchState.SEARCHING
            val results = BookMetadataFetcher.searchBooks(trimmedT, trimmedA)
            candidates = results

            if (results.isEmpty()) {
                searchState = AddBookSearchState.NO_MATCH
            } else {
                val top = results.first()
                selectedCandidateIndex = 0

                when {
                    top.confidence >= 0.80f -> {
                        searchState = AddBookSearchState.MATCHED
                        Feedback.success(context)
                        // Auto-fill only if user hasn't typed their own total pages and candidate count is positive
                        if (!userModifiedPages && top.pageCount > 0) {
                            totalPagesStr = top.pageCount.toString()
                            isAutoFilled = true
                        }
                        if (author.isBlank() && top.authors.isNotEmpty()) {
                            author = top.authors.joinToString(", ")
                        }
                    }
                    top.confidence >= 0.55f -> {
                        searchState = AddBookSearchState.POSSIBLE
                    }
                    top.coverUrl != null && top.pageCount <= 0 -> {
                        searchState = AddBookSearchState.COVER_ONLY
                    }
                    else -> {
                        searchState = AddBookSearchState.NO_MATCH
                    }
                }
            }
        }
    }

    NdotSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            BasicText(
                text = "ADD BOOK TO SHELF",
                style = TextStyle(color = Ink.BRASS, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(14.dp))

            // Prominent Bookly-Style SEARCH ONLINE Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Ink.SURFACE_3)
                    .border(1.dp, Ink.BRASS.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .clickable { showSearchOnlineSheet = true }
                    .padding(vertical = 12.dp, horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText("🔍  SEARCH ONLINE FOR BOOK", style = TextStyle(color = Ink.BRASS, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                }
            }

            Spacer(Modifier.height(10.dp))

            // Or enter manually divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f).height(1.dp).background(Ink.HAIRLINE_V4))
                BasicText("  OR ENTER DETAILS MANUALLY  ", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 9.sp, fontFamily = Fonts.mono))
                Box(Modifier.weight(1f).height(1.dp).background(Ink.HAIRLINE_V4))
            }

            Spacer(Modifier.height(14.dp))

            // Title Field
            BasicText("TITLE", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.SURFACE_3)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp, fontFamily = FontFamily.Default),
                    cursorBrush = SolidColor(Ink.BRASS),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(10.dp))

            // Author Field
            BasicText("AUTHOR", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.SURFACE_3)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                BasicTextField(
                    value = author,
                    onValueChange = { author = it },
                    textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp, fontFamily = FontFamily.Default),
                    cursorBrush = SolidColor(Ink.BRASS),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(10.dp))

            // Tracking Mode (Pages vs Percentage for Kindle / eBooks)
            BasicText("TRACKING MODE", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Ink.SURFACE_3)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    BookTrackMode.PAGES to "PAGES",
                    BookTrackMode.PERCENTAGE to "PERCENTAGE (%)"
                ).forEach { (mode, label) ->
                    val isSel = trackMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) Ink.BRASS else Color.Transparent)
                            .clickable {
                                trackMode = mode
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = label,
                            style = TextStyle(
                                color = if (isSel) Ink.GROUND else Ink.TEXT_MUTED_V4,
                                fontSize = 11.sp,
                                fontFamily = Fonts.mono,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Page count & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(
                        "TOTAL PAGES",
                        style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono)
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ink.SURFACE_3)
                            .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = totalPagesStr,
                            onValueChange = {
                                totalPagesStr = it.filter { c -> c.isDigit() }
                                userModifiedPages = true
                            },
                            readOnly = false,
                            textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp, fontFamily = Fonts.mono),
                            cursorBrush = SolidColor(Ink.BRASS),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Column(modifier = Modifier.weight(1.4f)) {
                    BasicText("STATUS", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ink.SURFACE_3)
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(
                            BookStatus.READING to "Reading",
                            BookStatus.COMPLETED to "Done"
                        ).forEach { (st, label) ->
                            val isSel = status == st
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) Ink.BRASS else Color.Transparent)
                                    .clickable { status = st }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicText(
                                    text = label,
                                    maxLines = 1,
                                    softWrap = false,
                                    style = TextStyle(
                                        color = if (isSel) Ink.GROUND else Ink.TEXT_MUTED_V4,
                                        fontSize = 11.sp,
                                        fontFamily = Fonts.mono,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // State Machine Banners (§5.4)
            when (searchState) {
                AddBookSearchState.IDLE -> {
                    BasicText(
                        "Type a title or author. We'll find the cover and page count.",
                        style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 12.sp, fontFamily = Fonts.sans)
                    )
                }
                AddBookSearchState.SEARCHING -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BasicText("Searching Google Books and Open Library...", style = TextStyle(color = Ink.BRASS, fontSize = 11.sp, fontFamily = Fonts.mono))
                        ShimmerSkeletonRow()
                        ShimmerSkeletonRow()
                    }
                }
                AddBookSearchState.MATCHED -> {
                    val cand = candidates.getOrNull(selectedCandidateIndex)
                    val conf = ((cand?.confidence ?: 0.9f) * 100).toInt()
                    val src = cand?.source?.name?.replace('_', ' ') ?: "GOOGLE BOOKS"

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ink.SAGE.copy(alpha = 0.15f))
                            .border(1.dp, Ink.SAGE, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                BasicText("✓ MATCHED · $src · $conf%", style = TextStyle(color = Ink.SAGE, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                                if (isAutoFilled) {
                                    BasicText("auto-filled", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            val pCount = cand?.pageCount ?: 0
                            val pStr = if (pCount > 0) "$pCount pages" else "Pages unlisted · enter manually"
                            BasicText("${cand?.title} · $pStr", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 12.sp, fontFamily = FontFamily.Default))
                        }
                    }
                }
                AddBookSearchState.POSSIBLE -> {
                    val cand = candidates.getOrNull(selectedCandidateIndex)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ink.BRASS.copy(alpha = 0.15f))
                            .border(1.dp, Ink.BRASS, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                BasicText("POSSIBLE MATCH · please confirm", style = TextStyle(color = Ink.BRASS, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                                BasicText(cand?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(color = Ink.TEXT_WARM, fontSize = 12.sp, fontFamily = FontFamily.Default))
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Ink.BRASS)
                                    .clickable {
                                        cand?.let {
                                            if (it.pageCount > 0) totalPagesStr = it.pageCount.toString()
                                            if (it.authors.isNotEmpty()) author = it.authors.joinToString(", ")
                                        }
                                        searchState = AddBookSearchState.MATCHED
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                BasicText("Use this", style = TextStyle(color = Ink.GROUND, fontSize = 11.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
                AddBookSearchState.COVER_ONLY -> {
                    BasicText("Found a cover but no page count. Enter pages manually.", style = TextStyle(color = Ink.LED, fontSize = 12.sp, fontFamily = Fonts.mono))
                }
                AddBookSearchState.NO_MATCH -> {
                    BasicText("No online match found. You can still add the book with a generated cover.", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 12.sp, fontFamily = Fonts.sans))
                }
                AddBookSearchState.OFFLINE -> {
                    BasicText("Offline. Add now and we'll fetch the cover when reconnected.", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 12.sp, fontFamily = Fonts.sans))
                }
            }

            // Cover Carousel (if candidates exist)
            if (candidates.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                BasicText("CHOOSE COVER (${candidates.size} CANDIDATES)", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                Spacer(Modifier.height(8.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(candidates.take(8)) { idx, cand ->
                        val isSelected = selectedCandidateIndex == idx
                        Column(
                            modifier = Modifier
                                .width(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(if (isSelected) 2.dp else 1.dp, if (isSelected) Ink.BRASS else Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                                .background(Ink.SURFACE_3)
                                .clickable {
                                    selectedCandidateIndex = idx
                                    if (cand.pageCount > 0) {
                                        totalPagesStr = cand.pageCount.toString()
                                        userModifiedPages = true
                                    }
                                    if (author.isBlank() && cand.authors.isNotEmpty()) {
                                        author = cand.authors.joinToString(", ")
                                    }
                                }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Candidate Cover Preview with Real Image
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                AsyncCoverThumbnail(
                                    imageUrl = cand.coverUrl,
                                    fallbackTitle = cand.title,
                                    fallbackAuthor = cand.authors.firstOrNull().orEmpty(),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            // Source badge
                            BasicText(
                                text = cand.source.name.replace('_', ' '),
                                style = TextStyle(color = Ink.BRASS, fontSize = 8.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                            )
                            BasicText(
                                text = cand.pixelResolution,
                                style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 8.sp, fontFamily = Fonts.mono)
                            )
                        }
                    }
                }

                // Edition Chips
                val uniquePages = candidates.map { it.pageCount }.filter { it > 0 }.distinct()
                if (uniquePages.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        uniquePages.forEach { p ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (totalPagesStr == p.toString()) Ink.BRASS else Ink.SURFACE_3)
                                    .clickable { totalPagesStr = p.toString() }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                BasicText(
                                    text = "$p p",
                                    style = TextStyle(
                                        color = if (totalPagesStr == p.toString()) Ink.GROUND else Ink.TEXT_MUTED_V4,
                                        fontSize = 10.sp,
                                        fontFamily = Fonts.mono
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Ink.SURFACE_3)
                        .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(10.dp))
                        .clickable { pickCoverLauncher.launch("image/*") }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    BasicText(
                        text = if (customCoverPath != null) "✓ CUSTOM COVER" else "🖼 FROM DEVICE",
                        style = TextStyle(color = if (customCoverPath != null) Ink.LED else Ink.BRASS, fontSize = 10.5.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(androidx.compose.ui.graphics.Color(0xFF222733))
                        .border(1.dp, androidx.compose.ui.graphics.Color(0xFF3B4457), RoundedCornerShape(10.dp))
                        .clickable { showOnlineCoverPicker = true }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    BasicText(
                        text = "🌐 SEARCH ONLINE",
                        style = TextStyle(color = androidx.compose.ui.graphics.Color(0xFF8AB4F8), fontSize = 10.5.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                    )
                }

                if (customCoverPath != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { customCoverPath = null }
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        BasicText("Reset", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.5.sp, fontFamily = Fonts.mono))
                    }
                }
            }

            // Live Shelf Preview Card (§3.2)
            Spacer(Modifier.height(14.dp))
            BasicText("LIVE SHELF PREVIEW", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(6.dp))
            val previewCand = candidates.getOrNull(selectedCandidateIndex)
            val previewCoverUrl = previewCand?.coverUrl
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Ink.SURFACE_2)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp, 72.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        if (customCoverPath != null) {
                            val previewBook = BookItem(
                                title = title.ifBlank { "Untitled" },
                                author = author.ifBlank { "Author" },
                                totalPages = totalPagesStr.toIntOrNull() ?: 200,
                                customCoverPath = customCoverPath
                            )
                            CoverImage(
                                book = previewBook,
                                cornerRadius = 8.dp,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            AsyncCoverThumbnail(
                                imageUrl = previewCoverUrl,
                                fallbackTitle = title.ifBlank { "Untitled" },
                                fallbackAuthor = author.ifBlank { "Author" },
                                cornerRadiusPx = 12f,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        BasicText(
                            text = title.ifBlank { "Book Title" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = Ink.TEXT_WARM, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Default)
                        )
                        Spacer(Modifier.height(2.dp))
                        BasicText(
                            text = author.ifBlank { "Author name" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp, fontFamily = FontFamily.Default)
                        )
                        Spacer(Modifier.height(4.dp))
                        val pVal = totalPagesStr.toIntOrNull() ?: 0
                        val pText = if (trackMode == BookTrackMode.PERCENTAGE) "$pVal pages (Tracked by %) · ${status.name}" else if (pVal > 0) "$pVal pages · ${status.name}" else "Pages not set · ${status.name}"
                        BasicText(
                            text = pText,
                            style = TextStyle(color = Ink.BRASS, fontSize = 10.sp, fontFamily = Fonts.mono)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Action Buttons: CANCEL / ADD TO SHELF
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextAction("CANCEL", Ink.TEXT_MUTED_V4, onDismiss)
                Spacer(Modifier.width(14.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (title.isNotBlank()) Ink.BRASS else Ink.SURFACE_3)
                        .clickable(enabled = title.isNotBlank()) {
                            val bookId = UUID.randomUUID().toString()
                            val totalP = totalPagesStr.toIntOrNull()?.coerceAtLeast(1) ?: 200
                            val chosenCand = candidates.getOrNull(selectedCandidateIndex)
                            val coverUrl = chosenCand?.coverUrl

                            scope.launch {
                                var localCoverName: String? = null
                                if (coverUrl != null && customCoverPath == null) {
                                    localCoverName = BookMetadataFetcher.downloadAndCacheCover(context, bookId, coverUrl)
                                }

                                val item = BookItem(
                                    id = bookId,
                                    title = title.trim(),
                                    author = author.trim().ifEmpty { "Unknown Author" },
                                    totalPages = totalP,
                                    currentPage = 0,
                                    categories = chosenCand?.categories ?: listOf("General"),
                                    coverLocalPath = localCoverName,
                                    coverRemoteUrl = coverUrl,
                                    customCoverPath = customCoverPath,
                                    rating = 5,
                                    status = status,
                                    trackMode = trackMode
                                )
                                onBookAdded(item)
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    BasicText(
                        text = "ADD TO SHELF",
                        style = TextStyle(
                            color = if (title.isNotBlank()) Ink.GROUND else Ink.TEXT_FAINT_V4,
                            fontSize = 12.sp,
                            fontFamily = Fonts.mono,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }

    if (showSearchOnlineSheet) {
        SearchOnlineSheet(
            initialQuery = title.ifBlank { "" },
            onDismiss = { showSearchOnlineSheet = false },
            onSelectBook = { cand ->
                title = cand.title
                if (cand.authors.isNotEmpty()) {
                    author = cand.authors.joinToString(", ")
                }
                if (cand.pageCount > 0) {
                    totalPagesStr = cand.pageCount.toString()
                    userModifiedPages = true
                }
                candidates = listOf(cand)
                selectedCandidateIndex = 0
                customCoverPath = null
                searchState = AddBookSearchState.MATCHED
                showSearchOnlineSheet = false
                Feedback.success(context)
            }
        )
    }

    if (showOnlineCoverPicker) {
        OnlineCoverPickerDialog(
            initialQuery = "${title.trim()} ${author.trim()}".trim(),
            bookId = "new_${System.currentTimeMillis()}",
            onCoverSelected = { localPath, _ ->
                customCoverPath = localPath
                showOnlineCoverPicker = false
            },
            onDismiss = { showOnlineCoverPicker = false }
        )
    }
}

@Composable
private fun SearchOnlineSheet(
    initialQuery: String = "",
    onDismiss: () -> Unit,
    onSelectBook: (BookSearchResult) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initialQuery) }
    var isSearching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<BookSearchResult>>(emptyList()) }
    var hasSearched by remember { mutableStateOf(false) }

    fun doSearch(q: String) {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) return
        isSearching = true
        hasSearched = true
        scope.launch {
            val list = BookMetadataFetcher.searchBooks(trimmed, "")
            results = list
            isSearching = false
        }
    }

    LaunchedEffect(Unit) {
        if (initialQuery.isNotBlank()) {
            doSearch(initialQuery)
        }
    }

    NdotSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText("🔍", style = TextStyle(fontSize = 14.sp))
                    BasicText(
                        text = "SEARCH BOOKS ONLINE",
                        style = TextStyle(color = Ink.BRASS, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Ink.SURFACE_3)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("×", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 18.sp))
                }
            }

            Spacer(Modifier.height(14.dp))

            // Search Bar Input + Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink.SURFACE_3)
                        .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp, fontFamily = Fonts.sans),
                        cursorBrush = SolidColor(Ink.BRASS),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                BasicText("Enter book title or author...", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 13.sp))
                            }
                            inner()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (query.isNotBlank()) Ink.BRASS else Ink.SURFACE_3)
                        .clickable(enabled = query.isNotBlank()) { doSearch(query) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        "SEARCH",
                        style = TextStyle(
                            color = if (query.isNotBlank()) Ink.GROUND else Ink.TEXT_FAINT_V4,
                            fontSize = 11.sp,
                            fontFamily = Fonts.mono,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // State & Results list
            if (isSearching) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                    BasicText("Searching Google Books and Open Library...", style = TextStyle(color = Ink.BRASS, fontSize = 11.sp, fontFamily = Fonts.mono))
                    ShimmerSkeletonRow()
                    ShimmerSkeletonRow()
                    ShimmerSkeletonRow()
                }
            } else if (hasSearched && results.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText("NO MATCHES FOUND", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(4.dp))
                        BasicText("Try searching with the Bengali or English name", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 11.sp))
                    }
                }
            } else if (results.isNotEmpty()) {
                BasicText("SELECT EDITION (${results.size} FOUND):", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                Spacer(Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    results.forEach { cand ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Ink.SURFACE_2)
                                .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                                .clickable { onSelectBook(cand) }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp, 64.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            ) {
                                AsyncCoverThumbnail(
                                    imageUrl = cand.coverUrl,
                                    fallbackTitle = cand.title,
                                    fallbackAuthor = cand.authors.firstOrNull().orEmpty(),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                BasicText(
                                    text = cand.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = TextStyle(color = Ink.TEXT_WARM, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                )
                                Spacer(Modifier.height(2.dp))
                                val authorStr = if (cand.authors.isNotEmpty()) cand.authors.joinToString(", ") else "Unknown Author"
                                BasicText(
                                    text = authorStr,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val pStr = if (cand.pageCount > 0) "${cand.pageCount} pages" else "Pages unlisted"
                                    BasicText(pStr, style = TextStyle(color = Ink.BRASS, fontSize = 10.sp, fontFamily = Fonts.mono))
                                    BasicText("·", style = TextStyle(color = Ink.TEXT_FAINT_V4, fontSize = 10.sp))
                                    BasicText(cand.source.name.replace('_', ' '), style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 9.sp, fontFamily = Fonts.mono))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShimmerSkeletonRow() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Ink.SURFACE_3.copy(alpha = alpha))
    )
}

// -----------------------------------------------------------------------------
// Book Focus Instrument (§3.5) - Timer-Linked Reading Session
// -----------------------------------------------------------------------------

@Composable
private fun BookFocusSheet(
    book: BookItem,
    onDismiss: () -> Unit,
    onSaveSession: (ReadingSessionLog) -> Unit,
    onUpdateBook: (BookItem) -> Unit
) {
    val context = LocalContext.current
    var isRunning by remember { mutableStateOf(true) }
    var startEpochMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var baseElapsedMillis by remember { mutableLongStateOf(0L) }
    var lastStartElapsedRealtime by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var totalElapsedSeconds by remember { mutableLongStateOf(0L) }

    val initialPage = book.currentPage
    var currentPage by remember { mutableIntStateOf(initialPage) }
    var showJumpDialog by remember { mutableStateOf(false) }

    // Screen-off resilient timer loop using elapsedRealtime
    LaunchedEffect(isRunning) {
        if (isRunning) {
            lastStartElapsedRealtime = SystemClock.elapsedRealtime()
            while (isRunning) {
                val nowRealtime = SystemClock.elapsedRealtime()
                totalElapsedSeconds = (baseElapsedMillis + (nowRealtime - lastStartElapsedRealtime)) / 1000L
                delay(500L)
            }
        }
    }

    val minutes = totalElapsedSeconds / 60
    val seconds = totalElapsedSeconds % 60
    val timeDisplay = if (minutes >= 60) {
        val hours = minutes / 60
        val remMin = minutes % 60
        String.format(Locale.US, "%02d:%02d:%02d", hours, remMin, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    val pagesRead = (currentPage - initialPage).coerceAtLeast(0)
    val paceMinutes = if (pagesRead > 0 && totalElapsedSeconds >= 30) {
        (totalElapsedSeconds / 60.0) / pagesRead
    } else null

    NdotSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) Ink.LED else Ink.TEXT_FAINT_V4)
                    )
                    BasicText(
                        text = "BOOK FOCUS INSTRUMENT",
                        style = TextStyle(color = Ink.BRASS, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Ink.SURFACE_3)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("×", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 18.sp))
                }
            }

            Spacer(Modifier.height(8.dp))

            BasicText(
                text = book.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Default)
            )
            BasicText(
                text = "By ${book.author}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 12.sp, fontFamily = FontFamily.Default)
            )

            Spacer(Modifier.height(20.dp))

            // Giant Timer Readout
            DotMatrixText(
                text = timeDisplay,
                dotPitch = 4.0.dp,
                litColor = if (isRunning) Ink.TEXT_WARM else Ink.TEXT_MUTED_V4,
                glow = isRunning
            )

            Spacer(Modifier.height(6.dp))
            BasicText(
                text = if (isRunning) "SESSION IN PROGRESS · SCREEN-OFF RESILIENT" else "PAUSED",
                style = TextStyle(color = if (isRunning) Ink.LED else Ink.TEXT_FAINT_V4, fontSize = 10.sp, fontFamily = Fonts.mono)
            )

            Spacer(Modifier.height(20.dp))

            // Page Stepper & Telemetry Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ink.SURFACE_2)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isPct = book.trackMode == BookTrackMode.PERCENTAGE
                        BasicText(if (isPct) "START: $initialPage%" else "START: P. $initialPage", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp, fontFamily = Fonts.mono))
                        BasicText(if (isPct) "TARGET: 100%" else "TARGET: P. ${book.totalPages}", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp, fontFamily = Fonts.mono))
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Ink.SURFACE_3.copy(alpha = 0.5f))
                            .clickable { showJumpDialog = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        val isPct = book.trackMode == BookTrackMode.PERCENTAGE
                        BasicText(if (isPct) "CURRENT PROGRESS:" else "CURRENT PAGE:", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 12.sp, fontFamily = Fonts.mono))
                        DotMatrixText(
                            text = if (isPct) "$currentPage%" else "$currentPage",
                            dotPitch = 2.6.dp,
                            litColor = Ink.BRASS,
                            glow = false
                        )
                        BasicText(" ✎", style = TextStyle(color = Ink.BRASS, fontSize = 12.sp))
                    }

                    Spacer(Modifier.height(12.dp))

                    // Stepper Buttons [-10] [-1] [+1] [+10]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isPct = book.trackMode == BookTrackMode.PERCENTAGE
                        listOf(-10, -1, 1, 10).forEach { delta ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Ink.SURFACE_3)
                                    .clickable {
                                        val next = (currentPage + delta).coerceIn(0, book.totalPages)
                                        currentPage = next
                                        Feedback.tick(context)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                val dText = if (delta > 0) "+$delta" else "$delta"
                                BasicText(
                                    text = if (isPct) "$dText%" else dText,
                                    style = TextStyle(color = Ink.TEXT_WARM, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Live Pace / Velocity Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val isPct = book.trackMode == BookTrackMode.PERCENTAGE
                        Column {
                            BasicText(if (isPct) "PROGRESS MADE" else "PAGES READ", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                            BasicText(if (isPct) "+$pagesRead%" else "+$pagesRead", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            BasicText("PACE VELOCITY", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                            val paceUnit = if (isPct) "min / 1%" else "min/page"
                            val paceStr = if (paceMinutes != null) String.format(Locale.US, "%.1f %s", paceMinutes, paceUnit) else "Measuring..."
                            BasicText(paceStr, style = TextStyle(color = Ink.BRASS, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Primary Control Buttons: [ PAUSE / RESUME ] and [ FINISH SESSION ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Pause / Resume Toggle
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ink.SURFACE_3)
                        .clickable {
                            if (isRunning) {
                                val nowRealtime = SystemClock.elapsedRealtime()
                                baseElapsedMillis += (nowRealtime - lastStartElapsedRealtime)
                                isRunning = false
                            } else {
                                lastStartElapsedRealtime = SystemClock.elapsedRealtime()
                                isRunning = true
                            }
                            Feedback.tick(context)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = if (isRunning) "PAUSE" else "RESUME",
                        style = TextStyle(color = Ink.TEXT_WARM, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                    )
                }

                // Finish Session
                Box(
                    modifier = Modifier
                        .weight(1.5f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ink.BRASS)
                        .clickable {
                            val nowRealtime = SystemClock.elapsedRealtime()
                            val finalElapsedSeconds = if (isRunning) {
                                (baseElapsedMillis + (nowRealtime - lastStartElapsedRealtime)) / 1000L
                            } else {
                                baseElapsedMillis / 1000L
                            }
                            val durationSec = finalElapsedSeconds.coerceAtLeast(1L)
                            val durationMin = (durationSec / 60L).toInt().coerceAtLeast(if (pagesRead > 0) 1 else 0)

                            if (pagesRead > 0 || durationSec >= 30L) {
                                val log = ReadingSessionLog(
                                    id = UUID.randomUUID().toString(),
                                    bookId = book.id,
                                    timestampMillis = startEpochMillis,
                                    startPage = initialPage,
                                    endPage = currentPage,
                                    pagesRead = pagesRead,
                                    durationMinutes = durationMin,
                                    durationSeconds = durationSec,
                                    manuallyLogged = false
                                )
                                onSaveSession(log)
                            }
                            val newStatus = if (currentPage >= book.totalPages && book.totalPages > 0) BookStatus.COMPLETED else book.status
                            val updatedBook = book.copy(currentPage = currentPage, status = newStatus)
                            onUpdateBook(updatedBook)
                            Feedback.success(context)
                            val unitText = if (book.trackMode == BookTrackMode.PERCENTAGE) "$pagesRead%" else "$pagesRead pages"
                            Toast.makeText(context, "Session saved: $unitText in ${durationMin}m", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = "FINISH SESSION",
                        style = TextStyle(color = Ink.GROUND, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            TextAction("DISCARD WITHOUT SAVING", Ink.VERMILION, onDismiss)
            Spacer(Modifier.height(10.dp))
        }
    }

    if (showJumpDialog) {
        NumericPageInputDialog(
            title = if (book.trackMode == BookTrackMode.PERCENTAGE) "ENTER PROGRESS %" else "ENTER PAGE NUMBER",
            initialPage = currentPage,
            maxPage = book.totalPages,
            isPercentage = book.trackMode == BookTrackMode.PERCENTAGE,
            onDismiss = { showJumpDialog = false },
            onConfirm = { targetPage ->
                currentPage = targetPage
                Feedback.success(context)
            }
        )
    }
}

// -----------------------------------------------------------------------------
// Book Edit Sheet (§3.3) - Metadata, Total Pages, and Device Cover Picker
// -----------------------------------------------------------------------------

@Composable
private fun BookEditSheet(
    book: BookItem,
    onDismiss: () -> Unit,
    onSave: (BookItem) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author) }
    var totalPagesStr by remember { mutableStateOf(book.totalPages.toString()) }
    var currentPageStr by remember { mutableStateOf(book.currentPage.toString()) }
    var trackMode by remember { mutableStateOf(book.trackMode) }
    var status by remember { mutableStateOf(book.status) }
    var categoriesStr by remember { mutableStateOf(book.categories.joinToString(", ")) }
    var customCoverPath by remember { mutableStateOf(book.customCoverPath) }
    var showOnlineCoverSearch by remember { mutableStateOf(false) }

    // Device gallery image picker
    val pickCoverLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (originalBitmap != null) {
                        val maxDim = 800
                        val scale = minOf(1f, maxDim.toFloat() / maxOf(originalBitmap.width, originalBitmap.height))
                        val finalBitmap = if (scale < 1f) {
                            Bitmap.createScaledBitmap(
                                originalBitmap,
                                (originalBitmap.width * scale).toInt(),
                                (originalBitmap.height * scale).toInt(),
                                true
                            )
                        } else originalBitmap

                        val coversDir = File(context.filesDir, "custom_covers").apply { mkdirs() }
                        val coverFile = File(coversDir, "cover_${book.id}_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(coverFile).use { out ->
                            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }

                        customCoverPath = coverFile.absolutePath
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    NdotSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            BasicText(
                text = "EDIT BOOK DETAILS",
                style = TextStyle(color = Ink.BRASS, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(14.dp))

            // Cover Preview & Picker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val previewBook = book.copy(
                    customCoverPath = customCoverPath,
                    title = title,
                    author = author
                )
                CoverImage(
                    book = previewBook,
                    cornerRadius = 8.dp,
                    modifier = Modifier.size(54.dp, 80.dp)
                )

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Ink.SURFACE_3)
                                .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(10.dp))
                                .clickable { pickCoverLauncher.launch("image/*") }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            BasicText(
                                text = "🖼 FROM DEVICE",
                                style = TextStyle(color = Ink.BRASS, fontSize = 10.5.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(androidx.compose.ui.graphics.Color(0xFF222733))
                                .border(1.dp, androidx.compose.ui.graphics.Color(0xFF3B4457), RoundedCornerShape(10.dp))
                                .clickable { showOnlineCoverSearch = true }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            BasicText(
                                text = "🌐 SEARCH ONLINE",
                                style = TextStyle(color = androidx.compose.ui.graphics.Color(0xFF8AB4F8), fontSize = 10.5.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    if (customCoverPath != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { customCoverPath = null }
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            BasicText("Reset to online cover", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 11.sp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Title
            BasicText("TITLE", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.SURFACE_3)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp, fontFamily = FontFamily.Default),
                    cursorBrush = SolidColor(Ink.BRASS),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(10.dp))

            // Author
            BasicText("AUTHOR", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.SURFACE_3)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                BasicTextField(
                    value = author,
                    onValueChange = { author = it },
                    textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp, fontFamily = FontFamily.Default),
                    cursorBrush = SolidColor(Ink.BRASS),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(10.dp))

            // Tracking Mode (Pages vs Percentage)
            BasicText("TRACKING MODE", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Ink.SURFACE_3)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    BookTrackMode.PAGES to "PAGES",
                    BookTrackMode.PERCENTAGE to "PERCENTAGE (%)"
                ).forEach { (mode, label) ->
                    val isSel = trackMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) Ink.BRASS else Color.Transparent)
                            .clickable {
                                if (trackMode != mode) {
                                    trackMode = mode
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = label,
                            style = TextStyle(
                                color = if (isSel) Ink.GROUND else Ink.TEXT_MUTED_V4,
                                fontSize = 11.sp,
                                fontFamily = Fonts.mono,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Pages Row (Total & Current)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    BasicText("CURRENT PAGE", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ink.SURFACE_3)
                            .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = currentPageStr,
                            onValueChange = { currentPageStr = it.filter { ch -> ch.isDigit() } },
                            textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp, fontFamily = Fonts.mono),
                            cursorBrush = SolidColor(Ink.BRASS),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    BasicText("TOTAL PAGES", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ink.SURFACE_3)
                            .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = totalPagesStr,
                            onValueChange = { totalPagesStr = it.filter { ch -> ch.isDigit() } },
                            readOnly = false,
                            textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp, fontFamily = Fonts.mono),
                            cursorBrush = SolidColor(Ink.BRASS),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Status Row
            BasicText("STATUS", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.SURFACE_3)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(
                    BookStatus.READING to "Reading",
                    BookStatus.COMPLETED to "Done"
                ).forEach { (st, label) ->
                    val isSel = status == st
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) Ink.BRASS else Color.Transparent)
                            .clickable { status = st }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = label,
                            maxLines = 1,
                            softWrap = false,
                            style = TextStyle(
                                color = if (isSel) Ink.GROUND else Ink.TEXT_MUTED_V4,
                                fontSize = 11.sp,
                                fontFamily = Fonts.mono,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Categories
            BasicText("CATEGORIES (COMMA-SEPARATED)", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.SURFACE_3)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                BasicTextField(
                    value = categoriesStr,
                    onValueChange = { categoriesStr = it },
                    textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp, fontFamily = FontFamily.Default),
                    cursorBrush = SolidColor(Ink.BRASS),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(20.dp))

            // Delete Action (quiet, separate row)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextAction("DELETE BOOK FROM SHELF", Ink.VERMILION, onDelete)
            }

            Spacer(Modifier.height(14.dp))

            // Primary Action Row: CANCEL + SAVE CHANGES
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink.SURFACE_3)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        "CANCEL",
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1.5f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink.BRASS)
                        .clickable {
                            val totalP = totalPagesStr.toIntOrNull()?.coerceAtLeast(1) ?: book.totalPages
                            val curP = currentPageStr.toIntOrNull()?.coerceIn(0, totalP) ?: book.currentPage
                            val cats = categoriesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            val updated = book.copy(
                                title = title.trim().ifEmpty { book.title },
                                author = author.trim().ifEmpty { book.author },
                                totalPages = totalP,
                                currentPage = curP,
                                status = status,
                                categories = if (cats.isNotEmpty()) cats else book.categories,
                                customCoverPath = customCoverPath,
                                trackMode = trackMode
                            )
                            onSave(updated)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        "SAVE CHANGES",
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(color = Ink.GROUND, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }

    if (showOnlineCoverSearch) {
        OnlineCoverPickerDialog(
            initialQuery = "${title.trim()} ${author.trim()}".trim(),
            bookId = book.id,
            onCoverSelected = { localPath, _ ->
                customCoverPath = localPath
                showOnlineCoverSearch = false
            },
            onDismiss = { showOnlineCoverSearch = false }
        )
    }
}

// -----------------------------------------------------------------------------
// Log Past Session Sheet (§3.6) - Offline / Paper Reading Entry
// -----------------------------------------------------------------------------

@Composable
private fun LogPastSessionSheet(
    book: BookItem,
    onDismiss: () -> Unit,
    onSave: (ReadingSessionLog) -> Unit
) {
    val context = LocalContext.current
    val isPct = book.trackMode == BookTrackMode.PERCENTAGE
    val initialStartVal = if (isPct) {
        if (book.totalPages > 0) ((book.currentPage.toFloat() / book.totalPages) * 100f).roundToInt().coerceIn(0, 100) else 0
    } else {
        book.currentPage
    }
    val initialEndVal = if (isPct) {
        (initialStartVal + 5).coerceAtMost(100)
    } else {
        (book.currentPage + 20).coerceAtMost(book.totalPages)
    }
    var startPageStr by remember { mutableStateOf(initialStartVal.toString()) }
    var endPageStr by remember { mutableStateOf(initialEndVal.toString()) }
    var durationMinutesStr by remember { mutableStateOf("30") }
    var selectedTimestampMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedQuickChip by remember { mutableIntStateOf(0) }

    val startNum = startPageStr.toIntOrNull() ?: initialStartVal
    val endNum = endPageStr.toIntOrNull() ?: startNum

    val startPage: Int
    val endPage: Int
    val pagesRead: Int
    val pctDelta: Int
    if (isPct) {
        val sPct = startNum.coerceIn(0, 100)
        val ePct = endNum.coerceIn(sPct, 100)
        startPage = if (book.totalPages > 0) (sPct / 100f * book.totalPages).roundToInt().coerceIn(0, book.totalPages) else sPct
        endPage = if (book.totalPages > 0) (ePct / 100f * book.totalPages).roundToInt().coerceIn(0, book.totalPages) else ePct
        pagesRead = (endPage - startPage).coerceAtLeast(0)
        pctDelta = (ePct - sPct).coerceAtLeast(0)
    } else {
        startPage = startNum.coerceIn(0, book.totalPages)
        endPage = endNum.coerceIn(startPage, book.totalPages)
        pagesRead = (endPage - startPage).coerceAtLeast(0)
        pctDelta = if (book.totalPages > 0) ((pagesRead.toFloat() / book.totalPages) * 100f).roundToInt() else 0
    }
    val durationMin = durationMinutesStr.toIntOrNull()?.coerceAtLeast(1) ?: 30

    NdotSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            BasicText(
                text = "LOG PAST READING SESSION",
                style = TextStyle(color = Ink.BRASS, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = book.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = Ink.TEXT_WARM, fontSize = 12.sp, fontFamily = FontFamily.Default)
            )

            Spacer(Modifier.height(14.dp))

            // Day Selector Chips (Part 5.1 Historical Date Picker)
            BasicText("WHEN DID YOU READ?", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val chips = listOf("Today", "Yesterday", "2 days ago", "3 days ago", "Pick Date 📅")
                chips.forEachIndexed { idx, label ->
                    val isSel = (selectedQuickChip == idx)
                    val chipText = if (idx == 4 && selectedQuickChip == 4) {
                        SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(selectedTimestampMs))
                    } else label
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) Ink.BRASS else Ink.SURFACE_3)
                            .clickable {
                                if (idx < 4) {
                                    selectedQuickChip = idx
                                    selectedTimestampMs = System.currentTimeMillis() - idx * 86_400_000L
                                } else {
                                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = selectedTimestampMs }
                                    android.app.DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            cal.set(year, month, dayOfMonth, 12, 0, 0)
                                            selectedTimestampMs = cal.timeInMillis
                                            selectedQuickChip = 4
                                        },
                                        cal.get(java.util.Calendar.YEAR),
                                        cal.get(java.util.Calendar.MONTH),
                                        cal.get(java.util.Calendar.DAY_OF_MONTH)
                                    ).apply {
                                        datePicker.maxDate = System.currentTimeMillis()
                                    }.show()
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        BasicText(
                            text = chipText,
                            style = TextStyle(
                                color = if (isSel) Ink.GROUND else Ink.TEXT_MUTED_V4,
                                fontSize = 11.sp,
                                fontFamily = Fonts.mono,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Page range
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val isPct = book.trackMode == BookTrackMode.PERCENTAGE
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(if (isPct) "START %" else "START PAGE", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ink.SURFACE_3)
                            .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = startPageStr,
                            onValueChange = { startPageStr = it.filter { ch -> ch.isDigit() } },
                            textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp, fontFamily = Fonts.mono),
                            cursorBrush = SolidColor(Ink.BRASS),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(if (isPct) "END %" else "END PAGE", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ink.SURFACE_3)
                            .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = endPageStr,
                            onValueChange = { endPageStr = it.filter { ch -> ch.isDigit() } },
                            textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp, fontFamily = Fonts.mono),
                            cursorBrush = SolidColor(Ink.BRASS),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Duration in minutes
            BasicText("DURATION (MINUTES)", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 10.sp, fontFamily = Fonts.mono))
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.SURFACE_3)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                BasicTextField(
                    value = durationMinutesStr,
                    onValueChange = { durationMinutesStr = it },
                    textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 14.sp, fontFamily = Fonts.mono),
                    cursorBrush = SolidColor(Ink.BRASS),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(14.dp))

            // Summary Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.SURFACE_2)
                    .padding(12.dp)
            ) {
                val isPct = book.trackMode == BookTrackMode.PERCENTAGE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val readText = if (isPct) "+$pctDelta% progress ($pagesRead pages read)" else "$pagesRead pages read"
                    BasicText(
                        text = readText,
                        style = TextStyle(color = Ink.TEXT_WARM, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                    )
                    val speed = if (durationMin > 0) ((pagesRead.toFloat() / durationMin) * 60f).roundToInt() else 0
                    val speedText = "$speed pgs/hr"
                    BasicText(
                        text = speedText,
                        style = TextStyle(color = Ink.BRASS, fontSize = 12.sp, fontFamily = Fonts.mono)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextAction("CANCEL", Ink.TEXT_MUTED_V4, onDismiss)
                Spacer(Modifier.width(14.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink.BRASS)
                        .clickable {
                            val epoch = if (selectedQuickChip == 4) selectedTimestampMs else (System.currentTimeMillis() - (selectedQuickChip * 86400000L))
                            val log = ReadingSessionLog(
                                id = UUID.randomUUID().toString(),
                                bookId = book.id,
                                timestampMillis = epoch,
                                startPage = startPage,
                                endPage = endPage,
                                pagesRead = pagesRead,
                                durationMinutes = durationMin,
                                durationSeconds = durationMin * 60L,
                                manuallyLogged = true
                            )
                            onSave(log)
                            Feedback.success(context)
                        }
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    BasicText("RECORD SESSION", style = TextStyle(color = Ink.GROUND, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Book Action Sheet (§3.4) - Primary Card Context Instrument
// -----------------------------------------------------------------------------

@Composable
private fun BookActionSheet(
    book: BookItem,
    onDismiss: () -> Unit,
    onStartFocus: () -> Unit,
    onOpenCaliper: () -> Unit,
    onOpenStatsStudio: () -> Unit,
    onEdit: () -> Unit,
    onLogPast: () -> Unit,
    onToggleStatus: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var currentBook by remember(book) { mutableStateOf(book) }
    var showJumpDialog by remember { mutableStateOf(false) }

    NdotSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverImage(
                    book = currentBook,
                    cornerRadius = 8.dp,
                    modifier = Modifier.size(48.dp, 72.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(
                        text = currentBook.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = Ink.TEXT_WARM, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Default)
                    )
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        text = "By ${currentBook.author}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 12.sp, fontFamily = FontFamily.Default)
                    )
                    Spacer(Modifier.height(4.dp))
                    val pct = if (currentBook.totalPages > 0) (currentBook.currentPage * 100 / currentBook.totalPages) else 0
                    val progressText = if (currentBook.trackMode == BookTrackMode.PERCENTAGE) "$pct%" else "${currentBook.currentPage} / ${currentBook.totalPages} · $pct%"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Ink.SURFACE_3.copy(alpha = 0.6f))
                            .clickable { showJumpDialog = true }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        DotMatrixText(
                            text = progressText,
                            dotPitch = 1.8.dp,
                            litColor = Ink.LED,
                            glow = false
                        )
                        Spacer(Modifier.width(4.dp))
                        BasicText("✎", style = TextStyle(color = Ink.BRASS, fontSize = 11.sp))
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Primary CTA: START FOCUS READING
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.BRASS)
                    .clickable { onStartFocus() },
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = "START FOCUS READING",
                    style = TextStyle(color = Ink.GROUND, fontSize = 13.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                )
            }

            Spacer(Modifier.height(8.dp))

            ContextActionRow("Jump to page / Update progress") { showJumpDialog = true }
            ContextActionRow("Open in Counter") { onOpenCaliper() }
            ContextActionRow("Share Book Report") { onOpenStatsStudio() }
            ContextActionRow("Edit book details & cover") { onEdit() }
            ContextActionRow("Log past reading session") { onLogPast() }
            ContextActionRow(if (currentBook.status == BookStatus.COMPLETED) "Mark as in-progress" else "Mark as completed") { onToggleStatus() }
            ContextActionRow("Delete book from shelf", textColor = Ink.VERMILION) { onDelete() }
        }
    }

    if (showJumpDialog) {
        NumericPageInputDialog(
            title = if (currentBook.trackMode == BookTrackMode.PERCENTAGE) "JUMP TO PERCENTAGE" else "JUMP TO PAGE",
            initialPage = currentBook.currentPage,
            maxPage = currentBook.totalPages,
            isPercentage = currentBook.trackMode == BookTrackMode.PERCENTAGE,
            onDismiss = { showJumpDialog = false },
            onConfirm = { targetPage ->
                val isComplete = (currentBook.totalPages > 0 && targetPage >= currentBook.totalPages) ||
                        (currentBook.trackMode == BookTrackMode.PERCENTAGE && targetPage >= 100)
                val newStatus = if (isComplete) BookStatus.COMPLETED else BookStatus.READING
                val updated = currentBook.copy(
                    currentPage = targetPage,
                    status = newStatus
                )
                currentBook = updated
                BookStore.upsertBook(context, updated)
                Feedback.success(context)
            }
        )
    }
}

@Composable
private fun NumericPageInputDialog(
    title: String,
    initialPage: Int,
    maxPage: Int,
    isPercentage: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var textVal by remember { mutableStateOf(initialPage.toString()) }
    val maxLimit = if (isPercentage) 100 else (if (maxPage > 0) maxPage else 99999)

    NdotSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = title,
                    style = TextStyle(color = Ink.BRASS, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Ink.SURFACE_3)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("×", style = TextStyle(color = Ink.TEXT_WARM, fontSize = 18.sp))
                }
            }

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.SURFACE_3)
                    .border(1.dp, Ink.HAIRLINE_V4, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                BasicTextField(
                    value = textVal,
                    onValueChange = { newVal ->
                        textVal = newVal.filter { it.isDigit() }.take(5)
                    },
                    textStyle = TextStyle(color = Ink.TEXT_WARM, fontSize = 20.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold),
                    cursorBrush = SolidColor(Ink.BRASS),
                    decorationBox = { inner ->
                        if (textVal.isEmpty()) {
                            BasicText(if (isPercentage) "0-100%" else "0-${maxLimit}", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 18.sp))
                        }
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Ink.SURFACE_2)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("CANCEL", style = TextStyle(color = Ink.TEXT_MUTED_V4, fontSize = 12.sp, fontFamily = Fonts.mono))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Ink.BRASS)
                        .clickable {
                            val parsed = textVal.toIntOrNull() ?: initialPage
                            val clamped = parsed.coerceIn(0, maxLimit)
                            onConfirm(clamped)
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText("SAVE", style = TextStyle(color = Ink.GROUND, fontSize = 12.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

