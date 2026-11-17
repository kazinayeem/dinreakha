package com.example.tracker.ui.grid

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.tracker.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tracker.AppContainer
import com.example.tracker.container
import com.example.tracker.data.repo.DayState
import com.example.tracker.data.schedule.PlanCalendar
import com.example.tracker.ui.components.SHORT_DATE
import com.example.tracker.ui.components.weekFrom
import com.example.tracker.ui.today.currentDateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate

class PlanGridViewModel(c: AppContainer) : ViewModel() {
    val state = combine(c.habits.observePlan(), currentDateFlow()) { days, today -> days to today }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun PlanGridScreen(onOpenDay: (LocalDate) -> Unit, onOpenReview: () -> Unit) {
    val c = LocalContext.current.container
    val vm: PlanGridViewModel = viewModel { PlanGridViewModel(c) }
    val st by vm.state.collectAsStateWithLifecycle()
    val (days, today) = st ?: return

    val firstDay by c.settings.settings.collectAsStateWithLifecycle(null)
    val weekOrder = weekFrom(firstDay?.firstDayOfWeek ?: DayOfWeek.SATURDAY)
    val lead = weekOrder.indexOf(PlanCalendar.planStart.dayOfWeek)
    val planDates = (1..PlanCalendar.planDays).map(PlanCalendar::dateOfDay)
    val elapsed = planDates.filter { !it.isAfter(today) }
    val avg = if (elapsed.isEmpty()) 0f else elapsed.map { days[it]?.completion ?: 0f }.average().toFloat()
    val currentDayNum = PlanCalendar.dayNumber(today).coerceIn(1, PlanCalendar.planDays)
    val daysLeft = (PlanCalendar.planDays - currentDayNum).coerceAtLeast(0)

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Executive Summary Card
        item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "30-Day Milestone",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${SHORT_DATE.format(PlanCalendar.planStart)} – ${SHORT_DATE.format(PlanCalendar.planEnd)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        FilledTonalButton(
                            onClick = onOpenReview,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Rounded.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Review", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }

                    // 3 KPI Metric Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MetricPill(
                            title = "Progress",
                            value = "${(avg * 100).toInt()}%",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        MetricPill(
                            title = "Current Day",
                            value = "Day $currentDayNum",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        MetricPill(
                            title = "Remaining",
                            value = "$daysLeft days",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }

                    // Heatmap Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Daily Completion:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(0f to "0%", 0.34f to "33%", 0.67f to "67%", 1f to "100%").forEach { (frac, lbl) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(cellColor(frac)),
                                    )
                                    Text(
                                        text = lbl,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Weekday Column Headers
        items(weekOrder) { d ->
            Text(
                text = d.name.take(2).lowercase().replaceFirstChar { it.uppercase() },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        // Empty lead cells
        items(lead) {
            Box(Modifier.aspectRatio(1f))
        }

        // Day cells
        items(planDates) { date ->
            DayCell(
                date = date,
                state = days[date],
                today = today,
                onClick = { onOpenDay(date) },
            )
        }
    }
}

@Composable
private fun MetricPill(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = color,
            )
        }
    }
}

@Composable
private fun cellColor(p: Float): Color {
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val full = MaterialTheme.colorScheme.primary
    return if (p <= 0f) empty else lerp(empty, full, 0.25f + 0.75f * p)
}

@Composable
private fun DayCell(date: LocalDate, state: DayState?, today: LocalDate, onClick: () -> Unit) {
    val future = date.isAfter(today)
    val isToday = date == today
    val p = state?.completion ?: 0f
    val bg = if (future) MaterialTheme.colorScheme.surfaceContainerLow else cellColor(p)
    val onBg = if (!future && p > 0.45f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val pct = (p * 100).toInt()

    Surface(
        onClick = onClick,
        color = bg,
        shape = RoundedCornerShape(12.dp),
        border = if (isToday) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .aspectRatio(1f)
            .semantics {
                contentDescription = "Day ${PlanCalendar.dayNumber(date)}, ${SHORT_DATE.format(date)}, " +
                    (if (future) "upcoming" else "$pct percent done") + (if (isToday) ", today" else "") +
                    (PlanCalendar.holidayName(date)?.let { ", $it" } ?: "")
            },
    ) {
        Box(
            modifier = Modifier
                .padding(4.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (!future && p >= 1f) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = onBg,
                    )
                }
                Text(
                    text = "${PlanCalendar.dayNumber(date)}",
                    fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Bold,
                    fontSize = 13.sp,
                    color = onBg,
                )
                Text(
                    text = if (PlanCalendar.isHoliday(date)) "off" else "${date.dayOfMonth}/${date.monthValue}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = onBg.copy(alpha = 0.75f),
                )
            }
        }
    }
}
