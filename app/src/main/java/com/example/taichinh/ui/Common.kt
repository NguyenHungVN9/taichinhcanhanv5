package com.example.taichinh.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

val Green = Color(0xFF2E7D32)
val Red = Color(0xFFC62828)

fun money(v: Long): String = NumberFormat.getInstance(Locale("vi", "VN")).format(v) + "đ"

data class DateRange(val from: LocalDate, val to: LocalDate) {
    fun fromMillis() = from.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun toMillis() = to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
}

fun thisMonth(): DateRange {
    val t = LocalDate.now()
    return DateRange(t.withDayOfMonth(1), t)
}

private val dayFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Chọn khoảng thời gian: các nút nhanh + chọn tùy ý bằng lịch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangeSelector(range: DateRange, onChange: (DateRange) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { onChange(DateRange(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), today)) },
                label = { Text("Tuần này") })
            AssistChip(onClick = { onChange(thisMonth()) }, label = { Text("Tháng này") })
            AssistChip(onClick = { onChange(DateRange(today.minusDays(29), today)) }, label = { Text("30 ngày") })
            AssistChip(onClick = { onChange(DateRange(today.withDayOfYear(1), today)) }, label = { Text("Năm nay") })
            AssistChip(onClick = { showPicker = true }, label = { Text("Tùy chọn…") })
        }
        Text("${range.from.format(dayFmt)}  →  ${range.to.format(dayFmt)}", style = MaterialTheme.typography.bodySmall)
    }
    if (showPicker) {
        val st = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = st.selectedStartDateMillis
                    val e = st.selectedEndDateMillis
                    if (s != null && e != null) {
                        onChange(
                            DateRange(
                                Instant.ofEpochMilli(s).atZone(ZoneOffset.UTC).toLocalDate(),
                                Instant.ofEpochMilli(e).atZone(ZoneOffset.UTC).toLocalDate()
                            )
                        )
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Huỷ") } }
        ) {
            DateRangePicker(state = st, modifier = Modifier.height(500.dp))
        }
    }
}

@Composable
fun TypeChips(sel: String, onSel: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("ALL" to "Tất cả", "CHI" to "Chi", "THU" to "Thu").forEach { (k, l) ->
            FilterChip(selected = sel == k, onClick = { onSel(k) }, label = { Text(l) })
        }
    }
}

@Composable
fun NameDialog(title: String, onResult: (String?) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onResult(null) },
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onResult(text.trim().ifEmpty { null }) }) { Text("Thêm") } },
        dismissButton = { TextButton(onClick = { onResult(null) }) { Text("Huỷ") } }
    )
}

@Composable
fun SummaryCard(thu: Long, chi: Long) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Tổng thu"); Text("+" + money(thu), color = Green)
            }
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Tổng chi"); Text("-" + money(chi), color = Red)
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Số dư", fontWeight = FontWeight.Bold)
                Text(money(thu - chi), fontWeight = FontWeight.Bold, color = if (thu >= chi) Green else Red)
            }
        }
    }
}
