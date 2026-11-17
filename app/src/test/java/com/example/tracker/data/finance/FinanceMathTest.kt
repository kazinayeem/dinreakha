package com.example.tracker.data.finance

import com.example.tracker.data.db.Account
import com.example.tracker.data.db.AccountType
import com.example.tracker.data.db.Txn
import com.example.tracker.data.db.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class FinanceMathTest {

    private val day = LocalDate.of(2026, 11, 1)
    private val bank = Account(1, "DBBL", AccountType.BANK, 50_000_00, day)
    private val cash = Account(2, "Cash", AccountType.CASH, 1_000_00, day)
    private val bkash = Account(3, "bKash", AccountType.BKASH, 0, day)
    private val at = LocalDateTime.of(2026, 11, 18, 12, 0)
    private val FEE_CAT = 99L

    private fun exp(acc: Long, amt: Long, cat: Long = 1, t: LocalDateTime = at) =
        Txn(type = TxnType.EXPENSE, amountPaisa = amt, accountId = acc, categoryId = cat, dateTime = t)

    private fun inc(acc: Long, amt: Long, t: LocalDateTime = at) =
        Txn(type = TxnType.INCOME, amountPaisa = amt, accountId = acc, categoryId = 50, dateTime = t)

    // ---- balances ----

    @Test fun balanceIsOpeningPlusTransactions() {
        val txns = listOf(inc(1, 20_000_00), exp(1, 1_500_50), exp(2, 200_00))
        assertEquals(50_000_00 + 20_000_00 - 1_500_50, FinanceMath.balance(bank, txns))
        assertEquals(800_00L, FinanceMath.balance(cash, txns))
    }

    @Test fun adjustmentIsSigned() {
        val adj = FinanceMath.adjustmentFor(current = 800_00, actual = 750_00)
        assertEquals(-50_00L, adj)
        val txns = listOf(exp(2, 200_00), Txn(type = TxnType.ADJUSTMENT, amountPaisa = adj, accountId = 2, dateTime = at))
        assertEquals(750_00L, FinanceMath.balance(cash, txns))
    }

    @Test fun archivedAccountsExcludedFromTotal() {
        val accounts = listOf(bank, cash.copy(archived = true))
        assertEquals(50_000_00L, FinanceMath.total(accounts, emptyList()))
    }

    // ---- transfers & fees ----

    @Test fun transferMovesMoneyWithoutChangingTotal() {
        val (t, fee) = FinanceMath.transferWithFee(1, 2, 5_000_00, 0, FEE_CAT, at, "ATM")
        assertNull(fee)
        val txns = listOf(t)
        assertEquals(45_000_00L, FinanceMath.balance(bank, txns))
        assertEquals(6_000_00L, FinanceMath.balance(cash, txns))
        assertEquals(51_000_00L, FinanceMath.total(listOf(bank, cash), txns))
    }

    @Test fun transferFeeIsSeparateExpenseFromSource() {
        // bKash cash-out 1,000 with ৳18.50 fee
        val start = listOf(inc(3, 2_000_00))
        val (t, fee) = FinanceMath.transferWithFee(3, 2, 1_000_00, 18_50, FEE_CAT, at, "cash-out")
        val txns = start + t + fee!!
        assertEquals(TxnType.EXPENSE, fee.type)
        assertEquals(FEE_CAT, fee.categoryId)
        assertEquals(2_000_00L - 1_000_00 - 18_50, FinanceMath.balance(bkash, txns))
        assertEquals(2_000_00L, FinanceMath.balance(cash, txns))
        // the fee counts as spending, the transfer does not
        val m = FinanceMath.monthTotals(YearMonth.of(2026, 11), txns)
        assertEquals(18_50L, m.expense)
        assertEquals(18_50L, m.byCategory[FEE_CAT])
    }

    @Test(expected = IllegalArgumentException::class)
    fun transferToSameAccountRejected() {
        FinanceMath.transferWithFee(1, 1, 100, 0, FEE_CAT, at, "")
    }

    // ---- monthly totals ----

    @Test fun monthTotalsIgnoreOtherMonthsTransfersAndAdjustments() {
        val oct = LocalDateTime.of(2026, 10, 31, 23, 59)
        val dec = LocalDateTime.of(2026, 12, 1, 0, 0)
        val txns = listOf(
            inc(1, 30_000_00), exp(2, 120_00, cat = 1), exp(2, 80_00, cat = 1),
            exp(1, 5_000_00, cat = 3), exp(2, 999_00, t = oct), exp(2, 777_00, t = dec),
            FinanceMath.transferWithFee(1, 2, 1_000_00, 0, FEE_CAT, at, "").first,
            Txn(type = TxnType.ADJUSTMENT, amountPaisa = -10_00, accountId = 2, dateTime = at),
        )
        val m = FinanceMath.monthTotals(YearMonth.of(2026, 11), txns)
        assertEquals(30_000_00L, m.income)
        assertEquals(5_200_00L, m.expense)
        assertEquals(200_00L, m.byCategory[1L])
        assertEquals(5_000_00L, m.byCategory[3L])
        assertEquals(24_800_00L, m.net)
        assertEquals(5_200_00L, m.byDay[at.toLocalDate()])
    }

    @Test fun spentToday() {
        val txns = listOf(exp(2, 50_00), exp(2, 25_25), inc(2, 100_00),
            exp(2, 10_00, t = at.minusDays(1)))
        assertEquals(75_25L, FinanceMath.spentOn(at.toLocalDate(), txns))
    }

    @Test fun changeVsLastMonth() {
        assertEquals(25, FinanceMath.changePercent(125_00, 100_00))
        assertEquals(-50, FinanceMath.changePercent(50_00, 100_00))
        assertNull(FinanceMath.changePercent(50_00, 0))
    }

    // ---- budgets ----

    @Test fun budgetPercentages() {
        assertEquals(0, FinanceMath.budgetPercent(0, 5_000_00))
        assertEquals(79, FinanceMath.budgetPercent(3_999_99, 5_000_00))
        assertEquals(80, FinanceMath.budgetPercent(4_000_00, 5_000_00))
        assertEquals(130, FinanceMath.budgetPercent(6_500_00, 5_000_00))
        assertEquals(0, FinanceMath.budgetPercent(100, 0))
    }

    @Test fun budgetAlertsFireOncePerThreshold() {
        val limit = 1_000_00L
        assertEquals(listOf(80), FinanceMath.crossedThresholds(700_00, 850_00, limit))
        assertEquals(emptyList<Int>(), FinanceMath.crossedThresholds(850_00, 900_00, limit))
        assertEquals(listOf(100), FinanceMath.crossedThresholds(900_00, 1_000_00, limit))
        assertEquals(listOf(80, 100), FinanceMath.crossedThresholds(0, 2_000_00, limit))
    }

    // ---- recurring ----

    @Test fun dueDateClampsToMonthEnd() {
        assertEquals(LocalDate.of(2027, 2, 28), FinanceMath.dueDate(YearMonth.of(2027, 2), 31))
        assertEquals(LocalDate.of(2026, 12, 5), FinanceMath.dueDate(YearMonth.of(2026, 12), 5))
    }
}
