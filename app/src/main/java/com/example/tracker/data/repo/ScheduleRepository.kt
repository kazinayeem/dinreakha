package com.example.tracker.data.repo

import androidx.room.withTransaction
import com.example.tracker.data.db.AppDatabase
import com.example.tracker.data.db.BusTimeEntity
import com.example.tracker.data.db.ClassSessionEntity
import com.example.tracker.data.db.FocusBlockEntity
import com.example.tracker.data.db.HolidayEntity
import com.example.tracker.data.db.Migrations
import com.example.tracker.data.db.NotificationLogEntity
import com.example.tracker.data.db.PlanPhaseEntity
import com.example.tracker.data.db.RoutineEntity
import com.example.tracker.data.db.Setting
import com.example.tracker.data.db.WakeOverrideEntity
import com.example.tracker.data.schedule.PlanCalendar
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.data.schedule.ScheduleConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * The editable plan. Every emission is also pushed into [PlanCalendar.config], so all
 * screens, the planner and the alarm scheduler see the same, current schedule.
 */
class ScheduleRepository(private val db: AppDatabase) {
    private val dao = db.scheduleDao()
    private val settings = db.settingsDao()
    private val notif = db.notificationDao()

    private data class Parts(
        val classes: List<ClassSessionEntity>, val buses: List<BusTimeEntity>, val blocks: List<FocusBlockEntity>,
        val phases: List<PlanPhaseEntity>, val overrides: List<WakeOverrideEntity>,
        val routines: List<RoutineEntity> = emptyList(),
    )

    val config: Flow<ScheduleConfig> = combine(
        combine(dao.observeClasses(), dao.observeBuses(), dao.observeBlocks(), dao.observePhases(), dao.observeWakeOverrides()) { a, b, c, d, e ->
            Parts(a, b, c, d, e)
        },
        dao.observeRoutines(),
        dao.observeHolidays(),
        settings.observeAll(),
    ) { p, routines, holidays, rows ->
        build(p.copy(routines = routines), holidays, rows.associate { it.key to it.value })
    }.onEach { PlanCalendar.config = it }

    /** Loads the current plan from the DB and makes it live. Call before computing reminders. */
    suspend fun load(): ScheduleConfig {
        val p = Parts(dao.classes(), dao.buses(), dao.blocks(), dao.phases(), dao.wakeOverrides(), dao.routines())
        return build(p, dao.holidays(), settings.all().associate { it.key to it.value }).also { PlanCalendar.config = it }
    }

    private fun build(p: Parts, holidays: List<HolidayEntity>, m: Map<String, String>): ScheduleConfig {
        val d = ScheduleConfig.DEFAULT
        val phases = p.phases.map { e ->
            Schedule.Phase(e.number, e.firstDay..e.lastDay, e.wake, e.bed,
                e.prayers.split(",").mapNotNull { k -> Schedule.Prayer.entries.firstOrNull { it.key == k.trim() } }.toSet(),
                e.expectedBlocks)
        }
        return ScheduleConfig(
            planStart = m["plan_start"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: d.planStart,
            planDays = m["plan_days"]?.toIntOrNull()?.coerceIn(1, 366) ?: d.planDays,
            holidays = holidays.associate { it.date to it.name },
            phases = phases.ifEmpty { listOf(Schedule.Phase(1, 1..365, LocalTime.of(7, 0), LocalTime.of(23, 0), emptySet(), 0)) },
            wakeOverrides = p.overrides.associate { it.dayOfWeek to it.time },
            classes = p.classes.map { Schedule.ClassSession(it.dayOfWeek, it.start, it.end, it.course, it.room, it.id) },
            buses = p.buses.mapNotNull { b ->
                runCatching { Schedule.BusDirection.valueOf(b.direction) }.getOrNull()?.let { Schedule.Bus(b.dayOfWeek, it, b.time, b.id) }
            },
            blocks = p.blocks.mapNotNull { b ->
                runCatching { Schedule.BlockType.valueOf(b.type) }.getOrNull()
                    ?.let { Schedule.Block(it, b.start, b.end, b.place, b.dayOfWeek, b.id) }
            },
            screensOffLeadMin = m["screens_off_lead"]?.toLongOrNull() ?: d.screensOffLeadMin,
            busRoute = m["bus_route"]?.takeIf { it.isNotBlank() } ?: Schedule.BUS_ROUTE,
            routines = p.routines.map { r ->
                Schedule.Routine(
                    id = r.id, title = r.title, icon = r.icon,
                    repeat = runCatching { Schedule.Repeat.valueOf(r.repeat) }.getOrDefault(Schedule.Repeat.DAILY),
                    days = r.days.split(",").mapNotNull { it.trim().toIntOrNull()?.takeIf { n -> n in 1..7 }?.let(DayOfWeek::of) }.toSet(),
                    dayOfMonth = r.dayOfMonth, time = r.time, remind = r.remind, active = r.active,
                )
            },
            hiddenHabits = m[HABIT_HIDDEN].orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
            habitNames = m.filterKeys { it.startsWith(HABIT_NAME_PREFIX) }
                .mapKeys { it.key.removePrefix(HABIT_NAME_PREFIX) }
                .filterValues { it.isNotBlank() },
        )
    }

    // ---- built-in habits: show / hide, rename ----
    suspend fun setHabitHidden(key: String, hidden: Boolean) {
        val now = PlanCalendar.config.hiddenHabits.let { if (hidden) it + key else it - key }
        if (now.isEmpty()) settings.remove(HABIT_HIDDEN) else settings.put(Setting(HABIT_HIDDEN, now.sorted().joinToString(",")))
    }

    /** Blank or null [name] goes back to the built-in name. */
    suspend fun setHabitName(key: String, name: String?) {
        val n = name?.trim().orEmpty()
        if (n.isEmpty()) settings.remove(HABIT_NAME_PREFIX + key) else settings.put(Setting(HABIT_NAME_PREFIX + key, n))
    }

    // ---- plan ----
    suspend fun setPlanStart(d: LocalDate) = settings.put(Setting("plan_start", d.toString()))
    suspend fun setPlanDays(n: Int) = settings.put(Setting("plan_days", n.coerceIn(1, 366).toString()))
    suspend fun setBusRoute(name: String) = settings.put(Setting("bus_route", name.trim()))
    suspend fun setScreensOffLead(min: Long) = settings.put(Setting("screens_off_lead", min.coerceIn(0, 240).toString()))

    suspend fun savePhase(p: Schedule.Phase) = dao.upsertPhase(PlanPhaseEntity(p.number, p.days.first, p.days.last,
        p.wake, p.bed, p.prayers.joinToString(",") { it.key }, p.expectedFocusBlocks))
    suspend fun deletePhase(n: Int) = dao.deletePhase(n)

    suspend fun setWakeOverride(d: DayOfWeek, t: LocalTime?) =
        if (t == null) dao.deleteWakeOverride(d) else dao.upsertWakeOverride(WakeOverrideEntity(d, t))

    // ---- classes / buses / blocks / holidays ----
    suspend fun saveClass(c: Schedule.ClassSession) =
        dao.upsertClass(ClassSessionEntity(c.id, c.day, c.start, c.end, c.title.trim(), c.room.trim()))
    suspend fun deleteClass(id: Long) = dao.deleteClass(id)

    suspend fun saveBus(b: Schedule.Bus) = dao.upsertBus(BusTimeEntity(b.id, b.day, b.direction.name, b.time))
    suspend fun deleteBus(id: Long) = dao.deleteBus(id)

    suspend fun saveBlock(b: Schedule.Block) =
        dao.upsertBlock(FocusBlockEntity(b.id, b.type.name, b.day, b.start, b.end, b.place.trim()))
    suspend fun deleteBlock(id: Long) = dao.deleteBlock(id)

    suspend fun saveHoliday(d: LocalDate, name: String) = dao.upsertHoliday(HolidayEntity(d, name.trim()))
    suspend fun deleteHoliday(d: LocalDate) = dao.deleteHoliday(d)

    // ---- my routines ----
    suspend fun saveRoutine(r: Schedule.Routine) = dao.upsertRoutine(RoutineEntity(r.id, r.title.trim(), r.icon.trim().ifEmpty { "✅" },
        r.repeat.name, r.days.joinToString(",") { it.value.toString() }, r.dayOfMonth.coerceIn(1, 31), r.time, r.remind && r.time != null, r.active))
    suspend fun deleteRoutine(id: Long) = dao.deleteRoutine(id)

    /** Restores your original plan (classes, buses, blocks, phases, overrides, holidays, plan dates). */
    suspend fun resetToDefaults() = db.withTransaction {
        dao.clearClasses(); dao.clearBuses(); dao.clearBlocks(); dao.clearPhases(); dao.clearWakeOverrides(); dao.clearHolidays()
        Migrations.seedSchedule(db.openHelper.writableDatabase)
    }

    // ---- notification log ----
    fun observeLog(): Flow<List<NotificationLogEntity>> = notif.observeLog()
    suspend fun logFired(slot: String, category: String, title: String, at: java.time.LocalDateTime) {
        notif.log(NotificationLogEntity(slot = slot, category = category, title = title, firedAt = at)); notif.trimLog()
    }
    suspend fun clearLog() = notif.clearLog()
}

private const val HABIT_HIDDEN = "habit_hidden"
private const val HABIT_NAME_PREFIX = "habit_name_"
