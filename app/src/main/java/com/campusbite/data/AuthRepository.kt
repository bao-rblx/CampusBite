package com.campusbite.data

import android.content.Context
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

class AuthRepository(context: Context, private val db: AppDatabase) {
    val cloudConfigured = FirebaseApp.getApps(context).isNotEmpty()
    private val firebase get() = FirebaseAuth.getInstance()
    private val prefs = EncryptedSharedPreferences.create(context, "session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    private val _uid = MutableStateFlow(if (prefs.getBoolean("demo", false)) DEMO_UID else if (cloudConfigured) firebase.currentUser?.uid else null)
    val uid = _uid.asStateFlow()
    val isDemo get() = _uid.value == DEMO_UID

    /** True when a Firebase session was restored automatically on app launch. */
    val hasPersistedSession get() = cloudConfigured && !prefs.getBoolean("demo", false) && firebase.currentUser != null

    suspend fun demo() {
        if (db.userDao().get(DEMO_UID) == null) db.userDao().upsert(UserEntity(DEMO_UID, "Campus Explorer", "demo@campusbite.local", null, null, true))
        prefs.edit().putBoolean("demo", true).apply()
        _uid.value = DEMO_UID
    }

    suspend fun authenticate(email: String, password: String, name: String?, confirmation: String) {
        require(cloudConfigured) { "Connect Firebase to use email accounts. You can explore the demo now." }
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { "Enter a valid email address." }
        require(password.length >= if (name == null) 6 else 8) { "Use at least ${if (name == null) 6 else 8} characters for your password." }
        if (name != null) {
            require(name.trim().length in 2..100) { "Enter a name between 2 and 100 characters." }
            require(password == confirmation) { "Passwords do not match." }
        }
        // Firebase Authentication calls run off the main thread on Dispatchers.IO.
        val result = withContext(Dispatchers.IO) {
            if (name == null) firebase.signInWithEmailAndPassword(email.trim(), password).await()
            else firebase.createUserWithEmailAndPassword(email.trim(), password).await()
        }
        val user = requireNotNull(result.user)
        if (name != null) withContext(Dispatchers.IO) {
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
        }
        val cached = db.userDao().get(user.uid)
        db.userDao().upsert(cached?.copy(email = user.email.orEmpty()) ?: UserEntity(user.uid,
            user.displayName ?: name ?: email.substringBefore('@'), user.email.orEmpty(), user.photoUrl?.toString(), null, true))
        // Firebase manages refresh tokens securely; only the display-mode flag is stored here.
        prefs.edit().putBoolean("demo", false).apply()
        _uid.value = user.uid
    }

    suspend fun resetPassword(email: String) {
        require(cloudConfigured) { "Password reset requires a connected Firebase project." }
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { "Enter your email first." }
        withContext(Dispatchers.IO) { firebase.sendPasswordResetEmail(email.trim()).await() }
    }

    suspend fun saveProfile(name: String, favouriteStall: String?, notifications: Boolean) {
        require(name.trim().length in 2..100) { "Use between 2 and 100 characters for your name." }
        val id = requireNotNull(uid.value)
        val user = requireNotNull(db.userDao().get(id))
        db.userDao().upsert(user.copy(displayName = name.trim(), favouriteStallId = favouriteStall, notificationsOn = notifications))
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
            // May require recent login. If it fails, retain local data and the session.
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

    companion object { const val DEMO_UID = "local-demo" }
}
