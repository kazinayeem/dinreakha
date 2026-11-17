package com.example.tracker.ui.wallet

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import com.example.tracker.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tracker.data.db.Account
import com.example.tracker.data.db.AccountType
import com.example.tracker.data.db.Category
import com.example.tracker.data.db.CategoryKind
import com.example.tracker.data.db.Recurring
import com.example.tracker.data.finance.FinanceMath
import com.example.tracker.data.finance.Money
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.ui.components.Gap
import com.example.tracker.ui.components.LabeledProgress
import com.example.tracker.ui.components.SectionCard
import com.example.tracker.ui.theme.Expense
import com.example.tracker.ui.theme.Warn
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ---------------- Budgets ----------------

@Composable
fun BudgetsScreen(vm: WalletViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val d = data ?: return
    var editing by remember { mutableStateOf<Category?>(null) }
    val totals = FinanceMath.monthTotals(d.month, d.txns)
    val budgetByCat = d.budgets.associateBy { it.categoryId }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Budgets for ${d.month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        items(d.categories.filter { it.kind == CategoryKind.EXPENSE }, key = { it.id }) { cat ->
            val spent = totals.byCategory[cat.id] ?: 0
            val b = budgetByCat[cat.id]
            Surface(
                onClick = { editing = cat },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (b == null) {
                        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${cat.icon} ${cat.name}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                            Text("${Money.format(spent)} spent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        val pct = FinanceMath.budgetPercent(spent, b.limitPaisa)
                        LabeledProgress("${cat.icon} ${cat.name}", "${Money.format(spent)} / ${Money.format(b.limitPaisa)}",
                            pct / 100f, when { pct >= 100 -> Expense; pct >= 80 -> Warn; else -> MaterialTheme.colorScheme.primary })
                    }
                }
            }
        }
    }

    editing?.let { cat ->
        val existing = budgetByCat[cat.id]
        var text by remember(cat.id) { mutableStateOf(existing?.let { Money.toInput(it.limitPaisa) } ?: "") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("${cat.icon} ${cat.name} limit") },
            text = {
                OutlinedTextField(text, { text = it }, label = { Text("Monthly limit (৳) — empty = no budget") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            },
            confirmButton = { TextButton(onClick = { vm.setBudget(cat.id, Money.parse(text)); editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

// ---------------- Recurring ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurringScreen(vm: WalletViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val d = data ?: return
    var editing by remember { mutableStateOf<Recurring?>(null) }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Recurring", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Gap(8.dp)
            Button(onClick = {
                editing = Recurring(title = "", amountPaisa = 0, categoryId = d.categories.firstOrNull { it.kind == CategoryKind.EXPENSE }?.id ?: 0,
                    accountId = d.activeAccounts.firstOrNull()?.id ?: 0, dayOfMonth = 1)
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text("+ New recurring item") }
        }
        items(d.recurring, key = { it.id }) { r ->
            val confirmed = r.lastConfirmedMonth == java.time.YearMonth.now(Schedule.ZONE).toString()
            Surface(onClick = { editing = r }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${d.categoryById[r.categoryId]?.icon ?: "🔁"} ${r.title}", style = MaterialTheme.typography.bodyLarge)
                        Text("Day ${r.dayOfMonth}, from ${d.accountById[r.accountId]?.name ?: "?"}" +
                            (if (!r.active) ", paused" else if (confirmed) ", added this month" else ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Money.format(r.amountPaisa), fontWeight = FontWeight.SemiBold)
                    if (r.active && !confirmed) TextButton(onClick = { vm.confirmRecurring(r.id) }) { Text("Add ✓") }
                }
            }
        }
    }

    editing?.let { r ->
        var title by remember(r.id) { mutableStateOf(r.title) }
        var amount by remember(r.id) { mutableStateOf(if (r.amountPaisa == 0L) "" else Money.toInput(r.amountPaisa)) }
        var day by remember(r.id) { mutableStateOf(r.dayOfMonth.toString()) }
        var cat by remember(r.id) { mutableStateOf(r.categoryId) }
        var acc by remember(r.id) { mutableStateOf(r.accountId) }
        var active by remember(r.id) { mutableStateOf(r.active) }
        val paisa = Money.parse(amount)
        val dom = day.toIntOrNull()
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (r.id == 0L) "New recurring" else "Edit recurring") },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    OutlinedTextField(title, { title = it }, label = { Text("Title (e.g. Rent)") }, singleLine = true)
                    OutlinedTextField(amount, { amount = it }, label = { Text("Amount ৳") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(day, { day = it }, label = { Text("Day of month (1–31)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Gap(6.dp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        d.categories.filter { it.kind == CategoryKind.EXPENSE }.forEach { c ->
                            FilterChip(cat == c.id, { cat = c.id }, { Text("${c.icon} ${c.name}") })
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        d.activeAccounts.forEach { a -> FilterChip(acc == a.id, { acc = a.id }, { Text(a.name) }) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Active", Modifier.weight(1f)); Switch(active, { active = it }) }
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank() && paisa != null && paisa > 0 && dom != null && dom in 1..31 && acc > 0,
                    onClick = {
                        vm.saveRecurring(r.copy(title = title.trim(), amountPaisa = paisa!!, dayOfMonth = dom!!,
                            categoryId = cat, accountId = acc, active = active))
                        editing = null
                    }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (r.id != 0L) TextButton(onClick = { vm.deleteRecurring(r.id); editing = null }) { Text("Delete", color = Expense) }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            },
        )
    }
}


// ---------------- Accounts ----------------

private val ACCOUNT_COLORS = listOf(0xFF3E8E63, 0xFF4F7CAC, 0xFFE2136E, 0xFFF26522, 0xFF8C3FBF, 0xFFB08D57, 0xFF5F6B7A)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountsScreen(vm: WalletViewModel, onCategories: () -> Unit) {
    val ctx = LocalContext.current
    val data by vm.data.collectAsStateWithLifecycle()
    val d = data ?: return
    var editing by remember { mutableStateOf<Account?>(null) }
    var adjusting by remember { mutableStateOf<Account?>(null) }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Accounts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { editing = Account(name = "", type = AccountType.BKASH, openingDate = LocalDate.now(Schedule.ZONE)) },
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("+ Add account") }
                TextButton(onClick = onCategories) { Text("Edit categories") }
            }
        }
        items(d.accounts, key = { it.id }) { a ->
            Surface(
                onClick = { editing = a },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(a.color).copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = a.type.icon(),
                            contentDescription = null,
                            tint = Color(a.color),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(a.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                            if (a.archived) {
                                Text(" (archived)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text("${a.type.label}${a.last4?.let { " ••$it" } ?: ""}, opened ${a.openingDate}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Money.format(d.balances[a.id] ?: 0), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                        TextButton(onClick = { adjusting = a }) { Text("Adjust") }
                    }
                }
            }
        }
    }

    editing?.let { a ->
        var name by remember(a.id) { mutableStateOf(a.name) }
        var type by remember(a.id) { mutableStateOf(a.type) }
        var opening by remember(a.id) { mutableStateOf(if (a.openingBalancePaisa == 0L) "" else Money.toInput(a.openingBalancePaisa)) }
        var last4 by remember(a.id) { mutableStateOf(a.last4 ?: "") }
        var color by remember(a.id) { mutableStateOf(a.color) }
        var archived by remember(a.id) { mutableStateOf(a.archived) }
        val openPaisa = Money.parse(opening.ifBlank { "0" })
        val last4ok = last4.isEmpty() || (last4.length == 4 && last4.all(Char::isDigit))
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (a.id == 0L) "New account" else "Edit account") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nickname (e.g. DBBL salary)") }, singleLine = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AccountType.entries.forEach { t -> FilterChip(type == t, { type = t }, { Text("${t.emoji} ${t.label}") }) }
                    }
                    OutlinedTextField(opening, { opening = it }, label = { Text("Opening balance ৳") }, singleLine = true,
                        isError = openPaisa == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(last4, { if (it.length <= 4) last4 = it.filter(Char::isDigit) }, label = { Text("Last 4 digits (optional)") },
                        singleLine = true, isError = !last4ok, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    Gap(6.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ACCOUNT_COLORS.forEach { col ->
                            Box(Modifier.size(if (color == col) 34.dp else 28.dp).clip(CircleShape).background(Color(col))
                                .clickable { color = col })
                        }
                    }
                    if (a.id != 0L) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Archived (hidden, history kept)", Modifier.weight(1f)); Switch(archived, { archived = it })
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && openPaisa != null && last4ok, onClick = {
                    vm.saveAccount(a.copy(name = name.trim(), type = type, openingBalancePaisa = openPaisa!!,
                        last4 = last4.ifEmpty { null }, color = color, archived = archived))
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (a.id != 0L) TextButton(onClick = {
                        vm.archiveOrDelete(a) { deleted ->
                            Toast.makeText(ctx, com.example.tracker.ui.i18n.tr(if (deleted) "Deleted" else "Has transactions — archived instead"), Toast.LENGTH_SHORT).show()
                        }
                        editing = null
                    }) { Text("Delete", color = Expense) }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            },
        )
    }

    adjusting?.let { a ->
        var actual by remember(a.id) { mutableStateOf("") }
        val p = Money.parse(actual)
        val cur = d.balances[a.id] ?: 0
        AlertDialog(
            onDismissRequest = { adjusting = null },
            title = { Text("Adjust ${a.name}") },
            text = {
                Column {
                    Text("Now: ${Money.format(cur)}")
                    OutlinedTextField(actual, { actual = it }, label = { Text("Real balance ৳") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    p?.let { Text("Difference: ${Money.format(it - cur, showSign = true)}") }
                }
            },
            confirmButton = { TextButton(enabled = p != null && p != cur, onClick = { vm.adjust(a, p!!, ""); adjusting = null }) { Text("Adjust") } },
            dismissButton = { TextButton(onClick = { adjusting = null }) { Text("Cancel") } },
        )
    }
}


// ---------------- Categories ----------------

@Composable
fun CategoriesScreen(vm: WalletViewModel) {
    val ctx = LocalContext.current
    val data by vm.data.collectAsStateWithLifecycle()
    val d = data ?: return
    var editing by remember { mutableStateOf<Category?>(null) }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Text("Categories", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { editing = Category(name = "", icon = "🏷️", kind = CategoryKind.EXPENSE) }) { Text("+ Expense category") }
                Button(onClick = { editing = Category(name = "", icon = "💰", kind = CategoryKind.INCOME) }) { Text("+ Income") }
            }
        }
        CategoryKind.entries.forEach { kind ->
            item { Text(kind.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 10.dp)) }
            items(d.categories.filter { it.kind == kind }, key = { it.id }) { c ->
                Surface(onClick = { editing = c }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Text("${c.icon}  ${c.name}", Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(14.dp))
                }
            }
        }
    }
    editing?.let { c ->
        var name by remember(c.id, c.kind) { mutableStateOf(c.name) }
        var icon by remember(c.id, c.kind) { mutableStateOf(c.icon) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (c.id == 0L) "New category" else "Edit category") },
            text = {
                Column {
                    OutlinedTextField(icon, { icon = it.take(4) }, label = { Text("Icon (emoji)") }, singleLine = true)
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(enabled = name.isNotBlank() && icon.isNotBlank(), onClick = {
                vm.saveCategory(c.copy(name = name.trim(), icon = icon.trim())); editing = null }) { Text("Save") } },
            dismissButton = {
                Row {
                    if (c.id != 0L) TextButton(onClick = {
                        vm.deleteCategory(c) { ok -> if (!ok) Toast.makeText(ctx, com.example.tracker.ui.i18n.tr("In use by transactions — kept"), Toast.LENGTH_SHORT).show() }
                        editing = null
                    }) { Text("Delete", color = Expense) }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            },
        )
    }
}
