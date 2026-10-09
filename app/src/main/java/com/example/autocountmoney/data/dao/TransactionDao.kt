package com.example.autocountmoney.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.example.autocountmoney.data.models.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tx: Transaction): Long

    @Query("SELECT * FROM bank_transactions ORDER BY timestamp DESC, id DESC")
    fun getAll(): Flow<List<Transaction>>

    @Query("SELECT * FROM bank_transactions WHERE id = :id")
    suspend fun find(id: Long): Transaction?

    @Query("UPDATE bank_transactions SET reason = :reason, category = :category, completed = 1 WHERE id = :id")
    suspend fun complete(id: Long, reason: String, category: String)

    @Query("DELETE FROM bank_transactions WHERE isDemo = 1")
    suspend fun deleteDemo()
}
