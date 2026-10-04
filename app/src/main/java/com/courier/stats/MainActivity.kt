package com.courier.stats

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b); enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            val cs = if (dark) darkColorScheme(primary = Color(0xFFFF8A3D), onPrimary = Color.Black,
                background = Color(0xFF121214), surface = Color(0xFF1E1E22), onSurface = Color(0xFFF2F2F2), onBackground = Color(0xFFF2F2F2))
            else lightColorScheme(primary = Color(0xFFF26A00), onPrimary = Color.White,
                background = Color(0xFFF6F3EF), surface = Color.White, onSurface = Color(0xFF1C1C20), onBackground = Color(0xFF1C1C20))
            MaterialTheme(cs) { Surface(Modifier.fillMaxSize(), color = cs.background) { Main() } }
        }
    }
}

fun fmt(ms: Long) = "%02d:%02d:%02d".format(ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60)
fun rub(v: Int) = "%,d ₽".format(v).replace(',', '\u00A0')

@Composable
fun Main() {
    val ctx = LocalContext.current
    var active by remember { mutableLongStateOf(Store.active(ctx)) }
    var shifts by remember { mutableStateOf(Store.shifts(ctx)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var ask by remember { mutableStateOf(false) }
    var period by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    fun begin() {
        Store.setActive(ctx, System.currentTimeMillis()); active = Store.active(ctx)
        ContextCompat.startForegroundService(ctx, Intent(ctx, TimerService::class.java))
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { begin() }
    fun onStart() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) perm.launch(Manifest.permission.POST_NOTIFICATIONS) else begin()
    }

    val zone = ZoneId.systemDefault()
    val perDay = shifts.groupBy { Instant.ofEpochMilli(it.end).atZone(zone).toLocalDate() }
        .mapValues { e -> e.value.sumOf { it.income } }
    val timed = shifts.filter { it.hours > 0 }
    val timedHours = timed.sumOf { it.hours }
    val avgHour = if (timedHours > 0) (timed.sumOf { it.income } / timedHours).toInt() else 0
    val today = LocalDate.now()
    val range = when (period) {
        0 -> listOf(today)
        1 -> (0..6).map { today.with(DayOfWeek.MONDAY).plusDays(it.toLong()) }
        else -> (1..today.lengthOfMonth()).map { today.withDayOfMonth(it) }
    }
    val amounts = range.map { perDay[it] ?: 0 }
    val monthTotal = perDay.filterKeys { it.month == today.month && it.year == today.year }.values.sum()
    val workedDays = perDay.keys.count { it.month == today.month && it.year == today.year }
    val forecast = monthTotal * today.lengthOfMonth() / today.dayOfMonth

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            Text("Курьер Статс", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("версия 1.2 · бонусы и ввод за прошлые дни", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }

        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(if (active > 0) "Смена идёт" else "Смена не начата", color = MaterialTheme.colorScheme.primary)
                Text(fmt(if (active > 0) now - active else 0), fontSize = 54.sp, fontWeight = FontWeight.Bold)
                Button(onClick = { if (active > 0) ask = true else onStart() },
                    modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) {
                    Text(if (active > 0) "Завершить смену" else "Начало смены", fontSize = 18.sp)
                }
                HorizontalDivider()
                Text("Средний доход за час", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(.6f))
                Text(if (avgHour > 0) rub(avgHour) + "/ч" else "—", fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
            }
        }

        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("День", "Неделя", "Месяц").forEachIndexed { i, t ->
                        SegmentedButton(period == i, { period = i }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(t) }
                    }
                }
                Text("Доход за период", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(.6f))
                Text(rub(amounts.sum()), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                if (range.size > 1) {
                    val c = MaterialTheme.colorScheme.primary; val mx = (amounts.maxOrNull() ?: 0).coerceAtLeast(1)
                    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                        val w = size.width / range.size
                        amounts.forEachIndexed { i, v ->
                            val h = size.height * v / mx
                            drawRoundRect(if (range[i] == today) c else c.copy(.45f), Offset(i * w + w * .15f, size.height - h.coerceAtLeast(4f)),
                                Size(w * .7f, h.coerceAtLeast(4f)), CornerRadius(8f))
                        }
                    }
                }
                val f = DateTimeFormatter.ofPattern("EEE, d MMM", Locale("ru"))
                range.zip(amounts).filter { it.second > 0 }.forEach { (d, v) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(d.format(f)); Text(rub(v), fontWeight = FontWeight.SemiBold)
                    }
                }
                if (amounts.sum() == 0) Text("Пока нет данных", color = MaterialTheme.colorScheme.onSurface.copy(.6f))
            }
        }

        CalendarCard(shifts, onDelete = { Store.remove(ctx, it); shifts = Store.shifts(ctx) },
            onAdd = { Store.add(ctx, it); shifts = Store.shifts(ctx) })

        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primary)) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Text("Прогноз до конца месяца", color = MaterialTheme.colorScheme.onPrimary.copy(.8f))
                Text(rub(forecast), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                Text("Отработано дней: $workedDays, заработано: ${rub(monthTotal)}",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimary.copy(.8f))
            }
        }
    }

    if (ask) {
        var n by remember { mutableStateOf("") }
        var bn by remember { mutableStateOf("") }
        val cnt = n.toIntOrNull() ?: 0
        val bon = bn.toIntOrNull() ?: 0
        AlertDialog(onDismissRequest = { ask = false },
            title = { Text("Завершить смену") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(n, { n = it.filter(Char::isDigit).take(3) }, label = { Text("Сколько заказов?") }, singleLine = true, keyboardOptions = NumKb)
                    OutlinedTextField(bn, { bn = it.filter(Char::isDigit).take(3) }, label = { Text("Бонусов (1 = $BONUS_PRICE ₽)") }, singleLine = true, keyboardOptions = NumKb)
                    Text("Доход: ${rub(cnt * ORDER_PRICE + bon * BONUS_PRICE)}")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Store.add(ctx, Shift(active, System.currentTimeMillis(), cnt, bon))
                    Store.setActive(ctx, 0); active = 0; shifts = Store.shifts(ctx); ask = false
                    ctx.stopService(Intent(ctx, TimerService::class.java))
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton({ ask = false }) { Text("Отмена") } })
    }
}
