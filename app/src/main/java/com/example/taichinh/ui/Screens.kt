package com.example.taichinh.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.List as ListIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.taichinh.data.Category
import com.example.taichinh.data.CategorySum
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun App(vm: MainViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            Column {
            NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Add, null) }, label = { Text("Thêm") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.ListIcon, null) }, label = { Text("Báo cáo") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.Settings, null) }, label = { Text("Cài đặt") })
            }
            AppFooter()
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> AddScreen(vm)
                1 -> ReportScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }
}

/** Dòng thông tin tác giả ở viền dưới cùng của app. Nhấn giữ để chọn và sao chép số tài khoản. */
@Composable
fun AppFooter() {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        SelectionContainer {
            Text(
                "Phần mềm được phát triển bởi Nguyễn Hùng: +84859633633, phát hành miễn phí trọn đời\n" +
                    "Donate: Techcombank: 19034588680013",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ───────────────────────── TAB 1: THÊM HÀNH ĐỘNG TÀI CHÍNH ─────────────────────────

@Composable
fun AddScreen(vm: MainViewModel) {
    var type by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<Category?>(null) }
    var addingCat by remember { mutableStateOf(false) }
    val chi by vm.chiCats.collectAsState()
    val thu by vm.thuCats.collectAsState()
    val t = type
    BackHandler(enabled = t != null) { type = null }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (t == null) {
            Text("Thêm hành động tài chính", style = MaterialTheme.typography.headlineSmall)
            BigCard("CHI TIÊU", MaterialTheme.colorScheme.errorContainer) { type = "CHI" }
            BigCard("THU NHẬP", MaterialTheme.colorScheme.primaryContainer) { type = "THU" }
        } else {
            val cats = if (t == "CHI") chi else thu
            Text(
                if (t == "CHI") "Chi tiêu – chọn mục" else "Thu nhập – chọn mục",
                style = MaterialTheme.typography.titleLarge
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cats, key = { it.id }) { c -> CatCard(c.name, false) { picked = c } }
                item { CatCard("Thêm nút", true) { addingCat = true } }
            }
        }
    }

    picked?.let { c -> AmountDialog(vm, c) { picked = null } }
    if (addingCat) {
        NameDialog("Tên mục mới") { name ->
            if (name != null && t != null) vm.addCategory(name, t)
            addingCat = false
        }
    }
}

@Composable
fun BigCard(title: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(140.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CatCard(label: String, isAdd: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().height(96.dp)) {
        Box(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) {
            if (isAdd) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                    Text(label, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text(label, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmountDialog(vm: MainViewModel, cat: Category, onDone: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var showDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(cat.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter(Char::isDigit).take(12) },
                    label = { Text("Số tiền (đ)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Ghi chú (tuỳ chọn)") }, singleLine = true
                )
                TextButton(onClick = { showDate = true }) {
                    Text("Ngày: ${date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = amount.toLongOrNull()
                if (v == null || v <= 0L) {
                    error = "Nhập số tiền hợp lệ"
                } else {
                    scope.launch {
                        val ts = date.atTime(LocalTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        vm.addTransaction(cat, v, ts, note)
                        Toast.makeText(ctx, "Đã lưu", Toast.LENGTH_SHORT).show()
                        onDone()
                    }
                }
            }) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Huỷ") } }
    )

    if (showDate) {
        val st = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDate = false
                }) { Text("OK") }
            }
        ) { DatePicker(state = st) }
    }
}

// ───────────────────────── TAB 2: BÁO CÁO TÀI CHÍNH ─────────────────────────

@Composable
fun ReportScreen(vm: MainViewModel) {
    var sel by remember { mutableIntStateOf(0) }
    BackHandler(enabled = sel != 0) { sel = 0 }
    when (sel) {
        0 -> Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Báo cáo tài chính", style = MaterialTheme.typography.headlineSmall)
            ReportCard("BÁO CÁO SỐ DƯ TỔNG HỢP") { sel = 1 }
            ReportCard("BÁO CÁO TẦN SUẤT THU CHI") { sel = 2 }
            ReportCard("BÁO CÁO DÒNG TIỀN RA VÀO THEO TỪNG MỤC") { sel = 3 }
        }
        1 -> BalanceReport(vm)
        2 -> CategoryReport(vm, byCount = true)
        else -> CategoryReport(vm, byCount = false)
    }
}

@Composable
fun ReportCard(title: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().height(100.dp)) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.CenterStart) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BalanceReport(vm: MainViewModel) {
    var range by remember { mutableStateOf(thisMonth()) }
    var weekly by remember { mutableStateOf(true) }
    var rows by remember { mutableStateOf<List<BalanceRow>>(emptyList()) }
    LaunchedEffect(range, weekly) { rows = vm.balance(range, weekly) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Báo cáo số dư tổng hợp", style = MaterialTheme.typography.titleLarge)
        RangeSelector(range) { range = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(weekly, { weekly = true }, label = { Text("Theo tuần") })
            FilterChip(!weekly, { weekly = false }, label = { Text("Theo tháng") })
        }
        SummaryCard(rows.sumOf { it.thu }, rows.sumOf { it.chi })
        if (rows.isEmpty()) Text("Chưa có dữ liệu trong khoảng này")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(r.label, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Thu"); Text("+" + money(r.thu), color = Green)
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Chi"); Text("-" + money(r.chi), color = Red)
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Số dư", fontWeight = FontWeight.Bold)
                            Text(money(r.balance), fontWeight = FontWeight.Bold, color = if (r.balance >= 0) Green else Red)
                        }
                    }
                }
            }
        }
    }
}

/**
 * byCount = true  → Báo cáo 2: số lần theo nhóm (thói quen), kèm tổng tiền.
 * byCount = false → Báo cáo 3: số tiền từng mục, xếp từ nhiều xuống ít.
 */
@Composable
fun CategoryReport(vm: MainViewModel, byCount: Boolean) {
    var range by remember { mutableStateOf(thisMonth()) }
    var filter by remember { mutableStateOf("ALL") }
    var data by remember { mutableStateOf<List<CategorySum>>(emptyList()) }
    LaunchedEffect(range) { data = vm.categorySums(range) }

    val shown = data.filter { filter == "ALL" || it.type == filter }
        .let { l -> if (byCount) l.sortedByDescending { it.cnt } else l.sortedByDescending { it.total } }
    val maxV = (shown.maxOfOrNull { if (byCount) it.cnt.toLong() else it.total } ?: 1L).coerceAtLeast(1L)

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (byCount) "Báo cáo tần suất thu chi" else "Dòng tiền ra vào theo từng mục",
            style = MaterialTheme.typography.titleLarge
        )
        RangeSelector(range) { range = it }
        TypeChips(filter) { filter = it }
        if (byCount) {
            Text("Tổng số lần: ${shown.sumOf { it.cnt }}", fontWeight = FontWeight.Medium)
        } else {
            SummaryCard(
                data.filter { it.type == "THU" }.sumOf { it.total },
                data.filter { it.type == "CHI" }.sumOf { it.total }
            )
        }
        if (shown.isEmpty()) Text("Chưa có dữ liệu trong khoảng này")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.categoryId }) { s ->
                val v = if (byCount) s.cnt.toLong() else s.total
                val color = if (s.type == "THU") Green else Red
                val sign = if (s.type == "THU") "+" else "-"
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text(s.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(
                                if (byCount) "${s.cnt} lần" else sign + money(s.total),
                                fontWeight = FontWeight.Bold, color = color
                            )
                        }
                        if (byCount) Text("Tổng tiền: $sign${money(s.total)}", style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(
                            progress = { (v.toFloat() / maxV).coerceIn(0f, 1f) },
                            color = color,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
