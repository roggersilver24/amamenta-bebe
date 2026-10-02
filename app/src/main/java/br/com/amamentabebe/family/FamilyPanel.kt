package br.com.amamentabebe.family

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import br.com.amamentabebe.AmamentaApplication
import kotlinx.coroutines.launch

@Composable fun FamilyPanel(app: AmamentaApplication) {
    val service = app.family
    val scope = rememberCoroutineScope()
    val syncStatus by app.familySync.status.collectAsState()
    var name by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }; var familyName by remember { mutableStateOf("") }
    var babyName by remember { mutableStateOf("") }; var inviteEmail by remember { mutableStateOf("") }
    var existingFamily by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }; var issuedToken by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }; var confirmShare by remember { mutableStateOf(false) }
    var conflictDialog by remember { mutableStateOf(false) }
    var participants by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var invitations by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    fun action(block: suspend () -> Unit) { if (!busy) { busy = true; scope.launch {
        try { block(); message = "Operação concluída."; refresh++ } catch (e: Exception) { message = e.message?.take(220) ?: "Operação indisponível. Dados locais preservados." } finally { busy = false }
    } } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Família", style = MaterialTheme.typography.titleLarge)
        if (!service.configured) {
            Text("Compartilhamento aguarda configuração Firebase do desenvolvedor. Seus registros continuam salvos neste celular.")
        } else {
            // refresh invalidates Compose after authentication operations (Firebase user is not Compose state).
            @Suppress("UNUSED_VARIABLE") val revision = refresh
            if (service.auth.currentUser == null) {
                OutlinedTextField(name, { name = it.take(100) }, label = { Text("Seu nome (para criar acesso)") })
                OutlinedTextField(email, { email = it }, label = { Text("Seu e-mail") })
                OutlinedTextField(password, { password = it }, label = { Text("Senha") }, visualTransformation = PasswordVisualTransformation())
                Row { TextButton(enabled = !busy, onClick = { action { service.signIn(email, password); password = ""; app.requestSync() } }) { Text("Entrar") }
                    TextButton(enabled = !busy, onClick = { action { service.register(name, email, password); password = ""; message = "Verifique o e-mail enviado." } }) { Text("Criar acesso") } }
            } else {
                Text("Acesso: ${service.auth.currentUser?.email}")
                Text("Cada pessoa usa sua própria conta. Confirme seu e-mail antes de compartilhar.")
                if (service.selection() == null) {
                    OutlinedTextField(familyName, { familyName = it.take(100) }, label = { Text("Nome da família") })
                    OutlinedTextField(babyName, { babyName = it.take(100) }, label = { Text("Nome do bebê") })
                    TextButton(enabled = !busy, onClick = { action { service.createFamily(familyName, babyName); app.requestSync() } }) { Text("Criar família e cadastrar bebê") }
                    OutlinedTextField(token, { token = it.trim() }, label = { Text("Código do convite") })
                    TextButton(enabled = !busy, onClick = { action { service.accept(token); token = ""; app.requestSync() } }) { Text("Aceitar convite") }
                    OutlinedTextField(existingFamily, { existingFamily = it.trim() }, label = { Text("ID da família da qual já participo") })
                    TextButton(enabled = !busy, onClick = { action { service.openFamily(existingFamily); app.requestSync() } }) { Text("Recuperar acesso à família") }
                } else {
                    androidx.compose.foundation.text.selection.SelectionContainer { Text("ID da família: ${service.selection()?.familyId}") }
                    Text(syncStatus)
                    TextButton(enabled = !busy, onClick = { app.requestSync() }) { Text("Sincronizar agora") }
                    TextButton(enabled = !busy, onClick = { confirmShare = true }) { Text("Compartilhar registros antigos…") }
                    if (syncStatus.contains("conflito")) TextButton(onClick = { conflictDialog = true }) { Text("Resolver conflitos…") }
                    OutlinedTextField(inviteEmail, { inviteEmail = it }, label = { Text("E-mail do familiar") })
                    TextButton(enabled = !busy, onClick = { action { issuedToken = service.invite(inviteEmail) } }) { Text("Criar convite de 24 horas (administrador)") }
                    if (issuedToken.isNotEmpty()) {
                        Text("Código pessoal para o e-mail informado:")
                        androidx.compose.foundation.text.selection.SelectionContainer { Text(issuedToken) }
                    }
                    TextButton(enabled = !busy, onClick = { action { participants = service.participants(); invitations = runCatching { service.invitations() }.getOrDefault(emptyList()) } }) { Text("Atualizar participantes e convites") }
                    participants.forEach { (uid, label) -> Row {
                        Text(label, Modifier.weight(1f))
                        if (uid != service.auth.currentUser?.uid) TextButton(enabled = !busy, onClick = { action { service.removeParticipant(uid); participants = service.participants() } }) { Text("Remover") }
                    } }
                    invitations.forEach { (id, label) -> Row { Text(label, Modifier.weight(1f)); TextButton(enabled = !busy, onClick = { action { service.revoke(id); invitations = service.invitations() } }) { Text("Revogar") } } }
                }
                TextButton(enabled = !busy, onClick = { service.signOut(); issuedToken = ""; participants = emptyList(); invitations = emptyList(); refresh++ }) { Text("Sair") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (message.isNotEmpty()) Text(message)
        }
    }
    if (confirmShare) AlertDialog(onDismissRequest = { confirmShare = false }, title = { Text("Compartilhar histórico antigo?") },
        text = { Text("Todos os registros locais de alimentação e fraldas serão associados a esta família e enviados quando houver internet. Os familiares autorizados poderão vê-los. Deseja confirmar?") },
        confirmButton = { TextButton(onClick = { confirmShare = false; action { app.familySync.shareExisting(true); app.requestSync() } }) { Text("Confirmar compartilhamento") } },
        dismissButton = { TextButton(onClick = { confirmShare = false }) { Text("Cancelar") } })
    if (conflictDialog) AlertDialog(onDismissRequest = { conflictDialog = false }, title = { Text("Resolver edições simultâneas") },
        text = { Text("Há edições locais pendentes e uma versão mais recente da família. A escolha se aplica a todos os conflitos pendentes. Exporte um backup antes de substituir versões.") },
        confirmButton = { TextButton(onClick = { conflictDialog = false; action { app.familySync.resolveConflicts(true); app.requestSync() } }) { Text("Manter local") } },
        dismissButton = { TextButton(onClick = { conflictDialog = false; action { app.familySync.resolveConflicts(false); app.requestSync() } }) { Text("Usar família") } })
}
