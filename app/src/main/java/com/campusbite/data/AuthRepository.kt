package com.campusbite.data

import android.content.Context
import android.util.Patterns
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.room.withTransaction
import com.campusbite.data.local.AppDatabase
import com.campusbite.data.local.entity.UserEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Session + identity boundary.
 *
 * Roles are a *demo-only* concept: a real Firebase account is always a STUDENT.
 * Vendors and managers are only reachable through the explicit demo entry points,
 * so a self-registered account can never grant itself staff privileges.
 */
class AuthRepository(context: Context, private val db: AppDatabase) {
    val cloudConfigured = FirebaseApp.getApps(context).isNotEmpty()
    private val firebase get() = FirebaseAuth.getInstance()
    private val prefs = EncryptedSharedPreferences.create(context, "session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)

    private val activeDemoUid get() = prefs.getString("demo_uid", DEMO_UID) ?: DEMO_UID
    private val _uid = MutableStateFlow(if (prefs.getBoolean("demo", false)) activeDemoUid else if (cloudConfigured) firebase.currentUser?.uid else null)
    val uid = _uid.asStateFlow()
    val isDemo get() = _uid.value in listOf(DEMO_UID, VENDOR_DEMO_UID, MANAGER_DEMO_UID)

    val hasPersistedSession get() = cloudConfigured && !prefs.getBoolean("demo", false) && firebase.currentUser != null

    /** Only the three explicit demo personas may be entered here. */
    suspend fun demo(role: String = "STUDENT") {
        require(role in listOf("STUDENT", "VENDOR", "MANAGER")) { "Unknown demo role." }
        val targetUid = when (role) {
            "VENDOR" -> VENDOR_DEMO_UID
            "MANAGER" -> MANAGER_DEMO_UID
            else -> DEMO_UID
        }
        val targetName = when (role) {
            "VENDOR" -> "Campus Stall Vendor"
            "MANAGER" -> "Campus Operations Manager"
            else -> "Guest Student"
        }
        val targetEmail = when (role) {
            "VENDOR" -> "vendor@campusbite.local"
            "MANAGER" -> "manager@campusbite.local"
            else -> "guest@campusbite.local"
        }
        if (db.userDao().get(targetUid) == null) {
            db.userDao().upsert(UserEntity(targetUid, targetName, targetEmail, null, null, true, role))
        }
        prefs.edit().putBoolean("demo", true).putString("demo_uid", targetUid).apply()
        _uid.value = targetUid
    }

    /**
     * Real Firebase sign-in / registration. The account is always stored as a
     * STUDENT: privilege is never chosen by the client at registration time.
     */
    suspend fun authenticate(email: String, password: String, name: String?, confirmation: String) {
        require(cloudConfigured) { "Connect Firebase to use email accounts. You can continue as guest now." }
        require(Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { "Enter a valid email address." }
        require(password.length >= if (name == null) 6 else 8) { "Use at least ${if (name == null) 6 else 8} characters for your password." }
        if (name != null) {
            require(name.trim().length in 2..100) { "Enter a name between 2 and 100 characters." }
            require(password == confirmation) { "Passwords do not match." }
        }
        val result = withContext(Dispatchers.IO) {
            if (name == null) firebase.signInWithEmailAndPassword(email.trim(), password).await()
            else firebase.createUserWithEmailAndPassword(email.trim(), password).await()
        }
        val user = requireNotNull(result.user)
        if (name != null) withContext(Dispatchers.IO) {
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
        }
        val cached = db.userDao().get(user.uid)
        db.userDao().upsert(cached?.copy(email = user.email.orEmpty(), role = STUDENT) ?: UserEntity(user.uid,
            user.displayName ?: name ?: email.substringBefore('@'), user.email.orEmpty(), user.photoUrl?.toString(), null, true, STUDENT))
        prefs.edit().putBoolean("demo", false).remove("demo_uid").apply()
        _uid.value = user.uid
    }

    suspend fun resetPassword(email: String) {
        require(cloudConfigured) { "Password reset requires a connected Firebase project." }
        require(Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { "Enter your email first." }
        withContext(Dispatchers.IO) { firebase.sendPasswordResetEmail(email.trim()).await() }
    }

    suspend fun saveProfile(name: String, favouriteStall: String?, notifications: Boolean) {
        require(name.trim().length in 2..100) { "Use between 2 and 100 characters for your name." }
        val id = requireNotNull(uid.value)
        val user = requireNotNull(db.userDao().get(id))
        db.userDao().upsert(user.copy(displayName = name.trim(), favouriteStallId = favouriteStall, notificationsOn = notifications))
    }

    /** Demo personas only — a real account can never promote itself. */
    suspend fun saveRole(role: String) {
        require(isDemo) { "Role switching is available in demo mode only." }
        require(role in listOf("STUDENT", "VENDOR", "MANAGER"))
        val id = requireNotNull(uid.value)
        val user = requireNotNull(db.userDao().get(id))
        db.userDao().upsert(user.copy(role = role))
    }

    suspend fun logout() {
        uid.value?.let { db.cartDao().clear(it) }
        if (cloudConfigured) firebase.signOut()
        prefs.edit().clear().apply()
        _uid.value = null
    }

    suspend fun deleteAccount() {
        val id = requireNotNull(uid.value)
        if (!isDemo) {
            withContext(Dispatchers.IO) { requireNotNull(firebase.currentUser).delete().await() }
        }
        db.withTransaction {
            db.cartDao().clear(id)
            db.orderDao().deleteForUser(id)
            db.reviewDao().deleteForUser(id)
            db.userDao().delete(id)
        }
        prefs.edit().clear().apply()
        _uid.value = null
    }

    companion object {
        const val STUDENT = "STUDENT"
        const val VENDOR = "VENDOR"
        const val MANAGER = "MANAGER"

        const val DEMO_UID = "local-demo"
        const val VENDOR_DEMO_UID = "vendor-demo"
        const val MANAGER_DEMO_UID = "manager-demo"
    }
}
