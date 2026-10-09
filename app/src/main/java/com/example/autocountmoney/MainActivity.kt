package com.example.autocountmoney

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.example.autocountmoney.notifications.ExpenseReminders
import com.example.autocountmoney.ui.WalletScreen
import com.example.autocountmoney.ui.theme.AutoCountMoneyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var requestedId by mutableStateOf<Long?>(null)
    private var permissionRevision by mutableStateOf(0)
    private val app get() = application as AutoCountMoneyApp
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionRevision++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedId = savedInstanceState?.getLong("requested_id")?.takeIf { it > 0 }
            ?: intent.getLongExtra(ExpenseReminders.TRANSACTION_ID, -1).takeIf { it > 0 }
        setContent {
            AutoCountMoneyTheme {
                WalletScreen(app, requestedId, permissionRevision,
                    onRequestedHandled = { requestedId = null; intent.removeExtra(ExpenseReminders.TRANSACTION_ID) },
                    onListenerSettings = { openSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    onOverlaySettings = { openSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) },
                    onNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                    },
                    onBackgroundDemo = { provider ->
                        lifecycleScope.launch {
                            delay(5000)
                            try {
                                val id = app.repository.simulate(provider, true)
                                val tx = app.repository.find(id) ?: return@launch
                                app.reminders.show(tx)
                                if (!app.activityVisible) app.overlay.enqueue(tx) else requestedId = id
                            } catch (_: Exception) {
                                android.widget.Toast.makeText(this@MainActivity, "Chưa tạo được giao dịch test", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        app.activityVisible = true
        app.overlay.closeAll()
    }

    override fun onResume() { super.onResume(); permissionRevision++ }
    override fun onStop() { app.activityVisible = false; super.onStop() }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedId = intent.getLongExtra(ExpenseReminders.TRANSACTION_ID, -1).takeIf { it > 0 }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        requestedId?.let { outState.putLong("requested_id", it) }
        super.onSaveInstanceState(outState)
    }

    private fun openSettings(intent: Intent) {
        try { startActivity(intent) }
        catch (_: android.content.ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
