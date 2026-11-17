package com.example.tracker.data.finance

import com.example.tracker.data.db.Account
import com.example.tracker.data.db.Txn
import com.example.tracker.data.db.TxnType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** Pure money maths. Balances are always CALCULATED, never stored. */
object FinanceMath {

    /** Effect of one transaction on one account, in paisa. */
    fun effectOn(accountId: Long, t: Txn): Long = when (t.type) {
        TxnType.INCOME -> if (t.accountId == accountId) t.amountPaisa else 0
        TxnType.EXPENSE -> if (t.accountId == accountId) -t.amountPaisa else 0
        TxnType.ADJUSTMENT -> if (t.accountId == accountId) t.amountPaisa else 0
        TxnType.TRANSFER -> when (accountId) {
            t.accountId -> -t.amountPaisa
            t.toAccountId -> t.amountPaisa
            else -> 0
        }
    }

    fun balance(account: Account, txns: List<Txn>): Long =
        account.openingBalancePaisa + txns.sumOf { effectOn(account.id, it) }

    fun balances(accounts: List<Account>, txns: List<Txn>): Map<Long, Long> =
        accounts.associate { it.id to balance(it, txns) }

    /** Total across non-archived accounts. */
    fun total(accounts: List<Account>, txns: List<Txn>): Long =
        accounts.filterNot { it.archived }.sumOf { balance(it, txns) }

    /** Adjustment that makes [current] equal [actual]: the signed difference. */
    fun adjustmentFor(current: Long, actual: Long): Long = actual - current

    /** Builds the transfer and (if fee > 0) the linked fee expense charged to the source account. */
    fun transferWithFee(
        from: Long, to: Long, amount: Long, fee: Long, feeCategoryId: Long,
        at: LocalDateTime, note: String,
    ): Pair<Txn, Txn?> {
        require(from != to) { "Transfer needs two different accounts" }
        require(amount > 0) { "Amount must be positive" }
        require(fee >= 0) { "Fee cannot be negative" }
        val transfer = Txn(type = TxnType.TRANSFER, amountPaisa = amount, accountId = from, toAccountId = to,
            dateTime = at, note = note)
        val feeTxn = if (fee > 0) Txn(type = TxnType.EXPENSE, amountPaisa = fee, accountId = from,
            categoryId = feeCategoryId, dateTime = at, note = "Fee: ${note.ifBlank { "transfer" }}") else null
        return transfer to feeTxn
    }

    // ---- Monthly report ----

    data class MonthTotals(
        val income: Long,
        val expense: Long,
        val byCategory: Map<Long?, Long>,
        val byDay: Map<LocalDate, Long>,
    ) {
        val net get() = income - expense
    }

    /** Income & expense in [month]. Transfers and adjustments are not income or spending. */
    fun monthTotals(month: YearMonth, txns: List<Txn>): MonthTotals {
        val inMonth = txns.filter { YearMonth.from(it.dateTime) == month }
        val expenses = inMonth.filter { it.type == TxnType.EXPENSE }
        return MonthTotals(
            income = inMonth.filter { it.type == TxnType.INCOME }.sumOf { it.amountPaisa },
            expense = expenses.sumOf { it.amountPaisa },
            byCategory = expenses.groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.amountPaisa } },
            byDay = expenses.groupBy { it.dateTime.toLocalDate() }.mapValues { (_, l) -> l.sumOf { it.amountPaisa } },
        )
    }

    fun spentOn(date: LocalDate, txns: List<Txn>): Long =
        txns.filter { it.type == TxnType.EXPENSE && it.dateTime.toLocalDate() == date }.sumOf { it.amountPaisa }

    /** Percent change vs last month, or null when last month was zero. */
    fun changePercent(current: Long, previous: Long): Int? =
        if (previous == 0L) null else (((current - previous) * 100) / previous).toInt()

    // ---- Budgets ----

    /** Whole percent of the budget used (integer maths, may exceed 100). */
    fun budgetPercent(spent: Long, limit: Long): Int =
        if (limit <= 0) 0 else ((spent * 100) / limit).toInt()

    /** Alert thresholds newly crossed by going from [before] to [after] spending. */
    fun crossedThresholds(before: Long, after: Long, limit: Long, thresholds: List<Int> = listOf(80, 100)): List<Int> =
        thresholds.filter { th -> budgetPercent(before, limit) < th && budgetPercent(after, limit) >= th }

    // ---- Recurring ----

    /** Due date of a day-of-month item in [month], clamped to the month's last day (e.g. 31 → 28 Feb). */
    fun dueDate(month: YearMonth, dayOfMonth: Int): LocalDate =
        month.atDay(dayOfMonth.coerceIn(1, month.lengthOfMonth()))

    fun monthKey(m: YearMonth): String = m.toString() // "2026-11"
}
