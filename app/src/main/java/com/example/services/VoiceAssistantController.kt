package com.example.services

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

enum class VoiceStatus {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

data class VoiceUiState(
    val status: VoiceStatus = VoiceStatus.IDLE,
    val partialTranscript: String = "",
    val rmsAmplitude: Float = 0f,
    val ttsReady: Boolean = false,
    val speechAvailable: Boolean = false,
    val availableVoices: List<String> = emptyList(),
    val activeVoiceLabel: String = "Female Default",
    val lastError: String? = null
)

class VoiceAssistantController(private val context: Context) {

    private val _state = MutableStateFlow(
        VoiceUiState(
            speechAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        )
    )
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechRate: Float = 1.0f
    private var speechPitch: Float = 1.12f
    private var preferredVoiceName: String = ""
    private var currentLocale: Locale = Locale.US

    init {
        tts = TextToSpeech(context.applicationContext) { initStatus ->
            if (initStatus == TextToSpeech.SUCCESS) {
                val engine = tts ?: return@TextToSpeech
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.value = _state.value.copy(status = VoiceStatus.SPEAKING, lastError = null)
                    }

                    override fun onDone(utteranceId: String?) {
                        if (_state.value.status == VoiceStatus.SPEAKING) {
                            _state.value = _state.value.copy(status = VoiceStatus.IDLE, rmsAmplitude = 0f)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _state.value = _state.value.copy(
                            status = VoiceStatus.IDLE,
                            lastError = "Speech playback encountered an audio error."
                        )
                    }
                })
                applyLocaleAndFemaleVoice(currentLocale, preferredVoiceName)
                val voices = engine.voices?.map { it.name }?.sorted()?.take(25) ?: emptyList()
                _state.value = _state.value.copy(ttsReady = true, availableVoices = voices)
            } else {
                _state.value = _state.value.copy(
                    ttsReady = false,
                    lastError = "Android Text-to-Speech engine unavailable on this device."
                )
            }
        }
    }

    fun configureVoice(languageTag: String, rate: Float, pitch: Float, voiceName: String) {
        currentLocale = Locale.forLanguageTag(languageTag)
        speechRate = rate.coerceIn(0.5f, 2.0f)
        speechPitch = pitch.coerceIn(0.7f, 1.5f)
        preferredVoiceName = voiceName
        applyLocaleAndFemaleVoice(currentLocale, voiceName)
    }

    private fun applyLocaleAndFemaleVoice(locale: Locale, explicitVoiceName: String) {
        val engine = tts ?: return
        engine.language = locale
        engine.setSpeechRate(speechRate)
        engine.setPitch(speechPitch)

        val voices = engine.voices.orEmpty()
        val chosen: Voice? = if (explicitVoiceName.isNotBlank()) {
            voices.firstOrNull { it.name == explicitVoiceName }
        } else {
            val langVoices = voices.filter {
                it.locale.language.equals(locale.language, ignoreCase = true)
            }
            // Prefer female-labeled voices for the Jarvis female voice assistant persona.
            langVoices.firstOrNull { v ->
                val lower = v.name.lowercase(Locale.ROOT)
                lower.contains("female") || lower.contains("sfg") || lower.contains("tpf") ||
                    lower.contains("iob") || lower.contains("x-hie") || lower.contains("x-j") ||
                    lower.contains("x-k")
            } ?: langVoices.firstOrNull()
        }

        if (chosen != null) {
            engine.voice = chosen
            _state.value = _state.value.copy(activeVoiceLabel = chosen.name)
        } else {
            _state.value = _state.value.copy(activeVoiceLabel = "${locale.displayLanguage} (Female Pitch)")
        }
    }

    fun speak(text: String) {
        val engine = tts ?: return
        if (!_state.value.ttsReady) return
        val cleanText = text
            .replace(Regex("```[\\s\\S]*?```"), "Code block omitted from voice playback.")
            .replace(Regex("[#*_`>]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(1800)
        if (cleanText.isBlank()) return
        engine.setSpeechRate(speechRate)
        engine.setPitch(speechPitch)
        engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stopSpeaking() {
        tts?.stop()
        if (_state.value.status == VoiceStatus.SPEAKING) {
            _state.value = _state.value.copy(status = VoiceStatus.IDLE, rmsAmplitude = 0f)
        }
    }

    fun setThinking(thinking: Boolean) {
        if (thinking) {
            _state.value = _state.value.copy(status = VoiceStatus.THINKING)
        } else if (_state.value.status == VoiceStatus.THINKING) {
            _state.value = _state.value.copy(status = VoiceStatus.IDLE)
        }
    }

    fun startListening(languageTag: String, onFinalResult: (String) -> Unit) {
        stopSpeaking()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = _state.value.copy(
                speechAvailable = false,
                status = VoiceStatus.IDLE,
                lastError = "Speech recognition service is not installed on this device."
            )
            return
        }

        stopListening()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = _state.value.copy(
                    status = VoiceStatus.LISTENING,
                    partialTranscript = "",
                    lastError = null
                )
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                _state.value = _state.value.copy(rmsAmplitude = normalized)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _state.value = _state.value.copy(rmsAmplitude = 0f)
            }

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network unavailable for speech recognition."
                    SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Tap mic to try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                    else -> "Voice recognition unavailable (code $error)."
                }
                _state.value = _state.value.copy(
                    status = VoiceStatus.IDLE,
                    rmsAmplitude = 0f,
                    lastError = message
                )
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val finalSpoken = matches?.firstOrNull().orEmpty().trim()
                _state.value = _state.value.copy(
                    status = VoiceStatus.IDLE,
                    partialTranscript = finalSpoken,
                    rmsAmplitude = 0f
                )
                if (finalSpoken.isNotBlank()) {
                    onFinalResult(finalSpoken)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (partial.isNotBlank()) {
                    _state.value = _state.value.copy(partialTranscript = partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer.startListening(intent)
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
        if (_state.value.status == VoiceStatus.LISTENING) {
            _state.value = _state.value.copy(status = VoiceStatus.IDLE, rmsAmplitude = 0f)
        }
    }

    fun release() {
        stopListening()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
