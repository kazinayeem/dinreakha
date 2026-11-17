package com.example.tracker.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface AccountDao {
    @Query("SELECT * FROM account ORDER BY archived, id") fun observeAll(): Flow<List<Account>>
    @Query("SELECT * FROM account ORDER BY id") suspend fun all(): List<Account>
    @Query("SELECT * FROM account WHERE id = :id") suspend fun get(id: Long): Account?
    @Upsert suspend fun upsert(account: Account): Long
    @Query("SELECT COUNT(*) FROM txn WHERE account_id = :id OR to_account_id = :id") suspend fun txnCount(id: Long): Int
    @Delete suspend fun delete(account: Account)
    @Query("DELETE FROM account") suspend fun clear()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category ORDER BY kind, id") fun observeAll(): Flow<List<Category>>
    @Query("SELECT * FROM category ORDER BY id") suspend fun all(): List<Category>
    @Query("SELECT * FROM category WHERE name = :name AND kind = :kind LIMIT 1")
    suspend fun findByName(name: String, kind: CategoryKind): Category?
    @Upsert suspend fun upsert(category: Category): Long
    @Query("SELECT COUNT(*) FROM txn WHERE category_id = :id") suspend fun txnCount(id: Long): Int
    @Delete suspend fun delete(category: Category)
    @Query("DELETE FROM category") suspend fun clear()
}

@Dao
abstract class TxnDao {
    @Query("SELECT * FROM txn ORDER BY date_time DESC, id DESC") abstract fun observeAll(): Flow<List<Txn>>
    @Query("SELECT * FROM txn ORDER BY date_time, id") abstract suspend fun all(): List<Txn>

    @Query("SELECT * FROM txn WHERE date_time >= :from AND date_time < :to ORDER BY date_time DESC")
    abstract fun observeBetween(from: LocalDateTime, to: LocalDateTime): Flow<List<Txn>>

    @Query("SELECT * FROM txn WHERE date_time >= :from AND date_time < :to")
    abstract suspend fun between(from: LocalDateTime, to: LocalDateTime): List<Txn>

    @Query("SELECT * FROM txn WHERE id = :id") abstract suspend fun get(id: Long): Txn?
    @Insert abstract suspend fun insert(txn: Txn): Long
    @Update abstract suspend fun update(txn: Txn)
    @Query("DELETE FROM txn WHERE id = :id") abstract suspend fun deleteById(id: Long)
    @Query("DELETE FROM txn") abstract suspend fun clear()

    /** Transfer + optional fee expense, linked both ways, in ONE database transaction. */
    @Transaction
    open suspend fun insertTransferWithFee(transfer: Txn, fee: Txn?): Long {
        val transferId = insert(transfer.copy(id = 0, linkedTxnId = null))
        if (fee != null) {
            val feeId = insert(fee.copy(id = 0, linkedTxnId = transferId))
            update(transfer.copy(id = transferId, linkedTxnId = feeId))
        }
        return transferId
    }

    /** Deletes a transaction and the fee/transfer linked to it, atomically. */
    @Transaction
    open suspend fun deleteWithLinked(id: Long) {
        val t = get(id) ?: return
        t.linkedTxnId?.let { deleteById(it) }
        deleteById(id)
    }

    /** Edits a transfer and replaces its fee atomically. */
    @Transaction
    open suspend fun updateTransferWithFee(transfer: Txn, fee: Txn?) {
        val old = get(transfer.id) ?: return
        old.linkedTxnId?.let { deleteById(it) }
        var linked: Long? = null
        if (fee != null) linked = insert(fee.copy(id = 0, linkedTxnId = transfer.id))
        update(transfer.copy(linkedTxnId = linked))
    }
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budget WHERE month = :month") fun observeMonth(month: String): Flow<List<Budget>>
    @Query("SELECT * FROM budget WHERE month = :month AND category_id = :categoryId")
    suspend fun get(month: String, categoryId: Long): Budget?
    @Query("SELECT * FROM budget ORDER BY month") suspend fun all(): List<Budget>
    @Upsert suspend fun upsert(budget: Budget)
    @Query("DELETE FROM budget WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM budget") suspend fun clear()
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring ORDER BY day_of_month") fun observeAll(): Flow<List<Recurring>>
    @Query("SELECT * FROM recurring ORDER BY id") suspend fun all(): List<Recurring>
    @Query("SELECT * FROM recurring WHERE id = :id") suspend fun get(id: Long): Recurring?
    @Upsert suspend fun upsert(r: Recurring): Long
    @Query("DELETE FROM recurring WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM recurring") suspend fun clear()
}
