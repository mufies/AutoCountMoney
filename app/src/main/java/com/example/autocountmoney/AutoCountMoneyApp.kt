package com.example.autocountmoney

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.example.autocountmoney.data.AppDatabase
import com.example.autocountmoney.data.TransactionRepository
import com.example.autocountmoney.notifications.ExpenseReminders
import com.example.autocountmoney.overlay.ExpenseOverlay

class AutoCountMoneyApp : Application() {
    val database: AppDatabase by lazy {
        // Leave the unfinished app-db untouched; the wallet MVP uses its own schema.
        Room.databaseBuilder<AppDatabase>(this, "wallet-tracker.db")
            .setDriver(AndroidSQLiteDriver())
            .build()
    }
    val repository by lazy { TransactionRepository(database.transactionDao()) }
    val reminders by lazy { ExpenseReminders(this) }
    val overlay by lazy { ExpenseOverlay(this) }
    var activityVisible = false

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) overlay.closeAll()
            }
        }, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}

