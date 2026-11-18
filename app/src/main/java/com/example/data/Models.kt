package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val preferredModel: String = "gemini-3.8-flash",
    val preferredLanguage: String = "en-US",
    val totalPrompts: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val title: String = "",
    val modelUsed: String = "gemini-3.8-flash",
    val language: String = "en-US",
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val role: String = "user",
    val text: String = "",
    val hasSources: Boolean = false,
    val sources: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class KnowledgeItem(
    @DocumentId val id: String = "",
    val title: String = "",
    val content: String = "",
    val folder: String = "Memory",
    val itemType: String = "memory",
    val pinned: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ArtworkItem(
    @DocumentId val id: String = "",
    val title: String = "",
    val prompt: String = "",
    val universe: String = "Original Japanese Anime",
    val style: String = "Graphite Pencil Sketch",
    val pencilGrade: String = "4B",
    val collectionName: String = "Anime Sketches",
    val localFilePath: String = "",
    val favorite: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StudioProject(
    @DocumentId val id: String = "",
    val title: String = "",
    val projectType: String = "code",
    val categoryTag: String = "Kotlin",
    val summary: String = "",
    val contentText: String = "",
    val status: String = "active",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ProductivityTask(
    @DocumentId val id: String = "",
    val title: String = "",
    val notes: String = "",
    val priority: String = "medium",
    val dueLabel: String = "",
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SearchRecord(
    @DocumentId val id: String = "",
    val query: String = "",
    val summary: String = "",
    val sourceCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SystemConfig(
    @DocumentId val id: String = "",
    val maintenanceMode: Boolean = false,
    val animeStudioEnabled: Boolean = true,
    val webSearchEnabled: Boolean = true,
    val codingStudioEnabled: Boolean = true,
    val updatedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdminLog(
    @DocumentId val id: String = "",
    val actorUid: String = "",
    val actorEmail: String = "",
    val action: String = "",
    val details: String = "",
    val severity: String = "info",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class WebSource(
    val title: String,
    val url: String,
    val snippet: String
)

data class DrawingTutorStep(
    val stepNumber: Int,
    val title: String,
    val pencilGrade: String,
    val instruction: String,
    val proTip: String
)

data class MangaPanel(
    val panelNumber: Int,
    val shotType: String,
    val visualDescription: String,
    val dialogue: String,
    val soundEffect: String
)

data class StoryboardScene(
    val sceneNumber: Int,
    val durationSec: Int,
    val cameraMovement: String,
    val visualPrompt: String,
    val voiceOver: String,
    val subtitle: String
)

enum class JarvisLanguage(val code: String, val displayName: String, val nativeLabel: String) {
    ENGLISH("en-US", "English", "English"),
    HINDI("hi-IN", "Hindi", "हिन्दी"),
    MARATHI("mr-IN", "Marathi", "मराठी"),
    JAPANESE("ja-JP", "Japanese", "日本語"),
    KOREAN("ko-KR", "Korean", "한국어");

    companion object {
        fun fromCode(code: String): JarvisLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

enum class GeminiModelOption(
    val modelId: String,
    val label: String,
    val tierTag: String,
    val description: String
) {
    FLASH_3_8("gemini-3.8-flash", "Gemini 3.8 Flash", "Default", "Balanced frontier intelligence & speed"),
    FLASH_LITE_3_5("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Ultra Fast", "Low-latency everyday assistant"),
    ARGON_4("gemini-4-argon", "Gemini 4 Argon", "Blaze Plan", "Deep coding, math & complex reasoning");

    companion object {
        fun fromId(id: String): GeminiModelOption =
            entries.firstOrNull { it.modelId == id } ?: FLASH_3_8
    }
}
