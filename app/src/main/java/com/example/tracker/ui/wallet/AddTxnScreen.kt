package com.example.tracker.ui.wallet

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import com.example.tracker.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tracker.data.db.CategoryKind
import com.example.tracker.data.db.Txn
import com.example.tracker.data.db.TxnType
import com.example.tracker.data.finance.Money
import com.example.tracker.data.schedule.Schedule
import com.example.tracker.ui.components.LocalFeedback
import com.example.tracker.ui.components.SHORT_DATE
import com.example.tracker.ui.theme.Expense
import com.example.tracker.ui.theme.Income
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

private val TABS = listOf(TxnType.EXPENSE, TxnType.INCOME, TxnType.TRANSFER, TxnType.ADJUSTMENT)

private fun TxnType.label() = when (this) {
    TxnType.EXPENSE -> "Expense"
    TxnType.INCOME -> "Income"
    TxnType.TRANSFER -> "Transfer"
    TxnType.ADJUSTMENT -> "Adjust"
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddTxnScreen(
    vm: WalletViewModel,
    txnId: Long?,
    onDone: () -> Unit,
    preType: TxnType? = null,
    preCategory: Long? = null,
) {
    val data by vm.data.collectAsStateWithLifecycle()
    val d = data ?: return
    val feedback = LocalFeedback.current
    val haptics = LocalHapticFeedback.current

    var type by rememberSaveable { mutableStateOf(preType ?: TxnType.EXPENSE) }
    var amount by rememberSaveable { mutableStateOf("") }
    var fee by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableLongStateOf(preCategory ?: -1L) }
    var accountId by rememberSaveable {
        mutableLongStateOf(
            d.txns.firstOrNull { t -> t.type != TxnType.ADJUSTMENT && d.accountById[t.accountId]?.archived == false }?.accountId
                ?: d.activeAccounts.firstOrNull()?.id ?: -1L
        )
    }
    var toAccountId by rememberSaveable { mutableLongStateOf(-1L) }
    var note by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now(Schedule.ZONE).toString()) }
    var time by rememberSaveable { mutableStateOf(LocalDateTime.now(Schedule.ZONE).toLocalTime().withSecond(0).withNano(0).toString()) }
    var loadedId by rememberSaveable { mutableLongStateOf(0L) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // Set on the first Save/Delete: a second tap during the exit animation used to save a duplicate.
    var submitted by remember { mutableStateOf(false) }

    // Load transaction once when editing
    LaunchedEffect(txnId, d.txns.size) {
        if (txnId == null || loadedId == txnId) return@LaunchedEffect
        val t = d.txns.firstOrNull { it.id == txnId } ?: return@LaunchedEffect
        val base = if (t.type == TxnType.EXPENSE && t.linkedTxnId != null) {
            d.txns.firstOrNull { it.id == t.linkedTxnId && it.type == TxnType.TRANSFER } ?: t
        } else {
            t
        }
        type = base.type
        amount = Money.toInput(if (base.type == TxnType.ADJUSTMENT) (d.balances[base.accountId] ?: 0) else base.amountPaisa)
        categoryId = base.categoryId ?: -1
        accountId = base.accountId
        toAccountId = base.toAccountId ?: -1
        note = base.note
        date = base.dateTime.toLocalDate().toString()
        time = base.dateTime.toLocalTime().toString()
        if (base.type == TxnType.TRANSFER) {
            fee = base.linkedTxnId?.let { id -> d.txns.firstOrNull { it.id == id } }
                ?.let { Money.toInput(it.amountPaisa) } ?: ""
        }
        loadedId = base.id
    }
    val editingId = if (txnId != null) loadedId else 0L

    val paisa = Money.parse(amount)
    val feePaisa = Money.parse(fee.ifBlank { "0" }) ?: -1
    val kind = if (type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val usage = remember(d.txns) { d.txns.filter { it.categoryId != null }.groupingBy { it.categoryId!! }.eachCount() }
    val cats = d.categories.filter { it.kind == kind }.sortedByDescending { usage[it.id] ?: 0 }
    val accounts = d.activeAccounts.ifEmpty { d.accounts }
    val adjAccount = d.accountById[accountId]
    val adjDiff = if (type == TxnType.ADJUSTMENT && paisa != null && adjAccount != null) paisa - (d.balances[accountId] ?: 0) else null

    val valid = when (type) {
        TxnType.EXPENSE, TxnType.INCOME -> paisa != null && paisa > 0 && categoryId > 0 && accountId > 0
        TxnType.TRANSFER -> paisa != null && paisa > 0 && accountId > 0 && toAccountId > 0 && accountId != toAccountId && feePaisa >= 0
        TxnType.ADJUSTMENT -> paisa != null && adjAccount != null && adjDiff != 0L
    }

    val typeColor by animateColorAsState(
        targetValue = when (type) {
            TxnType.EXPENSE -> Expense
            TxnType.INCOME -> Income
            TxnType.TRANSFER -> MaterialTheme.colorScheme.primary
            TxnType.ADJUSTMENT -> MaterialTheme.colorScheme.secondary
        },
        animationSpec = tween(250),
        label = "typeColor",
    )

    fun save() {
        if (submitted) return
        submitted = true
        val at = LocalDate.parse(date).atTime(LocalTime.parse(time))
        when (type) {
            TxnType.EXPENSE, TxnType.INCOME -> vm.saveSimple(
                Txn(
                    id = editingId,
                    type = type,
                    amountPaisa = paisa!!,
                    accountId = accountId,
                    categoryId = categoryId,
                    dateTime = at,
                    note = note.trim(),
                )
            )
            TxnType.TRANSFER -> vm.saveTransfer(editingId, accountId, toAccountId, paisa!!, feePaisa, at, note.trim())
            TxnType.ADJUSTMENT -> if (editingId != 0L) {
                vm.saveSimple(
                    Txn(
                        id = editingId,
                        type = type,
                        amountPaisa = (d.txns.firstOrNull { it.id == editingId }?.amountPaisa ?: 0) + adjDiff!!,
                        accountId = accountId,
                        dateTime = at,
                        note = note.trim(),
                    )
                )
            } else {
                vm.adjust(adjAccount!!, paisa!!, note.trim())
            }
        }
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        feedback?.show(if (editingId != 0L) "Saved" else "${type.label()} saved")
        onDone()
    }

    Column(Modifier.fillMaxSize()) {
        // Modern Segmented Type Switcher
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TABS.forEach { t ->
                    val selected = t == type
                    val icon = when (t) {
                        TxnType.EXPENSE -> Icons.AutoMirrored.Rounded.TrendingDown
                        TxnType.INCOME -> Icons.AutoMirrored.Rounded.TrendingUp
                        TxnType.TRANSFER -> Icons.Rounded.SwapHoriz
                        TxnType.ADJUSTMENT -> Icons.Rounded.Tune
                    }
                    val col = when (t) {
                        TxnType.EXPENSE -> Expense
                        TxnType.INCOME -> Income
                        TxnType.TRANSFER -> MaterialTheme.colorScheme.primary
                        TxnType.ADJUSTMENT -> MaterialTheme.colorScheme.secondary
                    }
                    Surface(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            type = t
                            categoryId = -1
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) col.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            modifier = Modifier.heightIn(min = 48.dp).padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (selected) col else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = t.label(),
                                maxLines = 1,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (selected) col else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        // Scrollable Form Area
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Hero Amount Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = if (type == TxnType.ADJUSTMENT) "Actual balance now" else "Amount",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${Money.SYMBOL}${amount.ifEmpty { "0" }}",
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = typeColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (adjDiff != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Records a difference of ${Money.format(adjDiff, showSign = true)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Category Selector
            if (type == TxnType.EXPENSE || type == TxnType.INCOME) {
                Column {
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
                    )
                    // 4-column grid of big tiles: emoji over name
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        cats.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { cat ->
                                    val selected = categoryId == cat.id
                                    Surface(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            categoryId = cat.id
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (selected) typeColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLow,
                                        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, typeColor) else null,
                                        modifier = Modifier.weight(1f).semantics { contentDescription = cat.name + if (selected) ", selected" else "" },
                                    ) {
                                        Column(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(cat.icon, fontSize = 24.sp)
                                            Spacer(Modifier.height(4.dp))
                                            Text(cat.name, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (selected) typeColor else MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }

            // Account Selector
            Column {
                Text(
                    text = if (type == TxnType.TRANSFER) "From" else "Account",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { a ->
                        val selected = accountId == a.id
                        FilterChip(
                            selected = selected,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                accountId = a.id
                            },
                            label = { Text(a.name) },
                            leadingIcon = { Icon(a.type.icon(), null, Modifier.size(16.dp)) },
                            shape = RoundedCornerShape(10.dp),
                        )
                    }
                }
            }

            // Transfer Destination
            if (type == TxnType.TRANSFER) {
                Column {
                    Text(
                        text = "To",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        accounts.filter { it.id != accountId }.forEach { a ->
                            val selected = toAccountId == a.id
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    toAccountId = a.id
                                },
                                label = { Text(a.name) },
                                leadingIcon = { Icon(a.type.icon(), null, Modifier.size(16.dp)) },
                                shape = RoundedCornerShape(10.dp),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = fee,
                    onValueChange = { fee = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Transfer fee (optional, e.g. bKash cash-out)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                    ),
                    isError = feePaisa < 0,
                    supportingText = { Text("Saved as a separate Fees & Charges expense") },
                )
            }

            // Note Field
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note (optional)") },
                placeholder = { Text("What was this for?") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            // Date Chips
            val todayD = LocalDate.now(Schedule.ZONE)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = LocalDate.parse(date) == todayD,
                    onClick = { date = todayD.toString() },
                    label = { Text("Today") },
                    shape = RoundedCornerShape(10.dp),
                )
                FilterChip(
                    selected = LocalDate.parse(date) == todayD.minusDays(1),
                    onClick = { date = todayD.minusDays(1).toString() },
                    label = { Text("Yesterday") },
                    shape = RoundedCornerShape(10.dp),
                )
                val other = LocalDate.parse(date).let { it != todayD && it != todayD.minusDays(1) }
                FilterChip(
                    selected = other,
                    onClick = { pickDate = true },
                    label = { Text(if (other) SHORT_DATE.format(LocalDate.parse(date)) else "Other Day") },
                    leadingIcon = { Icon(Icons.Rounded.CalendarToday, null, Modifier.size(14.dp)) },
                    shape = RoundedCornerShape(10.dp),
                )
            }

            if (editingId != 0L) {
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(Icons.Rounded.Delete, null, tint = Expense, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete transaction", color = Expense)
                }
            }
        }

        // Tactile Keypad
        Keypad(
            onKey = { k ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                amount = when (k) {
                    "⌫" -> amount.dropLast(1)
                    "." -> if ('.' in amount) amount else amount.ifEmpty { "0" } + "."
                    else -> {
                        val next = if (amount == "0") k else amount + k
                        if (next.substringAfter('.', "").length > 2 || next.length > 12) amount else next
                    }
                }
            }
        )

        // Primary Save Button
        Button(
            onClick = ::save,
            enabled = valid,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .height(54.dp),
        ) {
            Text(
                text = if (editingId != 0L) "Save Changes" else "Save ${type.label()}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
    }

    if (pickDate) {
        val st = rememberDatePickerState(LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    pickDate = false
                }) { Text("OK") }
            },
        ) { DatePicker(st) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this transaction?") },
            text = { Text(if (type == TxnType.TRANSFER) "Its linked fee is deleted too." else "This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    if (submitted) return@TextButton
                    submitted = true
                    vm.deleteWithUndo(editingId, feedback)
                    confirmDelete = false
                    onDone()
                }) { Text("Delete", color = Expense) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Keypad(onKey: (String) -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫"),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { k ->
                    Surface(
                        onClick = { onKey(k) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (k == "⌫") {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                    contentDescription = "Backspace",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                Text(
                                    text = k,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
