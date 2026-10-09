package com.example.autocountmoney.data.models

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "bank_transactions", indices = [Index(value = ["dedupeKey"], unique = true)])
data class Transaction(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val provider: String,
    val amount: Long,
    val balanceAfter: Long? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String = "",
    val category: String = "",
    val completed: Boolean = false,
    val isDemo: Boolean = false,
    val dedupeKey: String
)

val expenseCategories = listOf("Ăn uống", "Di chuyển", "Mua sắm", "Hóa đơn", "Giải trí", "Sức khỏe", "Khác")
