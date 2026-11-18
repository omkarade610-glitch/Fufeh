package com.example.data

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val OWNER_EMAIL = "tusharade31@gmail.com"

class JarvisRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun <T> Flow<T>.wrapFirestoreFlow(operationType: OperationType, path: String, expectedUid: String): Flow<T> =
        this.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val exceptionToLog = firestoreError ?: (error as? Exception)
            if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
                handleFirestoreError(exceptionToLog, operationType, path)
            }
            throw error
        }

    // --- USER PROFILE ---

    suspend fun ensureUserProfile(): Result<UserProfile> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
        val uid = user.uid
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                Result.success(existing ?: UserProfile(id = uid, userId = uid))
            } else {
                val displayName = (user.displayName ?: "Jarvis Commander").take(100).ifBlank { "Jarvis Commander" }
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to displayName,
                    "preferredModel" to "gemini-3.8-flash",
                    "preferredLanguage" to "en-US",
                    "totalPrompts" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                user.email?.take(200)?.takeIf { it.isNotBlank() }?.let { payload["email"] = it }
                user.photoUrl?.toString()?.take(600)?.takeIf { it.isNotBlank() }?.let { payload["photoUrl"] = it }
                docRef.set(payload).await()
                Result.success(
                    UserProfile(
                        id = uid,
                        userId = uid,
                        displayName = displayName,
                        email = user.email.orEmpty(),
                        photoUrl = user.photoUrl?.toString().orEmpty()
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    else null
                }
                .wrapFirestoreFlow(OperationType.GET, path, userId)
        )
    }

    suspend fun updateUserPreferences(model: String? = null, language: String? = null, incrementPrompts: Boolean = false): Result<Unit> {
        var path = "users/unknown"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            if (model != null) updates["preferredModel"] = model
            if (language != null) updates["preferredLanguage"] = language
            if (incrementPrompts) updates["totalPrompts"] = FieldValue.increment(1L)
            db.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- CONVERSATIONS & MESSAGES ---

    fun observeConversations(userId: String): Flow<List<Conversation>> = flow {
        val path = "users/$userId/conversations"
        emitAll(
            db.collection("users").document(userId).collection("conversations")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(40)
                .snapshots()
                .map { it.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .wrapFirestoreFlow(OperationType.LIST, path, userId)
        )
    }

    fun observeMessages(userId: String, conversationId: String): Flow<List<ChatMessage>> = flow {
        val path = "users/$userId/conversations/$conversationId/messages"
        emitAll(
            db.collection("users").document(userId)
                .collection("conversations").document(conversationId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(120)
                .snapshots()
                .map { it.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .wrapFirestoreFlow(OperationType.LIST, path, userId)
        )
    }

    suspend fun createConversation(title: String, modelUsed: String, language: String): Result<String> {
        var path = "users/unknown/conversations"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("conversations").document()
            path = ref.path
            val safeTitle = title.trim().ifBlank { "New Jarvis Session" }.take(160)
            ref.set(
                mapOf(
                    "title" to safeTitle,
                    "modelUsed" to modelUsed.take(64),
                    "language" to language.take(16),
                    "lastMessagePreview" to "",
                    "messageCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun appendMessage(
        conversationId: String,
        role: String,
        text: String,
        sources: List<WebSource> = emptyList(),
        modelUsed: String = "gemini-3.8-flash",
        language: String = "en-US"
    ): Result<Unit> {
        var path = "users/unknown/conversations/$conversationId/messages"
        return try {
            val uid = requireUserId()
            require(conversationId.isNotBlank()) { "Conversation ID cannot be blank" }
            val convRef = db.collection("users").document(uid).collection("conversations").document(conversationId)
            val msgRef = convRef.collection("messages").document()
            path = msgRef.path
            val safeText = text.trim().ifBlank { "(empty)" }.take(23500)
            val encodedSources = sources.take(8).map { "${it.title.take(160)}|${it.url.take(380)}".take(580) }

            val batch = db.batch()
            batch.set(
                msgRef,
                mapOf(
                    "role" to if (role == "assistant") "assistant" else "user",
                    "text" to safeText,
                    "hasSources" to encodedSources.isNotEmpty(),
                    "sources" to encodedSources,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                convRef,
                mapOf(
                    "lastMessagePreview" to safeText.replace('\n', ' ').take(380),
                    "modelUsed" to modelUsed.take(64),
                    "language" to language.take(16),
                    "messageCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(conversationId: String): Result<Unit> {
        var path = "users/unknown/conversations/$conversationId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("conversations").document(conversationId)
            path = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- KNOWLEDGE & MEMORY VAULT ---

    fun observeKnowledge(userId: String): Flow<List<KnowledgeItem>> = flow {
        val path = "users/$userId/knowledge"
        emitAll(
            db.collection("users").document(userId).collection("knowledge")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { it.toObjects(KnowledgeItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .wrapFirestoreFlow(OperationType.LIST, path, userId)
        )
    }

    suspend fun saveKnowledgeItem(
        title: String,
        content: String,
        folder: String = "Memory",
        itemType: String = "memory",
        pinned: Boolean = true
    ): Result<String> {
        var path = "users/unknown/knowledge"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("knowledge").document()
            path = ref.path
            val safeType = if (itemType in listOf("memory", "document", "prompt")) itemType else "memory"
            ref.set(
                mapOf(
                    "title" to title.trim().ifBlank { "Saved Note" }.take(180),
                    "content" to content.trim().ifBlank { "-" }.take(23500),
                    "folder" to folder.trim().ifBlank { "Memory" }.take(64),
                    "itemType" to safeType,
                    "pinned" to pinned,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateKnowledgeItem(itemId: String, title: String, content: String, folder: String, pinned: Boolean): Result<Unit> {
        var path = "users/unknown/knowledge/$itemId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("knowledge").document(itemId)
            path = ref.path
            ref.update(
                mapOf(
                    "title" to title.trim().ifBlank { "Saved Note" }.take(180),
                    "content" to content.trim().ifBlank { "-" }.take(23500),
                    "folder" to folder.trim().ifBlank { "Memory" }.take(64),
                    "pinned" to pinned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteKnowledgeItem(itemId: String): Result<Unit> {
        var path = "users/unknown/knowledge/$itemId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("knowledge").document(itemId)
            path = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- ANIME ART GALLERY ---

    fun observeArtworks(userId: String): Flow<List<ArtworkItem>> = flow {
        val path = "users/$userId/artworks"
        emitAll(
            db.collection("users").document(userId).collection("artworks")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { it.toObjects(ArtworkItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .wrapFirestoreFlow(OperationType.LIST, path, userId)
        )
    }

    suspend fun saveArtwork(
        title: String,
        prompt: String,
        universe: String,
        style: String,
        pencilGrade: String,
        collectionName: String,
        localFilePath: String
    ): Result<String> {
        var path = "users/unknown/artworks"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("artworks").document()
            path = ref.path
            ref.set(
                mapOf(
                    "title" to title.trim().ifBlank { "Anime Sketch" }.take(160),
                    "prompt" to prompt.trim().ifBlank { "Anime sketch" }.take(2900),
                    "universe" to universe.trim().ifBlank { "Original Japanese Anime" }.take(80),
                    "style" to style.trim().ifBlank { "Graphite Pencil Sketch" }.take(80),
                    "pencilGrade" to pencilGrade.trim().take(32),
                    "collectionName" to collectionName.trim().ifBlank { "Anime Sketches" }.take(80),
                    "localFilePath" to localFilePath.take(500),
                    "favorite" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateArtwork(artworkId: String, title: String, collectionName: String, favorite: Boolean): Result<Unit> {
        var path = "users/unknown/artworks/$artworkId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("artworks").document(artworkId)
            path = ref.path
            ref.update(
                mapOf(
                    "title" to title.trim().ifBlank { "Anime Sketch" }.take(160),
                    "collectionName" to collectionName.trim().ifBlank { "Anime Sketches" }.take(80),
                    "favorite" to favorite,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteArtwork(artworkId: String): Result<Unit> {
        var path = "users/unknown/artworks/$artworkId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("artworks").document(artworkId)
            path = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- STUDIO PROJECTS (ANIME SERIES, MANGA, CODE, WEB APP, STORYBOARD, APK CONFIG) ---

    fun observeProjects(userId: String): Flow<List<StudioProject>> = flow {
        val path = "users/$userId/projects"
        emitAll(
            db.collection("users").document(userId).collection("projects")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { it.toObjects(StudioProject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .wrapFirestoreFlow(OperationType.LIST, path, userId)
        )
    }

    suspend fun saveProject(
        title: String,
        projectType: String,
        categoryTag: String,
        summary: String,
        contentText: String,
        status: String = "active"
    ): Result<String> {
        var path = "users/unknown/projects"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("projects").document()
            path = ref.path
            val safeType = if (projectType in listOf("anime_series", "manga", "code", "web_app", "apk_config", "storyboard")) projectType else "code"
            val safeStatus = if (status in listOf("draft", "active", "completed")) status else "active"
            ref.set(
                mapOf(
                    "title" to title.trim().ifBlank { "Untitled Project" }.take(180),
                    "projectType" to safeType,
                    "categoryTag" to categoryTag.trim().ifBlank { "General" }.take(80),
                    "summary" to summary.trim().take(1150),
                    "contentText" to contentText.trim().ifBlank { "-" }.take(58000),
                    "status" to safeStatus,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteProject(projectId: String): Result<Unit> {
        var path = "users/unknown/projects/$projectId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("projects").document(projectId)
            path = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- PERSONAL PRODUCTIVITY TASKS ---

    fun observeTasks(userId: String): Flow<List<ProductivityTask>> = flow {
        val path = "users/$userId/tasks"
        emitAll(
            db.collection("users").document(userId).collection("tasks")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { it.toObjects(ProductivityTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .wrapFirestoreFlow(OperationType.LIST, path, userId)
        )
    }

    suspend fun addTask(title: String, notes: String, priority: String, dueLabel: String): Result<Unit> {
        var path = "users/unknown/tasks"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("tasks").document()
            path = ref.path
            val safePriority = if (priority in listOf("low", "medium", "high")) priority else "medium"
            ref.set(
                mapOf(
                    "title" to title.trim().ifBlank { "Task" }.take(180),
                    "notes" to notes.trim().take(1950),
                    "priority" to safePriority,
                    "dueLabel" to dueLabel.trim().take(64),
                    "completed" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleTaskCompleted(taskId: String, completed: Boolean): Result<Unit> {
        var path = "users/unknown/tasks/$taskId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("tasks").document(taskId)
            path = ref.path
            ref.update(
                mapOf(
                    "completed" to completed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(taskId: String): Result<Unit> {
        var path = "users/unknown/tasks/$taskId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("tasks").document(taskId)
            path = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- SEARCH HISTORY ---

    fun observeSearchHistory(userId: String): Flow<List<SearchRecord>> = flow {
        val path = "users/$userId/search_history"
        emitAll(
            db.collection("users").document(userId).collection("search_history")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(30)
                .snapshots()
                .map { it.toObjects(SearchRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .wrapFirestoreFlow(OperationType.LIST, path, userId)
        )
    }

    suspend fun saveSearchRecord(query: String, summary: String, sourceCount: Int): Result<Unit> {
        var path = "users/unknown/search_history"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("search_history").document()
            path = ref.path
            ref.set(
                mapOf(
                    "query" to query.trim().ifBlank { "Search" }.take(300),
                    "summary" to summary.trim().take(1950),
                    "sourceCount" to sourceCount.coerceIn(0, 20).toLong(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- SYSTEM CONFIG & OWNER ADMIN DASHBOARD ---

    suspend fun checkIsAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        if (user.isEmailVerified && user.email.equals(OWNER_EMAIL, ignoreCase = true)) {
            return true
        }
        return try {
            db.collection("admins").document(user.uid).get().await().exists()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    fun observeSystemConfig(userId: String): Flow<SystemConfig> = flow {
        val path = "app_config/system"
        emitAll(
            db.collection("app_config").document("system")
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(SystemConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            ?: SystemConfig()
                    } else {
                        SystemConfig()
                    }
                }
                .wrapFirestoreFlow(OperationType.GET, path, userId)
        )
    }

    suspend fun updateSystemConfig(
        maintenanceMode: Boolean,
        animeStudioEnabled: Boolean,
        webSearchEnabled: Boolean,
        codingStudioEnabled: Boolean
    ): Result<Unit> {
        val path = "app_config/system"
        return try {
            val uid = requireUserId()
            db.collection("app_config").document("system").set(
                mapOf(
                    "maintenanceMode" to maintenanceMode,
                    "animeStudioEnabled" to animeStudioEnabled,
                    "webSearchEnabled" to webSearchEnabled,
                    "codingStudioEnabled" to codingStudioEnabled,
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAdminEvent(
                action = "config_updated",
                details = "maintenance=$maintenanceMode, anime=$animeStudioEnabled, search=$webSearchEnabled, coding=$codingStudioEnabled",
                severity = "info"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun logAdminEvent(action: String, details: String, severity: String = "info"): Result<Unit> {
        var path = "admin_logs"
        return try {
            val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
            val ref = db.collection("admin_logs").document()
            path = ref.path
            val safeSeverity = if (severity in listOf("info", "warning", "error")) severity else "info"
            ref.set(
                mapOf(
                    "actorUid" to user.uid,
                    "actorEmail" to (user.email ?: "unknown").take(200),
                    "action" to action.trim().ifBlank { "event" }.take(100),
                    "details" to details.trim().take(1950),
                    "severity" to safeSeverity,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun fetchAdminUsers(): Result<List<UserProfile>> {
        val path = "users"
        return try {
            val docs = db.collection("users")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()
            Result.success(docs.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun fetchAdminLogs(): Result<List<AdminLog>> {
        val path = "admin_logs"
        return try {
            val docs = db.collection("admin_logs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(40)
                .get()
                .await()
            Result.success(docs.toObjects(AdminLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCurrentUserAccountData(): Result<Unit> {
        var path = "users/unknown"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            db.collection("users").document(uid).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
