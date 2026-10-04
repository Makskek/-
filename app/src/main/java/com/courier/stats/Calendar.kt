package com.courier.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

val NumKb = KeyboardOptions(keyboardType = KeyboardType.Number)

@Composable
fun CalendarCard(shifts: List<Shift>, onDelete: (Long) -> Unit, onAdd: (Shift) -> Unit) {
    val zone = ZoneId.systemDefault()
    val ru = Locale("ru")
    var ym by remember { mutableStateOf(YearMonth.now()) }
    var sel by remember { mutableStateOf<LocalDate?>(null) }
    var del by remember { mutableStateOf<Shift?>(null) }
    var adding by remember { mutableStateOf<LocalDate?>(null) }
    val today = LocalDate.now()
    val byDay = shifts.groupBy { Instant.ofEpochMilli(it.end).atZone(zone).toLocalDate() }
    val primary = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onSurface.copy(.45f)
    val monthDays = (1..ym.lengthOfMonth()).map { ym.atDay(it) }
    val work = monthDays.count { byDay.containsKey(it) }
    val off = monthDays.count { !it.isAfter(today) && !byDay.containsKey(it) }
    val title = ym.month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, ru)
        .replaceFirstChar { it.uppercase() } + " " + ym.year

    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton({ ym = ym.minusMonths(1) }) { Text("‹", fontSize = 26.sp) }
                Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                TextButton({ ym = ym.plusMonths(1) }) { Text("›", fontSize = 26.sp) }
            }
            Row {
                listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = dim)
                }
            }
            val offset = ym.atDay(1).dayOfWeek.value - 1
            val rows = (offset + ym.lengthOfMonth() + 6) / 7
            for (r in 0 until rows) Row {
                for (c in 0..6) {
                    val n = r * 7 + c - offset + 1
                    if (n !in 1..ym.lengthOfMonth()) { Spacer(Modifier.weight(1f)); continue }
                    val d = ym.atDay(n)
                    val sum = byDay[d]?.sumOf { it.income } ?: 0
                    val isWork = byDay.containsKey(d)
                    val shape = RoundedCornerShape(12.dp)
                    var mod = Modifier.weight(1f).padding(2.dp).height(54.dp).clip(shape)
                        .background(if (isWork) primary.copy(.2f) else Color.Transparent)
                    if (d == today) mod = mod.border(1.5.dp, primary, shape)
                    Column(mod.clickable { sel = d }, horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Text("$n", fontWeight = if (isWork) FontWeight.Bold else FontWeight.Normal,
                            color = if (!isWork && !d.isAfter(today)) dim else MaterialTheme.colorScheme.onSurface)
                        if (isWork) Text("$sum", fontSize = 10.sp, color = primary, maxLines = 1)
                    }
                }
            }
            Text("Рабочих дней: $work · Выходных: $off", fontSize = 13.sp, color = dim)
        }
    }

    sel?.let { d ->
        val list = byDay[d].orEmpty()
        AlertDialog(onDismissRequest = { sel = null },
            title = { Text(d.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", ru))) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (list.isEmpty()) Text(if (d.isAfter(today)) "День ещё впереди" else "Нерабочий день, дохода нет")
                    else {
                        Text("Рабочий день · ${rub(list.sumOf { it.income })}", fontWeight = FontWeight.Bold, color = primary)
                        val tf = DateTimeFormatter.ofPattern("HH:mm")
                        list.forEach { s ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    if (s.start == s.end) Text("Добавлено вручную")
                                    else Text("${Instant.ofEpochMilli(s.start).atZone(zone).format(tf)}–${Instant.ofEpochMilli(s.end).atZone(zone).format(tf)}")
                                    Text("${s.orders} зак." + (if (s.bonuses > 0) " · ${s.bonuses} бон." else "") + " · ${rub(s.income)}", fontSize = 13.sp, color = dim)
                                }
                                TextButton({ del = s }) { Text("Удалить", color = Color(0xFFE5484D)) }
                            }
                        }
                    }
                    if (!d.isAfter(today)) Button({ adding = d }, Modifier.fillMaxWidth()) { Text("＋ Добавить доход") }
                }
            },
            confirmButton = { TextButton({ sel = null }) { Text("Закрыть") } })
    }
    adding?.let { d ->
        var n by remember(d) { mutableStateOf("") }
        var b by remember(d) { mutableStateOf("") }
        var h by remember(d) { mutableStateOf("") }
        val cnt = n.toIntOrNull() ?: 0
        val bon = b.toIntOrNull() ?: 0
        val hrs = h.replace(',', '.').toDoubleOrNull() ?: 0.0
        AlertDialog(onDismissRequest = { adding = null },
            title = { Text("Доход за " + d.format(DateTimeFormatter.ofPattern("d MMMM", ru))) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(n, { n = it.filter(Char::isDigit).take(3) }, label = { Text("Заказов") }, singleLine = true, keyboardOptions = NumKb)
                    OutlinedTextField(b, { b = it.filter(Char::isDigit).take(3) }, label = { Text("Бонусов (1 = $BONUS_PRICE ₽)") }, singleLine = true, keyboardOptions = NumKb)
                    OutlinedTextField(h, { h = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(5) },
                        label = { Text("Часов на смене (необязательно)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Text("Доход: ${rub(cnt * ORDER_PRICE + bon * BONUS_PRICE)}", fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                TextButton(enabled = cnt + bon > 0, onClick = {
                    val end = d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
                    onAdd(Shift(end - (hrs * 3_600_000).toLong(), end, cnt, bon)); adding = null
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton({ adding = null }) { Text("Отмена") } })
    }
    del?.let { s ->
        AlertDialog(onDismissRequest = { del = null },
            title = { Text("Удалить доход?") },
            text = { Text("Смена на ${rub(s.income)} будет удалена без возможности восстановления.") },
            confirmButton = { TextButton({ onDelete(s.start); del = null; sel = null }) { Text("Удалить", color = Color(0xFFE5484D)) } },
            dismissButton = { TextButton({ del = null }) { Text("Отмена") } })
    }
}
