package com.example.tracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

/** All money is Long paisa (1 ৳ = 100 paisa). Never Float/Double. */

enum class AccountType(val label: String, val emoji: String) {
    BANK("Bank", "🏦"),
    CASH("Cash", "💵"),
    BKASH("bKash", "📱"),
    NAGAD("Nagad", "📱"),
    ROCKET("Rocket", "📱"),
    OTHER("Other", "💼");

    val isMobileWallet get() = this == BKASH || this == NAGAD || this == ROCKET
}

@Entity(tableName = "account")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Nickname only — never a full account/card number. */
    val name: String,
    val type: AccountType,
    @ColumnInfo(name = "opening_balance_paisa") val openingBalancePaisa: Long = 0,
    @ColumnInfo(name = "opening_date") val openingDate: LocalDate,
    /** Optional last 4 digits, for recognising the account. */
    val last4: String? = null,
    /** ARGB colour. */
    val color: Long = 0xFF4F7CAC,
    val archived: Boolean = false,
)

enum class CategoryKind { INCOME, EXPENSE }

@Entity(tableName = "category")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** An emoji, so icons are editable without an icon pack. */
    val icon: String,
    val kind: CategoryKind,
)

enum class TxnType { INCOME, EXPENSE, TRANSFER, ADJUSTMENT }

@Entity(
    tableName = "txn",
    indices = [
        Index("date_time"), Index("account_id"), Index("category_id"),
        Index("to_account_id"), Index("linked_txn_id"),
    ],
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxnType,
    /** Always > 0, except ADJUSTMENT which is the signed difference. */
    @ColumnInfo(name = "amount_paisa") val amountPaisa: Long,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "to_account_id") val toAccountId: Long? = null,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    @ColumnInfo(name = "date_time") val dateTime: LocalDateTime,
    val note: String = "",
    /** Transfer ↔ its fee expense. */
    @ColumnInfo(name = "linked_txn_id") val linkedTxnId: Long? = null,
)

@Entity(tableName = "budget", indices = [Index(value = ["category_id", "month"], unique = true)])
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    /** "YYYY-MM" */
    val month: String,
    @ColumnInfo(name = "limit_paisa") val limitPaisa: Long,
)

@Entity(tableName = "recurring")
data class Recurring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "amount_paisa") val amountPaisa: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    @ColumnInfo(name = "account_id") val accountId: Long,
    /** 1..31; clamped to the month's last day. */
    @ColumnInfo(name = "day_of_month") val dayOfMonth: Int,
    val active: Boolean = true,
    /** "YYYY-MM" of the last month it was confirmed — prevents double-adding. */
    @ColumnInfo(name = "last_confirmed_month") val lastConfirmedMonth: String? = null,
)
