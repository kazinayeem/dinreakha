package com.example.tracker.ui.wallet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tracker.alarm.Notifications
import com.example.tracker.container
import com.example.tracker.data.db.Account
import com.example.tracker.data.db.Budget
import com.example.tracker.data.db.Category
import com.example.tracker.data.db.Recurring
import com.example.tracker.data.db.Txn
import com.example.tracker.data.finance.FinanceMath
import com.example.tracker.data.repo.BudgetAlert
import com.example.tracker.data.schedule.Schedule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.YearMonth

data class WalletData(
    val accounts: List<Account>,
    val categories: List<Category>,
    val txns: List<Txn>,
    val recurring: List<Recurring>,
    val budgets: List<Budget>,
    val month: YearMonth,
) {
    val balances: Map<Long, Long> = FinanceMath.balances(accounts, txns)
    val total: Long = FinanceMath.total(accounts, txns)
    val activeAccounts get() = accounts.filterNot { it.archived }
    val categoryById = categories.associateBy { it.id }
    val accountById = accounts.associateBy { it.id }
    val thisMonth = FinanceMath.monthTotals(month, txns)
    val lastMonth = FinanceMath.monthTotals(month.minusMonths(1), txns)
}

/** One ViewModel shared by all wallet screens (scoped to the activity). */
@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val repo = c.finance

    /** Month shown in the report & budgets screens. */
    val month = MutableStateFlow(YearMonth.now(Schedule.ZONE))

    val data: StateFlow<WalletData?> = month.flatMapLatest { m ->
        combine(repo.accountsFlow, repo.categoriesFlow, repo.txnsFlow, repo.recurringFlow, repo.budgetsFlow(m)) { a, cat, t, r, b ->
            WalletData(a, cat, t, r, b, m)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun afterWrite(alerts: List<BudgetAlert>) {
        Notifications.showBudgetAlerts(getApplication(), alerts)
        c.appScope.launch { c.scheduler.refreshStatus() }
    }

    fun saveSimple(t: Txn) = viewModelScope.launch { afterWrite(repo.saveSimple(t)) }

    fun saveTransfer(id: Long, from: Long, to: Long, amount: Long, fee: Long, at: LocalDateTime, note: String) =
        viewModelScope.launch { afterWrite(repo.saveTransfer(id, from, to, amount, fee, at, note)) }

    fun adjust(account: Account, actual: Long, note: String) = viewModelScope.launch {
        repo.adjustBalance(account, actual, note); afterWrite(emptyList())
    }

    fun delete(id: Long) = viewModelScope.launch { repo.delete(id); afterWrite(emptyList()) }

    /** Delete with an Undo snackbar. */
    fun deleteWithUndo(id: Long, feedback: com.example.tracker.ui.components.Feedback?) = viewModelScope.launch {
        val removed = repo.deleteForUndo(id)
        afterWrite(emptyList())
        if (removed.isNotEmpty()) feedback?.show("Transaction deleted", "Undo") {
            viewModelScope.launch { repo.restore(removed); afterWrite(emptyList()) }
        }
    }

    fun saveAccount(a: Account) = viewModelScope.launch { repo.saveAccount(a) }
    fun archiveOrDelete(a: Account, done: (deleted: Boolean) -> Unit) = viewModelScope.launch { done(repo.deleteOrArchiveAccount(a)) }
    fun saveCategory(cat: Category) = viewModelScope.launch { repo.saveCategory(cat) }
    fun deleteCategory(cat: Category, done: (Boolean) -> Unit) = viewModelScope.launch { done(repo.deleteCategory(cat)) }
    fun setBudget(categoryId: Long, limit: Long?) = viewModelScope.launch { repo.setBudget(categoryId, month.value, limit) }

    fun saveRecurring(r: Recurring) = viewModelScope.launch { repo.saveRecurring(r); c.scheduler.rescheduleAll() }
    fun deleteRecurring(id: Long) = viewModelScope.launch { repo.deleteRecurring(id); c.scheduler.rescheduleAll() }
    fun confirmRecurring(id: Long) = viewModelScope.launch {
        afterWrite(repo.confirmRecurring(id)); c.scheduler.rescheduleAll()
    }
}
