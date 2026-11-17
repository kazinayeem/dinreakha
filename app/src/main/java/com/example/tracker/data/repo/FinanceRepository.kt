package com.example.tracker.data.repo

import androidx.room.withTransaction
import com.example.tracker.data.db.Account
import com.example.tracker.data.db.AppDatabase
import com.example.tracker.data.db.Budget
import com.example.tracker.data.db.Category
import com.example.tracker.data.db.CategoryKind
import com.example.tracker.data.db.FEES_CATEGORY
import com.example.tracker.data.db.Recurring
import com.example.tracker.data.db.Txn
import com.example.tracker.data.db.TxnType
import com.example.tracker.data.finance.FinanceMath
import com.example.tracker.data.finance.Money
import com.example.tracker.data.schedule.RecurringDue
import com.example.tracker.data.schedule.Schedule
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.time.YearMonth

/** A budget threshold the last write crossed (80 or 100 %). */
data class BudgetAlert(val category: Category, val percent: Int, val spent: Long, val limit: Long)

class FinanceRepository(private val db: AppDatabase) {
    private val accounts = db.accountDao()
    private val categories = db.categoryDao()
    private val txns = db.txnDao()
    private val budgets = db.budgetDao()
    private val recurring = db.recurringDao()

    val accountsFlow: Flow<List<Account>> = accounts.observeAll()
    val categoriesFlow: Flow<List<Category>> = categories.observeAll()
    val txnsFlow: Flow<List<Txn>> = txns.observeAll()
    val recurringFlow: Flow<List<Recurring>> = recurring.observeAll()
    fun budgetsFlow(month: YearMonth): Flow<List<Budget>> = budgets.observeMonth(month.toString())

    suspend fun txn(id: Long) = txns.get(id)

    // ---- accounts & categories ----

    suspend fun saveAccount(a: Account) { accounts.upsert(a) }

    /** Accounts with history are archived instead of deleted, so balances stay correct. */
    suspend fun deleteOrArchiveAccount(a: Account): Boolean =
        if (accounts.txnCount(a.id) > 0) { accounts.upsert(a.copy(archived = true)); false }
        else { accounts.delete(a); true }

    suspend fun saveCategory(c: Category) { categories.upsert(c) }

    /** Returns false if the category is in use (it is kept). */
    suspend fun deleteCategory(c: Category): Boolean =
        if (categories.txnCount(c.id) > 0) false else { categories.delete(c); true }

    private suspend fun feeCategoryId(): Long =
        categories.findByName(FEES_CATEGORY, CategoryKind.EXPENSE)?.id
            ?: categories.upsert(Category(name = FEES_CATEGORY, icon = "🧾", kind = CategoryKind.EXPENSE))

    // ---- transactions ----

    /** Income / expense / adjustment insert or update. Returns budget alerts the change triggered. */
    suspend fun saveSimple(t: Txn): List<BudgetAlert> = db.withTransaction {
        val before = if (t.type == TxnType.EXPENSE && t.categoryId != null) spentIn(t.categoryId, YearMonth.from(t.dateTime)) else 0
        if (t.id == 0L) txns.insert(t) else txns.update(t)
        budgetAlerts(t, before)
    }

    suspend fun saveTransfer(
        id: Long, from: Long, to: Long, amount: Long, fee: Long, at: LocalDateTime, note: String,
    ): List<BudgetAlert> = db.withTransaction {
        val feeCat = feeCategoryId()
        val (transfer, feeTxn) = FinanceMath.transferWithFee(from, to, amount, fee, feeCat, at, note)
        val before = spentIn(feeCat, YearMonth.from(at))
        if (id == 0L) txns.insertTransferWithFee(transfer, feeTxn)
        else txns.updateTransferWithFee(transfer.copy(id = id), feeTxn)
        feeTxn?.let { budgetAlerts(it, before) } ?: emptyList()
    }

    suspend fun adjustBalance(account: Account, actual: Long, note: String) {
        val all = txns.all()
        val diff = FinanceMath.adjustmentFor(FinanceMath.balance(account, all), actual)
        if (diff != 0L) txns.insert(Txn(type = TxnType.ADJUSTMENT, amountPaisa = diff, accountId = account.id,
            dateTime = LocalDateTime.now(Schedule.ZONE).withNano(0), note = note.ifBlank { "Balance adjustment" }))
    }

    suspend fun delete(id: Long) = txns.deleteWithLinked(id)

    /** Deletes and returns what was removed (the transaction + its linked fee/transfer) so it can be undone. */
    suspend fun deleteForUndo(id: Long): List<Txn> = db.withTransaction {
        val t = txns.get(id) ?: return@withTransaction emptyList()
        val linked = t.linkedTxnId?.let { txns.get(it) }
        txns.deleteWithLinked(id)
        listOfNotNull(t, linked)
    }

    /** Puts deleted rows back with their original ids (links stay intact). */
    suspend fun restore(rows: List<Txn>) = db.withTransaction { rows.forEach { txns.insert(it) } }

    // ---- budgets ----

    suspend fun setBudget(categoryId: Long, month: YearMonth, limit: Long?) {
        val existing = budgets.get(month.toString(), categoryId)
        when {
            limit == null || limit <= 0 -> existing?.let { budgets.delete(it.id) }
            else -> budgets.upsert(Budget(id = existing?.id ?: 0, categoryId = categoryId,
                month = month.toString(), limitPaisa = limit))
        }
    }

    private suspend fun spentIn(categoryId: Long, month: YearMonth): Long =
        txns.between(month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay())
            .filter { it.type == TxnType.EXPENSE && it.categoryId == categoryId }.sumOf { it.amountPaisa }

    private suspend fun budgetAlerts(t: Txn, before: Long): List<BudgetAlert> {
        val cat = t.categoryId ?: return emptyList()
        if (t.type != TxnType.EXPENSE) return emptyList()
        val month = YearMonth.from(t.dateTime)
        val budget = budgets.get(month.toString(), cat) ?: return emptyList()
        val after = spentIn(cat, month)
        val category = categories.all().firstOrNull { it.id == cat } ?: return emptyList()
        return FinanceMath.crossedThresholds(before, after, budget.limitPaisa)
            .map { BudgetAlert(category, it, after, budget.limitPaisa) }
    }

    // ---- recurring ----

    suspend fun saveRecurring(r: Recurring) { recurring.upsert(r) }
    suspend fun deleteRecurring(id: Long) = recurring.delete(id)
    suspend fun allRecurring() = recurring.all()

    /** One-tap confirm: records the expense for this month and marks it confirmed. */
    suspend fun confirmRecurring(id: Long, now: LocalDateTime = LocalDateTime.now(Schedule.ZONE)): List<BudgetAlert> {
        val r = recurring.get(id) ?: return emptyList()
        val month = YearMonth.from(now)
        if (r.lastConfirmedMonth == month.toString()) return emptyList()
        val alerts = saveSimple(Txn(type = TxnType.EXPENSE, amountPaisa = r.amountPaisa, accountId = r.accountId,
            categoryId = r.categoryId, dateTime = now.withNano(0), note = r.title))
        recurring.upsert(r.copy(lastConfirmedMonth = month.toString()))
        return alerts
    }

    suspend fun recurringDue(): List<RecurringDue> = recurring.all().filter { it.active }.map {
        RecurringDue(it.id, it.title, Money.format(it.amountPaisa), it.dayOfMonth, it.lastConfirmedMonth)
    }

    suspend fun spentToday(): Long {
        val today = java.time.LocalDate.now(Schedule.ZONE)
        return FinanceMath.spentOn(today, txns.between(today.atStartOfDay(), today.plusDays(1).atStartOfDay()))
    }
}
