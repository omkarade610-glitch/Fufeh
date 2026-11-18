package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.DrawingTutorStep
import com.example.data.JarvisLanguage
import com.example.data.MangaPanel
import com.example.data.StoryboardScene
import com.example.data.WebSource
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiService"

class GeminiService {

    private val defaultModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    private val fastModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.5-flash-lite")
    }

    private val deepModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-4-argon")
    }

    private fun textModel(modelId: String): GenerativeModel = when (modelId) {
        "gemini-3.5-flash-lite" -> fastModel
        "gemini-4-argon" -> deepModel
        else -> defaultModel
    }

    private fun imageModel(aspectRatio: AspectRatio? = null): GenerativeModel {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
            if (aspectRatio != null) {
                imageConfig = ImageConfig.builder()
                    .setAspectRatio(aspectRatio)
                    .setImageSize(ImageSize.SIZE_1K)
                    .build()
            }
        }
        return Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = config
        )
    }

    fun streamChatReply(
        modelId: String,
        language: JarvisLanguage,
        memoryContext: String,
        history: List<Pair<String, String>>,
        userPrompt: String,
        attachedImage: Bitmap? = null,
        webSources: List<WebSource> = emptyList()
    ): Flow<String> {
        val systemText = buildString {
            appendLine("You are JARVIS AI, an intelligent, refined, helpful personal AI command assistant.")
            appendLine("Respond clearly in ${language.displayName} (${language.nativeLabel}) unless the user explicitly asks for another language or translation.")
            appendLine("Never invent URLs, fake search results, or fake execution logs.")
            if (memoryContext.isNotBlank()) {
                appendLine("\nUser-approved AI Memory:")
                appendLine(memoryContext.take(2500))
            }
            if (webSources.isNotEmpty()) {
                appendLine("\nLive Web Search Results retrieved right now (cite these accurately, do NOT invent other links):")
                webSources.forEachIndexed { i, src ->
                    appendLine("[${i + 1}] ${src.title} (${src.url}): ${src.snippet}")
                }
            }
        }

        val chatModel = Firebase.ai.generativeModel(
            modelName = modelId.ifBlank { "gemini-3.8-flash" },
            systemInstruction = content { text(systemText) }
        )

        val historyContent: List<Content> = history.takeLast(14).map { (role, text) ->
            content(if (role == "assistant") "model" else "user") { text(text) }
        }

        val chat = chatModel.startChat(history = historyContent)
        val inputContent = content("user") {
            if (attachedImage != null) {
                image(attachedImage)
            }
            text(userPrompt)
        }

        return chat.sendMessageStream(inputContent).mapNotNull { chunk -> chunk.text }
    }

    suspend fun generateText(
        prompt: String,
        modelId: String = "gemini-3.8-flash",
        attachedImages: List<Bitmap> = emptyList()
    ): String = withTimeout(60_000L) {
        val model = textModel(modelId)
        val response = if (attachedImages.isEmpty()) {
            model.generateContent(prompt)
        } else {
            model.generateContent(
                content {
                    attachedImages.take(4).forEach { image(it) }
                    text(prompt)
                }
            )
        }
        response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Gemini returned an empty response.")
    }

    suspend fun generateAnimeOrArtImage(
        prompt: String,
        aspectRatioLabel: String = "1:1",
        referenceImage: Bitmap? = null
    ): Bitmap = withTimeout(60_000L) {
        val ratio = when (aspectRatioLabel) {
            "16:9" -> AspectRatio.LANDSCAPE_16x9
            "9:16" -> AspectRatio.PORTRAIT_9x16
            "4:3" -> AspectRatio.LANDSCAPE_4x3
            "3:4" -> AspectRatio.PORTRAIT_3x4
            else -> null
        }
        val model = imageModel(ratio)
        val response = if (referenceImage != null) {
            model.generateContent(
                content {
                    image(referenceImage)
                    text(prompt)
                }
            )
        } else {
            model.generateContent(prompt)
        }

        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()

        imagePart?.image
            ?: throw IllegalStateException("Image model did not return an image. Check Firebase Blaze plan & App Check.")
    }

    suspend fun generateAnimeDrawingTutor(
        subject: String,
        universe: String,
        pencilGrade: String
    ): List<DrawingTutorStep> = withTimeout(60_000L) {
        val tutorSchema = Schema.obj(
            properties = mapOf(
                "steps" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "stepNumber" to Schema.integer("Step number 1 to 8"),
                            "title" to Schema.string("Step title covering Face outline, Eyes, Nose and mouth, Hairstyle, Body proportions, Clothing, Shading, Final details"),
                            "pencilGrade" to Schema.string("Recommended graphite grade e.g. HB, 2B, 4B, 6B, 8B"),
                            "instruction" to Schema.string("Clear beginner-friendly step-by-step drawing instruction"),
                            "proTip" to Schema.string("Mangaka shading or linework tip")
                        )
                    )
                )
            )
        )

        val model = Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = tutorSchema
            }
        )

        val prompt = """
            Create an 8-step beginner-friendly Japanese anime pencil drawing tutorial for: "$subject"
            Style inspiration: $universe (original fan-art study). Primary pencil: $pencilGrade.
            Cover exactly these 8 progressive stages in order:
            1. Face outline, 2. Eyes, 3. Nose and mouth, 4. Hairstyle,
            5. Body proportions, 6. Clothing, 7. Shading & cross-hatching, 8. Final details & highlights.
        """.trimIndent()

        val rawJson = model.generateContent(prompt).text
            ?: throw IllegalStateException("Empty tutor response.")
        val root = JSONObject(rawJson)
        val arr = root.getJSONArray("steps")
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            DrawingTutorStep(
                stepNumber = obj.optInt("stepNumber", i + 1),
                title = obj.optString("title", "Step ${i + 1}"),
                pencilGrade = obj.optString("pencilGrade", pencilGrade),
                instruction = obj.optString("instruction", ""),
                proTip = obj.optString("proTip", "")
            )
        }
    }

    suspend fun generateMangaScript(
        title: String,
        genre: String,
        premise: String,
        panelCount: Int = 6
    ): List<MangaPanel> = withTimeout(60_000L) {
        val mangaSchema = Schema.obj(
            properties = mapOf(
                "panels" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "panelNumber" to Schema.integer("Sequential panel number"),
                            "shotType" to Schema.string("Camera shot e.g. Dramatic Close-Up, Wide Establishing, Speedlines Action"),
                            "visualDescription" to Schema.string("Black-and-white manga ink composition, expressions, screentones"),
                            "dialogue" to Schema.string("Speech bubble dialogue with speaker name"),
                            "soundEffect" to Schema.string("Japanese manga SFX with English translation e.g. ドン! (DON!)")
                        )
                    )
                )
            )
        )

        val model = Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = mangaSchema
            }
        )

        val prompt = """
            Write an original Japanese black-and-white manga page script with $panelCount comic panels.
            Title: $title | Genre: $genre | Premise: $premise
            Include intense expressions, dynamic panel framing, speech bubbles, and authentic manga SFX.
        """.trimIndent()

        val rawJson = model.generateContent(prompt).text
            ?: throw IllegalStateException("Empty manga script response.")
        val arr = JSONObject(rawJson).getJSONArray("panels")
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            MangaPanel(
                panelNumber = obj.optInt("panelNumber", i + 1),
                shotType = obj.optString("shotType", "Medium Shot"),
                visualDescription = obj.optString("visualDescription", ""),
                dialogue = obj.optString("dialogue", ""),
                soundEffect = obj.optString("soundEffect", "")
            )
        }
    }

    suspend fun generateVideoStoryboard(
        concept: String,
        style: String,
        sceneCount: Int = 5
    ): List<StoryboardScene> = withTimeout(60_000L) {
        val storyboardSchema = Schema.obj(
            properties = mapOf(
                "scenes" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "sceneNumber" to Schema.integer("Scene number"),
                            "durationSec" to Schema.integer("Duration in seconds (3 to 10)"),
                            "cameraMovement" to Schema.string("Camera pan/zoom/tracking instruction"),
                            "visualPrompt" to Schema.string("Detailed keyframe visual prompt"),
                            "voiceOver" to Schema.string("Voice-over narration line"),
                            "subtitle" to Schema.string("Concise subtitle caption")
                        )
                    )
                )
            )
        )

        val model = Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = storyboardSchema
            }
        )

        val prompt = """
            Create a $sceneCount-scene anime/video production storyboard with timeline durations,
            camera direction, visual prompts, voice-over lines, and subtitles for: "$concept"
            Visual style: $style.
        """.trimIndent()

        val rawJson = model.generateContent(prompt).text
            ?: throw IllegalStateException("Empty storyboard response.")
        val arr = JSONObject(rawJson).getJSONArray("scenes")
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            StoryboardScene(
                sceneNumber = obj.optInt("sceneNumber", i + 1),
                durationSec = obj.optInt("durationSec", 5),
                cameraMovement = obj.optString("cameraMovement", "Slow Dolly In"),
                visualPrompt = obj.optString("visualPrompt", ""),
                voiceOver = obj.optString("voiceOver", ""),
                subtitle = obj.optString("subtitle", "")
            )
        }
    }

    suspend fun generateAnimeSeriesBible(
        title: String,
        genre: String,
        premise: String,
        seasons: Int
    ): String {
        val prompt = """
            Act as a lead Japanese Anime Series Showrunner & Director.
            Create a complete original Japanese anime production bible for:
            Title: $title
            Genre: $genre
            Target Seasons: $seasons
            Core Premise: $premise

            Structure the output cleanly with sections:
            1. WORLD BUILDING & SETTING
            2. MAIN CHARACTERS (Hero, Rival/Ally, Villain with visual design DNA, relationships, and backstories)
            3. SEASON & EPISODE ARC BREAKDOWN (Season 1 to Season $seasons highlights + Episode 1 full scene breakdown)
            4. SAMPLE EPISODE 1 DIALOGUE SCRIPT
            5. OFFICIAL TRAILER SCRIPT & VOICE-OVER (with timing cues & subtitles)
            6. PRODUCTION TIMELINE (Pre-production, Key Animation, Voice Recording, Post)

            Ensure everything is 100% original creative writing.
        """.trimIndent()
        return generateText(prompt, "gemini-3.8-flash")
    }
}
