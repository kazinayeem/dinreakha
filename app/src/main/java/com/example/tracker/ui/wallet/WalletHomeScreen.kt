package com.example.tracker.ui.wallet

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.tracker.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tracker.data.db.Txn
import com.example.tracker.data.db.TxnType
import com.example.tracker.data.finance.FinanceMath
import com.example.tracker.data.finance.Money
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.ui.components.EmptyState
import com.example.tracker.ui.components.HM
import com.example.tracker.ui.components.LabeledProgress
import com.example.tracker.ui.components.SHORT_DATE
import com.example.tracker.ui.components.SectionCard
import com.example.tracker.ui.theme.Expense
import com.example.tracker.ui.theme.Income
import com.example.tracker.ui.theme.Warn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val MONTH_YEAR_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy")

@Composable
fun WalletHomeScreen(vm: WalletViewModel, nav: (String) -> Unit) {
    val data by vm.data.collectAsStateWithLifecycle()
    val d = data ?: return
    val today = LocalDate.now(Schedule.ZONE)
    val month = YearMonth.now(Schedule.ZONE)
    val cur = FinanceMath.monthTotals(month, d.txns)
    val dueToday = d.recurring.filter {
        it.active && it.lastConfirmedMonth != month.toString() && !FinanceMath.dueDate(month, it.dayOfMonth).isAfter(today)
    }

    var showBalance by rememberSaveable { mutableStateOf(true) }
    // Last 7 days of spending for the hero chart
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val weekSpend = week.map { FinanceMath.spentOn(it, d.txns) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ---- 1. Header: title, month, privacy + report ----
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Wallet", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                    Text(month.format(MONTH_YEAR_FORMAT), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showBalance = !showBalance }) {
                    Icon(if (showBalance) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        if (showBalance) "Hide amounts" else "Show amounts")
                }
                IconButton(onClick = { nav("wallet/report") }) { Icon(Icons.Rounded.PieChart, "Monthly report") }
            }
        }

        // ---- 2. Hero balance card ----
        item {
            HeroBalanceCard(
                total = d.total, income = cur.income, expense = cur.expense,
                week = week, weekSpend = weekSpend, today = today, show = showBalance,
                onClick = { nav("wallet/report") },
            )
        }

        // ---- 3. Primary actions ----
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                QuickActionButton(Icons.Rounded.NorthEast, "Expense", Expense, Expense.copy(alpha = 0.14f),
                    { nav("wallet/add?type=EXPENSE") }, Modifier.weight(1f))
                QuickActionButton(Icons.Rounded.SouthWest, "Income", Income, Income.copy(alpha = 0.14f),
                    { nav("wallet/add?type=INCOME") }, Modifier.weight(1f))
                QuickActionButton(Icons.Rounded.SwapHoriz, "Transfer", MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), { nav("wallet/add?type=TRANSFER") }, Modifier.weight(1f))
                QuickActionButton(Icons.Rounded.Savings, "Budgets", Warn, Warn.copy(alpha = 0.14f),
                    { nav("wallet/budgets") }, Modifier.weight(1f))
            }
        }

        // ---- 4. Bills due — act on these first ----
        if (dueToday.isNotEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = Warn.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Autorenew, null, tint = Warn, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Bills due", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        dueToday.forEach { r ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(d.categoryById[r.categoryId]?.icon ?: "🧾", fontSize = 22.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(r.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                                    Text(Money.format(r.amountPaisa), style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                FilledTonalButton(onClick = { vm.confirmRecurring(r.id) }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Pay") }
                            }
                        }
                    }
                }
            }
        }

        // ---- 5. Quick add by category ----
        item {
            val since = today.minusDays(90).atStartOfDay()
            val topCats = d.txns
                .filter { it.type == TxnType.EXPENSE && it.categoryId != null && it.dateTime.isAfter(since) }
                .groupingBy { it.categoryId!! }.eachCount().entries
                .sortedByDescending { it.value }.take(6)
                .mapNotNull { d.categoryById[it.key] }
                .ifEmpty { d.categories.filter { it.kind == com.example.tracker.data.db.CategoryKind.EXPENSE }.take(6) }
            if (topCats.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    topCats.forEach { cat ->
                        Surface(onClick = { nav("wallet/add?cat=${cat.id}") }, shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Row(Modifier.heightIn(min = 44.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(cat.icon, fontSize = 18.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(cat.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }

        // ---- 6. Accounts ----
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Accounts", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                    TextButton(onClick = { nav("wallet/accounts") }) { Text("Manage") }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    d.activeAccounts.forEach { a ->
                        AccountCard(account = a, balance = d.balances[a.id] ?: 0,
                            onClick = { nav("wallet/list?account=${a.id}") }, onLongClick = { nav("wallet/accounts") })
                    }
                    AddAccountCard(width = 140.dp) { nav("wallet/accounts") }
                }
            }
        }

        // ---- 7. Budgets ----
        if (d.budgets.isNotEmpty() && d.month == month) {
            item {
                SectionCard(title = "Budgets", icon = Icons.Rounded.Savings,
                    trailing = { TextButton(onClick = { nav("wallet/budgets") }) { Text("All") } }) {
                    d.budgets.map { b -> b to (cur.byCategory[b.categoryId] ?: 0L) }
                        .sortedByDescending { (b, spent) -> FinanceMath.budgetPercent(spent, b.limitPaisa) }
                        .take(3)
                        .forEach { (b, spent) ->
                            val pct = FinanceMath.budgetPercent(spent, b.limitPaisa)
                            val cat = d.categoryById[b.categoryId]
                            LabeledProgress(
                                label = "${cat?.icon ?: ""} ${cat?.name ?: ""}",
                                value = "${Money.format(spent)} / ${Money.format(b.limitPaisa)}",
                                progress = pct / 100f,
                                color = when { pct >= 100 -> Expense; pct >= 80 -> Warn; else -> MaterialTheme.colorScheme.primary },
                            )
                        }
                }
            }
        }

        // ---- 8. Recent activity ----
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                TextButton(onClick = { nav("wallet/list") }) { Text("See all") }
            }
            if (d.txns.isEmpty()) {
                EmptyState(icon = Icons.AutoMirrored.Rounded.ReceiptLong, title = "No transactions yet",
                    body = "Tap Expense or Income to record your first one.")
            }
        }

        val recent = d.txns.filter { it.linkedTxnId == null || it.type == TxnType.TRANSFER }.take(20)
        recent.groupBy { it.dateTime.toLocalDate() }.entries.take(7).forEach { (day, list) ->
            item(key = "day_$day") {
                val spent = FinanceMath.spentOn(day, d.txns)
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (day) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> SHORT_DATE.format(day) },
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (spent > 0) Text(if (showBalance) "−${Money.format(spent)}" else "••••",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            list.forEachIndexed { i, t ->
                                TxnRow(t = t, d = d, onClick = { nav("wallet/add?id=${t.id}") }, hidden = !showBalance)
                                if (i < list.lastIndex) HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                    modifier = Modifier.padding(start = 66.dp, end = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hero: deep palette gradient, the balance large, this month's in/out, and 7 bars of daily spending
 * (today highlighted). Amounts hide with the eye toggle.
 */
@Composable
private fun HeroBalanceCard(
    total: Long, income: Long, expense: Long,
    week: List<LocalDate>, weekSpend: List<Long>, today: LocalDate, show: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val on = cs.onPrimary
    Surface(onClick = onClick, shape = RoundedCornerShape(28.dp), color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(cs.primary, lerpColor(cs.primary, cs.tertiary, 0.55f))))
                .padding(22.dp),
        ) {
            Text("Total balance", style = MaterialTheme.typography.bodyMedium, color = on.copy(alpha = 0.8f))
            Text(
                if (show) Money.format(total) else "৳ ••••••",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em),
                color = on, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroFlow(Icons.Rounded.SouthWest, "In", if (show) Money.format(income) else "••••", on, Modifier.weight(1f))
                HeroFlow(Icons.Rounded.NorthEast, "Out", if (show) Money.format(expense) else "••••", on, Modifier.weight(1f))
            }
            Spacer(Modifier.height(18.dp))
            // 7-day spending bars
            val max = (weekSpend.maxOrNull() ?: 0L).coerceAtLeast(1L)
            Row(Modifier.fillMaxWidth().height(56.dp).semantics {
                contentDescription = "Spending, last 7 days: " + week.zip(weekSpend).joinToString { (d, v) -> "${d.dayOfWeek.name.take(3)} ${Money.format(v)}" }
            }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                week.zip(weekSpend).forEach { (day, v) ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight((v.toFloat() / max).coerceAtLeast(0.06f))
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (day == today) on else on.copy(alpha = 0.35f)),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                week.forEach { day ->
                    Text(day.dayOfWeek.name.take(1), Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium, color = on.copy(alpha = if (day == today) 1f else 0.7f))
                }
            }
        }
    }
}

@Composable
private fun HeroFlow(icon: ImageVector, label: String, value: String, on: Color, modifier: Modifier) {
    Row(modifier.clip(RoundedCornerShape(16.dp)).background(on.copy(alpha = 0.14f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(on.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = on, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = on.copy(alpha = 0.8f))
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = on, maxLines = 1)
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

/** Individual circular action button with caption. */
@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = background,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Modern transaction row designed to sit cleanly inside unified card containers.
 */
@Composable
fun TxnRow(t: Txn, d: WalletData, hidden: Boolean = false, onClick: () -> Unit) {
    val cat = t.categoryId?.let { d.categoryById[it] }
    val acc = d.accountById[t.accountId]
    val toAcc = t.toAccountId?.let { d.accountById[it] }

    val (icon, title) = when (t.type) {
        TxnType.TRANSFER -> "" to "${acc?.name ?: "Account"} → ${toAcc?.name ?: "Account"}"
        TxnType.ADJUSTMENT -> "" to "Balance Adjustment"
        else -> (cat?.icon ?: "•") to (cat?.name ?: "Uncategorised")
    }

    val (amountText, amountColor) = when (t.type) {
        TxnType.INCOME -> Money.format(t.amountPaisa, showSign = true) to Income
        TxnType.EXPENSE -> Money.format(-t.amountPaisa) to Expense
        TxnType.ADJUSTMENT -> Money.format(t.amountPaisa, showSign = true) to MaterialTheme.colorScheme.onSurface
        TxnType.TRANSFER -> Money.format(t.amountPaisa) to MaterialTheme.colorScheme.onSurface
    }

    val meta = buildList {
        if (t.type == TxnType.INCOME || t.type == TxnType.EXPENSE) {
            acc?.name?.let { add(it) }
        }
        add(HM.format(t.dateTime))
        if (t.note.isNotBlank()) add(t.note)
    }.joinToString(", ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Squircle Icon Badge
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    when (t.type) {
                        TxnType.INCOME -> Income.copy(alpha = 0.12f)
                        TxnType.EXPENSE -> Expense.copy(alpha = 0.12f)
                        TxnType.TRANSFER -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        TxnType.ADJUSTMENT -> MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (t.type) {
                TxnType.TRANSFER -> Icon(
                    imageVector = Icons.Rounded.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                TxnType.ADJUSTMENT -> Icon(
                    imageVector = Icons.Rounded.Balance,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                else -> Text(text = icon, fontSize = 18.sp)
            }
        }

        Spacer(Modifier.width(12.dp))

        // Main details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Amount
        Text(
            text = if (hidden) "••••" else amountText,
            color = amountColor,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}
