package br.com.amamentabebe.family

import androidx.room.withTransaction
import br.com.amamentabebe.data.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.UUID

/** Room is authoritative offline. Pending mutations and tombstones survive process death. */
class FamilySync(private val db: AppDatabase, private val service: FamilyService,
    private val identity: () -> Pair<FamilySelection, String>? = {
        service.selection()?.let { it to (service.auth.currentUser?.displayName ?: "Cuidador") }
    }
) {
    val status = MutableStateFlow("Compartilhamento não ativado")
    private val mutex = Mutex()
    suspend fun feeding(value: Feeding, deleted: Boolean = false, isNew: Boolean = false, originalAuthor: String? = null) = queue("FEEDING", value.id, encode(value), deleted, isNew, originalAuthor)
    suspend fun diaper(value: Diaper, deleted: Boolean = false, isNew: Boolean = false, originalAuthor: String? = null) = queue("DIAPER", value.id, encode(value), deleted, isNew, originalAuthor)
    private suspend fun queue(kind: String, localId: Long, payload: String, deleted: Boolean, isNew: Boolean, originalAuthor: String?) {
        val (s, authorName) = identity() ?: return
        db.withTransaction {
            val old = db.syncDao().find(kind, localId, s.familyId, s.babyId)
            // Old private history remains private until the explicit share-history action.
            if (old == null && !isNew) return@withTransaction
        val content = JSONObject(payload)
        require(content.getLong("timeMillis") in 1..9_000_000_000_000_000) { "Horário inválido para compartilhar." }
        if (kind == "FEEDING") {
            require(content.getString("note").length <= 5000) { "Observação muito longa para compartilhar. O registro privado foi preservado." }
            require(content.isNull("amountMl") || content.getInt("amountMl") in 0..9999) { "Quantidade inválida para compartilhar." }
            require(content.getLong("leftDurationMillis") in 0..9_000_000_000_000_000 && content.getLong("rightDurationMillis") in 0..9_000_000_000_000_000) { "Duração inválida para compartilhar." }
        }
            db.syncDao().save(old?.copy(payload = payload, deleted = deleted, pending = true) ?: SyncRecord(
                UUID.randomUUID().toString(), s.familyId, s.babyId, kind, localId, payload,
                s.uid, (originalAuthor ?: authorName).take(100), 0, deleted, true))
            status.value = "Registros pendentes; salvos neste celular"
        }
    }
    suspend fun shareExisting(confirmed: Boolean) {
        check(confirmed) { "Confirmação explícita necessária." }
        check(identity() != null)
        val knownAuthors = db.syncDao().observeAll().first().filter { !it.deleted }.associate { "${it.kind}:${it.localId}" to it.authorName }
        db.withTransaction {
            db.feedingDao().all().forEach { feeding(it, isNew = true, originalAuthor = knownAuthors["FEEDING:${it.id}"] ?: "Não informado (registro anterior)") }
            db.diaperDao().all().forEach { diaper(it, isNew = true, originalAuthor = knownAuthors["DIAPER:${it.id}"] ?: "Não informado (registro anterior)") }
        }
    }
    suspend fun sync() = mutex.withLock {
        val s = service.selection() ?: return@withLock
        status.value = "Sincronizando…"
        try {
            val family = service.cloud.collection("families").document(s.familyId)
            // Server authorization precedes any synchronization; revoked access cannot upload.
            check(family.collection("members").document(s.uid).get(Source.SERVER).await().exists()) { "Acesso à família removido." }
            val records = family.collection("babies").document(s.babyId).collection("records")
            var conflicts = 0
            for (item in db.syncDao().all(s.familyId, s.babyId).filter { it.pending }) {
                val ref = records.document(item.id)
                val uploaded = service.cloud.runTransaction { tx ->
                    val remote = tx.get(ref)
                    val revision = remote.getLong("revision") ?: 0
                    if (revision != item.revision) {
                        // An upload may have committed before the process stopped or lost its acknowledgement.
                        val same = remote.getString("kind") == item.kind && remote.getString("authorUid") == item.authorUid &&
                            remote.getBoolean("deleted") == item.deleted && remote.get("payload") == cloudPayload(item.payload)
                        same to revision
                    } else {
                        tx.set(ref, mapOf("kind" to item.kind, "payload" to cloudPayload(item.payload), "authorUid" to item.authorUid,
                            "authorName" to item.authorName, "modifiedUid" to s.uid, "revision" to (revision + 1),
                            "deleted" to item.deleted, "updatedAt" to FieldValue.serverTimestamp()))
                        true to (revision + 1)
                    }
                }.await()
                if (uploaded.first) db.withTransaction {
                    val current = db.syncDao().byId(item.id) ?: return@withTransaction
                    db.syncDao().save(current.copy(revision = uploaded.second,
                        pending = current.payload != item.payload || current.deleted != item.deleted))
                } else conflicts++
            }
            for (document in records.get(Source.SERVER).await().documents) {
                val old = db.syncDao().byId(document.id)
                check(old == null || (old.familyId == s.familyId && old.babyId == s.babyId)) { "Colisão de identificador entre famílias. Histórico local preservado." }
                // Keep both the remote record and pending local edit until user resolves it.
                if (old?.pending == true) continue
                val revision = document.getLong("revision") ?: continue
                if (old != null && old.revision >= revision) continue
                val kind = document.getString("kind") ?: continue
                val payload = (document.get("payload") as? Map<*, *>)?.let { JSONObject(it).toString() } ?: continue
                val deleted = document.getBoolean("deleted") ?: false
                if (old == null && deleted) continue
                db.withTransaction {
                    val current = db.syncDao().byId(document.id)
                    check(current == null || (current.familyId == s.familyId && current.babyId == s.babyId)) { "Colisão de identificador entre famílias. Histórico local preservado." }
                    if (current?.pending == true) return@withTransaction
                    val localId = applyRemote(kind, payload, current?.localId, deleted)
                    db.syncDao().save(SyncRecord(document.id, s.familyId, s.babyId, kind, localId, payload,
                        document.getString("authorUid") ?: "", document.getString("authorName") ?: "Cuidador", revision, deleted, false))
                }
            }
            val pending = db.syncDao().all(s.familyId, s.babyId).count { it.pending }
            status.value = if (conflicts > 0) "$conflicts conflito(s): edição local preservada; escolha qual versão manter" else if (pending > 0) "$pending registro(s) pendente(s)" else "Sincronizado"
        } catch (e: Exception) {
            status.value = "Sincronização pendente: ${e.message?.take(160) ?: "sem conexão"}. Dados locais preservados."
            throw e
        }
    }
    /** Explicit conflict resolution only: remote or local choice, never implicit data loss. */
    suspend fun resolveConflicts(keepLocal: Boolean) = mutex.withLock {
        val s = service.selection() ?: return@withLock
        val records = service.cloud.collection("families").document(s.familyId).collection("babies").document(s.babyId).collection("records")
        for (local in db.syncDao().all(s.familyId, s.babyId).filter { it.pending }) {
            val remote = records.document(local.id).get(Source.SERVER).await()
            if (!remote.exists()) continue
            val revision = remote.getLong("revision") ?: continue
            if (revision == local.revision) continue
            db.withTransaction {
                val current = db.syncDao().byId(local.id) ?: return@withTransaction
                if (current != local) return@withTransaction
                if (keepLocal) db.syncDao().save(local.copy(revision = revision)) else {
                    val payload = JSONObject(remote.get("payload") as Map<*, *>).toString(); val deleted = remote.getBoolean("deleted")!!
                    val id = applyRemote(local.kind, payload, local.localId, deleted)
                    db.syncDao().save(local.copy(localId = id, payload = payload, deleted = deleted, revision = revision, pending = false))
                }
            }
        }
    }
    private suspend fun applyRemote(kind: String, payload: String, id: Long?, deleted: Boolean): Long {
        val json = JSONObject(payload)
        val time = json.getLong("timeMillis"); require(time in 1..9_000_000_000_000_000)
        return when(kind) {
            "FEEDING" -> {
                val f = Feeding(id = id ?: 0, timeMillis = time, type = FeedingType.valueOf(json.getString("type")),
                    side = BreastSide.valueOf(json.getString("side")), amountMl = if (json.isNull("amountMl")) null else json.getInt("amountMl"),
                    note = json.getString("note"), leftDurationMillis = json.getLong("leftDurationMillis"), rightDurationMillis = json.getLong("rightDurationMillis"))
                require(f.note.length <= 5000 && f.leftDurationMillis in 0..9_000_000_000_000_000 && f.rightDurationMillis in 0..9_000_000_000_000_000 && (f.amountMl == null || f.amountMl in 0..9999))
                if (deleted) { if (id != null) db.feedingDao().delete(f); id ?: 0 }
                else if (id != null && db.feedingDao().byId(id) != null) { db.feedingDao().update(f); id }
                else db.feedingDao().insert(f.copy(id = 0))
            }
            "DIAPER" -> {
                val d = Diaper(id ?: 0, time, DiaperType.valueOf(json.getString("type")))
                if (deleted) { if (id != null) db.diaperDao().delete(d); id ?: 0 }
                else if (id != null && db.diaperDao().byId(id) != null) { db.diaperDao().update(d); id }
                else db.diaperDao().insert(d.copy(id = 0))
            }
            else -> error("Tipo compartilhado inválido")
        }
    }
    companion object {
        fun cloudPayload(json: String): Map<String, Any?> {
            val source = JSONObject(json)
            return source.keys().asSequence().associateWith { key ->
                if (source.isNull(key)) null else source.get(key).let { if (it is Number) it.toLong() else it }
            }
        }
        fun encode(f: Feeding): String = JSONObject().put("timeMillis", f.timeMillis).put("type", f.type.name).put("side", f.side.name)
            .put("amountMl", f.amountMl ?: JSONObject.NULL).put("note", f.note).put("leftDurationMillis", f.leftDurationMillis).put("rightDurationMillis", f.rightDurationMillis).toString()
        fun encode(d: Diaper): String = JSONObject().put("timeMillis", d.timeMillis).put("type", d.type.name).toString()
    }
}

