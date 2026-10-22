package com.example.taichinh.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.taichinh.SummarySettings
import com.example.taichinh.SummaryService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.taichinh.data.Category
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val chi by vm.chiCats.collectAsState()
    val thu by vm.thuCats.collectAsState()

    var showSummary by remember { mutableStateOf(SummarySettings.isEnabled(ctx)) }
    var tick by remember { mutableIntStateOf(0) }
    val activity = ctx as ComponentActivity
    DisposableEffect(activity) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        activity.lifecycle.addObserver(obs)
        onDispose { activity.lifecycle.removeObserver(obs) }
    }
    val overlayOk = remember(tick) { Settings.canDrawOverlays(ctx) }
    val usageOk = remember(tick) { SummarySettings.hasUsageAccess(ctx) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        SummaryService.refresh()
    }

    var addType by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<Category?>(null) }
    var range by remember { mutableStateOf(thisMonth()) }
    var bType by remember { mutableStateOf("ALL") }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            val msg = try {
                "Đã sao lưu ${vm.writeBackup(uri, range, bType)} dòng"
            } catch (e: Exception) {
                "Lỗi sao lưu: ${e.message}"
            }
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Cài đặt", style = MaterialTheme.typography.headlineSmall) }

        item { Section("Hiển thị tổng quan thường xuyên") }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Bật hiển thị (số dư thu chi, mục chi nhiều nhất)", Modifier.weight(1f))
                Switch(checked = showSummary, onCheckedChange = { on ->
                    showSummary = on
                    SummarySettings.setEnabled(ctx, on)
                    if (on) {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        SummaryService.start(ctx)
                    } else SummaryService.stop(ctx)
                })
            }
        }
        item {
            Text(
                "Khi bật: thông báo thường trực hiện cả trên màn hình khóa lúc màn hình sáng, " +
                "và bảng nổi hiện trên màn hình chính khi bạn nhấn nút Home (kéo để di chuyển, chạm để mở app). " +
                "Bảng nổi cần 2 quyền bên dưới.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        item {
            PermRow("Hiển thị trên các ứng dụng khác", overlayOk) {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            }
        }
        item {
            PermRow("Truy cập sử dụng (nhận biết khi bạn ở màn hình chính)", usageOk) {
                ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
        item { HorizontalDivider() }

        item { Section("Thẻ CHI TIÊU") }
        items(chi, key = { "c${it.id}" }) { c -> CategoryRow(c.name) { toDelete = c } }
        item { OutlinedButton(onClick = { addType = "CHI" }) { Text("+ Thêm thẻ chi tiêu") } }

        item { Section("Thẻ THU NHẬP") }
        items(thu, key = { "t${it.id}" }) { c -> CategoryRow(c.name) { toDelete = c } }
        item { OutlinedButton(onClick = { addType = "THU" }) { Text("+ Thêm thẻ thu nhập") } }

        item { HorizontalDivider() }
        item { Section("Sao lưu ra file text") }
        item { RangeSelector(range) { range = it } }
        item { TypeChips(bType) { bType = it } }
        item {
            Button(onClick = {
                launcher.launch("saoluu_${range.from}_${range.to}.txt")
            }) { Text("Sao lưu theo khoảng thời gian") }
        }
        item {
            Text(
                "File gồm 3 cột: TÊN HÀNH ĐỘNG / BIẾN ĐỘNG / NGÀY GIỜ (phân tách bằng Tab, mở được bằng Excel).",
                style = MaterialTheme.typography.bodySmall
            )
        }

    }

    addType?.let { t ->
        NameDialog("Tên thẻ mới") { n ->
            if (n != null) vm.addCategory(n, t)
            addType = null
        }
    }
    toDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Xóa thẻ \"${c.name}\"?") },
            text = { Text("Thẻ sẽ biến mất khỏi màn hình thêm. Lịch sử giao dịch cũ vẫn được giữ trong báo cáo.") },
            confirmButton = { TextButton(onClick = { vm.archiveCategory(c.id); toDelete = null }) { Text("Xóa") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Huỷ") } }
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun PermRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        if (granted) Text("✔ Đã cấp") else OutlinedButton(onClick = onGrant) { Text("Cấp quyền") }
    }
}

@Composable
private fun CategoryRow(name: String, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f))
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Xóa") }
    }
}
