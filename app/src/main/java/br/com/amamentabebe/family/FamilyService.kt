package br.com.amamentabebe.family

import android.content.Context
import br.com.amamentabebe.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Date
import java.util.UUID

data class FamilySelection(val uid: String, val familyId: String, val babyId: String)

/** Optional service: no cloud initialization or data upload without real configuration and sign-in. */
class FamilyService(context: Context) {
    val configured = listOf(BuildConfig.FIREBASE_API_KEY, BuildConfig.FIREBASE_APP_ID, BuildConfig.FIREBASE_PROJECT_ID).all { it.isNotBlank() }
    private val firebase by lazy {
        check(configured) { "Firebase ainda não configurado pelo desenvolvedor." }
        FirebaseApp.getApps(context).firstOrNull { it.name == "family" } ?: FirebaseApp.initializeApp(context,
            FirebaseOptions.Builder().setApiKey(BuildConfig.FIREBASE_API_KEY).setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID).build(), "family")
    }
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance(firebase)
    val cloud: FirebaseFirestore get() = FirebaseFirestore.getInstance(firebase)
    private val prefs = context.getSharedPreferences("family_selection", Context.MODE_PRIVATE)
    fun selection(): FamilySelection? {
        if (!configured) return null
        val uid = auth.currentUser?.uid ?: return null
        if (prefs.getString("uid", null) != uid) return null
        val fid = prefs.getString("family", null) ?: return null
        val bid = prefs.getString("baby", null) ?: return null
        return FamilySelection(uid, fid, bid)
    }
    private fun select(fid: String, bid: String) {
        prefs.edit().putString("uid", auth.currentUser!!.uid).putString("family", fid).putString("baby", bid).commit()
    }
    suspend fun signIn(email: String, password: String) { auth.signInWithEmailAndPassword(email.trim(), password).await() }
    suspend fun register(name: String, email: String, password: String) {
        require(name.trim().length in 1..100)
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user!!
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
        user.sendEmailVerification().await()
    }
    suspend fun verifyAccess() {
        val user = auth.currentUser ?: error("Entre com seu acesso pessoal.")
        user.reload().await(); user.getIdToken(true).await()
        check(user.isEmailVerified) { "Confirme seu e-mail antes de compartilhar os registros." }
    }
    fun signOut() { auth.signOut() }
    suspend fun createFamily(name: String, baby: String) {
        verifyAccess(); require(name.trim().length in 1..100 && baby.trim().length in 1..100)
        val user = auth.currentUser!!; val fid = UUID.randomUUID().toString(); val bid = UUID.randomUUID().toString()
        val family = cloud.collection("families").document(fid)
        cloud.batch().set(family, mapOf("ownerUid" to user.uid, "name" to name.trim(), "createdAt" to FieldValue.serverTimestamp()))
            .set(family.collection("members").document(user.uid), mapOf("role" to "ADMIN", "name" to (user.displayName ?: "Administrador"), "invitationId" to ""))
            .set(family.collection("babies").document(bid), mapOf("name" to baby.trim(), "createdAt" to FieldValue.serverTimestamp())).commit().await()
        select(fid, bid)
    }
    suspend fun invite(email: String): String {
        verifyAccess(); val s = selection() ?: error("Crie ou aceite uma família primeiro.")
        val token = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        cloud.collection("invitations").document(hashToken(token)).set(mapOf("familyId" to s.familyId,
            "createdBy" to s.uid, "targetEmail" to email.trim().lowercase(java.util.Locale.ROOT),
            "expiresAt" to Timestamp(Date(System.currentTimeMillis() + (23 * 60 + 50) * 60 * 1000)), "consumedBy" to "")).await()
        return token
    }
    suspend fun accept(token: String) {
        verifyAccess(); require(token.trim().matches(Regex("[a-f0-9]{64}"))) { "Código de convite inválido." }
        val uid = auth.currentUser!!.uid; val hash = hashToken(token.trim())
        val invite = cloud.collection("invitations").document(hash)
        val fid = cloud.runTransaction { tx ->
            val d = tx.get(invite); val familyId = d.getString("familyId") ?: error("Convite não encontrado.")
            check(d.getString("consumedBy").isNullOrEmpty()) { "Convite já utilizado." }
            tx.update(invite, "consumedBy", uid)
            tx.set(cloud.collection("families").document(familyId).collection("members").document(uid),
                mapOf("role" to "CAREGIVER", "name" to (auth.currentUser!!.displayName ?: "Cuidador"), "invitationId" to hash))
            familyId
        }.await()
        val babies = cloud.collection("families").document(fid).collection("babies").get().await()
        select(fid, babies.documents.firstOrNull()?.id ?: error("Família sem bebê cadastrado."))
    }
    suspend fun participants(): List<Pair<String, String>> {
        val s = selection() ?: return emptyList()
        return cloud.collection("families").document(s.familyId).collection("members").get().await().documents
            .map { it.id to "${it.getString("name")} • ${it.getString("role")}" }
    }
    suspend fun openFamily(id: String) {
        verifyAccess(); require(id.trim().matches(Regex("[a-zA-Z0-9-]{1,100}")))
        val family = cloud.collection("families").document(id.trim())
        check(family.collection("members").document(auth.currentUser!!.uid).get(com.google.firebase.firestore.Source.SERVER).await().exists()) { "Você não participa desta família." }
        val babies = family.collection("babies").get(com.google.firebase.firestore.Source.SERVER).await()
        select(id.trim(), babies.documents.firstOrNull()?.id ?: error("Família sem bebê."))
    }
    suspend fun invitations(): List<Pair<String, String>> {
        val s = selection() ?: return emptyList()
        return cloud.collection("invitations").whereEqualTo("familyId", s.familyId).get().await().documents
            .map { it.id to (it.getString("targetEmail") ?: "") }
    }
    suspend fun removeParticipant(uid: String) {
        val s = selection() ?: error("Sem família.")
        cloud.collection("families").document(s.familyId).collection("members").document(uid).delete().await()
    }
    suspend fun revoke(invitationId: String) { cloud.collection("invitations").document(invitationId).delete().await() }
    companion object {
        fun hashToken(token: String) = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
