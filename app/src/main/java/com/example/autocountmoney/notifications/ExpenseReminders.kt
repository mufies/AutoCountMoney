package com.example.autocountmoney.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.autocountmoney.MainActivity
import com.example.autocountmoney.data.models.Transaction

class ExpenseReminders(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun show(tx: Transaction) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL, "Bổ sung lý do chi tiêu", NotificationManager.IMPORTANCE_HIGH
        ))
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(TRANSACTION_ID, tx.id)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, tx.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            manager.notify(tx.id.toString(), 1, NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentTitle("${tx.provider} · Bổ sung chi tiêu")
                .setContentText("Chạm để nhập lý do và chọn danh mục")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build())
        } catch (_: SecurityException) { /* Pending data remains accessible in the app. */ }
    }

    fun cancel(id: Long) = manager.cancel(id.toString(), 1)
    companion object {
        const val TRANSACTION_ID = "transaction_id"
        private const val CHANNEL = "expense_reminders"
    }
}
