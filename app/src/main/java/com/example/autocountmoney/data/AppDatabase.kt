package com.example.autocountmoney.data

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.example.autocountmoney.data.dao.TransactionDao
import com.example.autocountmoney.data.models.Transaction

@Database(entities = [Transaction::class], version = 1)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

}
