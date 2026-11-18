package com.example.tracker.ui.nav

import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.example.tracker.ui.components.Feedback
import com.example.tracker.ui.components.LocalFeedback
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import com.example.tracker.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.tracker.container
import com.example.tracker.data.schedule.PlanCalendar
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.ui.components.SHORT_DATE
import com.example.tracker.ui.grid.PlanGridScreen
import com.example.tracker.ui.blocker.AppUsageStatsScreen
import com.example.tracker.ui.blocker.BlockerScreen
import com.example.tracker.ui.blocker.RuleEditorScreen
import com.example.tracker.ui.notes.CourseDetailScreen
import com.example.tracker.ui.notes.CoursesScreen
import com.example.tracker.ui.notes.NoteCaptureScreen
import com.example.tracker.ui.notes.NoteImageViewerScreen
import com.example.tracker.ui.onboarding.OnboardingScreen
import com.example.tracker.ui.review.ReviewScreen
import com.example.tracker.ui.settings.SettingsScreen
import com.example.tracker.ui.settings.NotificationSettingsScreen
import com.example.tracker.ui.settings.ScheduleEditorScreen
import com.example.tracker.ui.settings.RoutinesScreen
import com.example.tracker.ui.settings.NotificationLogScreen
import com.example.tracker.data.repo.LockScope
import com.example.tracker.data.report.CardType
import com.example.tracker.ui.report.DailyReportScreen
import com.example.tracker.ui.report.ShareCardStudioScreen
import com.example.tracker.ui.report.ThirtyDayReportScreen
import com.example.tracker.ui.report.WeeklyReportScreen
import com.example.tracker.ui.stats.StatsScreen
import com.example.tracker.ui.today.TodayScreen
import com.example.tracker.ui.wallet.AccountsScreen
import com.example.tracker.ui.wallet.AddTxnScreen
import com.example.tracker.ui.wallet.BudgetsScreen
import com.example.tracker.ui.wallet.CategoriesScreen
import com.example.tracker.ui.wallet.LockedSection
import com.example.tracker.ui.wallet.RecurringScreen
import com.example.tracker.ui.wallet.ReportScreen
import com.example.tracker.ui.wallet.TxnListScreen
import com.example.tracker.ui.wallet.WalletHomeScreen
import com.example.tracker.ui.wallet.WalletViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Filled icon when selected, outlined otherwise — state is never shown by colour alone. */
private data class Tab(val route: String, val label: String, val selected: ImageVector, val unselected: ImageVector)

private val TABS = listOf(
    Tab("today", "Today", Icons.Rounded.Today, Icons.Outlined.Today),
    Tab("calendar", "Calendar", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth),
    Tab("focus", "Study", Icons.Rounded.Timer, Icons.Outlined.Timer),
    Tab("wallet", "Wallet", Icons.Rounded.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    Tab("more", "Me", Icons.Rounded.Person, Icons.Outlined.Person),
)

private fun titleFor(route: String?, args: android.os.Bundle?): String? = when {
    route == null -> null
    route.startsWith("day/") -> args?.getString("date")?.let { "Day ${PlanCalendar.dayNumber(LocalDate.parse(it))}, ${SHORT_DATE.format(LocalDate.parse(it))}" }
    route.startsWith("review") -> "Weekly review"
    route.startsWith("wallet/add") -> if ((args?.getLong("id") ?: 0L) > 0) "Edit transaction" else "Add transaction"
    route == "wallet/list" -> "Transactions"
    route == "wallet/report" -> "Monthly report"
    route == "wallet/budgets" -> "Budgets"
    route == "wallet/recurring" -> "Recurring"
    route == "wallet/accounts" -> "Accounts"
    route == "wallet/categories" -> "Categories"
    route == "settings/notifications" -> "Notifications"
    route == "settings/schedule" -> "Schedule"
    route == "settings/routines" -> "Habits & routines"
    route == "settings/log" -> "Notification log"
    route == "settings/look" -> "Look & language"
    route == "settings/privacy" -> "Privacy & data"
    route == "about" -> "About"
    route == "privacy" -> "Privacy"
    route == "terms" -> "Terms"
    route?.startsWith("sleep") == true -> "Sleep & mood"
    route == "plan" -> "30-day plan"
    route == "stats" -> "Stats"
    route == "progress" -> "Progress"
    route == "settings" -> "Settings"
    route.startsWith("calendar/edit") -> if ((args?.getLong("id") ?: 0L) > 0) "Edit event" else "New event"
    route == "courses" -> "Class notes"
    route.startsWith("course/") -> "Course Notes"
    route.startsWith("notes/capture") -> "Capture Class Note"
    // These screens draw their own top bar (with share / stats / save actions) — no second bar here.
    route.startsWith("blocker") || route.startsWith("reports/") -> null
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav(
    pendingRoute: String?,
    sharedUris: List<Uri> = emptyList(),
    onRouteHandled: () -> Unit,
) {
    val ctx = LocalContext.current
    val c = ctx.container
    val settings by c.settings.settings.collectAsStateWithLifecycle(null)
    val s = settings ?: return
    val nav = rememberNavController()
    val walletVm: WalletViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route?.substringBefore('?')
    val isTab = TABS.any { it.route == route }
    val snackbar = remember { SnackbarHostState() }
    val feedback = remember { Feedback(snackbar, scope) }

    // Photos shared into the app wait here until the capture screen has taken them.
    var pendingShare by remember { mutableStateOf<List<Uri>>(emptyList()) }

    LockedSection(LockScope.APP) {
    CompositionLocalProvider(LocalFeedback provides feedback) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            titleFor(route, entry?.arguments)?.let { title ->
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            // Ignore a second tap while the first pop is still animating (it would pop two screens).
                            if (entry?.lifecycle?.currentState == Lifecycle.State.RESUMED) nav.popBackStack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (isTab) NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
            ) {
                TABS.forEach { tab ->
                    val sel = route == tab.route
                    NavigationBarItem(
                        selected = sel,
                        onClick = { nav.goTab(tab.route) },
                        icon = { Icon(if (sel) tab.selected else tab.unselected, contentDescription = null) },
                        label = {
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                ),
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
        ) {
            // Runs only once the NavHost below has set its graph (it is hidden while the app lock is up).
            LaunchedEffect(pendingRoute, s.onboardingDone) {
                if (pendingRoute != null && s.onboardingDone) {
                    if (pendingRoute.startsWith("notes/capture") && sharedUris.isNotEmpty()) pendingShare = sharedUris
                    nav.openDeepLink(pendingRoute)
                    onRouteHandled()
                }
            }
            LaunchedEffect(s.onboardingDone) {
                if (!s.onboardingDone && nav.currentDestination?.route != "onboarding") {
                    nav.navigate("onboarding") { popUpTo(0) { inclusive = true } }
                }
            }
            NavHost(
                navController = nav,
                startDestination = if (s.onboardingDone) "today" else "onboarding",
                // Tabs: fade-through (old out fast, new in after it — never both on screen).
                // Detail screens: short shared-axis slide. Durations follow Material motion (150–250 ms).
                enterTransition = {
                    if (isTabSwitch(initialState.destination.route, targetState.destination.route)) {
                        fadeIn(tween(160, delayMillis = 70, easing = LinearOutSlowInEasing))
                    } else {
                        fadeIn(tween(200, delayMillis = 40)) +
                            slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { (it * 0.12f).toInt() }
                    }
                },
                exitTransition = {
                    if (isTabSwitch(initialState.destination.route, targetState.destination.route)) fadeOut(tween(70))
                    else fadeOut(tween(90))
                },
                popEnterTransition = {
                    if (isTabSwitch(initialState.destination.route, targetState.destination.route)) {
                        fadeIn(tween(160, delayMillis = 70, easing = LinearOutSlowInEasing))
                    } else fadeIn(tween(200, delayMillis = 40))
                },
                popExitTransition = {
                    if (isTabSwitch(initialState.destination.route, targetState.destination.route)) fadeOut(tween(70))
                    else fadeOut(tween(120)) + slideOutHorizontally(tween(200, easing = FastOutLinearInEasing)) { (it * 0.12f).toInt() }
                },
            ) {
                composable("onboarding") {
                    OnboardingScreen(onDone = {
                        scope.launch { c.settings.setOnboardingDone(); c.scheduler.rescheduleAll() }
                        nav.navigate("today") { popUpTo("onboarding") { inclusive = true } }
                    })
                }
                composable("today") {
                    TodayScreen(
                        date = null,
                        onOpenReview = { nav.navigate("review/$it") },
                        onAddExpense = { nav.navigate("wallet/add") },
                        onOpenWallet = { nav.goTab("wallet") },
                        onStartFocus = { t, m -> nav.navigate("focus?type=$t&min=$m") { launchSingleTop = true } },
                        onOpenPlan = { nav.navigate("plan") },
                        onCaptureNote = { cid ->
                            nav.navigate(if (cid != null) "notes/capture?courseId=$cid" else "notes/capture")
                        },
                        onOpenCourses = { nav.openDeepLink("focus?sub=NOTES") },
                        onOpenBlocker = { nav.navigate("blocker") },
                        onOpenDailyReport = { d -> nav.navigate("progress?tab=DAY&date=$d") },
                        onLogSleep = { nav.navigate("sleep?log=true") },
                    )
                }
                composable("day/{date}", listOf(navArgument("date") { type = NavType.StringType })) { e ->
                    val date = LocalDate.parse(e.arguments!!.getString("date"))
                    TodayScreen(
                        date = date,
                        onOpenReview = { nav.navigate("review/$it") },
                        onAddExpense = { nav.navigate("wallet/add") },
                        onOpenWallet = { nav.goTab("wallet") },
                        onStartFocus = { t, m -> nav.navigate("focus?type=$t&min=$m") { launchSingleTop = true } },
                        onLogSleep = { nav.navigate("sleep?log=true") },
                        onOpenPlan = { nav.navigate("plan") },
                        onCaptureNote = { cid ->
                            nav.navigate(if (cid != null) "notes/capture?courseId=$cid" else "notes/capture")
                        },
                        onOpenCourses = { nav.openDeepLink("focus?sub=NOTES") },
                        onOpenBlocker = { nav.navigate("blocker") },
                        onOpenDailyReport = { d -> nav.navigate("progress?tab=DAY&date=$d") },
                    )
                }
                composable("plan") {
                    PlanGridScreen(onOpenDay = { nav.navigate("day/$it") },
                        onOpenReview = { nav.navigate("review/${PlanCalendar.weekNumber(LocalDate.now(Schedule.ZONE))}") })
                }
                composable("review") {
                    ReviewScreen(
                        initialWeek = PlanCalendar.weekNumber(LocalDate.now(Schedule.ZONE)),
                        onOpenWeeklyReport = { w -> nav.navigate("progress?tab=WEEK&week=$w") },
                    )
                }
                composable("review/{week}", listOf(navArgument("week") { type = NavType.IntType })) { e ->
                    ReviewScreen(
                        initialWeek = e.arguments!!.getInt("week"),
                        onOpenWeeklyReport = { w -> nav.navigate("progress?tab=WEEK&week=$w") },
                    )
                }
                composable(
                    "progress?tab={tab}&week={week}&date={date}",
                    listOf(
                        navArgument("tab") { type = NavType.StringType; defaultValue = "DAY" },
                        navArgument("week") { type = NavType.IntType; defaultValue = 0 },
                        navArgument("date") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { e ->
                    val a = e.arguments!!
                    val today = LocalDate.now(Schedule.ZONE)
                    com.example.tracker.ui.report.ProgressScreen(
                        initialTab = runCatching { com.example.tracker.ui.report.ProgressTab.valueOf(a.getString("tab") ?: "DAY") }
                            .getOrDefault(com.example.tracker.ui.report.ProgressTab.DAY),
                        date = a.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today,
                        week = a.getInt("week").takeIf { it > 0 } ?: PlanCalendar.weekNumber(today),
                        nav = { nav.navigate(it) },
                    )
                }
                composable("stats") { StatsScreen(onOpenPlan = { nav.navigate("plan") }) }
                composable("more") { com.example.tracker.ui.more.MoreScreen { nav.navigate(it) } }
                composable("focus?note={note}&type={type}&min={min}&sub={sub}", listOf(
                    navArgument("sub") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("note") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("min") { type = NavType.IntType; defaultValue = 0 },
                )) { e ->
                    val a = e.arguments!!
                    val preset = a.getString("type")?.let { t -> runCatching { com.example.tracker.data.focus.FocusType.valueOf(t) }.getOrNull() }
                        ?.let { it to a.getInt("min").takeIf { m -> m > 0 }.let { m -> m ?: 45 } }
                    val note = a.getLong("note").takeIf { it > 0 }
                    com.example.tracker.ui.focus.StudyScreen(
                        initialTab = if (a.getString("sub") == "NOTES" && note == null && preset == null)
                            com.example.tracker.ui.focus.StudyTab.NOTES else com.example.tracker.ui.focus.StudyTab.FOCUS,
                        noteForSession = note,
                        preset = preset,
                        onOpenCourse = { nav.navigate("course/$it") },
                        onCaptureNote = { cid -> nav.navigate(if (cid != null) "notes/capture?courseId=$cid" else "notes/capture") },
                    )
                }
                composable("calendar") { com.example.tracker.ui.calendar.CalendarScreen { nav.navigate(it) } }
                composable(
                    "calendar/edit?id={id}&date={date}&hour={hour}&minute={minute}&occ={occ}",
                    listOf(
                        navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("date") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("hour") { type = NavType.IntType; defaultValue = -1 },
                        navArgument("minute") { type = NavType.IntType; defaultValue = 0 },
                        navArgument("occ") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { e ->
                    val a = e.arguments!!
                    com.example.tracker.ui.calendar.EventEditorScreen(
                        eventId = a.getLong("id").takeIf { it > 0 },
                        date = a.getString("date")?.let(LocalDate::parse),
                        hour = a.getInt("hour").takeIf { it >= 0 },
                        minute = a.getInt("minute"),
                        occurrenceDate = a.getString("occ")?.let(LocalDate::parse),
                        onDone = dropUnlessResumed { nav.popBackStack() },
                    )
                }
                composable("settings") { LockedSection(LockScope.SETTINGS) { SettingsScreen { nav.navigate(it) } } }
                composable("settings/notifications") { LockedSection(LockScope.SETTINGS) { NotificationSettingsScreen { nav.navigate("settings/log") } } }
                composable("settings/look") { LockedSection(LockScope.SETTINGS) { SettingsScreen(com.example.tracker.ui.settings.SettingsPage.LOOK) { nav.navigate(it) } } }
                composable("settings/privacy") { LockedSection(LockScope.SETTINGS) { SettingsScreen(com.example.tracker.ui.settings.SettingsPage.PRIVACY) { nav.navigate(it) } } }
                composable("settings/schedule") { LockedSection(LockScope.SETTINGS) { ScheduleEditorScreen() } }
                composable("settings/routines") { LockedSection(LockScope.SETTINGS) { RoutinesScreen() } }
                composable("settings/log") { LockedSection(LockScope.SETTINGS) { NotificationLogScreen() } }
                composable("about") { com.example.tracker.ui.more.AboutScreen { nav.navigate(it) } }
                composable("privacy") { com.example.tracker.ui.more.PolicyScreen(terms = false) }
                composable("terms") { com.example.tracker.ui.more.PolicyScreen(terms = true) }
                composable("sleep?log={log}", listOf(navArgument("log") { type = NavType.BoolType; defaultValue = false })) { e ->
                    com.example.tracker.ui.sleep.SleepJournalScreen(openLogger = e.arguments?.getBoolean("log") == true)
                }

                composable("wallet") { LockedSection { WalletHomeScreen(walletVm) { nav.navigate(it) } } }
                composable("wallet/add?id={id}&cat={cat}&type={type}", listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("cat") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null },
                )) { e ->
                    val a = e.arguments!!
                    LockedSection {
                        AddTxnScreen(walletVm, a.getLong("id").takeIf { it > 0 }, onDone = dropUnlessResumed { nav.popBackStack() },
                            preType = a.getString("type")?.let { t -> runCatching { com.example.tracker.data.db.TxnType.valueOf(t) }.getOrNull() },
                            preCategory = a.getLong("cat").takeIf { it > 0 })
                    }
                }
                composable("wallet/list?account={account}", listOf(navArgument("account") { type = NavType.LongType; defaultValue = 0L })) { e ->
                    LockedSection { TxnListScreen(walletVm, { nav.navigate("wallet/add?id=$it") }, e.arguments!!.getLong("account").takeIf { it > 0 }) }
                }
                composable("wallet/report") { LockedSection { ReportScreen(walletVm) } }
                composable("wallet/budgets") { LockedSection { BudgetsScreen(walletVm) } }
                composable("wallet/recurring") { LockedSection { RecurringScreen(walletVm) } }
                composable("wallet/accounts") { LockedSection { AccountsScreen(walletVm) { nav.navigate("wallet/categories") } } }
                composable("wallet/categories") { LockedSection { CategoriesScreen(walletVm) } }

                // Notes & Courses
                composable("courses") {
                    CoursesScreen(
                        showTitle = false,
                        onOpenCourse = { nav.navigate("course/$it") },
                        onCaptureNote = { cid ->
                            nav.navigate(if (cid != null) "notes/capture?courseId=$cid" else "notes/capture")
                        },
                        onBack = dropUnlessResumed { nav.popBackStack() },
                    )
                }
                composable(
                    "course/{courseId}",
                    listOf(navArgument("courseId") { type = NavType.LongType }),
                ) { e ->
                    val cid = e.arguments!!.getLong("courseId")
                    CourseDetailScreen(
                        courseId = cid,
                        onBack = dropUnlessResumed { nav.popBackStack() },
                        onCaptureNote = { nav.navigate("notes/capture?courseId=$cid") },
                        onOpenImageViewer = { noteId, idx ->
                            nav.navigate("notes/viewer/$noteId/$idx")
                        },
                    )
                }
                composable(
                    "notes/capture?courseId={courseId}",
                    listOf(
                        navArgument("courseId") {
                            type = NavType.LongType
                            defaultValue = 0L
                        },
                    ),
                ) { e ->
                    val cid = e.arguments!!.getLong("courseId").takeIf { it > 0 }
                    NoteCaptureScreen(
                        prefilledCourseId = cid,
                        initialSharedUris = pendingShare,
                        onSharedConsumed = { pendingShare = emptyList() },
                        onOpenCourses = { nav.navigate("courses") },
                        onSaved = dropUnlessResumed { nav.popBackStack() },
                        onBack = dropUnlessResumed { nav.popBackStack() },
                    )
                }
                composable(
                    "notes/viewer/{noteId}/{idx}",
                    listOf(
                        navArgument("noteId") { type = NavType.LongType },
                        navArgument("idx") { type = NavType.IntType; defaultValue = 0 },
                    ),
                ) { e ->
                    val noteId = e.arguments!!.getLong("noteId")
                    val idx = e.arguments!!.getInt("idx")
                    NoteImageViewerScreen(
                        noteId = noteId,
                        initialIndex = idx,
                        onBack = dropUnlessResumed { nav.popBackStack() },
                    )
                }

                // App Blocker & Screen Time
                composable("blocker") {
                    BlockerScreen(
                        onOpenRule = { id ->
                            nav.navigate(if (id != null) "blocker/rule?id=$id" else "blocker/rule")
                        },
                        onOpenStats = { nav.navigate("blocker/stats") },
                        onBack = dropUnlessResumed { nav.popBackStack() },
                    )
                }
                composable(
                    "blocker/rule?id={id}",
                    listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L }),
                ) { e ->
                    val id = e.arguments!!.getLong("id").takeIf { it > 0 }
                    RuleEditorScreen(
                        ruleId = id,
                        onDone = dropUnlessResumed { nav.popBackStack() },
                        onBack = dropUnlessResumed { nav.popBackStack() },
                    )
                }
                composable("blocker/stats") {
                    AppUsageStatsScreen(onBack = dropUnlessResumed { nav.popBackStack() })
                }

                // Progress Reports & Share Cards
                composable(
                    "reports/daily?date={date}",
                    listOf(navArgument("date") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { e ->
                    val dStr = e.arguments?.getString("date")
                    val d = if (!dStr.isNullOrBlank()) LocalDate.parse(dStr) else LocalDate.now(Schedule.ZONE)
                    DailyReportScreen(
                        date = d,
                        onOpenCardStudio = { type -> nav.navigate("reports/card?type=$type&date=$d") },
                        onBack = dropUnlessResumed { nav.popBackStack() },
                    )
                }
                composable("reports/daily") {
                    DailyReportScreen(
                        date = LocalDate.now(Schedule.ZONE),
                        onOpenCardStudio = { type -> nav.navigate("reports/card?type=$type") },
                        onBack = dropUnlessResumed { nav.popBackStack() },
                    )
                }

                composable(
                    "reports/weekly?week={week}",
                    listOf(navArgument("week") { type = NavType.IntType; defaultValue = 0 }),
                ) { e ->
                    val wArg = e.arguments?.getInt("week") ?: 0
                    val w = if (wArg > 0) wArg else PlanCalendar.weekNumber(LocalDate.now(Schedule.ZONE))
                    WeeklyReportScreen(
                        initialWeek = w,
                        onOpenCardStudio = { type, weekNo -> nav.navigate("reports/card?type=$type&week=$weekNo") },
                        onOpenThirtyDayReport = { nav.navigate("reports/thirty_day") },
                        onBack = dropUnlessResumed { nav.popBackStack() },
                    )
                }
                composable("reports/weekly") {
                    WeeklyReportScreen(
                        initialWeek = PlanCalendar.weekNumber(LocalDate.now(Schedule.ZONE)),
                        onOpenCardStudio = { type, weekNo -> nav.navigate("reports/card?type=$type&week=$weekNo") },
                        onOpenThirtyDayReport = { nav.navigate("reports/thirty_day") },
                        onBack = dropUnlessResumed { nav.popBackStack() },
                    )
                }

                composable("reports/thirty_day") {
                    ThirtyDayReportScreen(
                        onOpenCardStudio = { type -> nav.navigate("reports/card?type=$type") },
                        onBack = dropUnlessResumed { nav.popBackStack() },
                    )
                }

                composable(
                    "reports/card?type={type}&date={date}&week={week}",
                    listOf(
                        navArgument("type") { type = NavType.StringType; defaultValue = "DAILY" },
                        navArgument("date") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("week") { type = NavType.IntType; defaultValue = 0 },
                    ),
                ) { e ->
                    val typeStr = e.arguments?.getString("type") ?: "DAILY"
                    val type = runCatching { CardType.valueOf(typeStr) }.getOrDefault(CardType.DAILY)
                    val dStr = e.arguments?.getString("date")
                    val d = if (!dStr.isNullOrBlank()) LocalDate.parse(dStr) else LocalDate.now(Schedule.ZONE)
                    val wArg = e.arguments?.getInt("week") ?: 0
                    val w = if (wArg > 0) wArg else PlanCalendar.weekNumber(LocalDate.now(Schedule.ZONE))
                    ShareCardStudioScreen(
                        initialType = type,
                        date = d,
                        week = w,
                        onBack = dropUnlessResumed { nav.popBackStack() },
                    )
                }
                composable("reports/card") {
                    ShareCardStudioScreen(
                        initialType = CardType.DAILY,
                        date = LocalDate.now(Schedule.ZONE),
                        week = PlanCalendar.weekNumber(LocalDate.now(Schedule.ZONE)),
                        onBack = dropUnlessResumed { nav.popBackStack() },
                    )
                }
            }
        }
    }
    }
    }
}

private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}


private val TAB_ROUTES = setOf("today", "calendar", "focus", "wallet", "more")

/**
 * Opens a route from a notification, widget or share. Tab routes switch tabs instead of stacking a
 * second copy on top (which left two "Today"s / "Wallet"s in the back stack); a tab with arguments
 * (e.g. focus?note=…) opens straight above the start screen; everything else stacks on top.
 */
private fun NavHostController.openDeepLink(route: String) {
    val base = route.substringBefore('?')
    when {
        route in TAB_ROUTES -> goTab(route)
        base in TAB_ROUTES -> navigate(route) {
            popUpTo(graph.findStartDestination().id)
            launchSingleTop = true
        }
        else -> navigate(route) { launchSingleTop = true }
    }
}

private fun isTabSwitch(from: String?, to: String?): Boolean {
    fun base(r: String?) = r?.substringBefore('?')
    return base(from) in TAB_ROUTES && base(to) in TAB_ROUTES
}
