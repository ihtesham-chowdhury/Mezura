package rocks.ndot.widgets.instruments.habits

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class CityTitle(val label: String, val minBuildings: Int) {
    CAMP("Camp", 0),
    HAMLET("Hamlet", 5),
    VILLAGE("Village", 15),
    TOWN("Town", 30),
    CITY("City", 50),
    METROPOLIS("Metropolis", 80);

    companion object {
        fun forCount(buildings: Int): CityTitle {
            return entries.lastOrNull { buildings >= it.minBuildings } ?: CAMP
        }
    }
}

enum class BuildingArchetype {
    COTTAGE,
    SHOP,
    APARTMENT,
    OFFICE_TOWER,
    PARK,
    FOUNTAIN_PLAZA,
    FERRIS_WHEEL,
    MONUMENT
}

enum class SlotKind {
    BUILDING,
    DREAM_TOWER,
    ACHIEVEMENT_LANDMARK
}

data class CitySlot(
    val index: Int,
    val gx: Int,
    val gy: Int,
    val archetype: BuildingArchetype,
    val colorHex: Long,
    val floors: Int = 1,
    val name: String = "Building",
    val slotKind: SlotKind = SlotKind.BUILDING,
    val landmarkStyle: LandmarkStyle? = null,
    val litFloors: Int = 0,
    val isComplete: Boolean = false,
    val isGilded: Boolean = false,
    val refId: String? = null,
    val subtitle: String? = null,
    val dateBuiltStr: String? = null
)

data class LandmarkRecord(
    val kind: String, // "DREAM" or "ACHIEVEMENT"
    val refId: String,
    val plotIndex: Int,
    val style: LandmarkStyle,
    val createdEpochDay: Long,
    val isGilded: Boolean = false
)

data class BuildLogEntry(
    val index: Int,
    val epochDay: Long,
    val gilded: Boolean = false
)

object CityModel {

    private const val PREFS = "habit_city"
    private const val KEY_BUILDINGS_BUILT = "storedBuildingsBuilt"
    private const val KEY_BUILD_LOG = "buildLog"
    private const val KEY_LANDMARKS = "landmarks"

    fun costOfBuilding(n: Int): Int = 20 + 6 * n

    fun buildingsUnlocked(lifetimeBricks: Int): Int {
        var k = 0
        var totalCost = 0
        while (true) {
            val nextCost = costOfBuilding(k)
            if (totalCost + nextCost <= lifetimeBricks) {
                totalCost += nextCost
                k++
            } else {
                break
            }
        }
        return k
    }

    fun bricksToNextBuilding(lifetimeBricks: Int): Pair<Int, Int> {
        val currentK = buildingsUnlocked(lifetimeBricks)
        var spentBefore = 0
        for (i in 0 until currentK) {
            spentBefore += costOfBuilding(i)
        }
        val nextCost = costOfBuilding(currentK)
        val progress = (lifetimeBricks - spentBefore).coerceIn(0, nextCost)
        return Pair(progress, nextCost)
    }

    fun calculateLifetimeBricks(habits: List<Habit>): Int {
        if (habits.isEmpty()) return 0

        val allDays = habits.flatMap { it.done }.distinct().sorted()
        var totalBricks = 0

        for (day in allDays) {
            val doneToday = habits.filter { it.isDone(day) }
            for (h in doneToday) {
                var s = 0
                var d = day
                while (h.isDone(d)) {
                    s++
                    d--
                }
                totalBricks += 10 + min(s, 10)
            }
            if (doneToday.size == habits.size && habits.isNotEmpty()) {
                totalBricks += 15 // Perfect-day bonus
            }
        }
        return totalBricks
    }

    fun todayEarnedBricks(habits: List<Habit>): Int {
        val today = LocalDate.now().toEpochDay()
        val doneToday = habits.filter { it.isDone(today) }
        var earned = 0
        for (h in doneToday) {
            earned += 10 + min(h.currentStreak(today), 10)
        }
        if (doneToday.size == habits.size && habits.isNotEmpty()) {
            earned += 15
        }
        return earned
    }

    fun getStoredBuildingsBuilt(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_BUILDINGS_BUILT, 0)
    }

    fun updateAndGetBuildingsBuilt(context: Context, habits: List<Habit>): Int {
        val lifetime = calculateLifetimeBricks(habits)
        val unlocked = buildingsUnlocked(lifetime)
        val stored = getStoredBuildingsBuilt(context)
        val built = max(stored, unlocked)
        if (built > stored) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_BUILDINGS_BUILT, built)
                .apply()
        }
        return built
    }

    // -------------------------------------------------------------------------
    // PERSISTED LANDMARKS & BUILD LOG (§6)
    // -------------------------------------------------------------------------

    fun getLandmarks(context: Context): List<LandmarkRecord> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LANDMARKS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<LandmarkRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val styleStr = obj.optString("style", LandmarkStyle.TOWER.name)
                val style = try { LandmarkStyle.valueOf(styleStr) } catch (_: Exception) { LandmarkStyle.TOWER }
                list.add(
                    LandmarkRecord(
                        kind = obj.optString("kind", "DREAM"),
                        refId = obj.optString("refId", ""),
                        plotIndex = obj.optInt("plotIndex", -1),
                        style = style,
                        createdEpochDay = obj.optLong("createdEpochDay", 0L),
                        isGilded = obj.optBoolean("isGilded", false)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveLandmarks(context: Context, landmarks: List<LandmarkRecord>) {
        val arr = JSONArray()
        for (l in landmarks) {
            val obj = JSONObject().apply {
                put("kind", l.kind)
                put("refId", l.refId)
                put("plotIndex", l.plotIndex)
                put("style", l.style.name)
                put("createdEpochDay", l.createdEpochDay)
                put("isGilded", l.isGilded)
            }
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANDMARKS, arr.toString())
            .apply()
    }

    fun getBuildLog(context: Context): List<BuildLogEntry> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_BUILD_LOG, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<BuildLogEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    BuildLogEntry(
                        index = obj.optInt("index", -1),
                        epochDay = obj.optLong("epochDay", 0L),
                        gilded = obj.optBoolean("gilded", false)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveBuildLog(context: Context, log: List<BuildLogEntry>) {
        val arr = JSONArray()
        for (entry in log) {
            val obj = JSONObject().apply {
                put("index", entry.index)
                put("epochDay", entry.epochDay)
                put("gilded", entry.gilded)
            }
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BUILD_LOG, arr.toString())
            .apply()
    }

    fun recordBuildingUnlock(context: Context, index: Int, epochDay: Long, isGoldDay: Boolean) {
        val log = getBuildLog(context).toMutableList()
        if (log.none { it.index == index }) {
            log.add(BuildLogEntry(index = index, epochDay = epochDay, gilded = isGoldDay))
            saveBuildLog(context, log)
        }
    }

    fun updateAndGetBuildingsBuiltWithLog(
        context: Context,
        lifetimeBricks: Int,
        todayEpochDay: Long,
        isGoldDay: Boolean
    ): Int {
        val unlocked = buildingsUnlocked(lifetimeBricks)
        val stored = getStoredBuildingsBuilt(context)
        val built = max(stored, unlocked)
        if (built > stored) {
            for (i in stored until built) {
                recordBuildingUnlock(context, i, todayEpochDay, isGoldDay)
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_BUILDINGS_BUILT, built)
                .apply()
        }
        return built
    }

    // -------------------------------------------------------------------------
    // COMPOSED SLOTS (Building base + Dream Towers + Achievement Landmarks)
    // -------------------------------------------------------------------------

    fun getComposedSlots(
        context: Context,
        buildingsCount: Int,
        dreams: List<Dream>,
        goals: List<Goal>,
        achievements: List<Achievement>
    ): List<CitySlot> {
        val existingLandmarks = getLandmarks(context).toMutableList()
        val buildLog = getBuildLog(context).associateBy { it.index }
        var landmarksChanged = false

        // 1. Assign or preserve Dream District plots (plots 1..8 reserved)
        val reservedDreamPlots = (1..8).toList()
        for (dream in dreams) {
            if (existingLandmarks.none { it.kind == "DREAM" && it.refId == dream.id }) {
                // Find next free plot in reservedDreamPlots
                val usedPlots = existingLandmarks.map { it.plotIndex }.toSet()
                var chosenPlot = reservedDreamPlots.firstOrNull { it !in usedPlots && it >= buildingsCount }
                if (chosenPlot == null) {
                    chosenPlot = reservedDreamPlots.firstOrNull { it !in usedPlots }
                }
                if (chosenPlot == null) {
                    // Find nearest free plot >= 1
                    chosenPlot = (1 until 60).firstOrNull { it !in usedPlots } ?: 1
                }
                existingLandmarks.add(
                    LandmarkRecord(
                        kind = "DREAM",
                        refId = dream.id,
                        plotIndex = chosenPlot,
                        style = dream.landmark,
                        createdEpochDay = LocalDate.now().toEpochDay(),
                        isGilded = dream.status == DreamStatus.ACHIEVED
                    )
                )
                landmarksChanged = true
            }
        }

        // 2. Assign or preserve Achievement Ring plots (outer ring 36..59 reserved, up to 24)
        val reservedAchPlots = (36 until min(60, 36 + 24)).toList()
        for (ach in achievements) {
            if (existingLandmarks.none { it.kind == "ACHIEVEMENT" && it.refId == ach.id }) {
                val usedPlots = existingLandmarks.map { it.plotIndex }.toSet()
                val freePlot = reservedAchPlots.firstOrNull { it !in usedPlots }
                    ?: (1 until 60).firstOrNull { it !in usedPlots }
                if (freePlot != null) {
                    val matchingGoal = goals.firstOrNull { it.id == ach.refId || it.name.equals(ach.title, ignoreCase = true) }
                    val suggestedStyle = when {
                        matchingGoal?.glyphKey == "course" -> LandmarkStyle.ACADEMY
                        matchingGoal?.glyphKey == "book" -> LandmarkStyle.LIBRARY
                        matchingGoal?.glyphKey == "code" || matchingGoal?.glyphKey == "flask" -> LandmarkStyle.OBSERVATORY
                        matchingGoal?.glyphKey == "pen" -> LandmarkStyle.ATELIER
                        matchingGoal?.glyphKey == "dome" -> LandmarkStyle.DOMED_HALL
                        else -> LandmarkStyle.TOWER
                    }
                    val isGilded = ach.deltaDays != null && ach.deltaDays > 0
                    existingLandmarks.add(
                        LandmarkRecord(
                            kind = "ACHIEVEMENT",
                            refId = ach.id,
                            plotIndex = freePlot,
                            style = suggestedStyle,
                            createdEpochDay = ach.epochDay,
                            isGilded = isGilded
                        )
                    )
                    landmarksChanged = true
                }
            }
        }

        if (landmarksChanged) {
            saveLandmarks(context, existingLandmarks)
        }

        // Map landmarks by plotIndex
        val landmarkByPlot = existingLandmarks.associateBy { it.plotIndex }

        // Compose active slots
        val baseSlots = MASTER_PLAN
        val resultSlots = mutableListOf<CitySlot>()

        for (idx in 0 until max(buildingsCount, 1)) {
            if (idx >= baseSlots.size) break
            val base = baseSlots[idx]
            val landmark = landmarkByPlot[idx]

            if (landmark != null && landmark.kind == "DREAM") {
                val d = dreams.firstOrNull { it.id == landmark.refId }
                val n = d?.rungs?.size ?: 3
                val floors = n.coerceIn(3, 10)
                val achievedRungs = d?.rungs?.count { it.isDone } ?: 0
                val litFloors = if (n > 0) {
                    (achievedRungs.toDouble() / n * floors).roundToInt().coerceAtLeast(if (achievedRungs > 0) 1 else 0)
                } else 0
                val isComplete = (d?.status == DreamStatus.ACHIEVED) || (n > 0 && achievedRungs == n)

                resultSlots.add(
                    base.copy(
                        name = d?.name ?: "Dream Tower",
                        slotKind = SlotKind.DREAM_TOWER,
                        landmarkStyle = landmark.style,
                        floors = floors,
                        litFloors = litFloors,
                        isComplete = isComplete,
                        isGilded = isComplete || landmark.isGilded,
                        refId = d?.id,
                        subtitle = "$achievedRungs OF $n RUNGS"
                    )
                )
            } else if (landmark != null && landmark.kind == "ACHIEVEMENT") {
                val ach = achievements.firstOrNull { it.id == landmark.refId }
                val matchingGoal = goals.firstOrNull { it.id == ach?.refId || it.name.equals(ach?.title, ignoreCase = true) }
                val floors = (matchingGoal?.milestones?.size ?: 3).coerceIn(1, 3)
                val isGilded = landmark.isGilded || (ach?.deltaDays != null && ach.deltaDays > 0)
                val subtitle = if (ach?.deltaDays != null && ach.deltaDays > 0) {
                    "${ach.deltaDays} days early"
                } else {
                    "Achieved"
                }

                resultSlots.add(
                    base.copy(
                        name = ach?.title ?: "Achievement",
                        slotKind = SlotKind.ACHIEVEMENT_LANDMARK,
                        landmarkStyle = landmark.style,
                        floors = floors,
                        litFloors = floors,
                        isComplete = true,
                        isGilded = isGilded,
                        refId = ach?.id,
                        subtitle = subtitle
                    )
                )
            } else {
                val logEntry = buildLog[idx]
                val isGilded = logEntry?.gilded ?: false
                val dateStr = logEntry?.let { LocalDate.ofEpochDay(it.epochDay).toString() }
                resultSlots.add(
                    base.copy(
                        slotKind = SlotKind.BUILDING,
                        isGilded = isGilded,
                        dateBuiltStr = dateStr
                    )
                )
            }
        }

        // Also add any landmarks that are situated on plots >= buildingsCount so user can always see their dream towers
        for (l in existingLandmarks) {
            val idx = l.plotIndex
            if (idx in buildingsCount until baseSlots.size && resultSlots.none { it.index == idx }) {
                val base = baseSlots[idx]
                if (l.kind == "DREAM") {
                    val d = dreams.firstOrNull { it.id == l.refId }
                    val n = d?.rungs?.size ?: 3
                    val floors = n.coerceIn(3, 10)
                    val achievedRungs = d?.rungs?.count { it.isDone } ?: 0
                    val litFloors = if (n > 0) {
                        (achievedRungs.toDouble() / n * floors).roundToInt().coerceAtLeast(if (achievedRungs > 0) 1 else 0)
                    } else 0
                    val isComplete = (d?.status == DreamStatus.ACHIEVED) || (n > 0 && achievedRungs == n)

                    resultSlots.add(
                        base.copy(
                            name = d?.name ?: "Dream Tower",
                            slotKind = SlotKind.DREAM_TOWER,
                            landmarkStyle = l.style,
                            floors = floors,
                            litFloors = litFloors,
                            isComplete = isComplete,
                            isGilded = isComplete || l.isGilded,
                            refId = d?.id,
                            subtitle = "$achievedRungs OF $n RUNGS"
                        )
                    )
                } else if (l.kind == "ACHIEVEMENT") {
                    val ach = achievements.firstOrNull { it.id == l.refId }
                    val matchingGoal = goals.firstOrNull { it.id == ach?.refId || it.name.equals(ach?.title, ignoreCase = true) }
                    val floors = (matchingGoal?.milestones?.size ?: 3).coerceIn(1, 3)
                    val isGilded = l.isGilded || (ach?.deltaDays != null && ach.deltaDays > 0)
                    val subtitle = if (ach?.deltaDays != null && ach.deltaDays > 0) {
                        "${ach.deltaDays} days early"
                    } else {
                        "Achieved"
                    }

                    resultSlots.add(
                        base.copy(
                            name = ach?.title ?: "Achievement",
                            slotKind = SlotKind.ACHIEVEMENT_LANDMARK,
                            landmarkStyle = l.style,
                            floors = floors,
                            litFloors = floors,
                            isComplete = true,
                            isGilded = isGilded,
                            refId = ach?.id,
                            subtitle = subtitle
                        )
                    )
                }
            }
        }

        return resultSlots
    }

    /**
     * Calculates the number of Gold days in the current year (where overall >= 1.25)
     */
    fun countGoldDaysThisYear(
        habits: List<HabitItem>,
        entries: List<Entry>,
        year: Int,
        autoQuantities: (String, Long) -> Double = { _, _ -> 0.0 }
    ): Int {
        val startEpoch = LocalDate.of(year, 1, 1).toEpochDay()
        val endEpoch = LocalDate.of(year, 12, 31).toEpochDay()
        val daysWithActivity = entries.filter { it.epochDay in startEpoch..endEpoch }.map { it.epochDay }.distinct()

        return daysWithActivity.count { d ->
            val dow = LocalDate.ofEpochDay(d).dayOfWeek.value
            val scheduled = habits.filter { it.period == Period.DAY && dow in it.daysOfWeek }
            if (scheduled.isEmpty()) false
            else {
                var totalW = 0.0
                var creditW = 0.0
                scheduled.forEach { h ->
                    val w = h.weight.factor
                    totalW += w
                    val qManual = entries.filter { it.habitId == h.id && it.epochDay == d && it.kind == EntryKind.LOG }.sumOf { it.quantity }
                    val q = qManual + autoQuantities(h.id, d)
                    val r = if (h.target > 0.0) q / h.target else 0.0
                    creditW += w * ScoreEngine.credit(r)
                }
                val overall = if (totalW > 0.0) creditW / totalW else 0.0
                overall >= 1.25
            }
        }
    }

    // 60-slot Master Plan, ordered outward from central plaza
    val MASTER_PLAN: List<CitySlot> by lazy {
        val colors = listOf(
            0xFFC9694A, // brick
            0xFFEFE3C8, // cream
            0xFF6FA8E8, // glass blue
            0xFF8FBF8F, // sage
            0xFF7E8794, // slate
            0xFFD9B77E, // sand
            0xFFF4F1EA  // white
        )

        val names = listOf(
            "Town Plaza", "Cozy Cottage", "Central Park", "Corner Bakery", "Coffee Roastery",
            "Bookshop Cafe", "Clock Tower", "Fountain Square", "Flower Market", "Tea House",
            "Artist Studio", "General Store", "Row House", "Town Hall", "Boutique Hotel",
            "Sunlit Terrace", "Observatory", "Harbor Office", "Glass Tower", "Ferris Wheel",
            "Monument Hill", "Garden Pavilion", "Loft Apartments", "City Library", "Grand Theater"
        )

        // Concentric diamond traversal outward from (5,5)
        val slots = mutableListOf<CitySlot>()
        val visited = mutableSetOf<Pair<Int, Int>>()

        // Roads cross at row 4/5, col 4/5
        val roadTiles = setOf(
            Pair(4, 0), Pair(4, 1), Pair(4, 2), Pair(4, 3), Pair(4, 4), Pair(4, 5), Pair(4, 6), Pair(4, 7), Pair(4, 8), Pair(4, 9),
            Pair(5, 0), Pair(5, 1), Pair(5, 2), Pair(5, 3), Pair(5, 4), Pair(5, 5), Pair(5, 6), Pair(5, 7), Pair(5, 8), Pair(5, 9),
            Pair(0, 4), Pair(1, 4), Pair(2, 4), Pair(3, 4), Pair(6, 4), Pair(7, 4), Pair(8, 4), Pair(9, 4),
            Pair(0, 5), Pair(1, 5), Pair(2, 5), Pair(3, 5), Pair(6, 5), Pair(7, 5), Pair(8, 5), Pair(9, 5)
        )

        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(Pair(3, 3))
        visited.add(Pair(3, 3))

        val deltas = listOf(Pair(0, 1), Pair(1, 0), Pair(0, -1), Pair(-1, 0), Pair(1, 1), Pair(-1, -1), Pair(1, -1), Pair(-1, 1))

        var idx = 0
        while (queue.isNotEmpty() && idx < 60) {
            val curr = queue.removeFirst()
            val (gx, gy) = curr

            // Skip roads for building slots, except slot 0 is plaza
            if (idx == 0 || !roadTiles.contains(curr)) {
                val seed = abs((gx * 31 + gy * 17 + idx).hashCode())
                val archetype = when {
                    idx == 0 -> BuildingArchetype.FOUNTAIN_PLAZA
                    idx == 1 -> BuildingArchetype.COTTAGE
                    idx == 2 -> BuildingArchetype.PARK
                    idx < 8 -> if (seed % 2 == 0) BuildingArchetype.COTTAGE else BuildingArchetype.SHOP
                    idx < 25 -> when (seed % 3) {
                        0 -> BuildingArchetype.APARTMENT
                        1 -> BuildingArchetype.SHOP
                        else -> BuildingArchetype.PARK
                    }
                    idx < 45 -> when (seed % 3) {
                        0 -> BuildingArchetype.OFFICE_TOWER
                        1 -> BuildingArchetype.APARTMENT
                        else -> BuildingArchetype.SHOP
                    }
                    idx == 45 -> BuildingArchetype.FERRIS_WHEEL
                    idx == 46 -> BuildingArchetype.MONUMENT
                    else -> when (seed % 4) {
                        0 -> BuildingArchetype.OFFICE_TOWER
                        1 -> BuildingArchetype.APARTMENT
                        2 -> BuildingArchetype.MONUMENT
                        else -> BuildingArchetype.PARK
                    }
                }

                val floors = when (archetype) {
                    BuildingArchetype.OFFICE_TOWER -> 8 + (seed % 6)
                    BuildingArchetype.APARTMENT -> 3 + (seed % 3)
                    BuildingArchetype.SHOP -> 2
                    BuildingArchetype.COTTAGE -> 1
                    else -> 1
                }

                val col = colors[seed % colors.size]
                val bName = names[idx % names.size]

                slots.add(CitySlot(idx, gx, gy, archetype, col, floors, bName))
                idx++
            }

            for ((dx, dy) in deltas) {
                val nx = gx + dx
                val ny = gy + dy
                if (nx in 0..9 && ny in 0..9 && visited.add(Pair(nx, ny))) {
                    queue.add(Pair(nx, ny))
                }
            }
        }
        slots
    }
}
