package com.torpille.app.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.torpille.app.data.*
import java.time.*
import java.time.format.DateTimeFormatter

private val weekDays = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")
private fun timeLabel(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)
@Composable
fun RankingScheduleSummary(schedule: RankingSchedule) {
    Column {
        Text("Horaires du classement", style = MaterialTheme.typography.titleMedium)
        Text(if (schedule.fullTime) "Actif à temps plein" else "Fuseau : ${schedule.timeZone}")
        if (!schedule.fullTime) {
            schedule.weekly.forEach { Text("${weekDays[(it.day - 1).coerceIn(0, 6)]} · ${timeLabel(it.startMinute)} – ${timeLabel(it.endMinute)}${if (it.endMinute < it.startMinute) " (lendemain)" else ""}") }
            val zone = runCatching { ZoneId.of(schedule.timeZone) }.getOrDefault(ZoneId.systemDefault())
            val format = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(zone)
            schedule.dates.forEach { Text("${format.format(Instant.ofEpochMilli(it.startMs))} – ${format.format(Instant.ofEpochMilli(it.endMs))}") }
        }
    }
}
@Composable
fun RankingScheduleEditor(schedule: RankingSchedule, enabled: Boolean, onChange: (RankingSchedule) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row { Text("Activer le classement à temps plein", Modifier.weight(1f)); Switch(schedule.fullTime, { onChange(schedule.copy(fullTime = it)) }, enabled = enabled) }
        if (!schedule.fullTime) {
            OutlinedTextField(schedule.timeZone, { onChange(schedule.copy(timeZone = it)) }, enabled = enabled,
                label = { Text("Fuseau : Europe/Brussels, Europe/Paris…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            schedule.weekly.forEachIndexed { index, w ->
                fun update(value: WeeklyRankingWindow) { onChange(schedule.copy(weekly = schedule.weekly.toMutableList().also { it[index] = value })) }
                var expanded by remember { mutableStateOf(false) }
                Column {
                    Box {
                        TextButton(enabled = enabled, onClick = { expanded = true }) { Text(weekDays[(w.day - 1).coerceIn(0, 6)]) }
                        DropdownMenu(expanded, { expanded = false }) { weekDays.forEachIndexed { i, name ->
                            DropdownMenuItem(text = { Text(name) }, onClick = { update(w.copy(day = i + 1)); expanded = false })
                        } }
                    }
                    Row {
                        TextButton(enabled = enabled, onClick = { TimePickerDialog(context, { _, h, m -> update(w.copy(startMinute = h * 60 + m)) }, w.startMinute / 60, w.startMinute % 60, true).show() }) { Text("Début : ${timeLabel(w.startMinute)}") }
                        TextButton(enabled = enabled, onClick = { TimePickerDialog(context, { _, h, m -> update(w.copy(endMinute = h * 60 + m)) }, w.endMinute / 60 % 24, w.endMinute % 60, true).show() }) { Text("Fin : ${timeLabel(w.endMinute)}") }
                    }
                    TextButton(enabled = enabled, onClick = { update(w.copy(startMinute = 0, endMinute = 1440)) }) { Text("Toute la journée") }
                    TextButton(enabled = enabled, onClick = { onChange(schedule.copy(weekly = schedule.weekly.filterIndexed { i, _ -> i != index })) }) { Text("Supprimer ce créneau") }
                }
            }
            TextButton(enabled = enabled && schedule.weekly.size < 28, onClick = { onChange(schedule.copy(weekly = schedule.weekly + WeeklyRankingWindow())) }) { Text("Ajouter un créneau hebdomadaire") }
            schedule.dates.forEachIndexed { index, w ->
                val zone = runCatching { ZoneId.of(schedule.timeZone) }.getOrDefault(ZoneId.systemDefault())
                fun pick(start: Boolean) {
                    val old = Instant.ofEpochMilli(if (start) w.startMs else w.endMs).atZone(zone)
                    DatePickerDialog(context, { _, y, month, day ->
                        TimePickerDialog(context, { _, h, m ->
                            val value = LocalDateTime.of(y, month + 1, day, h, m).atZone(zone).toInstant().toEpochMilli()
                            val replacement = if (start) w.copy(startMs = value) else w.copy(endMs = value)
                            onChange(schedule.copy(dates = schedule.dates.toMutableList().also { it[index] = replacement }))
                        }, old.hour, old.minute, true).show()
                    }, old.year, old.monthValue - 1, old.dayOfMonth).show()
                }
                val format = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(zone)
                TextButton(enabled = enabled, onClick = { pick(true) }) { Text("Du : ${format.format(Instant.ofEpochMilli(w.startMs))}") }
                TextButton(enabled = enabled, onClick = { pick(false) }) { Text("Au : ${format.format(Instant.ofEpochMilli(w.endMs))}") }
                TextButton(enabled = enabled, onClick = { onChange(schedule.copy(dates = schedule.dates.filterIndexed { i, _ -> i != index })) }) { Text("Supprimer cette période") }
            }
            TextButton(enabled = enabled && schedule.dates.size < 30, onClick = { onChange(schedule.copy(dates = schedule.dates + DatedRankingWindow())) }) { Text("Ajouter une période datée") }
            Text("Hors horaires : pas de nouvelle torpille ni de points. Les réponses restent possibles et ne relancent aucune obligation. Les pénalités et expulsions attendent la réouverture.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
