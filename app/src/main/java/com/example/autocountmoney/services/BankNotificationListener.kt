package com.example.autocountmoney.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.autocountmoney.AutoCountMoneyApp
import com.example.autocountmoney.data.models.Transaction
import com.example.autocountmoney.notifications.WalletParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BankNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in WalletParser.providers) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val app = application as AutoCountMoneyApp
        if (!getSharedPreferences("wallet_settings", MODE_PRIVATE).getBoolean(sbn.packageName, true)) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?.takeIf(String::isNotBlank)
            ?: extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val parsed = WalletParser.parse(sbn.packageName, "$title\n$body") ?: return
        val eventTime = sbn.notification.`when`.takeIf { it > 0 } ?: sbn.postTime
        scope.launch {
            try {
                val transaction = Transaction(
                    provider = parsed.provider, amount = parsed.amount, balanceAfter = parsed.balance,
                    timestamp = eventTime,
                    dedupeKey = "${sbn.packageName}:${sbn.key}:$eventTime:${parsed.amount}"
                )
                val id = app.repository.insert(transaction)
                if (id == -1L) return@launch
                val saved = transaction.copy(id = id)
                app.reminders.show(saved)
                if (!app.activityVisible) app.overlay.enqueue(saved)
            } catch (_: Exception) {
                // Do not log wallet notification contents. Future callbacks can still be handled.
            }
        }
    }

    override fun onListenerDisconnected() {
        (application as AutoCountMoneyApp).overlay.closeAll()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        scope.cancel()
        (application as AutoCountMoneyApp).overlay.closeAll()
        super.onDestroy()
    }
}
