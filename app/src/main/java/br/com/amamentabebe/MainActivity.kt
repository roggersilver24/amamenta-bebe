@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package br.com.amamentabebe

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import android.widget.Toast
import br.com.amamentabebe.alarm.AlarmScheduler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.amamentabebe.alarm.NotificationHelper
import br.com.amamentabebe.data.*
import br.com.amamentabebe.family.FamilyPanel
import br.com.amamentabebe.domain.FeedingCalculator
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            try {
            val app = application as AmamentaApplication
            val saved = app.settings.current()
            val alarm = saved.nextAlarmMillis ?: return@launch
            val scheduler = AlarmScheduler(this@MainActivity, app.settings)
            val expected = if (!NotificationHelper.canNotify(this@MainActivity)) AlarmStatus.NOTIFICATIONS_BLOCKED else if (scheduler.canScheduleExact()) AlarmStatus.EXACT else AlarmStatus.APPROXIMATE
            if (saved.alarmStatus != expected) scheduler.schedule(maxOf(alarm, System.currentTimeMillis() + 1_000))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { Toast.makeText(this@MainActivity, "Não foi possível recuperar o lembrete. Verifique as configurações.", Toast.LENGTH_LONG).show() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AmamentaApp() }
    }
}

private val LightColors = lightColorScheme(primary = Color(0xFF9C3F61), primaryContainer = Color(0xFFFFD9E3), secondary = Color(0xFF76565F), background = Color(0xFFFFF8F8))
private val DarkColors = darkColorScheme(primary = Color(0xFFFFB0C8), primaryContainer = Color(0xFF7E2949), background = Color(0xFF1B1114), surface = Color(0xFF24191C))

@Composable fun AmamentaApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
    val night = state.settings.nightMode == NightMode.ALWAYS || (state.settings.nightMode == NightMode.AUTO && (hour >= 22 || hour < 6))
    val dark = night || when (state.settings.theme) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (night) DarkColors.copy(background = Color.Black, surface = Color(0xFF080808)) else if (dark) DarkColors else LightColors) {
        val message by vm.message.collectAsStateWithLifecycle()
        if (message != null) {
            AlertDialog(onDismissRequest = vm::clearMessage, text = { Text(message!!) }, confirmButton = { TextButton(onClick = vm::clearMessage) { Text("OK") } })
        }
        var tab by remember { mutableIntStateOf(0) }
        Scaffold(bottomBar = {
            NavigationBar {
                listOf(Icons.Default.Home to "Início", Icons.Default.History to "Histórico", Icons.Default.Settings to "Configurações").forEachIndexed { index, item ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(item.first, null) }, label = { Text(item.second) })
                }
            }
        }) { padding ->
            when (tab) {
                0 -> HomeScreen(state, vm, night, Modifier.padding(padding))
                1 -> CombinedHistoryScreen(state, vm, Modifier.padding(padding))
                else -> SettingsScreen(state.settings, vm, Modifier.padding(padding))
            }
        }
    }
}

@Composable private fun Header() = Column {
    Text("Amamenta Bebê", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text("Controle de alimentação e fraldas", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun HomeScreen(state: UiState, vm: MainViewModel, night: Boolean, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
    var adding by remember { mutableStateOf(false) }; var diaper by remember { mutableStateOf(false) }
    val latest = state.feedings.firstOrNull()
    val next = state.settings.nextAlarmMillis ?: latest?.let { FeedingCalculator.nextTime(it.timeMillis, state.settings.intervalMinutes) }
    val today = state.feedings.filter { Instant.ofEpochMilli(it.timeMillis).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now() }
    val average = today.zipWithNext().map { (a, b) -> kotlin.math.abs(a.timeMillis - b.timeMillis) }.takeIf { it.isNotEmpty() }?.average()?.toLong()
    val lastSide = state.feedings.firstOrNull { it.type == FeedingType.BREAST && it.side != BreastSide.NONE }?.side
    LazyColumn(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Header(); if (night) Text(formatTime(now), fontSize = 40.sp) }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PRÓXIMA ALIMENTAÇÃO", fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(next?.let(::formatTime) ?: "--:--", fontSize = 54.sp, fontWeight = FontWeight.Bold)
                    Text(next?.let { formatRemaining(FeedingCalculator.remaining(it, now)) } ?: "Registre a primeira alimentação", fontSize = 17.sp)
                }
            }
        }
        item {
            Button(onClick = { vm.feedNow() }, Modifier.fillMaxWidth().height(68.dp), shape = RoundedCornerShape(22.dp)) {
                Text("🍼  REGISTRAR AGORA", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        item { Text("Última alimentação: ${latest?.let { formatTime(it.timeMillis) } ?: "—"}", fontSize = 20.sp) }
        item { OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Registrar alimentação detalhada") } }
        item { TimerCard(state.timer, now, vm) }
        item { Button(onClick = { diaper = true }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("TROQUEI A FRALDA", fontSize = 20.sp) } }
        if (!night && lastSide != null) item {
            val suggestion = when(lastSide) { BreastSide.LEFT -> "Direito"; BreastSide.RIGHT -> "Esquerdo"; else -> "Qualquer lado" }
            Text("Último: ${sideName(lastSide)}  •  Sugestão de registro: $suggestion", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!night) item {
            Text("Resumo de hoje", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryRow("Total de alimentações", today.size.toString())
                SummaryRow("Última alimentação", latest?.let { formatTime(it.timeMillis) } ?: "—")
                SummaryRow("Intervalo médio", average?.let(::formatDuration) ?: "—")
                SummaryRow("Total de mamadeira", "${today.sumOf { it.amountMl ?: 0 }} ml")
                val diapersToday = state.diapers.filter { Instant.ofEpochMilli(it.timeMillis).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now() }
                SummaryRow("Total de trocas", diapersToday.size.toString())
                SummaryRow("Com xixi", diapersToday.count { it.type != DiaperType.DIRTY }.toString())
                SummaryRow("Com cocô", diapersToday.count { it.type != DiaperType.WET }.toString())
                SummaryRow("Última troca", state.diapers.firstOrNull()?.let { formatTime(it.timeMillis) } ?: "—")
            } }
            Text("Dados informativos para organização familiar; não são avaliação ou recomendação médica.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
    if (adding) FeedingEditor(Feeding(timeMillis = now), { adding = false }, { vm.add(it); adding = false })
    if (diaper) DiaperChoiceDialog({ diaper = false }) { vm.addDiaper(it); diaper = false }
}

@Composable private fun SummaryRow(label: String, value: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value, fontWeight = FontWeight.SemiBold) }

@Composable private fun FeedingEditor(original: Feeding, onDismiss: () -> Unit, onSave: (Feeding) -> Unit) {
    var type by remember { mutableStateOf(original.type) }; var side by remember { mutableStateOf(if (original.type == FeedingType.BREAST && original.side == BreastSide.NONE) BreastSide.LEFT else original.side) }
    var amount by remember { mutableStateOf(original.amountMl?.toString() ?: "") }; var note by remember { mutableStateOf(original.note) }
    var dateText by remember { mutableStateOf(Instant.ofEpochMilli(original.timeMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()) }
    var leftDuration by remember { mutableStateOf((original.leftDurationMillis / 60000).toString()) }
    var rightDuration by remember { mutableStateOf((original.rightDurationMillis / 60000).toString()) }
    val initial = Instant.ofEpochMilli(original.timeMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    var hour by remember { mutableStateOf(initial.hour.toString().padStart(2, '0')) }; var minute by remember { mutableStateOf(initial.minute.toString().padStart(2, '0')) }
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val timestamp = runCatching {
        requireNotNull(date).atTime(LocalTime.of(requireNotNull(hour.toIntOrNull()), requireNotNull(minute.toIntOrNull())))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli().takeIf { it > 0 }
    }.getOrNull()
    val valid = timestamp != null && (type != FeedingType.BOTTLE || (amount.toIntOrNull() ?: 0) > 0)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Registrar / editar alimentação") }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Tipo", fontWeight = FontWeight.Bold); SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(FeedingType.QUICK to "Rápido", FeedingType.BREAST to "Peito", FeedingType.BOTTLE to "Mamadeira").forEachIndexed { i, pair ->
                    SegmentedButton(selected = type == pair.first, onClick = { type = pair.first; if (type == FeedingType.BREAST && side == BreastSide.NONE) side = BreastSide.LEFT }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(pair.second, fontSize = 12.sp) }
                }
            } }
            if (type == FeedingType.BREAST) item { Text("Lado", fontWeight = FontWeight.Bold); SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(BreastSide.LEFT to "Esq.", BreastSide.RIGHT to "Dir.", BreastSide.BOTH to "Ambos").forEachIndexed { i, pair ->
                    SegmentedButton(selected = side == pair.first, onClick = { side = pair.first }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(pair.second) }
                }
            } }
            if (type == FeedingType.BOTTLE) item { OutlinedTextField(amount, { amount = it.filter(Char::isDigit).take(4) }, label = { Text("Quantidade (ml)") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(dateText, { dateText = it }, label = { Text("Data (AAAA-MM-DD)") }, isError = date == null, modifier = Modifier.fillMaxWidth()) }
            if (type == FeedingType.BREAST) item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(leftDuration, { leftDuration = it.filter(Char::isDigit).take(4) }, label = { Text("Esq. (min)") }, modifier = Modifier.weight(1f))
                OutlinedTextField(rightDuration, { rightDuration = it.filter(Char::isDigit).take(4) }, label = { Text("Dir. (min)") }, modifier = Modifier.weight(1f))
            } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(hour, { hour = it.filter(Char::isDigit).take(2) }, label = { Text("Hora") }, modifier = Modifier.weight(1f)); OutlinedTextField(minute, { minute = it.filter(Char::isDigit).take(2) }, label = { Text("Min") }, modifier = Modifier.weight(1f)) } }
            item { OutlinedTextField(note, { note = it.take(5000) }, label = { Text("Observação") }, minLines = 2, modifier = Modifier.fillMaxWidth()) }
        }
    }, confirmButton = {
        TextButton(enabled = valid, onClick = {
            val millis = timestamp ?: return@TextButton
            onSave(original.copy(timeMillis = millis, type = type, side = if (type == FeedingType.BREAST) side else BreastSide.NONE, amountMl = if (type == FeedingType.BOTTLE) amount.toIntOrNull() else null, note = note.trim(), leftDurationMillis = if (type == FeedingType.BREAST) if (leftDuration == (original.leftDurationMillis / 60000).toString()) original.leftDurationMillis else (leftDuration.toLongOrNull() ?: 0) * 60000 else 0, rightDurationMillis = if (type == FeedingType.BREAST) if (rightDuration == (original.rightDurationMillis / 60000).toString()) original.rightDurationMillis else (rightDuration.toLongOrNull() ?: 0) * 60000 else 0))
        }) { Text("Salvar") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable private fun SettingsScreen(settings: AppSettings, vm: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val notificationGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val exactGranted = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var customDialog by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportBackup) }
    var importUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { importUri = it }
    importUri?.let { uri -> AlertDialog(onDismissRequest = { importUri = null }, title = { Text("Importar backup?") }, text = { Text("Os registros serão adicionados sem apagar o histórico. Preferências do backup serão restauradas.") }, confirmButton = { TextButton(onClick = { vm.importBackup(uri); importUri = null }) { Text("Importar") } }, dismissButton = { TextButton(onClick = { importUri = null }) { Text("Cancelar") } }) }
    LazyColumn(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Configurações", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        item { Text("Intervalo padrão", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("Usado apenas como lembrete de organização, não como recomendação médica.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(60 to "1h", 90 to "1h30", 120 to "2h", 150 to "2h30", 180 to "3h").forEach { (minutes, label) -> FilterChip(selected = settings.intervalMinutes == minutes, onClick = { vm.setInterval(minutes) }, label = { Text(label) }) }
            FilterChip(selected = settings.intervalMinutes !in listOf(60,90,120,150,180), onClick = { customDialog = true }, label = { Text("Personalizado") })
        } }
        item { HorizontalDivider(); Text("Avisos", fontSize = 20.sp, fontWeight = FontWeight.Bold); SettingSwitch("Som", settings.sound, vm::setSound); SettingSwitch("Vibração", settings.vibration, vm::setVibration) }
        item { HorizontalDivider(); Text("Tema", fontSize = 20.sp, fontWeight = FontWeight.Bold); SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(ThemeMode.SYSTEM to "Sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Escuro").forEachIndexed { i, pair -> SegmentedButton(settings.theme == pair.first, { vm.setTheme(pair.first) }, SegmentedButtonDefaults.itemShape(i,3)) { Text(pair.second) } }
        } }
        item { HorizontalDivider(); Text("Modo Madrugada", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("Automático entre 22h e 6h.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(NightMode.AUTO to "Automático", NightMode.ALWAYS to "Sempre", NightMode.OFF to "Desativado").forEach { (mode, label) -> FilterChip(settings.nightMode == mode, { vm.setNightMode(mode) }, label = { Text(label) }) } } }
        item { HorizontalDivider(); Text("Backup local", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { export.launch("Amamenta-Bebe-backup.json") }) { Text("Exportar backup") }
            TextButton(onClick = { import.launch(arrayOf("application/json", "text/plain")) }) { Text("Importar backup") } }
        item { HorizontalDivider(); FamilyPanel(context.applicationContext as AmamentaApplication) }
        item { AlarmStatusMessage(settings.alarmStatus) }
        item { HorizontalDivider(); Text("Permissões", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        item { PermissionCard("Notificações", notificationGranted, if (notificationGranted) null else ({
            if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else context.startActivity(NotificationHelper.openNotificationSettings(context))
        })) }
        item { PermissionCard("Alarmes e lembretes", exactGranted, if (exactGranted || Build.VERSION.SDK_INT < 31) null else ({ runCatching { context.startActivity(NotificationHelper.openExactAlarmSettings(context)) }; Unit })) }
        if (!notificationGranted || !exactGranted) item { Text("Sem essas permissões, o Android pode impedir ou atrasar o aviso. O registro de alimentação continua funcionando normalmente.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        item { HorizontalDivider(); Text("Sobre", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("Amamenta Bebê ${BuildConfig.VERSION_NAME}\nFunciona offline, sem anúncios."); SupportPanel() }
    }
    if (customDialog) CustomIntervalDialog(settings.intervalMinutes, { customDialog = false }, { vm.setInterval(it); customDialog = false })
}

@Composable private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Switch(checked, onChange) }
@Composable private fun PermissionCard(label: String, granted: Boolean, action: (() -> Unit)?) = Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (granted) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = if (granted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(label, fontWeight = FontWeight.Bold); Text(if (granted) "Ativadas / Permitido" else "Desativadas / Não permitido", fontSize = 13.sp) }; if (action != null) TextButton(onClick = action) { Text("Abrir") } } }

@Composable private fun CustomIntervalDialog(current: Int, dismiss: () -> Unit, save: (Int) -> Unit) {
    var hours by remember { mutableStateOf((current / 60).toString()) }; var minutes by remember { mutableStateOf((current % 60).toString()) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Intervalo personalizado") }, text = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(hours, { hours = it.filter(Char::isDigit).take(2) }, label = { Text("Horas") }, modifier = Modifier.weight(1f)); OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(2) }, label = { Text("Minutos") }, modifier = Modifier.weight(1f)) } }, confirmButton = { TextButton(onClick = { save(((hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)).coerceAtLeast(1)) }) { Text("Salvar") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}

private fun formatTime(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
private fun formatRemaining(millis: Long): String { val total = millis / 60_000; return if (total <= 0) "Horário alcançado" else "${total / 60}h ${total % 60}min restantes" }
private fun formatDuration(millis: Long): String { val total = millis / 60_000; return "${total / 60}h ${total % 60}min" }
private fun dayLabel(date: LocalDate): String = when(date) { LocalDate.now() -> "Hoje"; LocalDate.now().minusDays(1) -> "Ontem"; else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale("pt", "BR"))) }
private fun sideName(side: BreastSide) = when(side) { BreastSide.LEFT -> "Esquerdo"; BreastSide.RIGHT -> "Direito"; BreastSide.BOTH -> "Ambos"; else -> "Não informado" }
private fun feedingDescription(f: Feeding) = when(f.type) { FeedingType.QUICK -> "Registro rápido"; FeedingType.BREAST -> "Amamentação • ${sideName(f.side)} • ${formatDuration(f.leftDurationMillis + f.rightDurationMillis)}"; FeedingType.BOTTLE -> "Mamadeira${f.amountMl?.let { " • $it ml" } ?: ""}" }

@Composable private fun TimerCard(timer: br.com.amamentabebe.domain.TimerSnapshot, now: Long, vm: MainViewModel) {
    val (left, right) = timer.elapsed(now)
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Cronômetro de amamentação", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (!timer.active) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.startTimer(BreastSide.LEFT) }, modifier = Modifier.weight(1f).height(56.dp)) { Text("Esquerdo") }
            Button(onClick = { vm.startTimer(BreastSide.RIGHT) }, modifier = Modifier.weight(1f).height(56.dp)) { Text("Direito") }
        } else {
            Text("${timerClock(left + right)} • ${sideName(timer.activeSide)}", fontSize = 32.sp)
            Text("Esquerdo: ${timerClock(left)} • Direito: ${timerClock(right)}")
            if (left + right >= 1_800_000) Text("30 minutos atingidos", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (timer.running) vm.pauseTimer() else vm.resumeTimer() }) { Text(if (timer.running) "Pausar" else "Continuar") }
                OutlinedButton(onClick = { vm.switchTimer() }) { Text("Trocar lado") }
                Button(onClick = { vm.finishTimer() }) { Text("Finalizar") }
            }
        }
    } }
}
private fun timerClock(ms: Long): String { val seconds = ms / 1000; return "%02d:%02d".format(seconds / 60, seconds % 60) }
@Composable private fun DiaperChoiceDialog(dismiss: () -> Unit, choose: (DiaperType) -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("Troca de fralda") }, text = { Column {
        DiaperType.entries.forEach { type -> Button(onClick = { choose(type) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(56.dp)) { Text(diaperName(type)) } }
    } }, confirmButton = {}, dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}
private fun diaperName(type: DiaperType) = when (type) { DiaperType.WET -> "Xixi"; DiaperType.DIRTY -> "Cocô"; DiaperType.BOTH -> "Xixi + Cocô" }
private data class HistoryEntry(val time: Long, val feeding: Feeding? = null, val diaper: Diaper? = null)
@Composable private fun CombinedHistoryScreen(state: UiState, vm: MainViewModel, modifier: Modifier) {
    var filter by remember { mutableIntStateOf(0) }
    var feedingEdit by remember { mutableStateOf<Feeding?>(null) }
    var diaperEdit by remember { mutableStateOf<Diaper?>(null) }
    var deleting by remember { mutableStateOf<HistoryEntry?>(null) }
    val entries = (state.feedings.filter { filter == 0 || (filter == 1 && it.type == FeedingType.BREAST) || (filter == 2 && it.type == FeedingType.BOTTLE) }.map { HistoryEntry(it.timeMillis, feeding = it) } + if (filter == 0 || filter == 3) state.diapers.map { HistoryEntry(it.timeMillis, diaper = it) } else emptyList()).sortedByDescending { it.time }
    LazyColumn(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Histórico", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Todos", "Amamentação", "Mamadeira", "Fraldas").forEachIndexed { index, label -> FilterChip(filter == index, { filter = index }, label = { Text(label) }) } } }
        entries.groupBy { Instant.ofEpochMilli(it.time).atZone(ZoneId.systemDefault()).toLocalDate() }.forEach { (date, records) ->
            item { Text(dayLabel(date), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            items(records, key = { if (it.feeding != null) "f${it.feeding.id}" else "d${it.diaper!!.id}" }) { entry ->
                Card(onClick = { feedingEdit = entry.feeding; diaperEdit = entry.diaper }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(formatTime(entry.time), fontWeight = FontWeight.Bold)
                            Text(entry.feeding?.let(::feedingDescription) ?: "Fralda • ${diaperName(entry.diaper!!.type)}")
                            val authorKey = if (entry.feeding != null) "FEEDING:${entry.feeding.id}" else "DIAPER:${entry.diaper!!.id}"
                            state.authors[authorKey]?.let { Text("Registrado por $it", fontSize = 13.sp) }
                            entry.feeding?.note?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        }
                        IconButton(onClick = { deleting = entry }) { Icon(Icons.Default.Delete, "Excluir registro") }
                    }
                }
            }
        }
        if (entries.isEmpty()) item { Text("Nenhum registro neste filtro.") }
    }
    feedingEdit?.let { value -> FeedingEditor(value, { feedingEdit = null }, { vm.update(it); feedingEdit = null }) }
    diaperEdit?.let { value -> DiaperEditor(value, { diaperEdit = null }, { vm.updateDiaper(it); diaperEdit = null }) }
    deleting?.let { value -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Excluir registro?") }, text = { Text("Esta ação não pode ser desfeita.") }, confirmButton = { TextButton(onClick = { value.feeding?.let(vm::delete); value.diaper?.let(vm::deleteDiaper); deleting = null }) { Text("Excluir") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }) }
}
@Composable private fun DiaperEditor(original: Diaper, dismiss: () -> Unit, save: (Diaper) -> Unit) {
    var type by remember { mutableStateOf(original.type) }
    val initial = Instant.ofEpochMilli(original.timeMillis).atZone(ZoneId.systemDefault())
    var date by remember { mutableStateOf(initial.toLocalDate().toString()) }
    var time by remember { mutableStateOf(initial.format(DateTimeFormatter.ofPattern("HH:mm"))) }
    val parsed = runCatching { LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli().takeIf { it > 0 } }.getOrNull()
    AlertDialog(onDismissRequest = dismiss, title = { Text("Editar fralda") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow { DiaperType.entries.forEach { item -> FilterChip(type == item, { type = item }, label = { Text(diaperName(item)) }) } }
        OutlinedTextField(date, { date = it }, label = { Text("Data (AAAA-MM-DD)") })
        OutlinedTextField(time, { time = it }, label = { Text("Horário (HH:mm)") }, isError = parsed == null)
    } }, confirmButton = { TextButton(enabled = parsed != null, onClick = { parsed?.let { save(original.copy(timeMillis = it, type = type)) } }) { Text("Salvar") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}
@Composable private fun AlarmStatusMessage(status: AlarmStatus) {
    val text = when (status) {
        AlarmStatus.APPROXIMATE -> "Alarme aproximado: o Android pode atrasar o aviso. Autorize alarmes exatos para maior precisão."
        AlarmStatus.NOTIFICATIONS_BLOCKED -> "Notificações bloqueadas. Ative a permissão abaixo para receber lembretes."
        AlarmStatus.FAILED -> "Não foi possível programar o alarme. Revise as permissões."
        else -> null
    }
    if (text != null) Text(text, color = MaterialTheme.colorScheme.error)
}
