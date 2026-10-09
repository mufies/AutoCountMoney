package com.example.autocountmoney.data

import com.example.autocountmoney.data.dao.TransactionDao
import com.example.autocountmoney.data.models.Transaction
import com.example.autocountmoney.data.models.expenseCategories
import java.util.UUID
import com.example.autocountmoney.notifications.NotificationSamples
import com.example.autocountmoney.notifications.WalletParser

class TransactionRepository(private val dao: TransactionDao) {
    val transactions = dao.getAll()
    suspend fun insert(tx: Transaction) = dao.insert(tx)
    suspend fun find(id: Long) = dao.find(id)

    suspend fun complete(id: Long, reason: String, category: String) {
        require(reason.isNotBlank() && category in expenseCategories)
        dao.complete(id, reason.trim(), category)
    }

    suspend fun simulate(provider: String, withBalance: Boolean): Long {
        val packageName = WalletParser.providers.entries.single { it.value == provider }.key
        val parsed = requireNotNull(WalletParser.parse(packageName, NotificationSamples.expense(provider, withBalance)))
        return dao.insert(
            Transaction(
                provider = parsed.provider, amount = parsed.amount, balanceAfter = parsed.balance,
                isDemo = true, dedupeKey = "demo:${UUID.randomUUID()}"
            )
        )
    }

    suspend fun deleteDemo() = dao.deleteDemo()
}
