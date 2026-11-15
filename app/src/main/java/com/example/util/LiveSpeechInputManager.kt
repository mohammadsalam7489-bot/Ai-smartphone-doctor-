package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.data.ai.LanguageDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-app live SpeechRecognizer engine supporting:
 * - Immediate AI TTS barge-in interruption when the user initiates speaking
 * - Live partial transcript feedback
 * - Hindi ("hi-IN"), Hinglish ("hi-IN"), and English ("en-US") locales
 * - Automatic graceful fallback signaling when SpeechRecognizer is absent on a device
 */
class LiveSpeechInputManager(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _rmsLevelDb = MutableStateFlow(0f)
    val rmsLevelDb: StateFlow<Float> = _rmsLevelDb.asStateFlow()

    fun isAvailable(): Boolean = runCatching {
        SpeechRecognizer.isRecognitionAvailable(appContext)
    }.getOrDefault(false)

    fun startListening(
        language: String,
        onInterruptAiSpeech: () -> Unit,
        onFinalTranscript: (String) -> Unit,
        onErrorOrUnavailable: (shouldUseIntentFallback: Boolean, reason: String) -> Unit
    ) {
        // Immediate barge-in interruption so user never talks over AI response
        onInterruptAiSpeech()

        if (!isAvailable()) {
            _isListening.value = false
            onErrorOrUnavailable(true, "Inline SpeechRecognizer unavailable; opening system voice prompt.")
            return
        }

        mainHandler.post {
            try {
                runCatching { recognizer?.destroy() }
                val sr = SpeechRecognizer.createSpeechRecognizer(appContext)
                recognizer = sr

                val localeTag = when (LanguageDetector.normalizeUiLanguage(language)) {
                    LanguageDetector.LANG_HINDI,
                    LanguageDetector.LANG_HINGLISH -> "hi-IN"
                    else -> "en-US"
                }

                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _partialTranscript.value = ""
                    }

                    override fun onBeginningOfSpeech() {
                        // Ensure AI speech stays interrupted once user begins speaking
                        onInterruptAiSpeech()
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        _rmsLevelDb.value = rmsdB
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        _rmsLevelDb.value = 0f
                        val msg = mapRecognizerError(error)
                        val shouldFallback = error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                            error == SpeechRecognizer.ERROR_CLIENT
                        if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            onErrorOrUnavailable(shouldFallback, msg)
                        } else {
                            onErrorOrUnavailable(false, msg)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        _rmsLevelDb.value = 0f
                        val transcript = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()
                        _partialTranscript.value = transcript
                        if (transcript.isNotBlank()) {
                            onFinalTranscript(transcript)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()
                        if (partial.isNotBlank()) {
                            _partialTranscript.value = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                sr.startListening(intent)
                _isListening.value = true
            } catch (t: Throwable) {
                _isListening.value = false
                onErrorOrUnavailable(true, t.localizedMessage ?: "Speech recognizer error")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            runCatching { recognizer?.stopListening() }
            _isListening.value = false
            _rmsLevelDb.value = 0f
        }
    }

    fun destroy() {
        mainHandler.post {
            runCatching { recognizer?.destroy() }
            recognizer = null
            _isListening.value = false
            _partialTranscript.value = ""
            _rmsLevelDb.value = 0f
        }
    }

    private fun mapRecognizerError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Could not hear clearly. Please try speaking again."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during speech recognition."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for live voice capture."
        else -> "Voice recognition unavailable ($code)."
    }
}
