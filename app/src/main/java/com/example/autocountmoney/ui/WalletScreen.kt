package com.example.autocountmoney.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.autocountmoney.AutoCountMoneyApp
import com.example.autocountmoney.data.models.Transaction
import com.example.autocountmoney.data.models.expenseCategories
import com.example.autocountmoney.notifications.WalletParser
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalletScreen(
    app: AutoCountMoneyApp, requestedId: Long?, permissionRevision: Int,
    onRequestedHandled: () -> Unit, onListenerSettings: () -> Unit,
    onOverlaySettings: () -> Unit, onNotificationPermission: () -> Unit,
    onBackgroundDemo: (String) -> Unit
) {
    var databaseError by remember { mutableStateOf(false) }
    val flow = remember { app.repository.transactions.catch { databaseError = true; emit(emptyList()) } }
    val loaded by flow.collectAsStateWithLifecycle<List<Transaction>?>(initialValue = null)
    val all = loaded.orEmpty()
    var tab by rememberSaveable { mutableStateOf(0) }
    var demo by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("Tất cả") }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var provider by rememberSaveable { mutableStateOf("MoMo") }
    var withBalance by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val preferences = remember { app.getSharedPreferences("wallet_settings", Context.MODE_PRIVATE) }
    val access = remember(permissionRevision) {
        NotificationManagerCompat.getEnabledListenerPackages(app).contains(app.packageName)
    }
    val overlay = remember(permissionRevision) { Settings.canDrawOverlays(app) }
    val notifications = remember(permissionRevision) { NotificationManagerCompat.from(app).areNotificationsEnabled() }
    val visible = all.filter { it.isDemo == demo }
    val pending = visible.filterNot { it.completed }
    val selected = all.find { it.id == selectedId }

    LaunchedEffect(requestedId, all) {
        requestedId?.let { id ->
            all.find { it.id == id }?.let { tx -> demo = tx.isDemo; selectedId = id; onRequestedHandled() }
        }
    }
    // Fresh real expenses prompt while the app is visible; existing pending entries stay in the list.
    var lastSeen by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(loaded) {
        if (loaded == null) return@LaunchedEffect
        val newest = all.maxOfOrNull { it.id } ?: 0L
        if (lastSeen != null && newest > lastSeen!! && selectedId == null) {
            all.firstOrNull { it.id > lastSeen!! && !it.isDemo && !it.completed }?.let {
                demo = false; selectedId = it.id
            }
        }
        lastSeen = newest
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf("Tổng quan", "Giao dịch", "Thiết lập").forEachIndexed { index, label ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index },
                        icon = { Text(listOf("◉", "≡", "⚙")[index], style = MaterialTheme.typography.titleLarge) },
                        label = { Text(label) })
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("AUTOCOUNT", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(listOf("Sổ chi tiêu của bạn", "Mỗi khoản chi, một lý do", "Kết nối ngân hàng và ví")[tab],
                            style = MaterialTheme.typography.titleMedium)
                    }
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("₫", Modifier.padding(horizontal = 17.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
            if (databaseError) item {
                InfoCard("Chưa đọc được dữ liệu", "Đóng và mở lại ứng dụng để thử lại. Các thao tác test hiện có thể không hoạt động.")
            }
            if (tab != 2) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !demo, onClick = { demo = false }, label = { Text("Giao dịch thật") })
                    FilterChip(selected = demo, onClick = { demo = true }, label = { Text("Mô phỏng") })
                }
            }
            if (tab == 0) {
                item { SummaryCard(visible, demo) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("Chờ bổ sung", "${pending.size}", Modifier.weight(1f))
                        StatCard("Đã phân loại", "${visible.count { it.completed }}", Modifier.weight(1f))
                    }
                }
                if (!access) item {
                    Card(shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Kết nối ngân hàng và ví", fontWeight = FontWeight.Bold)
                            Text("Bật quyền đọc thông báo để ghi nhận khoản chi từ MB Bank, TPBank, Vietcombank, MoMo và ZaloPay.")
                            TextButton(onClick = { tab = 2 }) { Text("Thiết lập kết nối →") }
                        }
                    }
                }
                item { SectionTitle("Số dư gần nhất") }
                val balances = visible.filter { it.balanceAfter != null }.distinctBy { it.provider }
                if (balances.isEmpty()) item {
                    InfoCard("Chưa có thông tin số dư", "Số dư chỉ xuất hiện khi thông báo ngân hàng hoặc ví cung cấp dữ liệu này.")
                } else items(balances, key = { "balance:${it.id}" }) { tx ->
                    Card(shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text(tx.provider, fontWeight = FontWeight.Bold); Text(transactionTime(tx.timestamp), style = MaterialTheme.typography.bodySmall) }
                            Text(money(tx.balanceAfter!!), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item { SectionTitle("${if (pending.isNotEmpty()) "Chờ bạn bổ sung" else "Giao dịch gần đây"}") }
                val recent = (if (pending.isNotEmpty()) pending else visible).take(4)
                if (recent.isEmpty()) item { EmptyTransactions(demo) }
                else items(recent, key = { "recent:${it.id}" }) { tx -> TransactionCard(tx) { selectedId = tx.id } }
                if (visible.isNotEmpty()) item {
                    TextButton(onClick = { tab = 1; filter = "Tất cả" }) { Text("Xem tất cả giao dịch →") }
                }
                val totals = visible.filter { it.completed }.groupBy { it.category }.mapValues { it.value.sumOf(Transaction::amount) }
                if (totals.isNotEmpty()) {
                    item { SectionTitle("Chi tiêu theo danh mục") }
                    items(totals.entries.sortedByDescending { it.value }, key = { "category:${it.key}" }) { entry ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(entry.key); Text(money(entry.value), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else if (tab == 1) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Tất cả", "Chờ bổ sung", "Đã lưu").forEach {
                            FilterChip(selected = filter == it, onClick = { filter = it }, label = { Text(it) })
                        }
                    }
                }
                val filtered = visible.filter { when (filter) { "Chờ bổ sung" -> !it.completed; "Đã lưu" -> it.completed; else -> true } }
                if (filtered.isEmpty()) item { EmptyTransactions(demo) }
                else items(filtered, key = { it.id }) { tx -> TransactionCard(tx) { selectedId = tx.id } }
            } else {
                item {
                    InfoCard("Chỉ lưu trên thiết bị", "Ứng dụng chỉ xử lý thông báo từ ngân hàng và ví được bật bên dưới. Không cần đăng nhập ngân hàng hay ví trong ứng dụng.")
                }
                item { SectionTitle("Quyền truy cập") }
                item { PermissionCard("Đọc thông báo ngân hàng/ví", "Nhận khoản chi từ MB Bank, TPBank, Vietcombank, MoMo và ZaloPay", access, onListenerSettings) }
                item { PermissionCard("Gửi lời nhắc", "Mở form từ thông báo khi chưa dùng popup", notifications, onNotificationPermission) }
                item { PermissionCard("Popup trên ứng dụng khác", "Nhập lý do ngay khi đang dùng ứng dụng khác", overlay, onOverlaySettings) }
                item { SectionTitle("Ngân hàng và ví theo dõi") }
                items(WalletParser.providers.entries.toList(), key = { it.key }) { entry ->
                    var enabled by remember { mutableStateOf(preferences.getBoolean(entry.key, true)) }
                    Card(shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.value, fontWeight = FontWeight.Bold)
                                Text("Nhận diện bản thử nghiệm", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = enabled, onCheckedChange = { enabled = it; preferences.edit().putBoolean(entry.key, it).apply() })
                        }
                    }
                }
                item { Text("Mẫu thông báo thực tế cần được đối chiếu. Thông báo không rõ số tiền hoặc trạng thái sẽ được bỏ qua.", style = MaterialTheme.typography.bodySmall) }
            }
            if (tab == 0 || tab == 2) item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Thử một khoản chi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Tạo khoản chi 50.000 ₫ để thử form. Dữ liệu mô phỏng được tách khỏi giao dịch thật.")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WalletParser.providers.values.forEach {
                                FilterChip(selected = provider == it, onClick = { provider = it }, label = { Text(it) })
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = withBalance, onCheckedChange = { withBalance = it })
                            Text("Thông báo có số dư")
                        }
                        Button(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                try { selectedId = app.repository.simulate(provider, withBalance); demo = true }
                                catch (_: Exception) { snackbar.showSnackbar("Chưa tạo được giao dịch. Vui lòng thử lại.") }
                                finally { busy = false }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Mô phỏng giao dịch") }
                        OutlinedButton(enabled = !busy, onClick = {
                            onBackgroundDemo(provider)
                            scope.launch { snackbar.showSnackbar("Bấm Home trong 5 giây để thử popup ở nền") }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Test popup sau 5 giây") }
                        Text("Popup cần quyền hiển thị trên ứng dụng khác. Nếu chưa cấp quyền, hãy mở giao dịch từ lời nhắc hoặc danh sách.", style = MaterialTheme.typography.bodySmall)
                        if (all.any { it.isDemo }) TextButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    app.overlay.closeAll()
                                    all.filter { it.isDemo }.forEach { app.reminders.cancel(it.id) }
                                    app.repository.deleteDemo()
                                    snackbar.showSnackbar("Đã xóa dữ liệu mô phỏng")
                                } catch (_: Exception) { snackbar.showSnackbar("Chưa xóa được dữ liệu mô phỏng") }
                                finally { busy = false }
                            }
                        }) { Text("Xóa dữ liệu mô phỏng") }
                    }
                }
            }
            item { Text("Một khoản chi rõ ràng. Một ngày nhẹ đầu hơn.", Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        }
    }

    selected?.let { tx ->
        ExpenseDialog(tx, onDismiss = { selectedId = null }, onSave = { reason, category ->
            app.repository.complete(tx.id, reason, category)
            app.reminders.cancel(tx.id)
            selectedId = null
            scope.launch {
                snackbar.showSnackbar("Đã lưu khoản chi" + (tx.balanceAfter?.let { " · Số dư: ${money(it)}" }
                    ?: " · Thông báo không cung cấp số dư"))
            }
        })
    }
}

@Composable private fun SummaryCard(transactions: List<Transaction>, demo: Boolean) {
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (demo) "CHI TIÊU MÔ PHỎNG" else "TỔNG CHI ĐÃ GHI NHẬN", color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge)
            Text(money(transactions.sumOf { it.amount }), style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            Text("${transactions.size} khoản chi · Bao gồm khoản chờ bổ sung", color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun StatCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun SectionTitle(title: String) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

@Composable private fun InfoCard(title: String, text: String) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun EmptyTransactions(demo: Boolean) {
    InfoCard("Chưa có giao dịch", if (demo) "Tạo một giao dịch mô phỏng bên dưới để thử nhập lý do và chọn danh mục."
        else "Các khoản chi nhận diện được từ thông báo ngân hàng và ví sẽ xuất hiện ở đây.")
}

@Composable private fun PermissionCard(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                Text(if (enabled) "Đã bật" else "Chạm để cấp quyền", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium)
            }
            Text(if (enabled) "✓" else "→", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable private fun TransactionCard(tx: Transaction, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tx.provider + if (tx.isDemo) " · Test" else "", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("−${money(tx.amount)}", fontWeight = FontWeight.Bold)
            }
            Text(if (tx.completed) tx.reason else "Khoản này dùng để làm gì?", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(transactionTime(tx.timestamp), style = MaterialTheme.typography.bodySmall)
                Text(if (tx.completed) tx.category else "Chờ bổ sung →", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ExpenseDialog(tx: Transaction, onDismiss: () -> Unit, onSave: suspend (String, String) -> Unit) {
    var reason by rememberSaveable(tx.id) { mutableStateOf(tx.reason) }
    var category by rememberSaveable(tx.id) { mutableStateOf(tx.category) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tx.provider + if (tx.isDemo) " · Mô phỏng" else "", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Text("Chi ${money(tx.amount)}", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ghi lại một lý do nhỏ, để dễ nhớ về sau.")
                OutlinedTextField(value = reason, onValueChange = { reason = it; error = null },
                    label = { Text("Lý do chi tiêu") }, placeholder = { Text("Ví dụ: Ăn trưa") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 3, enabled = !busy)
                Text("Danh mục", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    expenseCategories.forEach { option ->
                        FilterChip(selected = category == option, onClick = { category = option },
                            enabled = !busy, label = { Text(option) })
                    }
                }
                Text(tx.balanceAfter?.let { "Số dư sau giao dịch: ${money(it)}" } ?: "Thông báo không cung cấp số dư",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !busy && reason.isNotBlank() && category.isNotBlank(), onClick = {
                busy = true
                scope.launch {
                    try { onSave(reason, category) }
                    catch (_: Exception) { error = "Chưa lưu được. Vui lòng thử lại." }
                    finally { busy = false }
                }
            }) { Text(if (busy) "Đang lưu…" else "Lưu khoản chi") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(if (tx.completed) "Đóng" else "Để sau") } })
}

