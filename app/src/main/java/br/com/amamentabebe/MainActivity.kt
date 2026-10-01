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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.amamentabebe.alarm.NotificationHelper
import br.com.amamentabebe.data.*
import br.com.amamentabebe.domain.FeedingCalculator
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AmamentaApp() }
    }
}

private val LightColors = lightColorScheme(primary = Color(0xFF9C3F61), primaryContainer = Color(0xFFFFD9E3), secondary = Color(0xFF76565F), background = Color(0xFFFFF8F8))
private val DarkColors = darkColorScheme(primary = Color(0xFFFFB0C8), primaryContainer = Color(0xFF7E2949), background = Color(0xFF1B1114), surface = Color(0xFF24191C))

@Composable fun AmamentaApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val dark = when (state.settings.theme) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors) {
        var tab by remember { mutableIntStateOf(0) }
        Scaffold(bottomBar = {
            NavigationBar {
                listOf(Icons.Default.Home to "Início", Icons.Default.History to "Histórico", Icons.Default.Settings to "Configurações").forEachIndexed { index, item ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(item.first, null) }, label = { Text(item.second) })
                }
            }
        }) { padding ->
            when (tab) {
                0 -> HomeScreen(state, vm::feedNow, Modifier.padding(padding))
                1 -> HistoryScreen(state.feedings, vm::update, vm::delete, Modifier.padding(padding))
                else -> SettingsScreen(state.settings, vm, Modifier.padding(padding))
            }
        }
    }
}

@Composable private fun Header() = Column {
    Text("Amamenta Bebê", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text("Controle das mamadas", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun HomeScreen(state: UiState, onFeed: () -> Unit, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
    val latest = state.feedings.firstOrNull()
    val next = state.settings.nextAlarmMillis ?: latest?.let { FeedingCalculator.nextTime(it.timeMillis, state.settings.intervalMinutes) }
    val today = state.feedings.filter { Instant.ofEpochMilli(it.timeMillis).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now() }
    val average = today.zipWithNext().map { (a, b) -> kotlin.math.abs(a.timeMillis - b.timeMillis) }.takeIf { it.isNotEmpty() }?.average()?.toLong()
    val lastSide = state.feedings.firstOrNull { it.type == FeedingType.BREAST && it.side != BreastSide.NONE }?.side
    LazyColumn(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Header() }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PRÓXIMA MAMADA", fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(next?.let(::formatTime) ?: "--:--", fontSize = 54.sp, fontWeight = FontWeight.Bold)
                    Text(next?.let { formatRemaining(FeedingCalculator.remaining(it, now)) } ?: "Registre a primeira mamada", fontSize = 17.sp)
                }
            }
        }
        item {
            Button(onClick = onFeed, Modifier.fillMaxWidth().height(68.dp), shape = RoundedCornerShape(22.dp)) {
                Text("🍼  MAMOU AGORA", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (lastSide != null) item {
            val suggestion = when(lastSide) { BreastSide.LEFT -> "Direito"; BreastSide.RIGHT -> "Esquerdo"; else -> "Qualquer lado" }
            Text("Último: ${sideName(lastSide)}  •  Sugestão de registro: $suggestion", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Text("Resumo de hoje", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryRow("Total de mamadas", today.size.toString())
                SummaryRow("Última mamada", latest?.let { formatTime(it.timeMillis) } ?: "—")
                SummaryRow("Intervalo médio", average?.let(::formatDuration) ?: "—")
                SummaryRow("Total de mamadeira", "${today.sumOf { it.amountMl ?: 0 }} ml")
            } }
            Text("Dados informativos para organização familiar; não são avaliação ou recomendação médica.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable private fun SummaryRow(label: String, value: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value, fontWeight = FontWeight.SemiBold) }

@Composable private fun HistoryScreen(feedings: List<Feeding>, update: (Feeding) -> Unit, delete: (Feeding) -> Unit, modifier: Modifier = Modifier) {
    var editing by remember { mutableStateOf<Feeding?>(null) }; var deleting by remember { mutableStateOf<Feeding?>(null) }
    val groups = feedings.groupBy { Instant.ofEpochMilli(it.timeMillis).atZone(ZoneId.systemDefault()).toLocalDate() }
    LazyColumn(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Histórico", fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("Toque em um registro para complementar ou corrigir.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        groups.forEach { (date, entries) ->
            item { Text(dayLabel(date), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) }
            items(entries, key = { it.id }) { feeding ->
                Card(onClick = { editing = feeding }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(formatTime(feeding.timeMillis), fontWeight = FontWeight.Bold); Text(feedingDescription(feeding), color = MaterialTheme.colorScheme.onSurfaceVariant); if (feeding.note.isNotBlank()) Text(feeding.note, fontSize = 13.sp) }
                        IconButton(onClick = { deleting = feeding }) { Icon(Icons.Default.Delete, "Excluir") }
                    }
                }
            }
        }
        if (feedings.isEmpty()) item { Box(Modifier.fillParentMaxHeight(.6f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Nenhuma mamada registrada ainda.") } }
    }
    editing?.let { FeedingEditor(it, onDismiss = { editing = null }, onSave = { update(it); editing = null }) }
    deleting?.let { item -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Excluir registro?") }, text = { Text("Esta ação não pode ser desfeita.") }, confirmButton = { TextButton(onClick = { delete(item); deleting = null }) { Text("Excluir") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }) }
}

@Composable private fun FeedingEditor(original: Feeding, onDismiss: () -> Unit, onSave: (Feeding) -> Unit) {
    var type by remember { mutableStateOf(original.type) }; var side by remember { mutableStateOf(original.side) }
    var amount by remember { mutableStateOf(original.amountMl?.toString() ?: "") }; var note by remember { mutableStateOf(original.note) }
    val initial = Instant.ofEpochMilli(original.timeMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    var hour by remember { mutableStateOf(initial.hour.toString().padStart(2, '0')) }; var minute by remember { mutableStateOf(initial.minute.toString().padStart(2, '0')) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Editar mamada") }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Tipo", fontWeight = FontWeight.Bold); SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(FeedingType.QUICK to "Rápido", FeedingType.BREAST to "Peito", FeedingType.BOTTLE to "Mamadeira").forEachIndexed { i, pair ->
                    SegmentedButton(selected = type == pair.first, onClick = { type = pair.first }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(pair.second, fontSize = 12.sp) }
                }
            } }
            if (type == FeedingType.BREAST) item { Text("Lado", fontWeight = FontWeight.Bold); SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(BreastSide.LEFT to "Esq.", BreastSide.RIGHT to "Dir.", BreastSide.BOTH to "Ambos").forEachIndexed { i, pair ->
                    SegmentedButton(selected = side == pair.first, onClick = { side = pair.first }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(pair.second) }
                }
            } }
            if (type == FeedingType.BOTTLE) item { OutlinedTextField(amount, { amount = it.filter(Char::isDigit).take(4) }, label = { Text("Quantidade (ml)") }, modifier = Modifier.fillMaxWidth()) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(hour, { hour = it.filter(Char::isDigit).take(2) }, label = { Text("Hora") }, modifier = Modifier.weight(1f)); OutlinedTextField(minute, { minute = it.filter(Char::isDigit).take(2) }, label = { Text("Min") }, modifier = Modifier.weight(1f)) } }
            item { OutlinedTextField(note, { note = it }, label = { Text("Observação") }, minLines = 2, modifier = Modifier.fillMaxWidth()) }
        }
    }, confirmButton = {
        TextButton(onClick = {
            val h = hour.toIntOrNull()?.coerceIn(0,23) ?: initial.hour; val m = minute.toIntOrNull()?.coerceIn(0,59) ?: initial.minute
            val date = Instant.ofEpochMilli(original.timeMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            val millis = ZonedDateTime.of(date, LocalTime.of(h,m), ZoneId.systemDefault()).toInstant().toEpochMilli()
            onSave(original.copy(timeMillis = millis, type = type, side = if (type == FeedingType.BREAST) side else BreastSide.NONE, amountMl = if (type == FeedingType.BOTTLE) amount.toIntOrNull() else null, note = note.trim()))
        }) { Text("Salvar") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable private fun SettingsScreen(settings: AppSettings, vm: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val notificationGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val exactGranted = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var customDialog by remember { mutableStateOf(false) }
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
        item { HorizontalDivider(); Text("Permissões", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        item { PermissionCard("Notificações", notificationGranted, if (notificationGranted) null else ({
            if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else context.startActivity(NotificationHelper.openNotificationSettings(context))
        })) }
        item { PermissionCard("Alarmes e lembretes", exactGranted, if (exactGranted || Build.VERSION.SDK_INT < 31) null else ({ runCatching { context.startActivity(NotificationHelper.openExactAlarmSettings(context)) }; Unit })) }
        if (!notificationGranted || !exactGranted) item { Text("Sem essas permissões, o Android pode impedir ou atrasar o aviso. O registro das mamadas continua funcionando normalmente.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        item { HorizontalDivider(); Text("Sobre", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("Amamenta Bebê 1.0.0\nFunciona offline, sem conta, anúncios ou envio de dados.") }
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
private fun feedingDescription(f: Feeding) = when(f.type) { FeedingType.QUICK -> "Registro rápido"; FeedingType.BREAST -> sideName(f.side); FeedingType.BOTTLE -> "Mamadeira${f.amountMl?.let { " • $it ml" } ?: ""}" }
