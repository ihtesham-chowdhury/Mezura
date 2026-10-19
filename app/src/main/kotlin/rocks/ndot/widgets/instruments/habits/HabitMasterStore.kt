package rocks.ndot.widgets.instruments.habits

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlin.math.max

/**
 * HabitMasterStore is the unified repository for Habits, Entries, Goals,
 * Dreams, and Achievements.
 *
 * Implements Section 2.7 Lossless Migration:
 * Migrates existing legacy HabitStore habits and completions losslessly into
 * quantity-aware HabitItems and Entries.
 */
object HabitMasterStore {

    private const val PREFS = "habit_master_prefs"
    private const val KEY_MIGRATED = "migrated_to_v2"
    private const val FILE_NAME = "habit_master_data.json"

    // In-memory cache
    private var cachedHabits: MutableList<HabitItem>? = null
    private var cachedEntries: MutableList<Entry>? = null
    private var cachedGoals: MutableList<Goal>? = null
    private var cachedProgress: MutableList<GoalProgress>? = null
    private var cachedReadings: MutableList<MetricReading>? = null
    private var cachedDreams: MutableList<Dream>? = null
    private var cachedAchievements: MutableList<Achievement>? = null
    private var cachedDayNotes: MutableMap<Long, String>? = null

    fun today(): Long = LocalDate.now().toEpochDay()

    private fun getStorageFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (cachedHabits != null) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val file = getStorageFile(context)

        if (!file.exists()) {
            // First run or need migration
            cachedHabits = mutableListOf()
            cachedEntries = mutableListOf()
            cachedGoals = mutableListOf()
            cachedProgress = mutableListOf()
            cachedReadings = mutableListOf()
            cachedDreams = mutableListOf()
            cachedAchievements = mutableListOf()
            cachedDayNotes = mutableMapOf()

            val alreadyMigrated = prefs.getBoolean(KEY_MIGRATED, false)
            if (!alreadyMigrated) {
                migrateFromLegacyHabitStore(context)
                prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
                save(context)
            }
            return
        }

        try {
            val content = file.readText()
            val root = JSONObject(content)

            // 1. Habits
            val habitsArr = root.optJSONArray("habits") ?: JSONArray()
            val habits = mutableListOf<HabitItem>()
            for (i in 0 until habitsArr.length()) {
                val o = habitsArr.getJSONObject(i)
                val daysArr = o.optJSONArray("daysOfWeek")
                val daysSet = if (daysArr != null) {
                    (0 until daysArr.length()).map { daysArr.getInt(it) }.toSet()
                } else setOf(1, 2, 3, 4, 5, 6, 7)

                habits.add(
                    HabitItem(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        glyphKey = o.optString("glyphKey", "book"),
                        measure = runCatching { Measure.valueOf(o.optString("measure", "DONE")) }.getOrDefault(Measure.DONE),
                        unit = o.optString("unit", "pages"),
                        target = o.optDouble("target", 1.0),
                        minimum = if (o.has("minimum") && !o.isNull("minimum")) o.getDouble("minimum") else null,
                        stretch = if (o.has("stretch") && !o.isNull("stretch")) o.getDouble("stretch") else null,
                        period = runCatching { Period.valueOf(o.optString("period", "DAY")) }.getOrDefault(Period.DAY),
                        daysOfWeek = daysSet,
                        weight = runCatching { Weight.valueOf(o.optString("weight", "STANDARD")) }.getOrDefault(Weight.STANDARD),
                        autoSource = runCatching { AutoSource.valueOf(o.optString("autoSource", "NONE")) }.getOrDefault(AutoSource.NONE),
                        goalId = o.optString("goalId").takeIf { it.isNotBlank() },
                        dreamId = o.optString("dreamId").takeIf { it.isNotBlank() },
                        contributionRatio = o.optDouble("contributionRatio", 1.0),
                        quickSteps = o.optJSONArray("quickSteps")?.let { qa -> (0 until qa.length()).map { qa.getDouble(it) } } ?: emptyList(),
                        targetHistory = o.optJSONArray("targetHistory")?.let { tha ->
                            (0 until tha.length()).map { idx ->
                                val th = tha.getJSONObject(idx)
                                TargetChange(
                                    effectiveFromEpochDay = th.getLong("effectiveFromEpochDay"),
                                    target = th.getDouble("target"),
                                    minimum = if (th.has("minimum") && !th.isNull("minimum")) th.getDouble("minimum") else null,
                                    stretch = if (th.has("stretch") && !th.isNull("stretch")) th.getDouble("stretch") else null
                                )
                            }
                        } ?: emptyList(),
                        startEpochDay = o.optLong("startEpochDay", today()),
                        archivedEpochDay = if (o.has("archivedEpochDay") && !o.isNull("archivedEpochDay")) o.getLong("archivedEpochDay") else null
                    )
                )
            }
            cachedHabits = habits

            // 2. Entries
            val entriesArr = root.optJSONArray("entries") ?: JSONArray()
            val entries = mutableListOf<Entry>()
            for (i in 0 until entriesArr.length()) {
                val o = entriesArr.getJSONObject(i)
                entries.add(
                    Entry(
                        id = o.getString("id"),
                        habitId = o.getString("habitId"),
                        epochDay = o.getLong("epochDay"),
                        quantity = o.optDouble("quantity", 1.0),
                        kind = runCatching { EntryKind.valueOf(o.optString("kind", "LOG")) }.getOrDefault(EntryKind.LOG),
                        note = o.optString("note").takeIf { it.isNotBlank() },
                        createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis())
                    )
                )
            }
            cachedEntries = entries

            // 3. Goals
            val goalsArr = root.optJSONArray("goals") ?: JSONArray()
            val goals = mutableListOf<Goal>()
            for (i in 0 until goalsArr.length()) {
                val o = goalsArr.getJSONObject(i)
                val mArr = o.optJSONArray("milestones") ?: JSONArray()
                val milestones = mutableListOf<Milestone>()
                for (j in 0 until mArr.length()) {
                    val mo = mArr.getJSONObject(j)
                    milestones.add(
                        Milestone(
                            id = mo.getString("id"),
                            title = mo.getString("title"),
                            kind = runCatching { MilestoneKind.valueOf(mo.optString("kind", "TICK")) }.getOrDefault(MilestoneKind.TICK),
                            threshold = if (mo.has("threshold") && !mo.isNull("threshold")) mo.getDouble("threshold") else null,
                            goalId = mo.optString("goalId").takeIf { it.isNotBlank() },
                            doneEpochDay = if (mo.has("doneEpochDay") && !mo.isNull("doneEpochDay")) mo.getLong("doneEpochDay") else null
                        )
                    )
                }

                goals.add(
                    Goal(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        why = o.optString("why").takeIf { it.isNotBlank() },
                        glyphKey = o.optString("glyphKey", "code"),
                        finish = runCatching { FinishLine.valueOf(o.optString("finish", "CHECKLIST")) }.getOrDefault(FinishLine.CHECKLIST),
                        countTotal = if (o.has("countTotal") && !o.isNull("countTotal")) o.getDouble("countTotal") else null,
                        countUnit = o.optString("countUnit").takeIf { it.isNotBlank() },
                        metricBaseline = if (o.has("metricBaseline") && !o.isNull("metricBaseline")) o.getDouble("metricBaseline") else null,
                        metricTarget = if (o.has("metricTarget") && !o.isNull("metricTarget")) o.getDouble("metricTarget") else null,
                        metricUnit = o.optString("metricUnit").takeIf { it.isNotBlank() },
                        metricHigherIsBetter = o.optBoolean("metricHigherIsBetter", true),
                        startEpochDay = o.optLong("startEpochDay", today()),
                        targetEpochDay = if (o.has("targetEpochDay") && !o.isNull("targetEpochDay")) o.getLong("targetEpochDay") else null,
                        dreamId = o.optString("dreamId").takeIf { it.isNotBlank() },
                        milestones = milestones,
                        status = runCatching { GoalStatus.valueOf(o.optString("status", "ACTIVE")) }.getOrDefault(GoalStatus.ACTIVE),
                        achievedEpochDay = if (o.has("achievedEpochDay") && !o.isNull("achievedEpochDay")) o.getLong("achievedEpochDay") else null
                    )
                )
            }
            cachedGoals = goals

            // 4. Progress & Readings
            val progArr = root.optJSONArray("progress") ?: JSONArray()
            val progress = mutableListOf<GoalProgress>()
            for (i in 0 until progArr.length()) {
                val o = progArr.getJSONObject(i)
                progress.add(
                    GoalProgress(
                        id = o.getString("id"),
                        goalId = o.getString("goalId"),
                        epochDay = o.getLong("epochDay"),
                        delta = o.getDouble("delta"),
                        note = o.optString("note").takeIf { it.isNotBlank() }
                    )
                )
            }
            cachedProgress = progress

            val readingsArr = root.optJSONArray("readings") ?: JSONArray()
            val readings = mutableListOf<MetricReading>()
            for (i in 0 until readingsArr.length()) {
                val o = readingsArr.getJSONObject(i)
                readings.add(
                    MetricReading(
                        id = o.getString("id"),
                        goalId = o.getString("goalId"),
                        epochDay = o.getLong("epochDay"),
                        value = o.getDouble("value"),
                        note = o.optString("note").takeIf { it.isNotBlank() }
                    )
                )
            }
            cachedReadings = readings

            // 5. Dreams
            val dreamsArr = root.optJSONArray("dreams") ?: JSONArray()
            val dreams = mutableListOf<Dream>()
            for (i in 0 until dreamsArr.length()) {
                val o = dreamsArr.getJSONObject(i)
                val rArr = o.optJSONArray("rungs") ?: JSONArray()
                val rungs = mutableListOf<Milestone>()
                for (j in 0 until rArr.length()) {
                    val ro = rArr.getJSONObject(j)
                    rungs.add(
                        Milestone(
                            id = ro.getString("id"),
                            title = ro.getString("title"),
                            kind = runCatching { MilestoneKind.valueOf(ro.optString("kind", "TICK")) }.getOrDefault(MilestoneKind.TICK),
                            threshold = if (ro.has("threshold") && !ro.isNull("threshold")) ro.getDouble("threshold") else null,
                            goalId = ro.optString("goalId").takeIf { it.isNotBlank() },
                            doneEpochDay = if (ro.has("doneEpochDay") && !ro.isNull("doneEpochDay")) ro.getLong("doneEpochDay") else null
                        )
                    )
                }

                val horizonStr = o.optString("horizonType", "SOMEDAY")
                val horizon: DreamHorizon = when (horizonStr) {
                    "YEAR" -> DreamHorizon.Year(o.optInt("horizonYear", 2026))
                    "DATE" -> DreamHorizon.Date(o.optLong("horizonDate", today()))
                    else -> DreamHorizon.Someday
                }

                dreams.add(
                    Dream(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        why = o.optString("why").takeIf { it.isNotBlank() },
                        glyphKey = o.optString("glyphKey", "dome"),
                        landmark = runCatching { LandmarkStyle.valueOf(o.optString("landmark", "ACADEMY")) }.getOrDefault(LandmarkStyle.ACADEMY),
                        horizon = horizon,
                        rungs = rungs,
                        status = runCatching { DreamStatus.valueOf(o.optString("status", "ACTIVE")) }.getOrDefault(DreamStatus.ACTIVE),
                        pinned = o.optBoolean("pinned", false),
                        achievedEpochDay = if (o.has("achievedEpochDay") && !o.isNull("achievedEpochDay")) o.getLong("achievedEpochDay") else null
                    )
                )
            }
            cachedDreams = dreams

            // 6. Achievements
            val achArr = root.optJSONArray("achievements") ?: JSONArray()
            val achievements = mutableListOf<Achievement>()
            for (i in 0 until achArr.length()) {
                val o = achArr.getJSONObject(i)
                achievements.add(
                    Achievement(
                        id = o.getString("id"),
                        kind = runCatching { AchievementKind.valueOf(o.optString("kind", "GOAL")) }.getOrDefault(AchievementKind.GOAL),
                        refId = o.getString("refId"),
                        title = o.getString("title"),
                        epochDay = o.getLong("epochDay"),
                        plannedDays = if (o.has("plannedDays") && !o.isNull("plannedDays")) o.getInt("plannedDays") else null,
                        actualDays = if (o.has("actualDays") && !o.isNull("actualDays")) o.getInt("actualDays") else null,
                        deltaDays = if (o.has("deltaDays") && !o.isNull("deltaDays")) o.getInt("deltaDays") else null,
                        bricks = o.optInt("bricks", 100),
                        landmark = if (o.has("landmark")) runCatching { LandmarkStyle.valueOf(o.getString("landmark")) }.getOrNull() else null
                    )
                )
            }
            cachedAchievements = achievements

            // 7. Day Notes
            val notesObj = root.optJSONObject("dayNotes")
            val notes = mutableMapOf<Long, String>()
            if (notesObj != null) {
                val keys = notesObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    notes[k.toLong()] = notesObj.getString(k)
                }
            }
            cachedDayNotes = notes

        } catch (e: Exception) {
            e.printStackTrace()
            cachedHabits = mutableListOf()
            cachedEntries = mutableListOf()
            cachedGoals = mutableListOf()
            cachedProgress = mutableListOf()
            cachedReadings = mutableListOf()
            cachedDreams = mutableListOf()
            cachedAchievements = mutableListOf()
            cachedDayNotes = mutableMapOf()
        }
    }

    private fun migrateFromLegacyHabitStore(context: Context) {
        val legacy = HabitStore.load(context)
        if (legacy.isEmpty()) {
            // Seed a starter habit if fresh
            cachedHabits?.add(
                HabitItem(
                    id = UUID.randomUUID().toString(),
                    name = "Read",
                    glyphKey = "book",
                    measure = Measure.COUNT,
                    unit = "pages",
                    target = 20.0,
                    minimum = 5.0,
                    stretch = 40.0,
                    period = Period.DAY,
                    daysOfWeek = setOf(1, 2, 3, 4, 5, 6, 7),
                    weight = Weight.STANDARD,
                    autoSource = AutoSource.FOLIO_PAGES,
                    startEpochDay = today()
                )
            )
            return
        }

        legacy.forEach { old ->
            val habitId = old.id.toString()
            cachedHabits?.add(
                HabitItem(
                    id = habitId,
                    name = old.name,
                    glyphKey = "check",
                    measure = Measure.DONE,
                    unit = "done",
                    target = 1.0,
                    minimum = 1.0,
                    period = Period.DAY,
                    daysOfWeek = setOf(1, 2, 3, 4, 5, 6, 7),
                    weight = Weight.STANDARD,
                    startEpochDay = old.createdDay
                )
            )
            old.done.forEach { doneDay ->
                cachedEntries?.add(
                    Entry(
                        id = UUID.randomUUID().toString(),
                        habitId = habitId,
                        epochDay = doneDay,
                        quantity = 1.0,
                        kind = EntryKind.LOG,
                        createdAtMillis = doneDay * 86400000L
                    )
                )
            }
        }
    }

    @Synchronized
    fun save(context: Context) {
        val root = JSONObject()

        // 1. Habits
        val habitsArr = JSONArray()
        cachedHabits?.forEach { h ->
            habitsArr.put(JSONObject().apply {
                put("id", h.id)
                put("name", h.name)
                put("glyphKey", h.glyphKey)
                put("measure", h.measure.name)
                put("unit", h.unit)
                put("target", h.target)
                put("minimum", h.minimum)
                put("stretch", h.stretch)
                put("period", h.period.name)
                put("daysOfWeek", JSONArray().apply { h.daysOfWeek.forEach { put(it) } })
                put("weight", h.weight.name)
                put("autoSource", h.autoSource.name)
                put("goalId", h.goalId)
                put("dreamId", h.dreamId)
                put("contributionRatio", h.contributionRatio)
                put("quickSteps", JSONArray(h.quickSteps))
                val histArr = JSONArray()
                h.targetHistory.forEach { th ->
                    histArr.put(JSONObject().apply {
                        put("effectiveFromEpochDay", th.effectiveFromEpochDay)
                        put("target", th.target)
                        put("minimum", th.minimum)
                        put("stretch", th.stretch)
                    })
                }
                put("targetHistory", histArr)
                put("startEpochDay", h.startEpochDay)
                put("archivedEpochDay", h.archivedEpochDay)
            })
        }
        root.put("habits", habitsArr)

        // 2. Entries
        val entriesArr = JSONArray()
        cachedEntries?.forEach { e ->
            entriesArr.put(JSONObject().apply {
                put("id", e.id)
                put("habitId", e.habitId)
                put("epochDay", e.epochDay)
                put("quantity", e.quantity)
                put("kind", e.kind.name)
                put("note", e.note)
                put("createdAtMillis", e.createdAtMillis)
            })
        }
        root.put("entries", entriesArr)

        // 3. Goals
        val goalsArr = JSONArray()
        cachedGoals?.forEach { g ->
            goalsArr.put(JSONObject().apply {
                put("id", g.id)
                put("name", g.name)
                put("why", g.why)
                put("glyphKey", g.glyphKey)
                put("finish", g.finish.name)
                put("countTotal", g.countTotal)
                put("countUnit", g.countUnit)
                put("metricBaseline", g.metricBaseline)
                put("metricTarget", g.metricTarget)
                put("metricUnit", g.metricUnit)
                put("metricHigherIsBetter", g.metricHigherIsBetter)
                put("startEpochDay", g.startEpochDay)
                put("targetEpochDay", g.targetEpochDay)
                put("dreamId", g.dreamId)
                put("status", g.status.name)
                put("achievedEpochDay", g.achievedEpochDay)

                val mArr = JSONArray()
                g.milestones.forEach { m ->
                    mArr.put(JSONObject().apply {
                        put("id", m.id)
                        put("title", m.title)
                        put("kind", m.kind.name)
                        put("threshold", m.threshold)
                        put("goalId", m.goalId)
                        put("doneEpochDay", m.doneEpochDay)
                    })
                }
                put("milestones", mArr)
            })
        }
        root.put("goals", goalsArr)

        // 4. Progress & Readings
        val progArr = JSONArray()
        cachedProgress?.forEach { p ->
            progArr.put(JSONObject().apply {
                put("id", p.id)
                put("goalId", p.goalId)
                put("epochDay", p.epochDay)
                put("delta", p.delta)
                put("note", p.note)
            })
        }
        root.put("progress", progArr)

        val readArr = JSONArray()
        cachedReadings?.forEach { r ->
            readArr.put(JSONObject().apply {
                put("id", r.id)
                put("goalId", r.goalId)
                put("epochDay", r.epochDay)
                put("value", r.value)
                put("note", r.note)
            })
        }
        root.put("readings", readArr)

        // 5. Dreams
        val dreamsArr = JSONArray()
        cachedDreams?.forEach { d ->
            dreamsArr.put(JSONObject().apply {
                put("id", d.id)
                put("name", d.name)
                put("why", d.why)
                put("glyphKey", d.glyphKey)
                put("landmark", d.landmark.name)
                put("status", d.status.name)
                put("pinned", d.pinned)
                put("achievedEpochDay", d.achievedEpochDay)

                when (val h = d.horizon) {
                    is DreamHorizon.Year -> {
                        put("horizonType", "YEAR")
                        put("horizonYear", h.year)
                    }
                    is DreamHorizon.Date -> {
                        put("horizonType", "DATE")
                        put("horizonDate", h.epochDay)
                    }
                    else -> put("horizonType", "SOMEDAY")
                }

                val rArr = JSONArray()
                d.rungs.forEach { r ->
                    rArr.put(JSONObject().apply {
                        put("id", r.id)
                        put("title", r.title)
                        put("kind", r.kind.name)
                        put("threshold", r.threshold)
                        put("goalId", r.goalId)
                        put("doneEpochDay", r.doneEpochDay)
                    })
                }
                put("rungs", rArr)
            })
        }
        root.put("dreams", dreamsArr)

        // 6. Achievements
        val achArr = JSONArray()
        cachedAchievements?.forEach { a ->
            achArr.put(JSONObject().apply {
                put("id", a.id)
                put("kind", a.kind.name)
                put("refId", a.refId)
                put("title", a.title)
                put("epochDay", a.epochDay)
                put("plannedDays", a.plannedDays)
                put("actualDays", a.actualDays)
                put("deltaDays", a.deltaDays)
                put("bricks", a.bricks)
                put("landmark", a.landmark?.name)
            })
        }
        root.put("achievements", achArr)

        // 7. Day Notes
        val notesObj = JSONObject()
        cachedDayNotes?.forEach { (day, note) ->
            notesObj.put(day.toString(), note)
        }
        root.put("dayNotes", notesObj)

        // Atomic write
        val file = getStorageFile(context)
        val tempFile = File(context.filesDir, "$FILE_NAME.tmp")
        tempFile.writeText(root.toString())
        tempFile.renameTo(file)
    }

    // -------------------------------------------------------------------------
    // ACCESSORS
    // -------------------------------------------------------------------------

    fun getHabits(context: Context): List<HabitItem> {
        ensureLoaded(context)
        return cachedHabits?.filter { !it.isArchived } ?: emptyList()
    }

    fun getAllHabits(context: Context): List<HabitItem> {
        ensureLoaded(context)
        return cachedHabits?.toList() ?: emptyList()
    }

    fun getEntries(context: Context): List<Entry> {
        ensureLoaded(context)
        return cachedEntries?.toList() ?: emptyList()
    }

    fun getGoals(context: Context): List<Goal> {
        ensureLoaded(context)
        return cachedGoals?.filter { it.status == GoalStatus.ACTIVE } ?: emptyList()
    }

    fun getAllGoals(context: Context): List<Goal> {
        ensureLoaded(context)
        return cachedGoals?.toList() ?: emptyList()
    }

    fun getDreams(context: Context): List<Dream> {
        ensureLoaded(context)
        return cachedDreams?.filter { it.status == DreamStatus.ACTIVE } ?: emptyList()
    }

    fun getAllDreams(context: Context): List<Dream> {
        ensureLoaded(context)
        return cachedDreams?.toList() ?: emptyList()
    }

    fun getAchievements(context: Context): List<Achievement> {
        ensureLoaded(context)
        return cachedAchievements?.sortedByDescending { it.epochDay } ?: emptyList()
    }

    fun getDayNote(context: Context, epochDay: Long): String? {
        ensureLoaded(context)
        return cachedDayNotes?.get(epochDay)
    }

    fun setDayNote(context: Context, epochDay: Long, note: String?) {
        ensureLoaded(context)
        if (note.isNullOrBlank()) {
            cachedDayNotes?.remove(epochDay)
        } else {
            cachedDayNotes?.put(epochDay, note.trim())
        }
        save(context)
    }

    // -------------------------------------------------------------------------
    // MUTATIONS
    // -------------------------------------------------------------------------

    fun saveHabit(context: Context, habit: HabitItem) {
        ensureLoaded(context)
        val list = cachedHabits ?: return
        val idx = list.indexOfFirst { it.id == habit.id }
        if (idx >= 0) list[idx] = habit else list.add(habit)
        save(context)
    }

    fun archiveHabit(context: Context, id: String) {
        ensureLoaded(context)
        val list = cachedHabits ?: return
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list[idx] = list[idx].copy(archivedEpochDay = today())
            save(context)
        }
    }

    fun deleteHabit(context: Context, id: String) {
        ensureLoaded(context)
        cachedHabits?.removeAll { it.id == id }
        cachedEntries?.removeAll { it.habitId == id }
        save(context)
    }

    fun logEntry(context: Context, habitId: String, epochDay: Long, quantity: Double, kind: EntryKind = EntryKind.LOG, note: String? = null): Entry {
        ensureLoaded(context)
        val entry = Entry(
            id = UUID.randomUUID().toString(),
            habitId = habitId,
            epochDay = epochDay,
            quantity = quantity,
            kind = kind,
            note = note,
            createdAtMillis = System.currentTimeMillis()
        )
        cachedEntries?.add(entry)
        save(context)
        return entry
    }

    fun removeEntry(context: Context, entryId: String) {
        ensureLoaded(context)
        cachedEntries?.removeAll { it.id == entryId }
        save(context)
    }

    fun toggleDoneHabit(context: Context, habitId: String, epochDay: Long) {
        ensureLoaded(context)
        val existing = cachedEntries?.filter { it.habitId == habitId && it.epochDay == epochDay }
        if (!existing.isNullOrEmpty()) {
            cachedEntries?.removeAll(existing.toSet())
        } else {
            cachedEntries?.add(
                Entry(
                    id = UUID.randomUUID().toString(),
                    habitId = habitId,
                    epochDay = epochDay,
                    quantity = 1.0,
                    kind = EntryKind.LOG,
                    createdAtMillis = System.currentTimeMillis()
                )
            )
        }
        save(context)
    }

    fun saveGoal(context: Context, goal: Goal) {
        ensureLoaded(context)
        val list = cachedGoals ?: return
        val idx = list.indexOfFirst { it.id == goal.id }
        if (idx >= 0) list[idx] = goal else list.add(goal)
        save(context)
    }

    fun deleteGoal(context: Context, goalId: String) {
        ensureLoaded(context)
        cachedGoals?.removeAll { it.id == goalId }
        cachedProgress?.removeAll { it.goalId == goalId }
        save(context)
    }

    fun addGoalProgress(context: Context, goalId: String, delta: Double, note: String? = null) {
        ensureLoaded(context)
        cachedProgress?.add(
            GoalProgress(
                id = UUID.randomUUID().toString(),
                goalId = goalId,
                epochDay = today(),
                delta = delta,
                note = note
            )
        )
        save(context)
    }

    fun addMetricReading(context: Context, goalId: String, value: Double, note: String? = null) {
        ensureLoaded(context)
        cachedReadings?.add(
            MetricReading(
                id = UUID.randomUUID().toString(),
                goalId = goalId,
                epochDay = today(),
                value = value,
                note = note
            )
        )
        save(context)
    }

    fun toggleGoalMilestone(context: Context, goalId: String, milestoneId: String) {
        ensureLoaded(context)
        val goals = cachedGoals ?: return
        val gIdx = goals.indexOfFirst { it.id == goalId }
        if (gIdx >= 0) {
            val goal = goals[gIdx]
            val updated = goal.milestones.map { m ->
                if (m.id == milestoneId) {
                    m.copy(doneEpochDay = if (m.isDone) null else today())
                } else m
            }
            goals[gIdx] = goal.copy(milestones = updated)
            save(context)
        }
    }

    fun markGoalAchieved(context: Context, goalId: String, evidence: Evidence? = null): Achievement? {
        ensureLoaded(context)
        val goals = cachedGoals ?: return null
        val gIdx = goals.indexOfFirst { it.id == goalId }
        if (gIdx < 0) return null

        val goal = goals[gIdx]
        val achievedDay = today()
        val plannedDays = goal.targetEpochDay?.let { (it - goal.startEpochDay).toInt() }
        val actualDays = (achievedDay - goal.startEpochDay).toInt()
        val deltaDays = plannedDays?.let { it - actualDays }

        val baseBricks = ScoreEngine.Economy.goalAchievedBase(plannedDays)
        val earlyBonus = ScoreEngine.Economy.earlyFinishBonus(baseBricks, deltaDays, plannedDays)
        val totalBricks = baseBricks + earlyBonus

        goals[gIdx] = goal.copy(
            status = GoalStatus.ACHIEVED,
            achievedEpochDay = achievedDay,
            evidence = evidence
        )

        val ach = Achievement(
            id = UUID.randomUUID().toString(),
            kind = AchievementKind.GOAL,
            refId = goal.id,
            title = goal.name,
            epochDay = achievedDay,
            plannedDays = plannedDays,
            actualDays = actualDays,
            deltaDays = deltaDays,
            bricks = totalBricks,
            evidence = evidence
        )
        cachedAchievements?.add(ach)
        save(context)
        return ach
    }

    fun saveDream(context: Context, dream: Dream) {
        ensureLoaded(context)
        val list = cachedDreams ?: return
        val idx = list.indexOfFirst { it.id == dream.id }
        if (idx >= 0) list[idx] = dream else list.add(dream)
        save(context)
    }

    fun deleteDream(context: Context, dreamId: String) {
        ensureLoaded(context)
        cachedDreams?.removeAll { it.id == dreamId }
        save(context)
    }

    fun toggleDreamRung(context: Context, dreamId: String, rungId: String) {
        ensureLoaded(context)
        val dreams = cachedDreams ?: return
        val dIdx = dreams.indexOfFirst { it.id == dreamId }
        if (dIdx >= 0) {
            val dream = dreams[dIdx]
            val updated = dream.rungs.map { r ->
                if (r.id == rungId) {
                    r.copy(doneEpochDay = if (r.isDone) null else today())
                } else r
            }
            dreams[dIdx] = dream.copy(rungs = updated)
            save(context)
        }
    }

    fun markDreamAchieved(context: Context, dreamId: String): Achievement? {
        ensureLoaded(context)
        val dreams = cachedDreams ?: return null
        val dIdx = dreams.indexOfFirst { it.id == dreamId }
        if (dIdx < 0) return null

        val dream = dreams[dIdx]
        val achievedDay = today()
        dreams[dIdx] = dream.copy(
            status = DreamStatus.ACHIEVED,
            achievedEpochDay = achievedDay
        )

        val ach = Achievement(
            id = UUID.randomUUID().toString(),
            kind = AchievementKind.DREAM,
            refId = dream.id,
            title = dream.name,
            epochDay = achievedDay,
            plannedDays = null,
            actualDays = null,
            deltaDays = null,
            bricks = ScoreEngine.Economy.DREAM_ACHIEVED_BONUS,
            landmark = dream.landmark
        )
        cachedAchievements?.add(ach)
        save(context)
        return ach
    }

    // -------------------------------------------------------------------------
    // CALCULATIONS & AGGREGATES
    // -------------------------------------------------------------------------

    fun getDayTotalQuantity(context: Context, habitId: String, epochDay: Long): Double {
        ensureLoaded(context)
        val habit = cachedHabits?.firstOrNull { it.id == habitId } ?: return 0.0
        val manual = cachedEntries
            ?.filter { it.habitId == habitId && it.epochDay == epochDay && it.kind == EntryKind.LOG }
            ?.sumOf { it.quantity } ?: 0.0
        val auto = getAutoSourceQuantity(context, habit.autoSource, epochDay)
        return manual + auto
    }

    fun getAutoSourceQuantity(context: Context, autoSource: AutoSource, epochDay: Long): Double {
        return when (autoSource) {
            AutoSource.FOLIO_PAGES -> {
                // Read from BookStore session logs for epochDay
                val sessions = rocks.ndot.widgets.instruments.folio.BookStore.loadSessions(context)
                val dayStartMillis = epochDay * 86400000L
                val dayEndMillis = dayStartMillis + 86400000L
                sessions.filter { it.timestampMillis in dayStartMillis until dayEndMillis }.sumOf { it.pagesRead }.toDouble()
            }
            AutoSource.FOLIO_MINUTES -> {
                val sessions = rocks.ndot.widgets.instruments.folio.BookStore.loadSessions(context)
                val dayStartMillis = epochDay * 86400000L
                val dayEndMillis = dayStartMillis + 86400000L
                sessions.filter { it.timestampMillis in dayStartMillis until dayEndMillis }.sumOf { it.durationSeconds } / 60.0
            }
            else -> 0.0
        }
    }

    /**
     * Replays history to derive lifetime bricks (Section 2.5)
     */
    fun calculateLifetimeBricks(context: Context): Int {
        ensureLoaded(context)
        val habits = cachedHabits ?: return 0
        val entries = cachedEntries ?: return 0
        if (habits.isEmpty() && entries.isEmpty()) return 0

        val scheduledHabits = habits.filter { it.period == Period.DAY }
        val allDays = entries.map { it.epochDay }.distinct().sorted()
        var totalBricks = 0

        for (day in allDays) {
            var dayQualCount = 0
            var scheduledTodayCount = 0
            val dow = java.time.LocalDate.ofEpochDay(day).dayOfWeek.value

            scheduledHabits.forEach { h ->
                if (dow in h.daysOfWeek) {
                    scheduledTodayCount++
                    val q = entries.filter { it.habitId == h.id && it.epochDay == day && it.kind == EntryKind.LOG }.sumOf { it.quantity } +
                            getAutoSourceQuantity(context, h.autoSource, day)
                    if (ScoreEngine.qualifies(q, h.minimum, h.target)) {
                        dayQualCount++
                    }
                    totalBricks += ScoreEngine.periodBricks(h, q)
                }
            }

            // Perfect day bonus
            if (scheduledTodayCount >= 2 && dayQualCount == scheduledTodayCount) {
                totalBricks += ScoreEngine.Economy.PERFECT_DAY_BONUS
            }
        }

        // Achievements bonus
        cachedAchievements?.forEach { a ->
            totalBricks += a.bricks
        }

        return totalBricks
    }

    fun getTodayBricks(context: Context): Int {
        ensureLoaded(context)
        val today = today()
        val habits = cachedHabits ?: return 0
        val entries = cachedEntries ?: return 0
        val dow = java.time.LocalDate.ofEpochDay(today).dayOfWeek.value

        var bricks = 0
        var scheduledCount = 0
        var qualCount = 0

        habits.filter { it.period == Period.DAY }.forEach { h ->
            if (dow in h.daysOfWeek) {
                scheduledCount++
                val q = entries.filter { it.habitId == h.id && it.epochDay == today && it.kind == EntryKind.LOG }.sumOf { it.quantity } +
                        getAutoSourceQuantity(context, h.autoSource, today)
                if (ScoreEngine.qualifies(q, h.minimum, h.target)) {
                    qualCount++
                }
                bricks += ScoreEngine.periodBricks(h, q)
            }
        }

        if (scheduledCount >= 2 && qualCount == scheduledCount) {
            bricks += ScoreEngine.Economy.PERFECT_DAY_BONUS
        }

        return bricks
    }

    /**
     * Computes DayAggregate for Almanac dot grid rendering (Section 5.2)
     */
    fun computeDayAggregate(context: Context, epochDay: Long, habits: List<HabitItem>, entries: List<Entry>): DayAggregate {
        val dow = java.time.LocalDate.ofEpochDay(epochDay).dayOfWeek.value
        val scheduled = habits.filter { it.period == Period.DAY && dow in it.daysOfWeek }
        val dayEntries = entries.filter { it.epochDay == epochDay }
        val isRest = dayEntries.any { it.kind == EntryKind.REST }

        if (scheduled.isEmpty()) {
            val hasLogs = dayEntries.any { it.kind == EntryKind.LOG }
            return DayAggregate(
                epochDay = epochDay,
                completion = if (hasLogs) 0.85 else 0.0,
                overall = if (hasLogs) 1.0 else 0.0,
                entryCount = dayEntries.size,
                totalBricks = 0,
                isRest = isRest,
                isGold = false,
                isMended = false
            )
        }

        var totalWeight = 0.0
        var completionWeight = 0.0
        var overallWeight = 0.0

        scheduled.forEach { h ->
            val w = h.weight.factor
            totalWeight += w
            val q = dayEntries.filter { it.habitId == h.id && it.kind == EntryKind.LOG }.sumOf { it.quantity } +
                    getAutoSourceQuantity(context, h.autoSource, epochDay)
            val r = if (h.target > 0.0) q / h.target else 0.0
            val c = ScoreEngine.credit(r)
            completionWeight += w * kotlin.math.min(c, 1.0)
            overallWeight += w * c
        }

        val completion = if (totalWeight > 0.0) completionWeight / totalWeight else 0.0
        val overall = if (totalWeight > 0.0) overallWeight / totalWeight else 0.0

        val hasAch = cachedAchievements?.any { it.epochDay == epochDay } ?: false
        val hasMile = (cachedGoals?.any { g -> g.milestones.any { it.doneEpochDay == epochDay } } ?: false) ||
                (cachedDreams?.any { d -> d.rungs.any { it.doneEpochDay == epochDay } } ?: false)
        val hasDead = cachedGoals?.any { it.targetEpochDay == epochDay } ?: false
        val hasPeriodLog = entries.any { e ->
            val h = habits.firstOrNull { it.id == e.habitId }
            h != null && (h.period == Period.WEEK || h.period == Period.MONTH) && e.epochDay == epochDay
        }

        return DayAggregate(
            epochDay = epochDay,
            completion = completion,
            overall = overall,
            entryCount = dayEntries.size,
            totalBricks = 0,
            isRest = isRest,
            isGold = overall >= 1.25,
            isMended = false,
            hasAchievement = hasAch,
            hasMilestone = hasMile,
            hasDeadline = hasDead,
            hasPeriodHabitLog = hasPeriodLog
        )
    }
}

/**
 * AutoQuantityProvider (Part 2 Section 7.2)
 * Pure factual read-only provider for auto-sourced habit values.
 * Keyed by local day, deriving directly from canonical instrument records.
 */
object AutoQuantityProvider {
    fun getQuantity(context: Context, source: AutoSource, epochDay: Long): Double {
        val dayStart = epochDay * 86_400_000L
        val dayEnd = dayStart + 86_400_000L

        return when (source) {
            AutoSource.NONE -> 0.0
            AutoSource.FOLIO_PAGES -> {
                val sessions = rocks.ndot.widgets.instruments.folio.BookStore.loadSessions(context)
                sessions.filter { it.timestampMillis in dayStart until dayEnd }.sumOf { it.pagesRead }.toDouble()
            }
            AutoSource.FOLIO_MINUTES -> {
                val sessions = rocks.ndot.widgets.instruments.folio.BookStore.loadSessions(context)
                sessions.filter { it.timestampMillis in dayStart until dayEnd }.sumOf { it.durationMinutes }.toDouble()
            }
            AutoSource.FOLIO_BOOKS_COMPLETED -> {
                val books = rocks.ndot.widgets.instruments.folio.BookStore.loadAll(context)
                books.count { it.finishedEpochMillis != null && (it.finishedEpochMillis / 86_400_000L) == epochDay }.toDouble()
            }
            AutoSource.FOCUS_MINUTES -> {
                val events = rocks.ndot.widgets.instruments.trace.TraceLedger.getDayEvents(context, epochDay)
                val focusSec = events.filter { it.activityType == rocks.ndot.widgets.instruments.trace.ActivityType.FOCUS }
                    .sumOf { it.durationSeconds }
                (focusSec / 60.0)
            }
            AutoSource.BREATHING_MINUTES -> {
                val events = rocks.ndot.widgets.instruments.trace.TraceLedger.getDayEvents(context, epochDay)
                val breathSec = events.filter { it.activityType == rocks.ndot.widgets.instruments.trace.ActivityType.BREATHING }
                    .sumOf { it.durationSeconds }
                (breathSec / 60.0)
            }
            AutoSource.METRONOME_MINUTES -> {
                val events = rocks.ndot.widgets.instruments.trace.TraceLedger.getDayEvents(context, epochDay)
                val metSec = events.filter { it.activityType == rocks.ndot.widgets.instruments.trace.ActivityType.METRONOME }
                    .sumOf { it.durationSeconds }
                (metSec / 60.0)
            }
        }
    }
}

