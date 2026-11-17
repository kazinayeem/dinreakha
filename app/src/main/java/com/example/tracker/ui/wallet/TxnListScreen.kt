package com.example.tracker.ui.wallet

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.example.tracker.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tracker.data.db.TxnType
import com.example.tracker.data.finance.Money
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.ui.components.EmptyState
import com.example.tracker.ui.theme.Expense
import com.example.tracker.ui.theme.Income
import java.time.LocalDate
import java.time.YearMonth

private enum class Range(val label: String) {
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    LAST_90("90 days"),
    ALL("All"),
}

@Composable
fun TxnListScreen(vm: WalletViewModel, onOpen: (Long) -> Unit, preAccount: Long? = null) {
    val data by vm.data.collectAsStateWithLifecycle()
    val d = data ?: return
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf<TxnType?>(null) }
    var range by rememberSaveable { mutableStateOf(Range.THIS_MONTH) }
    var accountId by rememberSaveable { mutableStateOf(preAccount) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }

    val today = LocalDate.now(Schedule.ZONE)
    val (from, to) = when (range) {
        Range.THIS_MONTH -> YearMonth.from(today).atDay(1) to today.plusDays(1)
        Range.LAST_MONTH -> YearMonth.from(today).minusMonths(1).let { it.atDay(1) to it.plusMonths(1).atDay(1) }
        Range.LAST_90 -> today.minusDays(90) to today.plusDays(1)
        Range.ALL -> LocalDate.MIN to LocalDate.MAX
    }

    val list = d.txns.filter { t ->
        val dd = t.dateTime.toLocalDate()
        !dd.isBefore(from) && dd.isBefore(to) &&
            (type == null || t.type == type) &&
            (accountId == null || t.accountId == accountId || t.toAccountId == accountId) &&
            (categoryId == null || t.categoryId == categoryId) &&
            (query.isBlank() || t.note.contains(query, ignoreCase = true))
    }

    val spent = list.filter { it.type == TxnType.EXPENSE }.sumOf { it.amountPaisa }
    val earned = list.filter { it.type == TxnType.INCOME }.sumOf { it.amountPaisa }
    val net = earned - spent

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Search Input with leading icon and clear button
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search transactions or notes") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else null,
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
            )

            // Executive Summary Card
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(18.dp),
                    ),
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Income",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = Money.format(earned),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Income,
                            maxLines = 1,
                        )
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Spent",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = Money.format(spent),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Expense,
                            maxLines = 1,
                        )
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Net",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = Money.format(net, showSign = true),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (net >= 0) Income else Expense,
                            maxLines = 1,
                        )
                    }
                }
            }

            // Date Range Filter Chips
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Range.entries.forEach { r ->
                    FilterChip(
                        selected = range == r,
                        onClick = { range = r },
                        label = { Text(r.label) },
                    )
                }
            }

            // Transaction Type Chips
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = type == null,
                    onClick = { type = null },
                    label = { Text("All types") },
                )
                TxnType.entries.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = { type = if (type == t) null else t },
                        label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            // Account & Category Pickers
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(
                    label = "Account",
                    selected = d.accounts.firstOrNull { it.id == accountId }?.name,
                    options = d.accounts.map { it.id to it.name },
                ) { accountId = it }

                Picker(
                    label = "Category",
                    selected = d.categoryById[categoryId]?.name,
                    options = d.categories.map { it.id to "${it.icon} ${it.name}" },
                ) { categoryId = it }
            }

            // Results count
            Text(
                text = "${list.size} transaction${if (list.size == 1) "" else "s"} found",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, top = 2.dp),
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, top = 6.dp, end = 14.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(list, key = { it.id }) { t ->
                TxnRow(t, d) { onOpen(t.id) }
            }
            if (list.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = "No transactions found",
                        body = "Try adjusting your search terms or clearing some filters.",
                    )
                }
            }
        }
    }
}

@Composable
private fun Picker(label: String, selected: String?, options: List<Pair<Long, String>>, onPick: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = { Text(selected ?: "$label ▾") },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("All ${label.lowercase()}s") },
                onClick = { onPick(null); open = false },
            )
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onPick(id); open = false },
                )
            }
        }
    }
}
