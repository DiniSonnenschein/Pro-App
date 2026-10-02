package de.produktivitaet.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val store = vm.store
    var dailyValue by rememberSaveable { mutableStateOf(store.dailyGoal.value.toString()) }
    var dailyUnit by rememberSaveable { mutableStateOf(store.dailyGoal.unit) }
    var weeklyValue by rememberSaveable { mutableStateOf(store.weeklyGoal.value.toString()) }
    var weeklyUnit by rememberSaveable { mutableStateOf(store.weeklyGoal.unit) }
    val daily = dailyValue.toIntOrNull()?.takeIf { it > 0 }
    val weekly = weeklyValue.toIntOrNull()?.takeIf { it > 0 }

    Column(Modifier.fillMaxSize()) {
        TopBar("Einstellungen", vm::back)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            GoalEditor(
                title = "Tagesziel",
                hint = "Ist es erreicht, gibt es den Pilz des Tages.",
                value = dailyValue,
                onValue = { dailyValue = it },
                unit = dailyUnit,
                onUnit = { dailyUnit = it },
            )
            GoalEditor(
                title = "Wochenziel",
                hint = "Gilt immer von Montag bis Sonntag. Ist es erreicht, gibt es ein Pilz-Wesen.",
                value = weeklyValue,
                onValue = { weeklyValue = it },
                unit = weeklyUnit,
                onUnit = { weeklyUnit = it },
            )
        }
        Column(Modifier.padding(16.dp)) {
            if (daily == null || weekly == null) {
                Text("Bitte gib für beide Ziele eine Zahl größer als 0 ein.", color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
            }
            OutlineButton(
                "Speichern",
                { if (daily != null && weekly != null) vm.saveGoals(Goal(daily, dailyUnit), Goal(weekly, weeklyUnit)) },
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                enabled = daily != null && weekly != null,
            )
        }
    }
}

@Composable
private fun GoalEditor(
    title: String,
    hint: String,
    value: String,
    onValue: (String) -> Unit,
    unit: GoalUnit,
    onUnit: (GoalUnit) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(hint, color = Muted, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GoalUnit.entries.forEach { u ->
                Pill(u.label, selected = u == unit) { onUnit(u) }
            }
        }
        TextInput(
            value = value,
            onValueChange = onValue,
            label = if (unit == GoalUnit.TASKS) "Anzahl Aufgaben" else "Minuten",
            numeric = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Kleiner Umschalter: weiß gefüllt, wenn ausgewählt. */
@Composable
private fun Pill(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) White else Black)
            .border(1.dp, if (enabled) White else Dim, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text, color = if (selected) Black else if (enabled) White else Dim, fontSize = 15.sp)
    }
}

private enum class StatsRange { WEEK, MONTH }

@Composable
fun StatisticsScreen(vm: MainViewModel) {
    var range by rememberSaveable { mutableStateOf(StatsRange.WEEK) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val today = LocalDate.now()
    val start: LocalDate
    val days: Int
    val periodLabel: String
    if (range == StatsRange.WEEK) {
        start = LocalDate.ofEpochDay(weekStart(today.toEpochDay())).plusWeeks(offset.toLong())
        days = 7
        val f = DateTimeFormatter.ofPattern("dd.MM.")
        periodLabel = "${start.format(f)} – ${start.plusDays(6).format(f)}${start.plusDays(6).year}"
    } else {
        start = today.withDayOfMonth(1).plusMonths(offset.toLong())
        days = start.lengthOfMonth()
        periodLabel = start.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.GERMAN))
    }
    val first = start.toEpochDay()
    val tasks = IntArray(days)
    val minutes = IntArray(days)
    for (c in vm.store.completions) {
        val i = (c.epochDay - first).toInt()
        if (i in 0 until days) {
            tasks[i]++
            minutes[i] += c.minutes
        }
    }
    val labels = if (range == StatsRange.WEEK) {
        listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    } else {
        List(days) { d -> if (d == 0 || (d + 1) % 5 == 0) "${d + 1}" else "" }
    }
    val goal = vm.store.dailyGoal

    Column(Modifier.fillMaxSize()) {
        TopBar("Statistik", vm::back)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Woche", selected = range == StatsRange.WEEK) {
                    range = StatsRange.WEEK
                    offset = 0
                }
                Pill("Monat", selected = range == StatsRange.MONTH) {
                    range = StatsRange.MONTH
                    offset = 0
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("‹", selected = false) { offset-- }
                Text(
                    periodLabel,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Pill("›", selected = false, enabled = offset < 0) { if (offset < 0) offset++ }
            }
            Text(
                "${tasks.sum()} Aufgaben · ${formatMinutes(minutes.sum())}",
                color = Muted,
                fontSize = 15.sp,
            )
            ChartBlock(
                title = "Aufgaben pro Tag",
                values = tasks,
                labels = labels,
                color = LightBlue,
                goal = if (goal.unit == GoalUnit.TASKS) goal.value else null,
                showValues = range == StatsRange.WEEK,
            )
            ChartBlock(
                title = "Minuten pro Tag",
                values = minutes,
                labels = labels,
                color = Color(0xFFB6F09C),
                goal = if (goal.unit == GoalUnit.MINUTES) goal.value else null,
                showValues = range == StatsRange.WEEK,
            )
            if (vm.store.completions.isEmpty()) {
                Text("Noch keine erledigten Aufgaben. Die Statistik füllt sich ab jetzt mit jeder erledigten Aufgabe.", color = Muted, fontSize = 14.sp)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun formatMinutes(m: Int): String = when {
    m < 60 -> "$m Min."
    m % 60 == 0 -> "${m / 60} Std."
    else -> "${m / 60} Std. ${m % 60} Min."
}

@Composable
private fun ChartBlock(title: String, values: IntArray, labels: List<String>, color: Color, goal: Int?, showValues: Boolean) {
    Column(
        Modifier.fillMaxWidth().border(1.dp, White, CardShape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (goal != null) Text("Gestrichelt: Tagesziel ($goal)", color = Muted, fontSize = 12.sp)
        BarChart(values, labels, color, goal, showValues, Modifier.fillMaxWidth().height(190.dp))
    }
}

/** Einfaches Balkendiagramm mit optionaler Ziel-Linie. */
@Composable
private fun BarChart(values: IntArray, labels: List<String>, color: Color, goal: Int?, showValues: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val labelSpace = 22.dp.toPx()
        val topSpace = if (showValues) 18.dp.toPx() else 6.dp.toPx()
        val chartHeight = size.height - labelSpace - topSpace
        val max = maxOf(values.maxOrNull() ?: 0, goal ?: 0, 1)
        val slot = size.width / values.size
        val barWidth = slot * 0.62f
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.rgb(154, 154, 154)
            textSize = 11.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
        }
        // Grundlinie
        drawLine(Dim, Offset(0f, topSpace + chartHeight), Offset(size.width, topSpace + chartHeight), strokeWidth = 1.dp.toPx())
        values.forEachIndexed { i, v ->
            val x = slot * i + (slot - barWidth) / 2
            val h = chartHeight * v / max
            if (v > 0) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, topSpace + chartHeight - h),
                    size = Size(barWidth, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(minOf(barWidth / 2, 6.dp.toPx())),
                )
                if (showValues) {
                    drawContext.canvas.nativeCanvas.drawText("$v", x + barWidth / 2, topSpace + chartHeight - h - 5.dp.toPx(), textPaint)
                }
            }
            val label = labels.getOrNull(i).orEmpty()
            if (label.isNotEmpty()) {
                drawContext.canvas.nativeCanvas.drawText(label, slot * i + slot / 2, size.height - 4.dp.toPx(), textPaint)
            }
        }
        if (goal != null) {
            val y = topSpace + chartHeight - chartHeight * goal / max
            drawLine(
                White,
                Offset(0f, y),
                Offset(size.width, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )
        }
    }
}
