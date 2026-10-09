package com.example.autocountmoney.overlay

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.autocountmoney.AutoCountMoneyApp
import com.example.autocountmoney.data.models.Transaction
import com.example.autocountmoney.data.models.expenseCategories
import com.example.autocountmoney.ui.money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** One focusable window; reminders remain available if overlays cannot be displayed. */
class ExpenseOverlay(private val app: AutoCountMoneyApp) {
    private val windows = app.getSystemService(WindowManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queue = ArrayDeque<Transaction>()
    private var view: View? = null
    private var currentId: Long? = null
    private var preparing = false
    private var generation = 0

    fun enqueue(tx: Transaction) {
        if (!Settings.canDrawOverlays(app)) return
        if (currentId == tx.id || queue.any { it.id == tx.id }) return
        queue.addLast(tx)
        showNext()
    }

    private fun showNext() {
        if (view != null || preparing || queue.isEmpty()) return
        val locked = app.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val interactive = app.getSystemService(PowerManager::class.java).isInteractive
        if (app.activityVisible || locked || !interactive || !Settings.canDrawOverlays(app)) {
            queue.clear()
            return
        }
        val tx = queue.removeFirst()
        preparing = true
        val requestGeneration = generation
        scope.launch {
            try {
                if (app.repository.find(tx.id)?.completed != false) return@launch
                // Recheck after the suspend call before touching WindowManager.
                if (requestGeneration != generation || view != null || app.activityVisible || !Settings.canDrawOverlays(app) ||
                    app.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return@launch
                show(tx)
            } catch (_: Exception) {
                // The transaction remains pending; its notification is still available.
            } finally {
                preparing = false
                showNext()
            }
        }
    }

    private fun show(tx: Transaction) {
        val context = android.view.ContextThemeWrapper(app, android.R.style.Theme_Material_Light_NoActionBar)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(16))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(24).toFloat() }
            elevation = dp(12).toFloat()
        }
        fun label(text: String, size: Float, bold: Boolean = false) {
            layout.addView(TextView(context).apply {
                this.text = text; textSize = size; setTextColor(Color.rgb(26, 45, 40))
                if (bold) setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(4), 0, dp(8))
            })
        }
        label(if (tx.isDemo) "${tx.provider} · Mô phỏng" else tx.provider, 14f)
        label("Chi ${money(tx.amount)}", 26f, true)
        label("Khoản này dùng để làm gì?", 16f)
        val reason = EditText(context).apply {
            hint = "Ví dụ: Ăn trưa"; maxLines = 3
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        layout.addView(reason)
        val categories = listOf("Chọn danh mục") + expenseCategories
        val spinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, categories)
        }
        layout.addView(spinner, LinearLayout.LayoutParams(-1, dp(52)))
        val actions = LinearLayout(context)
        val later = Button(context).apply { text = "Để sau"; setOnClickListener { closeCurrent(); showNext() } }
        val save = Button(context).apply { text = "Lưu khoản chi" }
        actions.addView(later, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(save, LinearLayout.LayoutParams(0, -2, 1f))
        layout.addView(actions)
        save.setOnClickListener {
            if (reason.text.isNullOrBlank()) { reason.error = "Nhập lý do chi tiêu"; return@setOnClickListener }
            if (spinner.selectedItemPosition == 0) {
                Toast.makeText(app, "Chọn danh mục trước khi lưu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val reasonText = reason.text.toString()
            val category = categories[spinner.selectedItemPosition]
            save.isEnabled = false; later.isEnabled = false
            scope.launch {
                try {
                    app.repository.complete(tx.id, reasonText, category)
                    app.reminders.cancel(tx.id)
                    Toast.makeText(app, "Đã lưu" + (tx.balanceAfter?.let { " · Số dư: ${money(it)}" }
                        ?: " · Thông báo không cung cấp số dư"), Toast.LENGTH_LONG).show()
                    if (currentId == tx.id) { closeCurrent(); showNext() }
                } catch (_: Exception) {
                    save.isEnabled = true; later.isEnabled = true
                    reason.error = "Chưa lưu được. Vui lòng thử lại."
                }
            }
        }
        val width = minOf(app.resources.displayMetrics.widthPixels - dp(32), dp(380))
        val params = WindowManager.LayoutParams(width, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        val container = ScrollView(context).apply { addView(layout) }
        try {
            windows.addView(container, params)
            currentId = tx.id; view = container
        } catch (_: Exception) { queue.clear() }
    }

    fun closeAll() { generation++; queue.clear(); closeCurrent() }
    private fun closeCurrent() {
        view?.let { try { windows.removeViewImmediate(it) } catch (_: IllegalArgumentException) {} }
        view = null; currentId = null
    }
    private fun dp(value: Int) = (value * app.resources.displayMetrics.density).toInt()
}
