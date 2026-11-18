package com.example.tracker.ui.report

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tracker.ui.i18n.Text
import com.example.tracker.ui.stats.StatsScreen
import java.time.LocalDate

/** The four views of "how am I doing" — they used to be seven separate screens. */
enum class ProgressTab(val label: String) { DAY("Today"), WEEK("Week"), PLAN("Plan"), HABITS("Habits") }

/**
 * One place for progress: today's score, the week (with the review), the whole plan, and habit
 * success rates. Each view keeps its share-card button; [nav] opens the card studio, the review
 * editor and the day-by-day grid.
 */
@Composable
fun ProgressScreen(
    initialTab: ProgressTab,
    date: LocalDate,
    week: Int,
    nav: (String) -> Unit,
) {
    var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) } // a new link to another tab switches to it
    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            ProgressTab.entries.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = tab == t,
                    onClick = { tab = t },
                    shape = SegmentedButtonDefaults.itemShape(i, ProgressTab.entries.size),
                ) { Text(t.label, maxLines = 1) }
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                ProgressTab.DAY -> DailyReportScreen(
                    date = date,
                    onOpenCardStudio = { type -> nav("reports/card?type=$type&date=$date") },
                    onBack = {},
                    embedded = true,
                )
                ProgressTab.WEEK -> WeeklyReportScreen(
                    initialWeek = week,
                    onOpenCardStudio = { type, w -> nav("reports/card?type=$type&week=$w") },
                    onOpenThirtyDayReport = { tab = ProgressTab.PLAN },
                    onBack = {},
                    embedded = true,
                    onWriteReview = { w -> nav("review/$w") },
                )
                ProgressTab.PLAN -> ThirtyDayReportScreen(
                    onOpenCardStudio = { type -> nav("reports/card?type=$type") },
                    onBack = {},
                    embedded = true,
                    onOpenGrid = { nav("plan") },
                )
                ProgressTab.HABITS -> StatsScreen(onOpenPlan = { nav("plan") })
            }
        }
    }
}
